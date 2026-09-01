package com.acme.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Wraps the inbound request so every downstream reader of the {@value CorrelationIdFilter#HEADER}
 * header (including Spring MVC's binding of the generated controller's {@code UUID} request-header
 * parameter) sees the resolved, always-valid correlation id, not the raw client-supplied value.
 */
final class CorrelationIdRequestWrapper extends HttpServletRequestWrapper {

  private final String resolvedCorrelationId;

  CorrelationIdRequestWrapper(HttpServletRequest request, String resolvedCorrelationId) {
    super(request);
    this.resolvedCorrelationId = resolvedCorrelationId;
  }

  @Override
  public String getHeader(String name) {
    if (CorrelationIdFilter.HEADER.equalsIgnoreCase(name)) {
      return resolvedCorrelationId;
    }
    return super.getHeader(name);
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    if (CorrelationIdFilter.HEADER.equalsIgnoreCase(name)) {
      return Collections.enumeration(Collections.singletonList(resolvedCorrelationId));
    }
    return super.getHeaders(name);
  }
}
