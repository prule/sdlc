package com.acme.platform.availability.domain.model;

import java.util.Objects;

/** The result of an availability check (liveness only; see UC-000). */
public record Availability(AvailabilityStatus status) {

  public Availability {
    Objects.requireNonNull(status, "status must not be null");
  }
}
