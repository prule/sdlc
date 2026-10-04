package com.acme.catalog.movies.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for the {@code genre} table (design D5). */
@Entity
@Table(name = "genre")
public class GenreJpaEntity {

  @Id private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  protected GenreJpaEntity() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
