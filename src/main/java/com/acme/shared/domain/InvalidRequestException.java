package com.acme.shared.domain;

import java.util.Objects;

/**
 * Raised when a request is asked in a way a business rule does not allow, such as an unknown sort
 * order or a reversed range. Carries {@link #field()}, the published name of the request element at
 * fault, so the platform can name it in a {@code 400 BAD_REQUEST} problem without ever echoing the
 * supplied value (design D3). JDK-only.
 */
public final class InvalidRequestException extends DomainException {

  private final String field;

  public InvalidRequestException(String field) {
    super("BAD_REQUEST", "Invalid request element: " + Objects.requireNonNull(field, "field"));
    this.field = field;
  }

  /** The published name of the request element at fault. Always set by code, never by input. */
  public String field() {
    return field;
  }
}
