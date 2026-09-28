package com.acme.shared.domain;

/**
 * Base for every business-rule violation raised by a domain or application layer. JDK-only: no
 * Spring, no JPA, no generated DTOs (standards/clean-architecture.md, standards/error-handling.md
 * §1). Carries a stable, machine-readable {@link #code()} separate from the human-readable message,
 * so {@code GlobalExceptionHandler} can map it to an HTTP status without inspecting the message
 * text.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
