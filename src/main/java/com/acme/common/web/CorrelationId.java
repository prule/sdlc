package com.acme.common.web;

import java.util.UUID;
import org.slf4j.MDC;

/** Reads the current request's correlation id, set by {@link CorrelationIdFilter}. */
public final class CorrelationId {

  private CorrelationId() {}

  /**
   * Returns the correlation id for the current request, or a freshly generated one if none is set
   * (e.g. outside the filter chain, such as in a unit test).
   */
  public static String current() {
    String id = MDC.get(CorrelationIdFilter.MDC_KEY);
    return id != null ? id : UUID.randomUUID().toString();
  }
}
