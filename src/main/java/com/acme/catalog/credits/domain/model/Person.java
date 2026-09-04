package com.acme.catalog.credits.domain.model;

import java.util.Objects;

/**
 * An individual who worked on a Movie (actor, director, writer, …), exposed as a small
 * identity+label value inline on a {@link Credit} — not a full, independently addressable
 * aggregate. No {@code /people/{id}} endpoint exists; a Person is never linked to itself.
 */
public record Person(PersonId id, String name) {

  public Person {
    Objects.requireNonNull(id, "id must not be null");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }
}
