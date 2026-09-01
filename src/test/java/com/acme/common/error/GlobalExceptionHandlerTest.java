package com.acme.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/**
 * Direct unit tests of {@link GlobalExceptionHandler}'s handlers that are otherwise hard to trigger
 * end to end through the single (now correlation-id-normalized) {@code /ping} endpoint — closes the
 * task 9.5 test gap for the 400/405/415 rows and the {@link DomainException} fallback.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Mock private HttpServletRequest request;

  @Test
  void onBadRequest_mapsIllegalArgumentExceptionTo400WithBadRequestCode() {
    when(request.getRequestURI()).thenReturn("/api/v1/ping");

    ProblemDetail problem =
        handler.onBadRequest(new IllegalArgumentException("bad input"), request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getProperties()).containsEntry("code", "BAD_REQUEST");
    assertThat(problem.getProperties()).containsKey("correlationId");
  }

  @Test
  void onBadRequest_mapsUnreadableRequestBodyTo400() {
    when(request.getRequestURI()).thenReturn("/api/v1/ping");
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException(
            "malformed body", (org.springframework.http.HttpInputMessage) null);

    ProblemDetail problem = handler.onBadRequest(exception, request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getProperties()).containsEntry("code", "BAD_REQUEST");
  }

  @Test
  void onMethodNotSupported_mapsTo405() {
    when(request.getRequestURI()).thenReturn("/api/v1/ping");
    HttpRequestMethodNotSupportedException exception =
        new HttpRequestMethodNotSupportedException("POST");

    ProblemDetail problem = handler.onMethodNotSupported(exception, request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    assertThat(problem.getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED");
  }

  @Test
  void onMediaTypeNotSupported_mapsTo415() {
    when(request.getRequestURI()).thenReturn("/api/v1/ping");
    HttpMediaTypeNotSupportedException exception =
        new HttpMediaTypeNotSupportedException("unsupported content type");

    ProblemDetail problem = handler.onMediaTypeNotSupported(exception, request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    assertThat(problem.getProperties()).containsEntry("code", "UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void onDomainException_fallsBackToTheExceptionsOwnCodeAndABadRequestStatus() {
    when(request.getRequestURI()).thenReturn("/api/v1/ping");
    DomainException exception = new ConflictException("SOME_FUTURE_CODE", "future subtype");

    ProblemDetail problem = handler.onDomainException(exception, request);

    assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problem.getProperties()).containsEntry("code", "SOME_FUTURE_CODE");
  }
}
