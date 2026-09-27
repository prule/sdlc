package com.acme.platform.web;

import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Accessor for the correlation id {@link CorrelationIdFilter} stored for the current request. Any
 * code handling the request (controllers, exception handlers, the error controller) can read the
 * same id without threading it through method signatures.
 */
public final class CorrelationId {

  /** The inbound/outbound HTTP header name. */
  public static final String HEADER_NAME = "X-Correlation-Id";

  /** The request attribute and MDC key under which the resolved id is stored. */
  public static final String ATTRIBUTE_NAME = "correlationId";

  private CorrelationId() {}

  /** Returns the id stored for the request currently being handled on this thread, if any. */
  public static Optional<String> current() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return Optional.empty();
    }
    Object value = attributes.getAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
    return Optional.ofNullable((String) value);
  }
}
