package com.acme.common.error;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public final class ResourceNotFoundException extends DomainException {

  public ResourceNotFoundException(String message) {
    super("NOT_FOUND", message);
  }

  /**
   * Raised with a more specific, stable code than the generic {@code NOT_FOUND} (e.g. {@code
   * MOVIE_NOT_FOUND}), for callers that want a resource-specific machine code in the {@code
   * Problem} response while still mapping to HTTP 404.
   */
  public ResourceNotFoundException(String code, String message) {
    super(code, message);
  }
}
