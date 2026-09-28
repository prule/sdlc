package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A movie summary (UC-002 BR-2): the same identifying information as {@link Movie}, but never the
 * synopsis. Genres are normalized identically to {@link Movie}, via {@link GenreNames} (design D3).
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
