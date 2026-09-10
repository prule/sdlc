package com.acme.catalog.people.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A lightweight movie-summary value local to the {@code catalog.people} slice — the same shape as
 * the {@code catalog.movies} slice's UC-002 {@code MovieSummary} (id, title, releaseYear, genres,
 * optional runtime/rating), duplicated rather than imported to keep the slices decoupled (see
 * {@link Genre}). No Spring/JPA imports — see standards/clean-architecture.md.
 */
public record FilmographyMovieSummary(
    UUID id,
    String title,
    int releaseYear,
    Set<Genre> genres,
    Optional<Integer> runtimeMinutes,
    Optional<Rating> rating) {

  public FilmographyMovieSummary {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(runtimeMinutes, "runtimeMinutes must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    if (title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    genres = Set.copyOf(genres);
  }

  /**
   * Creates a {@link FilmographyMovieSummary}, accepting raw nullable values instead of requiring
   * the caller to wrap each optional field themselves.
   */
  public static FilmographyMovieSummary of(
      UUID id,
      String title,
      int releaseYear,
      List<Genre> genres,
      Integer runtimeMinutes,
      Rating rating) {
    return new FilmographyMovieSummary(
        id,
        title,
        releaseYear,
        genres == null ? Set.of() : Set.copyOf(genres),
        Optional.ofNullable(runtimeMinutes),
        Optional.ofNullable(rating));
  }
}
