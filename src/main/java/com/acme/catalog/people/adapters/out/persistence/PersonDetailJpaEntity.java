package com.acme.catalog.people.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Read-only JPA persistence model for a person's core details, mapping the existing {@code people}
 * table (created by {@code V3__credits.sql}, UC-003). Lives only in {@code
 * adapters/out/persistence} and is mapped to/from the domain {@link
 * com.acme.catalog.people.domain.model.Person} by {@link PersonDetailPersistenceAdapter} — never
 * exposed outside this package. Its own entity (not the {@code catalog.movies} slice's {@code
 * PersonJpaEntity}) so the {@code people} slice does not import another slice's adapter classes;
 * both read the same table read-only.
 */
@Entity
@Table(name = "people")
public class PersonDetailJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  protected PersonDetailJpaEntity() {}

  public PersonDetailJpaEntity(UUID id, String name) {
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
