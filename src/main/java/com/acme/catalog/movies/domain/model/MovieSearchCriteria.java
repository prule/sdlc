package com.acme.catalog.movies.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The criteria a movie search is asked with, all optional (design D3). {@code titleTerm} is blank-
 * normalized to empty but never trimmed; {@code genres} are held exactly as requested (the
 * application layer resolves them against the curated vocabulary); the release-year range is
 * validated here, because a reversed range is a domain-detected failure.
 */
public record MovieSearchCriteria(
    Optional<String> titleTerm,
    Set<String> genres,
    Optional<Integer> releaseYearFrom,
    Optional<Integer> releaseYearTo,
    Optional<Rating> minRating) {

  public MovieSearchCriteria {
    Objects.requireNonNull(titleTerm, "titleTerm must not be null");
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(releaseYearFrom, "releaseYearFrom must not be null");
    Objects.requireNonNull(releaseYearTo, "releaseYearTo must not be null");
    Objects.requireNonNull(minRating, "minRating must not be null");
    titleTerm = titleTerm.filter(term -> !term.isBlank());
    if (releaseYearFrom.isPresent()
        && releaseYearTo.isPresent()
        && releaseYearFrom.get() > releaseYearTo.get()) {
      throw new InvalidSearchCriterionException(SearchCriterion.RELEASE_YEAR_RANGE);
    }
  }

  public static MovieSearchCriteria of(
      Optional<String> titleTerm,
      Set<String> genres,
      Optional<Integer> releaseYearFrom,
      Optional<Integer> releaseYearTo,
      Optional<Rating> minRating) {
    return new MovieSearchCriteria(titleTerm, genres, releaseYearFrom, releaseYearTo, minRating);
  }
}
