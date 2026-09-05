package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersonSortTest {

  @Test
  void default_isNameAscending() {
    assertThat(PersonSort.DEFAULT.field()).isEqualTo(PersonSortField.NAME);
    assertThat(PersonSort.DEFAULT.direction()).isEqualTo(SortDirection.ASC);
  }

  @Test
  void constructor_createsAValidSort() {
    PersonSort sort = new PersonSort(PersonSortField.NAME, SortDirection.DESC);

    assertThat(sort.field()).isEqualTo(PersonSortField.NAME);
    assertThat(sort.direction()).isEqualTo(SortDirection.DESC);
  }

  @Test
  void constructor_rejectsNullField() {
    assertThatThrownBy(() -> new PersonSort(null, SortDirection.ASC))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_rejectsNullDirection() {
    assertThatThrownBy(() -> new PersonSort(PersonSortField.NAME, null))
        .isInstanceOf(NullPointerException.class);
  }
}
