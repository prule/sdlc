package com.acme.catalog.credits.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A Person's stable, opaque identifier. Wraps a {@link UUID} so the domain never confuses it with
 * an internal database id. There is no {@code /people/{id}} endpoint yet, so this id is never
 * addressed as a resource — see {@link Person}.
 */
public record PersonId(UUID value) {

  public PersonId {
    Objects.requireNonNull(value, "value must not be null");
  }

  public static PersonId of(UUID value) {
    return new PersonId(value);
  }
}
