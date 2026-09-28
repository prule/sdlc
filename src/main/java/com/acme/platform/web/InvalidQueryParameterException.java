package com.acme.platform.web;

/**
 * Raised by a controller to refuse a request with an invalid query parameter, once a business
 * (rather than bounds/type) validation failure is detected (design D2/D6). Platform-level, not a
 * {@link com.acme.shared.domain.DomainException}: capability-specific exceptions (for example
 * catalog's {@code InvalidSearchCriterionException}) are translated to this one at the web adapter,
 * so {@code GlobalExceptionHandler} stays independent of any capability.
 */
public final class InvalidQueryParameterException extends RuntimeException {

  private final String parameterName;

  public InvalidQueryParameterException(String parameterName) {
    super("Invalid query parameter: " + parameterName);
    this.parameterName = parameterName;
  }

  public String parameterName() {
    return parameterName;
  }
}
