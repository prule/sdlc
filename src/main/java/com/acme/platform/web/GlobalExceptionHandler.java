package com.acme.platform.web;

import com.acme.shared.domain.InvalidRequestException;
import com.acme.shared.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The only place in the service that maps a failure to an HTTP status and a body. Spring's base
 * class already resolves every standard MVC exception (404/405/406/415/400-family) to its correct
 * status; this class rebuilds the body from that resolved status via {@link ProblemFactory} so
 * every failure, however it was detected, produces the same uniform shape (design D3).
 *
 * <p>A {@code 400} caused by a single, named request parameter names it in {@code detail}, using
 * the name published in the interface description, never the supplied value (design D3 of
 * add-movie-search). A constraint violation is a client fault only when it comes from validating a
 * web handler's parameters; any other violation stays a {@code 500}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemFactory problemFactory;

  public GlobalExceptionHandler(ProblemFactory problemFactory) {
    this.problemFactory = problemFactory;
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    logFailure(ex, statusCode);
    ProblemDetail problem = problemFactory.create(statusCode);
    // Emit the classified kind's status, not the resolved one: statuses outside the closed kind
    // set (e.g. 409, 413, 503) collapse to 400/500, and the HTTP status must equal body.status.
    // Delegating to super keeps its committed-response guard.
    return super.handleExceptionInternal(
        ex, problem, headers, HttpStatusCode.valueOf(problem.getStatus()), request);
  }

  /** An ill-typed parameter value: names the parameter (design D3). */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
      return namedBadRequest(ex, mismatch.getName(), headers, request);
    }
    return super.handleTypeMismatch(ex, headers, status, request);
  }

  /** A missing required parameter: names the parameter (design D3). */
  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return namedBadRequest(ex, ex.getParameterName(), headers, request);
  }

  /**
   * Safety net for Spring MVC's built-in method validation (design D3): names the invalid parameter
   * with the lowest index, or gives the generic {@code 400} if it has no published name.
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Optional<String> name =
        ex.getParameterValidationResults().stream()
            .min(Comparator.comparingInt(r -> r.getMethodParameter().getParameterIndex()))
            .map(ParameterValidationResult::getMethodParameter)
            .flatMap(GlobalExceptionHandler::publishedName);
    if (name.isPresent()) {
      return namedBadRequest(ex, name.get(), headers, request);
    }
    return super.handleHandlerMethodValidationException(ex, headers, status, request);
  }

  /**
   * A request asked in a way a business rule does not allow (design D3). Names {@link
   * InvalidRequestException#field()}.
   */
  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex) {
    log.debug(
        "Client request failure [correlationId={}]", CorrelationId.current().orElse(null), ex);
    ProblemDetail problem = problemFactory.createBadRequest(ex.field());
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /**
   * Bean Validation on a handler's parameters, enforced by the AOP proxy of a {@code @Validated}
   * controller (the main path, design D3). Mapped to a named {@code 400} only when every violation
   * is on a method parameter of a {@code @RestController}; any other violation is a server fault
   * and goes to {@link #handleUnexpected}.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<ConstraintViolation<?>> violations = List.copyOf(ex.getConstraintViolations());
    if (violations.isEmpty()
        || !violations.stream().allMatch(GlobalExceptionHandler::isOnAControllerParameter)) {
      return handleUnexpected(ex, request);
    }
    ConstraintViolation<?> chosen =
        violations.stream()
            .min(
                Comparator.comparingInt(GlobalExceptionHandler::parameterIndex)
                    .thenComparing(
                        v ->
                            v.getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName()))
            .orElseThrow();
    log.debug(
        "Client request failure [correlationId={}]", CorrelationId.current().orElse(null), ex);
    ProblemDetail problem =
        publishedName(chosen)
            .map(problemFactory::createBadRequest)
            .orElseGet(() -> problemFactory.create(400));
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /**
   * A lookup found nothing (design D2/D3). Mapped to the platform's fixed {@code NOT_FOUND}
   * problem; the exception's message is never echoed to the client.
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex) {
    log.debug("Resource not found [correlationId={}]", CorrelationId.current().orElse(null), ex);
    ProblemDetail problem = problemFactory.create(404);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /** Catches anything the base class does not already resolve. Always 500. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error(
        "Unhandled server error [correlationId={}]", CorrelationId.current().orElse(null), ex);
    ProblemDetail problem = problemFactory.create(500);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  private ResponseEntity<Object> namedBadRequest(
      Exception ex, String parameterName, HttpHeaders headers, WebRequest request) {
    log.debug(
        "Client request failure [correlationId={}]", CorrelationId.current().orElse(null), ex);
    ProblemDetail problem = problemFactory.createBadRequest(parameterName);
    return super.handleExceptionInternal(
        ex, problem, headers, HttpStatusCode.valueOf(problem.getStatus()), request);
  }

  private void logFailure(Exception ex, HttpStatusCode statusCode) {
    String correlationId = CorrelationId.current().orElse(null);
    if (statusCode.is5xxServerError()) {
      log.error("Unhandled server error [correlationId={}]", correlationId, ex);
    } else {
      log.debug("Client request failure [correlationId={}]", correlationId, ex);
    }
  }

  private static boolean isOnAControllerParameter(ConstraintViolation<?> violation) {
    Class<?> rootBeanClass = violation.getRootBeanClass();
    if (rootBeanClass == null
        || !AnnotatedElementUtils.hasAnnotation(
            ClassUtils.getUserClass(rootBeanClass), RestController.class)) {
      return false;
    }
    return parameterNode(violation.getPropertyPath()).isPresent();
  }

  private static int parameterIndex(ConstraintViolation<?> violation) {
    return parameterNode(violation.getPropertyPath())
        .map(Path.ParameterNode::getParameterIndex)
        .orElse(Integer.MAX_VALUE);
  }

  private static Optional<Path.ParameterNode> parameterNode(Path path) {
    for (Path.Node node : path) {
      if (node.getKind() == ElementKind.PARAMETER) {
        return Optional.of(node.as(Path.ParameterNode.class));
      }
    }
    return Optional.empty();
  }

  private static Optional<Path.MethodNode> methodNode(Path path) {
    for (Path.Node node : path) {
      if (node.getKind() == ElementKind.METHOD) {
        return Optional.of(node.as(Path.MethodNode.class));
      }
    }
    return Optional.empty();
  }

  /**
   * Resolves the violated Java parameter to its published name by reading the {@code
   * RequestParam}/{@code PathVariable} annotation, searching interfaces too (the annotations live
   * on the generated API interface). Never falls back to the Java parameter name.
   */
  private static Optional<String> publishedName(ConstraintViolation<?> violation) {
    Optional<Path.MethodNode> methodNode = methodNode(violation.getPropertyPath());
    Optional<Path.ParameterNode> parameterNode = parameterNode(violation.getPropertyPath());
    if (methodNode.isEmpty() || parameterNode.isEmpty()) {
      return Optional.empty();
    }
    Class<?> beanClass = ClassUtils.getUserClass(violation.getRootBeanClass());
    Method method =
        ReflectionUtils.findMethod(
            beanClass,
            methodNode.get().getName(),
            methodNode.get().getParameterTypes().toArray(Class<?>[]::new));
    if (method == null) {
      return Optional.empty();
    }
    MethodParameter[] parameters = new AnnotatedMethod(method).getMethodParameters();
    int index = parameterNode.get().getParameterIndex();
    if (index < 0 || index >= parameters.length) {
      return Optional.empty();
    }
    return publishedName(parameters[index]);
  }

  private static Optional<String> publishedName(MethodParameter parameter) {
    RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
    if (requestParam != null) {
      return firstText(requestParam.name(), requestParam.value());
    }
    PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
    if (pathVariable != null) {
      return firstText(pathVariable.name(), pathVariable.value());
    }
    return Optional.empty();
  }

  /** The annotation's name; read both aliases, as the annotation may not be synthesized. */
  private static Optional<String> firstText(String name, String value) {
    if (StringUtils.hasText(name)) {
      return Optional.of(name);
    }
    return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
  }
}
