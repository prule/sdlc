package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RatingTest {

  @Test
  void acceptsAMidRangeValue() {
    Rating rating = new Rating(new BigDecimal("4.5"));

    assertThat(rating.value()).isEqualTo(new BigDecimal("4.5"));
  }

  @Test
  void acceptsTheLowerBoundary() {
    Rating rating = new Rating(BigDecimal.ZERO);

    assertThat(rating.value()).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void acceptsTheUpperBoundary() {
    Rating rating = new Rating(new BigDecimal("5"));

    assertThat(rating.value()).isEqualTo(new BigDecimal("5"));
  }

  @Test
  void rejectsJustBelowTheLowerBoundary() {
    assertThatThrownBy(() -> new Rating(new BigDecimal("-0.1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsJustAboveTheUpperBoundary() {
    assertThatThrownBy(() -> new Rating(new BigDecimal("5.1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsANullValue() {
    assertThatThrownBy(() -> new Rating(null)).isInstanceOf(NullPointerException.class);
  }
}
