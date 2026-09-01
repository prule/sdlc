package com.acme.platform.health.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain value object representing the outcome of a liveness ping: the service's status and the
 * instant at which it was determined.
 */
public record PingStatus(String status, Instant timestamp) {

  public static final String OK = "ok";

  public PingStatus {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    if (!OK.equals(status)) {
      throw new IllegalArgumentException("status must be '" + OK + "', was: " + status);
    }
  }

  /** Creates a {@link PingStatus} representing a healthy service at the given instant. */
  public static PingStatus ok(Instant timestamp) {
    return new PingStatus(OK, timestamp);
  }
}
