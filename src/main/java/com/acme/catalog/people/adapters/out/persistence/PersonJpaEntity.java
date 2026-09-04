package com.acme.catalog.people.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA row for a Person. JPA annotations live only in this package — never on the domain. Named
 * {@code CatalogPeoplePerson} to avoid a Hibernate entity-name collision with {@code
 * catalog.credits.adapters.out.persistence.PersonJpaEntity} — a distinct entity that happens to map
 * the same {@code people} table under the same default (simple-class-name) entity name.
 */
@Entity(name = "CatalogPeoplePerson")
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
