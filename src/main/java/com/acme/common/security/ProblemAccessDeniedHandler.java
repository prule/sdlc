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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders a 403 {@code Problem} body for authorization failures raised in the Spring Security
 * filter chain, which {@code @RestControllerAdvice} cannot catch. See standards/error-handling.md
 * §3 and standards/security.md §6.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemAccessDeniedHandler.class);

  private final ObjectMapper objectMapper;

  public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException, ServletException {
    String correlationId = CorrelationId.current();
    log.warn(
        "Authorization failure [correlationId={}, path={}, sub={}]: {}",
        correlationId,
        request.getRequestURI(),
        currentSubject(),
        ex.getMessage());

    ProblemWriter.write(
        objectMapper,
        response,
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You are not allowed to perform this action.",
        request.getRequestURI(),
        correlationId);
  }

  private static String currentSubject() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null ? authentication.getName() : "unknown";
  }
}
