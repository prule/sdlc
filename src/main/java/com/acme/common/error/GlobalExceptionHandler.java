package com.acme.common.error;

import com.acme.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single place that translates exceptions into RFC 7807 {@code Problem} responses. See
 * standards/error-handling.md §2-3 for the code&harr;status mapping. Never returns a stack trace,
 * SQL, or internal class name to the client; 5xx bodies get a generic message plus the correlation
 * id, with the real detail logged server-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail onNotFound(ResourceNotFoundException e, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, e.code(), e.getMessage(), request);
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail onConflict(ConflictException e, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, e.code(), e.getMessage(), request);
  }

  @ExceptionHandler(ValidationException.class)
  ProblemDetail onValidation(ValidationException e, HttpServletRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.UNPROCESSABLE_ENTITY, e.code(), e.getMessage(), request);
    problem.setProperty(
        "errors",
        e.errors().stream()
            .map(fe -> Map.of("field", fe.field(), "message", fe.message()))
            .toList());
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail onBeanValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
    ProblemDetail problem =
        problem(
            HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Validation failed.", request);
    problem.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList());
    return problem;
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail onNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request);
  }

  /**
   * Malformed request input that Spring MVC could not bind/parse: an illegal argument, a request
   * parameter/header that doesn't match its declared type, an unreadable/malformed request body, or
   * a missing required parameter. Client mistakes, not incidents — logged at WARN, not ERROR.
   */
  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class
  })
  ProblemDetail onBadRequest(Exception e, HttpServletRequest request) {
    log.warn(
        "Bad request [correlationId={}, path={}]: {}",
        CorrelationId.current(),
        request.getRequestURI(),
        e.getMessage());
    return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request was malformed.", request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ProblemDetail onMethodNotSupported(
      HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
    log.warn(
        "Method not allowed [correlationId={}, path={}]: {}",
        CorrelationId.current(),
        request.getRequestURI(),
        e.getMessage());
    return problem(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "This HTTP method is not supported for this resource.",
        request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ProblemDetail onMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
    log.warn(
        "Unsupported media type [correlationId={}, path={}]: {}",
        CorrelationId.current(),
        request.getRequestURI(),
        e.getMessage());
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The request's content type is not supported.",
        request);
  }

  /**
   * Fallback for any {@link DomainException} subtype without a more specific handler above, so a
   * future sealed subtype never falls through to the generic 500 catch-all.
   */
  @ExceptionHandler(DomainException.class)
  ProblemDetail onDomainException(DomainException e, HttpServletRequest request) {
    log.warn(
        "Domain exception [correlationId={}, code={}]: {}",
        CorrelationId.current(),
        e.code(),
        e.getMessage());
    return problem(HttpStatus.BAD_REQUEST, e.code(), e.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail onUnexpected(Exception e, HttpServletRequest request) {
    log.error("Unhandled error [correlationId={}]", CorrelationId.current(), e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred.",
        request);
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    problem.setProperty("code", code);
    problem.setProperty("correlationId", CorrelationId.current());
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
