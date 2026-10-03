package com.acme.catalog.movies.domain.model;

import com.acme.shared.domain.InvalidRequestException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a movie search matches on (UC-002). Every criterion is optional and they combine with AND. A
 * blank title term and blank genre names mean "no criterion" and are dropped; the title term is
 * otherwise kept exactly as given (not trimmed). The factory checks {@code minRating} (0–5
 * inclusive) and then the release-year range (lower bound not after the upper), in that order
 * (design D2/D4).
 */
public record MovieSearchCriteria(
    Optional<String> titleTerm,
    Set<String> genres,
    Optional<Integer> releaseYearFrom,
    Optional<Integer> releaseYearTo,
    Optional<BigDecimal> minRating) {

  private static final BigDecimal MIN_RATING = BigDecimal.ZERO;
  private static final BigDecimal MAX_RATING = new BigDecimal("5");

  public MovieSearchCriteria {
    Objects.requireNonNull(titleTerm, "titleTerm must not be null");
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(releaseYearFrom, "releaseYearFrom must not be null");
    Objects.requireNonNull(releaseYearTo, "releaseYearTo must not be null");
    Objects.requireNonNull(minRating, "minRating must not be null");
    titleTerm = titleTerm.filter(term -> !term.isBlank());
    genres = withoutBlanks(genres);
    if (minRating.isPresent()
        && (minRating.get().compareTo(MIN_RATING) < 0
            || minRating.get().compareTo(MAX_RATING) > 0)) {
      throw new InvalidRequestException("minRating");
    }
    if (releaseYearFrom.isPresent()
        && releaseYearTo.isPresent()
        && releaseYearFrom.get() > releaseYearTo.get()) {
      throw new InvalidRequestException("releaseYearFrom");
    }
  }

  /** Builds criteria from raw, possibly-null request values. */
  public static MovieSearchCriteria of(
      String titleTerm,
      List<String> genres,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating) {
    return new MovieSearchCriteria(
        Optional.ofNullable(titleTerm),
        genres == null ? Set.of() : new LinkedHashSet<>(genres),
        Optional.ofNullable(releaseYearFrom),
        Optional.ofNullable(releaseYearTo),
        Optional.ofNullable(minRating));
  }

  /** The same criteria with the genre names replaced (for example by their canonical names). */
  public MovieSearchCriteria withGenres(Collection<String> canonicalGenres) {
    return new MovieSearchCriteria(
        titleTerm, new LinkedHashSet<>(canonicalGenres), releaseYearFrom, releaseYearTo, minRating);
  }

  private static Set<String> withoutBlanks(Collection<String> genres) {
    Set<String> kept = new LinkedHashSet<>();
    for (String genre : genres) {
      if (genre != null && !genre.isBlank()) {
        kept.add(genre);
      }
    }
    return Collections.unmodifiableSet(kept);
  }
}
