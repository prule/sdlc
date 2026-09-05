package com.acme.catalog.people.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * The filter a person search may be narrowed by. {@code name} is the only supported filter — no
 * role, department, known-for, or has-credits filter exists at the domain level. No Spring/JPA
 * imports — pure domain.
 */
public record PersonSearchCriteria(Optional<String> name) {

  public static final PersonSearchCriteria NONE = new PersonSearchCriteria(Optional.empty());

  public PersonSearchCriteria {
    Objects.requireNonNull(name, "name must not be null (use Optional.empty())");
  }
}
