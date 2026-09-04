package com.acme.catalog.people.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.test.PostgresIntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres, no H2) test for {@link PersonPersistenceAdapter}. Inserts its own
 * fixtures — never relies on the demo seed.
 */
@Transactional
class PersonPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private PersonJpaRepository personJpaRepository;
  @Autowired private PersonPersistenceAdapter personPersistenceAdapter;

  @Test
  void load_returnsThePersonWithFieldsMapped_whenFound() {
    UUID personId = UUID.randomUUID();
    personJpaRepository.save(new PersonJpaEntity(personId, "Keanu Reeves"));

    Optional<Person> result = personPersistenceAdapter.load(new PersonId(personId));

    assertThat(result).isPresent();
    Person person = result.orElseThrow();
    assertThat(person.id()).isEqualTo(new PersonId(personId));
    assertThat(person.name()).isEqualTo("Keanu Reeves");
  }

  @Test
  void load_returnsEmpty_whenNoPersonForId() {
    Optional<Person> result = personPersistenceAdapter.load(new PersonId(UUID.randomUUID()));

    assertThat(result).isEmpty();
  }
}
