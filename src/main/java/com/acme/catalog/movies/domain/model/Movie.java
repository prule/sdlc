package com.acme.catalog.movies.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single film title in the catalog (UC-001). The factory (this record's canonical constructor)
 * owns the invariants a curated movie must satisfy: a non-blank title, and genres de-duplicated and
 * ordered alphabetically by name, ignoring letter case, with a tie broken by the exact name (BR-4,
 * A-SORT) — so the same movie always lists its genres in the same order, whatever order they were
 * curated in. Optional fields are held as {@link Optional}, never a sentinel value (BR-3).
 */
public record Movie(
    MovieId id,
    String title,
    int releaseYear,
    List<String> genres,
    Optional<RuntimeMinutes> runtime,
    Optional<String> synopsis,
    Optional<Rating> rating) {

  public Movie {
    Objects.requireNonNull(id, "id must not be null");
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    Objects.requireNonNull(genres, "genres must not be null");
    Objects.requireNonNull(runtime, "runtime must not be null");
    Objects.requireNonNull(synopsis, "synopsis must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    genres = GenreNames.normalize(genres);
  }
}
