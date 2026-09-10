package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Person} value object's validation. */
class PersonTest {

  @Test
  void withIdAndName_isAccepted() {
    UUID id = UUID.randomUUID();

    Person person = new Person(id, "Ava Solano");

    assertThat(person.id()).isEqualTo(id);
    assertThat(person.name()).isEqualTo("Ava Solano");
  }

  @Test
  void withNullId_throwsNullPointerException() {
    assertThatThrownBy(() -> new Person(null, "Ava Solano"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void withNullName_throwsNullPointerException() {
    assertThatThrownBy(() -> new Person(UUID.randomUUID(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void withBlankName_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new Person(UUID.randomUUID(), "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }
}
