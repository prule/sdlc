package com.acme.catalog.people.adapters.out.persistence;

import com.acme.catalog.people.application.port.out.LoadPersonByIdPort;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implements {@link LoadPersonByIdPort} against Postgres via {@link PersonJpaRepository}, mapping
 * the JPA entity to/from the domain {@link Person}. The domain is never annotated {@code @Entity}.
 */
@Component
public class PersonPersistenceAdapter implements LoadPersonByIdPort {

  private final PersonJpaRepository personJpaRepository;

  public PersonPersistenceAdapter(PersonJpaRepository personJpaRepository) {
    this.personJpaRepository = personJpaRepository;
  }

  @Override
  public Optional<Person> load(PersonId id) {
    return personJpaRepository.findById(id.value()).map(PersonPersistenceAdapter::toDomain);
  }

  static Person toDomain(PersonJpaEntity entity) {
    return new Person(new PersonId(entity.getId()), entity.getName());
  }
}
