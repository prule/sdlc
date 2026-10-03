package com.acme.platform.web;

import com.acme.platform.web.ProblemFactory.InvalidParam;
import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final String NOT_A_VALID_VALUE = "is not a valid value";
  private static final String REQUIRED = "is required";
  private static final String OUT_OF_RANGE = "is outside the allowed values";

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
    ProblemDetail problem = problemFactory.create(statusCode.value(), invalidParamsOf(ex));
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
   * A request asked in a way that isn't allowed (UC-002 BR-10, design D3/D4): {@code 400} naming
   * the offending parameters. The violation messages are fixed texts and never hold a value.
   */
  @ExceptionHandler(InvalidCriteriaException.class)
  public ResponseEntity<Object> handleInvalidCriteria(InvalidCriteriaException ex) {
    log.debug("Invalid criteria [correlationId={}]", CorrelationId.current().orElse(null), ex);
    List<InvalidParam> invalidParams =
        ex.violations().stream().map(v -> new InvalidParam(v.field(), v.message())).toList();
    ProblemDetail problem = problemFactory.create(400, invalidParams);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  /**
   * A declared parameter constraint failed through the AOP method-validation path (the generated
   * API interfaces are {@code @Validated}). A client fault, never a {@code 500} (design D4).
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
    log.debug(
        "Client request failure [correlationId={}]", CorrelationId.current().orElse(null), ex);
    List<InvalidParam> invalidParams =
        ex.getConstraintViolations().stream()
            .map(violation -> lastNodeName(violation.getPropertyPath()))
            .distinct()
            .map(field -> new InvalidParam(field, OUT_OF_RANGE))
            .toList();
    ProblemDetail problem = problemFactory.create(400, invalidParams);
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

  /**
   * The named parameters behind a framework-detected client fault, with a fixed reason per kind of
   * fault; empty when the fault is not attributable to a named parameter.
   */
  private static List<InvalidParam> invalidParamsOf(Exception ex) {
    return switch (ex) {
      case MethodArgumentTypeMismatchException mismatch ->
          List.of(new InvalidParam(mismatch.getName(), NOT_A_VALID_VALUE));
      case MissingServletRequestParameterException missing ->
          List.of(new InvalidParam(missing.getParameterName(), REQUIRED));
      case HandlerMethodValidationException validation ->
          validation.getParameterValidationResults().stream()
              .map(result -> parameterName(result.getMethodParameter()))
              .distinct()
              .map(field -> new InvalidParam(field, OUT_OF_RANGE))
              .toList();
      default -> List.of();
    };
  }

  private static String parameterName(MethodParameter parameter) {
    RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
    if (requestParam != null && !requestParam.name().isEmpty()) {
      return requestParam.name();
    }
    PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
    if (pathVariable != null && !pathVariable.name().isEmpty()) {
      return pathVariable.name();
    }
    return parameter.getParameterName();
  }

  private static String lastNodeName(Path path) {
    String name = null;
    for (Path.Node node : path) {
      name = node.getName();
    }
    return name;
  }

  private void logFailure(Exception ex, HttpStatusCode statusCode) {
    String correlationId = CorrelationId.current().orElse(null);
    if (statusCode.is5xxServerError()) {
      log.error("Unhandled server error [correlationId={}]", correlationId, ex);
    } else {
      log.debug("Client request failure [correlationId={}]", correlationId, ex);
    }
  }
}
