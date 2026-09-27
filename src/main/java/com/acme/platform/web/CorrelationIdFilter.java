package com.acme.platform.web;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id: honours a well-formed inbound {@code X-Correlation-Id},
 * otherwise generates one. Never rejects a request over this header. The id is echoed on the
 * response header, stored in a request attribute (for {@link CorrelationId} and the ERROR
 * dispatch), and put in the MDC for the duration of request handling so every log line carries it.
 *
 * <p>Runs on the ERROR dispatch too (see {@link #shouldNotFilterErrorDispatch()}), reusing the id
 * already stored in the request attribute rather than generating a new one, so a failure rendered
 * outside normal MVC handling still carries the original id in its header, body and log lines.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = resolveCorrelationId(request);
    request.setAttribute(CorrelationId.ATTRIBUTE_NAME, correlationId);
    response.setHeader(CorrelationId.HEADER_NAME, correlationId);
    MDC.put(CorrelationId.ATTRIBUTE_NAME, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.ATTRIBUTE_NAME);
    }
  }

  private static String resolveCorrelationId(HttpServletRequest request) {
    if (request.getDispatcherType() == DispatcherType.ERROR) {
      Object stored = request.getAttribute(CorrelationId.ATTRIBUTE_NAME);
      if (stored != null) {
        return stored.toString();
      }
    }

    String inbound = request.getHeader(CorrelationId.HEADER_NAME);
    if (inbound != null && isWellFormedUuid(inbound)) {
      return inbound;
    }
    return UUID.randomUUID().toString();
  }

  private static boolean isWellFormedUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
