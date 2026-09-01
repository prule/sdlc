package com.acme.common.error;

/**
 * Base of the sealed exception taxonomy for business-rule violations. Carries a stable, machine
 * readable {@code code} (distinct from the human-readable message) that the global exception
 * handler maps to an HTTP status and surfaces in the {@code Problem} response body.
 */
public abstract sealed class DomainException extends RuntimeException
    permits ResourceNotFoundException, ValidationException, ConflictException {

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
