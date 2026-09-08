package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link MovieSort#parse(String)}. */
class MovieSortTest {

  @Test
  void parse_supportedFieldsAndDirections_areAccepted() {
    assertThat(MovieSort.parse("title,asc"))
        .isEqualTo(new MovieSort(MovieSort.Field.TITLE, MovieSort.Direction.ASC));
    assertThat(MovieSort.parse("title,desc"))
        .isEqualTo(new MovieSort(MovieSort.Field.TITLE, MovieSort.Direction.DESC));
    assertThat(MovieSort.parse("releaseYear,asc"))
        .isEqualTo(new MovieSort(MovieSort.Field.RELEASE_YEAR, MovieSort.Direction.ASC));
    assertThat(MovieSort.parse("releaseYear,desc"))
        .isEqualTo(new MovieSort(MovieSort.Field.RELEASE_YEAR, MovieSort.Direction.DESC));
    assertThat(MovieSort.parse("rating,asc"))
        .isEqualTo(new MovieSort(MovieSort.Field.RATING, MovieSort.Direction.ASC));
    assertThat(MovieSort.parse("rating,desc"))
        .isEqualTo(new MovieSort(MovieSort.Field.RATING, MovieSort.Direction.DESC));
  }

  @Test
  void parse_isCaseInsensitive() {
    assertThat(MovieSort.parse("TITLE,ASC"))
        .isEqualTo(new MovieSort(MovieSort.Field.TITLE, MovieSort.Direction.ASC));
  }

  @Test
  void parse_nullOrBlank_returnsTheDefault() {
    assertThat(MovieSort.parse(null)).isEqualTo(MovieSort.defaultSort());
    assertThat(MovieSort.parse("")).isEqualTo(MovieSort.defaultSort());
  }

  @Test
  void defaultSort_isReleaseYearDescending() {
    assertThat(MovieSort.defaultSort())
        .isEqualTo(new MovieSort(MovieSort.Field.RELEASE_YEAR, MovieSort.Direction.DESC));
  }

  @Test
  void parse_unsupportedField_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> MovieSort.parse("popularity,asc"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parse_unsupportedDirection_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> MovieSort.parse("title,sideways"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parse_malformedValue_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> MovieSort.parse("title")).isInstanceOf(IllegalArgumentException.class);
  }
}
