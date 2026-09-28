package com.acme.catalog.movies.domain.model;

/** A movie's curated runtime, in whole minutes. Always positive (BR-3). */
public record RuntimeMinutes(int value) {

  public RuntimeMinutes {
    if (value <= 0) {
      throw new IllegalArgumentException("value must be positive: " + value);
    }
  }
}
