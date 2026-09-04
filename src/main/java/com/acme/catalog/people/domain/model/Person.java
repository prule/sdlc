package com.acme.catalog.people.domain.model;

import java.util.Objects;

/**
 * The Person aggregate: an individual who worked on movies. Carries its stable opaque {@link
 * PersonId} and name. No Spring/JPA/HATEOAS imports — pure domain.
 */
public record Person(PersonId id, String name) {

  public Person {
    Objects.requireNonNull(id, "id must not be null");
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }
}
