package com.acme.platform.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders, as problem+json, failures raised outside normal MVC handling (filter/container faults)
 * that the servlet container dispatches to {@code /error}. Because this bean exists, Boot's {@code
 * BasicErrorController} backs off ({@code @ConditionalOnMissingBean(ErrorController.class)} ), so
 * the whitelabel HTML error page is never rendered (design D3, finding 5).
 */
@RestController
public class ProblemErrorController implements ErrorController {

  private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);

  private final ProblemFactory problemFactory;

  public ProblemErrorController(ProblemFactory problemFactory) {
    this.problemFactory = problemFactory;
  }

  @RequestMapping("/error")
  public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
    int status = resolveStatus(request);
    Throwable cause = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    logFailure(status, cause);

    ProblemDetail problem = problemFactory.create(status);
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * The container always sets the error status on an ERROR dispatch. Its absence means a client
   * requested {@code /error} directly: nothing is offered there, so that is a 404, not a fault.
   */
  private static int resolveStatus(HttpServletRequest request) {
    Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    if (statusAttribute instanceof Integer status) {
      return status;
    }
    return 404;
  }

  private static void logFailure(int status, Throwable cause) {
    String correlationId = CorrelationId.current().orElse(null);
    if (status >= 500) {
      log.error(
          "Unhandled error rendered via /error [correlationId={}, status={}]",
          correlationId,
          status,
          cause);
    } else {
      log.debug(
          "Client request failure rendered via /error [correlationId={}, status={}]",
          correlationId,
          status,
          cause);
    }
  }
}
