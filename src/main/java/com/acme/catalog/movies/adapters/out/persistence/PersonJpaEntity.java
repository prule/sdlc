package com.acme.catalog.movies.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA persistence model for a person credited on a movie. Lives only in {@code
 * adapters/out/persistence} and is mapped to/from the domain {@link
 * com.acme.catalog.movies.domain.model.Person} by {@link MovieCreditsPersistenceAdapter} — never
 * exposed outside this package.
 */
@Entity
@Table(name = "people")
public class PersonJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  protected PersonJpaEntity() {}

  public PersonJpaEntity(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
