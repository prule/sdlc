package com.acme.catalog.people.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.people.domain.model.Person;
import com.acme.common.test.PostgresIntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence adapter test against real Postgres (Testcontainers, never H2). Own fixtures,
 * independent of the demo seed.
 */
class PersonDetailPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private PersonDetailJpaRepository personDetailJpaRepository;

  @Autowired private PersonDetailPersistenceAdapter adapter;

  @Test
  void loadPerson_seededPerson_returnsItMappedToDomain() {
    UUID id = UUID.randomUUID();
    personDetailJpaRepository.save(new PersonDetailJpaEntity(id, "Ava Solano"));

    Optional<Person> result = adapter.loadPerson(id);

    assertThat(result).isPresent();
    Person person = result.orElseThrow();
    assertThat(person.id()).isEqualTo(id);
    assertThat(person.name()).isEqualTo("Ava Solano");
  }

  @Test
  void loadPerson_unknownId_returnsEmpty() {
    Optional<Person> result = adapter.loadPerson(UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  void loadPerson_personCreditedInNoMovies_isStillServedIndependentOfAnyCredits() {
    UUID id = UUID.randomUUID();
    personDetailJpaRepository.save(new PersonDetailJpaEntity(id, "Uncredited Person"));

    Optional<Person> result = adapter.loadPerson(id);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().name()).isEqualTo("Uncredited Person");
  }
}
