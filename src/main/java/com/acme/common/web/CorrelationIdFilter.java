package com.acme.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has a correlation id that is guaranteed to be a valid UUID (the contract
 * documented in the OpenAPI spec: {@code components/parameters/common.yaml#/CorrelationId} and
 * {@code Meta.correlationId} are both {@code format: uuid}).
 *
 * <p>Contract: if the inbound {@value #HEADER} header is present and parses as a UUID, it is
 * adopted verbatim. If it is absent, blank, or does not parse as a UUID, a fresh UUID is generated
 * instead (an invalid inbound value is silently replaced, not rejected — logged at DEBUG). Either
 * way, the resolved id is placed in the MDC (so it appears in every log line for the request),
 * echoed on the response header, and the downstream request is wrapped so any later {@code
 * X-Correlation-Id} header read (including Spring MVC's binding of the generated
 * {@code @RequestHeader UUID} controller parameter) sees the resolved, always-valid value — never
 * the raw invalid input. This keeps the correlation id normalization coherent with {@link
 * com.acme.common.error.GlobalExceptionHandler}'s general 400 handling: a genuinely malformed value
 * bound to a UUID-typed request parameter elsewhere still fails with 400, but the correlation id
 * specifically is corrected before any binding happens, so it can never cause an error response on
 * its own.
 *
 * <p>Runs with high precedence so the id is available to every downstream filter/handler.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = resolve(request.getHeader(HEADER));

    MDC.put(MDC_KEY, correlationId);
    response.setHeader(HEADER, correlationId);
    try {
      filterChain.doFilter(new CorrelationIdRequestWrapper(request, correlationId), response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static String resolve(String incoming) {
    if (incoming == null || incoming.isBlank()) {
      return newId();
    }
    try {
      UUID.fromString(incoming);
      return incoming;
    } catch (IllegalArgumentException e) {
      log.debug("Invalid inbound {} '{}' replaced with a generated id", HEADER, incoming);
      return newId();
    }
  }

  private static String newId() {
    return UUID.randomUUID().toString();
  }
}
