package com.acme.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
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

  /** Catches anything the base class does not already resolve. Always 500. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error(
        "Unhandled server error [correlationId={}]", CorrelationId.current().orElse(null), ex);
    ProblemDetail problem = problemFactory.create(500);
    return ResponseEntity.status(problem.getStatus()).body(problem);
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
