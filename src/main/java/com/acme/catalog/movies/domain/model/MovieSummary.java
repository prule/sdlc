package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The shorter form of a {@link Movie} shown in search results (UC-002 BR-2): no synopsis. Genres
 * follow the same presentation rule as a movie's details; unrecorded runtime and rating are held as
 * {@link Optional}, never a sentinel value.
 */
public record MovieSummary(
    MovieId id,
    String title,
    int releaseYear,
    List<String> genres,
    Optional<RuntimeMinutes> runtime,
    Optional<Rating> rating) {

  public MovieSummary {
    Objects.requireNonNull(id, "id must not be null");
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(runtime, "runtime must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    genres = GenreNames.normalize(genres);
  }
}
