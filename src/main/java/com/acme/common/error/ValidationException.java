package com.acme.common.error;

import java.util.List;

/** Thrown when field-level validation fails outside of Bean Validation. Maps to HTTP 422. */
public final class ValidationException extends DomainException {

  private final List<FieldError> errors;

  public ValidationException(String message, List<FieldError> errors) {
    super("VALIDATION_FAILED", message);
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }

  /** A single field-level validation failure. */
  public record FieldError(String field, String message, String code) {}
}
