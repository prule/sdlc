package com.acme.catalog.movies.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** A movie's curated aggregate rating: a 0-to-5-star score, curated to one decimal place (BR-3). */
public record Rating(BigDecimal value) {

  private static final BigDecimal MIN = BigDecimal.ZERO;
  private static final BigDecimal MAX = new BigDecimal("5");

  public Rating {
    Objects.requireNonNull(value, "value must not be null");
    if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
      throw new IllegalArgumentException("value must be between 0 and 5 inclusive: " + value);
    }
  }
}
