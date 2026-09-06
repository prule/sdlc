package com.acme.catalog.movies.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** A movie's aggregate rating on a 0–5 star scale. */
public record Rating(BigDecimal value) {

  public Rating {
    Objects.requireNonNull(value, "value must not be null");
    if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(5)) > 0) {
      throw new IllegalArgumentException("rating must be between 0 and 5 inclusive: " + value);
    }
  }
}
