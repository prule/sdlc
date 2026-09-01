package com.acme.common.error;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public final class ResourceNotFoundException extends DomainException {

  public ResourceNotFoundException(String message) {
    super("NOT_FOUND", message);
  }
}
