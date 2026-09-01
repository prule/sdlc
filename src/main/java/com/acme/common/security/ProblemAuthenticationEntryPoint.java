package com.acme.common.security;

import com.acme.common.web.CorrelationId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Renders a 401 {@code Problem} body for authentication failures raised in the Spring Security
 * filter chain (before the {@code DispatcherServlet}), which {@code @RestControllerAdvice} cannot
 * catch. See standards/error-handling.md §3 and standards/security.md §6.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log = LoggerFactory.getLogger(ProblemAuthenticationEntryPoint.class);

  private final ObjectMapper objectMapper;

  public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    String correlationId = CorrelationId.current();
    log.warn(
        "Authentication failure [correlationId={}, path={}]: {}",
        correlationId,
        request.getRequestURI(),
        authException.getMessage());

    ProblemWriter.write(
        objectMapper,
        response,
        HttpStatus.UNAUTHORIZED,
        "UNAUTHENTICATED",
        "Authentication is required to access this resource.",
        request.getRequestURI(),
        correlationId);
  }
}
