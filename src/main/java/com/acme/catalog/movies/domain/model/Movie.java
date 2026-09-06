package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A movie in the catalog. Constructed only via {@link #of} which enforces the aggregate's
 * invariants: identifier, title, and release year present; at least one genre; optional
 * runtime/synopsis/rating. No Spring/JPA imports — see standards/clean-architecture.md.
 */
public record Movie(
    UUID id,
    String title,
    int releaseYear,
    Set<Genre> genres,
    Optional<Integer> runtimeMinutes,
    Optional<String> synopsis,
    Optional<Rating> rating) {

  public Movie {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(runtimeMinutes, "runtimeMinutes must not be null");
    Objects.requireNonNull(synopsis, "synopsis must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    if (title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    if (genres.isEmpty()) {
      throw new IllegalArgumentException("a movie must have at least one genre");
    }
    genres = Set.copyOf(genres);
  }

  /**
   * Creates a {@link Movie}, enforcing its invariants. Prefer this factory over the canonical
   * record constructor when any optional field may be absent, since it accepts raw nullable values
   * instead of requiring the caller to wrap each in {@link Optional} themselves.
   */
  public static Movie of(
      UUID id,
      String title,
      int releaseYear,
      List<Genre> genres,
      Integer runtimeMinutes,
      String synopsis,
      Rating rating) {
    return new Movie(
        id,
        title,
        releaseYear,
        genres == null ? Set.of() : Set.copyOf(genres),
        Optional.ofNullable(runtimeMinutes),
        Optional.ofNullable(synopsis),
        Optional.ofNullable(rating));
  }
}
