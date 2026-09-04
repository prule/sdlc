package com.acme.catalog.credits.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA row for a Person. JPA annotations live only in this package — never on the domain. */
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
