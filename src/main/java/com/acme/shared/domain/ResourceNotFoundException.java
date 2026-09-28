package com.acme.shared.domain;

/**
 * Raised when a lookup by identifier finds nothing, whatever the resource kind. Mapped to {@code
 * 404} {@code NOT_FOUND} by the platform's {@code GlobalExceptionHandler} (design D2/D3); the
 * message is never echoed to the client.
 */
public final class ResourceNotFoundException extends DomainException {

  public ResourceNotFoundException(String message) {
    super("NOT_FOUND", message);
  }
}
