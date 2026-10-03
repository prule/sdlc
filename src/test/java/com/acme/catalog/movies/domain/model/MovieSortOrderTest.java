package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.catalog.movies.domain.model.MovieSortOrder.SortField;
import com.acme.shared.domain.InvalidRequestException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MovieSortOrderTest {

  @ParameterizedTest
  @CsvSource({
    "title, TITLE, false",
    "-title, TITLE, true",
    "releaseYear, RELEASE_YEAR, false",
    "-releaseYear, RELEASE_YEAR, true",
    "rating, RATING, false",
    "-rating, RATING, true"
  })
  void parsesEachSupportedValue(String value, SortField field, boolean descending) {
    assertThat(MovieSortOrder.parse(Optional.of(value)))
        .isEqualTo(new MovieSortOrder(field, descending));
  }

  @Test
  void absentMeansReleaseYearNewestFirst() {
    assertThat(MovieSortOrder.parse(Optional.empty()))
        .isEqualTo(new MovieSortOrder(SortField.RELEASE_YEAR, true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"TITLE", "popularity", "", "-", "--title", "title ", "RELEASE_YEAR"})
  void anyOtherValueIsRefusedNamingSort(String value) {
    assertThatThrownBy(() -> MovieSortOrder.parse(Optional.of(value)))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("sort"));
  }
}
