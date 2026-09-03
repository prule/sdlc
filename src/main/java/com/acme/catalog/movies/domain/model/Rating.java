package com.acme.catalog.movies.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A Movie's curated aggregate rating: a single score on a 0-5 star scale (no vote count). Curated,
 * not user-submitted.
 */
public record Rating(BigDecimal score) {

  private static final BigDecimal MIN = BigDecimal.ZERO;
  private static final BigDecimal MAX = BigDecimal.valueOf(5);

  public Rating {
    Objects.requireNonNull(score, "score must not be null");
    if (score.compareTo(MIN) < 0 || score.compareTo(MAX) > 0) {
      throw new IllegalArgumentException("score must be between 0 and 5, was: " + score);
    }
  }
}
