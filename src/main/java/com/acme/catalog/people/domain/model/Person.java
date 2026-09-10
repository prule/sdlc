package com.acme.catalog.people.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A person's core details in the {@code catalog/people} bounded context — identifier and name only,
 * deliberately minimal (no biographical fields, BR-3). No Spring/JPA/HAL imports — see
 * standards/clean-architecture.md.
 */
public record Person(UUID id, String name) {

  public Person {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }
}
