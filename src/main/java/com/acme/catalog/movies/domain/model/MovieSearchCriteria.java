package com.acme.catalog.movies.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Optional, conjunctive search/browse criteria for the movie catalog. Every field is optional; a
 * movie matches only if it satisfies every criterion that is present. No Spring/JPA imports — see
 * standards/clean-architecture.md.
 */
public record MovieSearchCriteria(
    Optional<String> title,
    Set<Genre> genres,
    Optional<Integer> releaseYearFrom,
    Optional<Integer> releaseYearTo,
    Optional<BigDecimal> minRating) {

  public MovieSearchCriteria {
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(releaseYearFrom, "releaseYearFrom must not be null");
    Objects.requireNonNull(releaseYearTo, "releaseYearTo must not be null");
    Objects.requireNonNull(minRating, "minRating must not be null");
    genres = Set.copyOf(genres);
  }

  /** An empty set of criteria — matches every movie in the catalog. */
  public static MovieSearchCriteria none() {
    return new MovieSearchCriteria(
        Optional.empty(), Set.of(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Creates criteria from raw, possibly-null/empty values, accepting {@code null} for any absent
   * field/collection instead of requiring the caller to wrap each in {@link Optional} themselves.
   */
  public static MovieSearchCriteria of(
      String title,
      Set<Genre> genres,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating) {
    return new MovieSearchCriteria(
        Optional.ofNullable(title).filter(t -> !t.isBlank()),
        genres == null ? Set.of() : Set.copyOf(genres),
        Optional.ofNullable(releaseYearFrom),
        Optional.ofNullable(releaseYearTo),
        Optional.ofNullable(minRating));
  }
}
