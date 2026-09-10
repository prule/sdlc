package com.acme.catalog.people.adapters.out.persistence;

import com.acme.catalog.people.application.port.out.LoadPersonPort;
import com.acme.catalog.people.domain.model.Person;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link LoadPersonPort}, mapping the JPA entity to/from the domain {@link Person}. Uses
 * a Spring Data derived query ({@code findById}) — no native SQL, so the CLAUDE.md native-query
 * UUID-portability rule does not apply here.
 */
@Component
public class PersonDetailPersistenceAdapter implements LoadPersonPort {

  private final PersonDetailJpaRepository personDetailJpaRepository;

  public PersonDetailPersistenceAdapter(PersonDetailJpaRepository personDetailJpaRepository) {
    this.personDetailJpaRepository = personDetailJpaRepository;
  }

  @Override
  public Optional<Person> loadPerson(UUID personId) {
    return personDetailJpaRepository
        .findById(personId)
        .map(PersonDetailPersistenceAdapter::toDomain);
  }

  private static Person toDomain(PersonDetailJpaEntity entity) {
    return new Person(entity.getId(), entity.getName());
  }
}
