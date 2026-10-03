package com.acme.catalog.movies.domain.model;

import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.InvalidCriteriaException.Violation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * What a movie search must match (UC-002 BR-3, BR-4); every criterion is optional and all of them
 * combine. Violation field names are deliberately the interface description's parameter names
 * (design D2).
 *
 * @param titleTerm a non-blank term matched anywhere in the title, ignoring case
 * @param genres lower-cased genre names a movie must all carry (vocabulary membership is checked by
 *     the application service)
 * @param minRating inclusive; movies without a rating never match
 */
public record MovieSearchCriteria(
    Optional<String> titleTerm,
    Set<String> genres,
    OptionalInt releaseYearFrom,
    OptionalInt releaseYearTo,
    Optional<Rating> minRating) {

  public MovieSearchCriteria {
    Objects.requireNonNull(titleTerm, "titleTerm must not be null");
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(releaseYearFrom, "releaseYearFrom must not be null");
    Objects.requireNonNull(releaseYearTo, "releaseYearTo must not be null");
    Objects.requireNonNull(minRating, "minRating must not be null");
    genres = Set.copyOf(genres);
  }

  /**
   * Builds criteria from raw request values, collecting every violation into one exception.
   *
   * @throws InvalidCriteriaException if a genre is blank, the year range is reversed, or {@code
   *     minRating} is outside 0–5
   */
  public static MovieSearchCriteria of(
      String title,
      Collection<String> genres,
      Integer releaseYearFrom,
      Integer releaseYearTo,
      BigDecimal minRating) {
    List<Violation> violations = new ArrayList<>();

    Set<String> genreNames = new LinkedHashSet<>();
    for (String genre : genres == null ? List.<String>of() : genres) {
      if (genre == null || genre.isBlank()) {
        violations.add(new Violation("genre", "must name a genre in the curated vocabulary"));
      } else {
        genreNames.add(genre.toLowerCase(Locale.ROOT));
      }
    }
    if (releaseYearFrom != null && releaseYearTo != null && releaseYearFrom > releaseYearTo) {
      violations.add(new Violation("releaseYearFrom", "must not be after releaseYearTo"));
      violations.add(new Violation("releaseYearTo", "must not be before releaseYearFrom"));
    }
    Optional<Rating> rating = Optional.empty();
    if (minRating != null) {
      if (minRating.signum() < 0 || minRating.compareTo(BigDecimal.valueOf(5)) > 0) {
        violations.add(new Violation("minRating", "must be between 0 and 5"));
      } else {
        rating = Optional.of(new Rating(minRating));
      }
    }
    if (!violations.isEmpty()) {
      throw new InvalidCriteriaException(violations);
    }

    return new MovieSearchCriteria(
        Optional.ofNullable(title).filter(term -> !term.isBlank()),
        genreNames,
        releaseYearFrom == null ? OptionalInt.empty() : OptionalInt.of(releaseYearFrom),
        releaseYearTo == null ? OptionalInt.empty() : OptionalInt.of(releaseYearTo),
        rating);
  }
}
