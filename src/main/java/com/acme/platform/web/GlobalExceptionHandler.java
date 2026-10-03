package com.acme.platform.web;

import com.acme.shared.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The only place in the service that maps a failure to an HTTP status and a body. Spring's base
 * class already resolves every standard MVC exception (404/405/406/415/400-family) to its correct
 * status; this class rebuilds the body from that resolved status via {@link ProblemFactory} so
 * every failure, however it was detected, produces the same uniform shape (design D3).
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

  /**
   * A search or other collection refusal detected in a capability's controller (design D2/D6), for
   * example an unsupported {@code sort} or an unknown genre. Kept independent of any capability:
   * the controller translates its own domain exception to this platform one before it is thrown.
   */
  @ExceptionHandler(InvalidQueryParameterException.class)
  public ResponseEntity<Object> handleInvalidQueryParameter(InvalidQueryParameterException ex) {
    log.debug("Invalid query parameter [correlationId={}]", CorrelationId.current().orElse(null));
    ProblemDetail problem = problemFactory.invalidQueryParameter(ex.parameterName());
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /**
   * A bean-validation constraint failure, classified by ownership (design D2, {@code
   * standards/error-handling.md} §3). Only when every violation is on the handling method's own
   * parameters is it a client fault ({@code 400}); anything else (a result constraint, a called
   * component's constraint, or a mix) is a server-side fault ({@code 500}).
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(
      ConstraintViolationException ex, @Nullable HandlerMethod handlerMethod) {
    // No HandlerMethod: the violation did not come from a request handler.
    if (handlerMethod == null) {
      return internalError(ex);
    }

    // No violations at all names no client input, so it cannot be a client fault (allMatch would
    // be vacuously true on an empty set).
    Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
    if (violations == null
        || violations.isEmpty()
        || !violations.stream().allMatch(v -> isHandlerParameterViolation(v, handlerMethod))) {
      return internalError(ex);
    }

    log.debug("Invalid query parameter [correlationId={}]", CorrelationId.current().orElse(null));

    // The lowest-index violated parameter whose @RequestParam name resolves.
    Optional<String> parameterName =
        violations.stream()
            .map(violation -> requestParamName(violation, handlerMethod))
            .flatMap(Optional::stream)
            .min(Comparator.comparingInt(NamedParameter::index))
            .map(NamedParameter::name);

    ProblemDetail problem =
        parameterName
            .map(problemFactory::invalidQueryParameter)
            // No @RequestParam name resolves (e.g. a path variable): the generic 400.
            .orElseGet(() -> problemFactory.create(400));
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /**
   * Overridden so a query-parameter type mismatch (for example {@code page=abc}) names the
   * parameter, the same as a bounds violation (design D2). Path variables (for example UC-001's
   * {@code id}) keep the base class's generic detail.
   */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    logFailure(ex, status);
    if (ex instanceof MethodArgumentTypeMismatchException typeMismatch) {
      Optional<String> name = requestParamName(typeMismatch.getParameter());
      if (name.isPresent()) {
        ProblemDetail problem = problemFactory.invalidQueryParameter(name.get());
        return ResponseEntity.status(problem.getStatus()).body(problem);
      }
    }
    ProblemDetail problem = problemFactory.create(status);
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

  private ResponseEntity<Object> internalError(Exception ex) {
    log.error(
        "Unhandled server error [correlationId={}]", CorrelationId.current().orElse(null), ex);
    ProblemDetail problem = problemFactory.create(500);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /**
   * A violation is on one of {@code handlerMethod}'s own parameters only if all hold (design D2):
   * the violation's root bean class is assignable to the handler's bean type; the property path's
   * first node is a {@code METHOD} node whose name and parameter types match the handler's method;
   * and the second node is a {@code PARAMETER} node. Deeper nodes (for example a container element
   * of a {@code List} parameter) are still on that parameter.
   */
  private static boolean isHandlerParameterViolation(
      ConstraintViolation<?> violation, HandlerMethod handlerMethod) {
    if (!handlerMethod.getBeanType().isAssignableFrom(violation.getRootBeanClass())) {
      return false;
    }
    Iterator<Path.Node> nodes = violation.getPropertyPath().iterator();
    if (!nodes.hasNext()) {
      return false;
    }
    Path.Node first = nodes.next();
    if (first.getKind() != ElementKind.METHOD) {
      return false;
    }
    Path.MethodNode methodNode = first.as(Path.MethodNode.class);
    boolean sameMethod =
        methodNode.getName().equals(handlerMethod.getMethod().getName())
            && methodNode
                .getParameterTypes()
                .equals(List.of(handlerMethod.getMethod().getParameterTypes()));
    return sameMethod && nodes.hasNext() && nodes.next().getKind() == ElementKind.PARAMETER;
  }

  /**
   * The {@code @RequestParam} name of a handler-parameter violation's {@code PARAMETER} node, if it
   * declares one (design D2). The Java argument name is never used.
   */
  private static Optional<NamedParameter> requestParamName(
      ConstraintViolation<?> violation, HandlerMethod handlerMethod) {
    Iterator<Path.Node> nodes = violation.getPropertyPath().iterator();
    nodes.next(); // the METHOD node, already matched by isHandlerParameterViolation
    int index = nodes.next().as(Path.ParameterNode.class).getParameterIndex();
    MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
    if (index < 0 || index >= methodParameters.length) {
      return Optional.empty();
    }
    return requestParamName(methodParameters[index]).map(name -> new NamedParameter(index, name));
  }

  /**
   * The query name declared by the parameter's {@code @RequestParam} ({@code name} or its alias
   * {@code value}). Empty when there is no {@code @RequestParam} or it declares no name: the Java
   * argument name is never used (design D2).
   */
  private static Optional<String> requestParamName(MethodParameter parameter) {
    RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
    if (requestParam == null) {
      return Optional.empty();
    }
    String name = requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
    return name.isEmpty() ? Optional.empty() : Optional.of(name);
  }

  private record NamedParameter(int index, String name) {}

  private void logFailure(Exception ex, HttpStatusCode statusCode) {
    String correlationId = CorrelationId.current().orElse(null);
    if (statusCode.is5xxServerError()) {
      log.error("Unhandled server error [correlationId={}]", correlationId, ex);
    } else {
      log.debug("Client request failure [correlationId={}]", correlationId, ex);
    }
  }
}
