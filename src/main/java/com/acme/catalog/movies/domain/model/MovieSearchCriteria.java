package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The filters a movie search may be narrowed by. All supplied filters combine with AND; {@code
 * genres} additionally requires a matching movie to carry ALL of the supplied genres (AND within
 * the filter). No Spring/JPA imports — pure domain.
 */
public record MovieSearchCriteria(
    Optional<String> title,
    List<Genre> genres,
    Optional<Integer> yearFrom,
    Optional<Integer> yearTo,
    Optional<Rating> minRating) {

  public static final MovieSearchCriteria NONE =
      new MovieSearchCriteria(
          Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty());

  public MovieSearchCriteria {
    Objects.requireNonNull(title, "title must not be null (use Optional.empty())");
    Objects.requireNonNull(genres, "genres must not be null");
    genres = List.copyOf(genres);
    Objects.requireNonNull(yearFrom, "yearFrom must not be null (use Optional.empty())");
    Objects.requireNonNull(yearTo, "yearTo must not be null (use Optional.empty())");
    Objects.requireNonNull(minRating, "minRating must not be null (use Optional.empty())");
  }
}
