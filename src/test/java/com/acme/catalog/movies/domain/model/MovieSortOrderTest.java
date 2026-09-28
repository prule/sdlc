package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link MovieSortOrder#parse} (design D3, task 3.3). */
class MovieSortOrderTest {

  @ParameterizedTest
  @CsvSource({
    "title,TITLE_ASC",
    "-title,TITLE_DESC",
    "releaseYear,RELEASE_YEAR_ASC",
    "-releaseYear,RELEASE_YEAR_DESC",
    "rating,RATING_ASC",
    "-rating,RATING_DESC"
  })
  void parsesEachSupportedValue(String value, MovieSortOrder expected) {
    assertThat(MovieSortOrder.parse(Optional.of(value))).isEqualTo(expected);
  }

  @Test
  void absentGivesDefault() {
    assertThat(MovieSortOrder.parse(Optional.empty())).isEqualTo(MovieSortOrder.DEFAULT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Title", "synopsis"})
  void unsupportedValuesThrow(String value) {
    assertThatThrownBy(() -> MovieSortOrder.parse(Optional.of(value)))
        .isInstanceOf(InvalidSearchCriterionException.class)
        .extracting(ex -> ((InvalidSearchCriterionException) ex).criterion())
        .isEqualTo(SearchCriterion.SORT);
  }
}
