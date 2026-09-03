package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Movie aggregate: the core catalog entity. Carries its stable opaque {@link MovieId}, title,
 * release year, and at least one {@link Genre}; optionally a runtime, synopsis, and aggregate
 * {@link Rating}. No Spring/JPA/HATEOAS imports — pure domain.
 */
public record Movie(
    MovieId id,
    String title,
    int releaseYear,
    List<Genre> genres,
    Optional<Integer> runtimeMinutes,
    Optional<String> synopsis,
    Optional<Rating> rating) {

  public Movie {
    Objects.requireNonNull(id, "id must not be null");
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    Objects.requireNonNull(genres, "genres must not be null");
    if (genres.isEmpty()) {
      throw new IllegalArgumentException("genres must contain at least one genre");
    }
    genres = List.copyOf(genres);
    Objects.requireNonNull(
        runtimeMinutes, "runtimeMinutes must not be null (use Optional.empty())");
    Objects.requireNonNull(synopsis, "synopsis must not be null (use Optional.empty())");
    Objects.requireNonNull(rating, "rating must not be null (use Optional.empty())");
  }
}
