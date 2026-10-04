package com.acme.catalog.movies.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A movie's stable, opaque catalog identifier. */
public record MovieId(UUID value) {

  public MovieId {
    Objects.requireNonNull(value, "value must not be null");
  }
}
