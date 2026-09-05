package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PersonSearchCriteriaTest {

  @Test
  void none_hasNoNameFilter() {
    assertThat(PersonSearchCriteria.NONE.name()).isEmpty();
  }

  @Test
  void constructor_acceptsAPresentName() {
    PersonSearchCriteria criteria = new PersonSearchCriteria(Optional.of("keanu"));

    assertThat(criteria.name()).contains("keanu");
  }

  @Test
  void constructor_rejectsNullNameOptional() {
    assertThatThrownBy(() -> new PersonSearchCriteria(null))
        .isInstanceOf(NullPointerException.class);
  }
}
