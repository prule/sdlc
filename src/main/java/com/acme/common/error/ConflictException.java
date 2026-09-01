package com.acme.common.error;

/** Thrown on a state/uniqueness conflict. Maps to HTTP 409. */
public final class ConflictException extends DomainException {

  public ConflictException(String code, String message) {
    super(code, message);
  }
}
