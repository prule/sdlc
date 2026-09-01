package com.acme.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes an RFC 7807 {@code Problem} body directly to the servlet response. Used by the security
 * filter-chain error handlers ({@link ProblemAuthenticationEntryPoint}, {@link
 * ProblemAccessDeniedHandler}), which run before the {@code DispatcherServlet} and so cannot rely
 * on {@code @RestControllerAdvice}.
 */
final class ProblemWriter {

  private ProblemWriter() {}

  static void write(
      ObjectMapper objectMapper,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail,
      String instance,
      String correlationId)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", URI.create("about:blank").toString());
    body.put("title", status.getReasonPhrase());
    body.put("status", status.value());
    body.put("detail", detail);
    body.put("instance", instance);
    body.put("code", code);
    body.put("correlationId", correlationId);

    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
