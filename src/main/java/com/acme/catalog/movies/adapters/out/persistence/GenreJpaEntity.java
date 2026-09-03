package com.acme.catalog.movies.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA row for a Genre. JPA annotations live only in this package — never on the domain. */
@Entity
@Table(name = "genres")
public class GenreJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  protected GenreJpaEntity() {}

  public GenreJpaEntity(UUID id, String name) {
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
