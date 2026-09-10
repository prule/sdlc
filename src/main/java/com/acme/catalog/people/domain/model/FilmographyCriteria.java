package com.acme.catalog.people.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Optional, conjunctive filmography filters (BR-9): every field is optional; an entry matches only
 * if it satisfies every criterion that is present. Supplying none returns the whole filmography. No
 * Spring/JPA imports — see standards/clean-architecture.md.
 */
public record FilmographyCriteria(
    Optional<Capacity.Type> capacity, Optional<Integer> yearFrom, Optional<Integer> yearTo) {

  public FilmographyCriteria {
    Objects.requireNonNull(capacity, "capacity must not be null");
    Objects.requireNonNull(yearFrom, "yearFrom must not be null");
    Objects.requireNonNull(yearTo, "yearTo must not be null");
  }

  /** No filters — matches the person's whole filmography. */
  public static FilmographyCriteria none() {
    return new FilmographyCriteria(Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Creates criteria from raw, possibly-null values, accepting {@code null} for any absent field
   * instead of requiring the caller to wrap each in {@link Optional} themselves.
   */
  public static FilmographyCriteria of(Capacity.Type capacity, Integer yearFrom, Integer yearTo) {
    return new FilmographyCriteria(
        Optional.ofNullable(capacity), Optional.ofNullable(yearFrom), Optional.ofNullable(yearTo));
  }
}
