package com.acme.catalog.movies.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A Movie's stable, opaque identifier. Wraps a {@link UUID} so the domain never confuses it with an
 * internal database id.
 */
public record MovieId(UUID value) {

  public MovieId {
    Objects.requireNonNull(value, "value must not be null");
  }

  public static MovieId of(UUID value) {
    return new MovieId(value);
  }
}
