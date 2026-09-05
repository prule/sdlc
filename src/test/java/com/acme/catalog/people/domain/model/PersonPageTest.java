package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonPageTest {

  private static Person person() {
    return new Person(new PersonId(UUID.randomUUID()), "Keanu Reeves");
  }

  @Test
  void constructor_createsAValidPage() {
    PersonPage page = new PersonPage(List.of(person()), 0, 20, 1, 1);

    assertThat(page.items()).hasSize(1);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  @Test
  void constructor_allowsEmptyItems() {
    PersonPage page = new PersonPage(List.of(), 0, 20, 0, 0);

    assertThat(page.items()).isEmpty();
  }

  @Test
  void constructor_rejectsNullItems() {
    assertThatThrownBy(() -> new PersonPage(null, 0, 20, 0, 0))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_defensivelyCopiesItems() {
    List<Person> mutable = new java.util.ArrayList<>();
    mutable.add(person());
    PersonPage page = new PersonPage(mutable, 0, 20, 1, 1);

    mutable.add(person());

    assertThat(page.items()).hasSize(1);
  }
}
