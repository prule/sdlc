package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.InvalidCriteriaException.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MovieOrderTest {

  @ParameterizedTest
  @CsvSource({
    "title, TITLE_ASC",
    "-title, TITLE_DESC",
    "releaseYear, RELEASE_YEAR_ASC",
    "-releaseYear, RELEASE_YEAR_DESC",
    "rating, RATING_ASC",
    "-rating, RATING_DESC"
  })
  void parsesEverySupportedValue(String value, MovieOrder expected) {
    assertThat(MovieOrder.parse(value)).isEqualTo(expected);
  }

  @Test
  void defaultsToReleaseYearNewestFirst() {
    assertThat(MovieOrder.parse(null)).isEqualTo(MovieOrder.RELEASE_YEAR_DESC);
  }

  @ParameterizedTest
  @ValueSource(strings = {"popularity", "Title", "", "-", "+title", "title,desc"})
  void refusesAnyOtherValueNamingSortWithoutEchoingIt(String value) {
    assertThatThrownBy(() -> MovieOrder.parse(value))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex -> {
              assertThat(ex.violations()).extracting(Violation::field).containsExactly("sort");
              assertThat(ex.violations().getFirst().message()).doesNotContain("popularity");
            });
  }
}
