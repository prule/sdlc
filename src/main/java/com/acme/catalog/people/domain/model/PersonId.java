package com.acme.catalog.people.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A Person's stable, opaque identifier. Wraps a {@link UUID} so the domain never confuses it with
 * an internal database id.
 */
public record PersonId(UUID value) {

  public PersonId {
    Objects.requireNonNull(value, "value must not be null");
  }

  public static PersonId of(UUID value) {
    return new PersonId(value);
  }
}
