package com.acme.shared.domain;

import java.util.List;
import java.util.Objects;

/**
 * Raised when a request is asked in a way that isn't allowed (UC-002 BR-10), before any data is
 * read. Each {@link Violation} names the offending criterion by the same name the interface
 * description gives its parameter, with a fixed reason that never contains the supplied value.
 * Mapped to {@code 400} {@code BAD_REQUEST} with an {@code errors} list by the platform's {@code
 * GlobalExceptionHandler} (design D3/D4).
 */
public final class InvalidCriteriaException extends DomainException {

  private final List<Violation> violations;

  public InvalidCriteriaException(List<Violation> violations) {
    super("BAD_REQUEST", "Request criteria are not allowed: " + fieldsOf(violations));
    if (violations.isEmpty()) {
      throw new IllegalArgumentException("violations must not be empty");
    }
    this.violations = List.copyOf(violations);
  }

  public InvalidCriteriaException(String field, String message) {
    this(List.of(new Violation(field, message)));
  }

  public List<Violation> violations() {
    return violations;
  }

  private static List<String> fieldsOf(List<Violation> violations) {
    return violations.stream().map(Violation::field).toList();
  }

  /** One offending criterion and a fixed, value-free reason. */
  public record Violation(String field, String message) {

    public Violation {
      Objects.requireNonNull(field, "field must not be null");
      Objects.requireNonNull(message, "message must not be null");
    }
  }
}
