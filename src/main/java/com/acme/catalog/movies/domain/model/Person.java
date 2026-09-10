package com.acme.catalog.movies.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A person credited on a movie (as cast or crew). Named-only in this change — no onward link to a
 * standalone person resource, since none exists yet (CAT-003; see design.md decision 2). No
 * Spring/JPA imports — see standards/clean-architecture.md.
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
