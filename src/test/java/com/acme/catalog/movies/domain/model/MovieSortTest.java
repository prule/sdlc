package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MovieSortTest {

  @Test
  void default_isReleaseYearDescending() {
    assertThat(MovieSort.DEFAULT.field()).isEqualTo(MovieSortField.RELEASE_YEAR);
    assertThat(MovieSort.DEFAULT.direction()).isEqualTo(SortDirection.DESC);
  }

  @Test
  void constructor_createsAValidSort() {
    MovieSort sort = new MovieSort(MovieSortField.TITLE, SortDirection.ASC);

    assertThat(sort.field()).isEqualTo(MovieSortField.TITLE);
    assertThat(sort.direction()).isEqualTo(SortDirection.ASC);
  }

  @Test
  void constructor_rejectsNullField() {
    assertThatThrownBy(() -> new MovieSort(null, SortDirection.ASC))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_rejectsNullDirection() {
    assertThatThrownBy(() -> new MovieSort(MovieSortField.TITLE, null))
        .isInstanceOf(NullPointerException.class);
  }
}
