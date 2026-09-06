package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Rating} value object's 0–5 bounds. */
class RatingTest {

  @Test
  void withValueWithinBounds_isAccepted() {
    Rating rating = new Rating(BigDecimal.valueOf(3.5));

    assertThat(rating.value()).isEqualByComparingTo(BigDecimal.valueOf(3.5));
  }

  @Test
  void withValueAtLowerBound_isAccepted() {
    Rating rating = new Rating(BigDecimal.ZERO);

    assertThat(rating.value()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void withValueAtUpperBound_isAccepted() {
    Rating rating = new Rating(BigDecimal.valueOf(5));

    assertThat(rating.value()).isEqualByComparingTo(BigDecimal.valueOf(5));
  }

  @Test
  void withValueBelowLowerBound_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new Rating(BigDecimal.valueOf(-0.1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withValueAboveUpperBound_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new Rating(BigDecimal.valueOf(5.1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withNullValue_throwsNullPointerException() {
    assertThatThrownBy(() -> new Rating(null)).isInstanceOf(NullPointerException.class);
  }
}
