package com.acme.catalog.people.domain.model;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One item of a Person's filmography: a Movie the Person is credited on, in exactly one {@link
 * FilmographyCapacity}. A Person credited in several capacities on one Movie produces several
 * {@link FilmographyEntry} values — one per capacity — each carrying the Movie summary fields
 * (reusing {@code catalog.movies.domain.model} value types, mirroring how {@code catalog.credits}
 * already depends on them) plus the distinguishing {@code creditId}.
 *
 * <p>Ordering of a Person's filmography ({@code releaseYear} descending, then {@code movieTitle}
 * ascending under Postgres collation, then {@code creditId} as the tiebreak) is owned by the
 * persistence adapter's query, not by this domain type.
 */
public record FilmographyEntry(
    MovieId movieId,
    String movieTitle,
    int releaseYear,
    List<Genre> genres,
    Optional<Integer> runtimeMinutes,
    Optional<Rating> rating,
    FilmographyCapacity capacity,
    UUID creditId) {

  public FilmographyEntry {
    Objects.requireNonNull(movieId, "movieId must not be null");
    if (movieTitle == null || movieTitle.isBlank()) {
      throw new IllegalArgumentException("movieTitle must not be blank");
    }
    Objects.requireNonNull(genres, "genres must not be null");
    genres = List.copyOf(genres);
    Objects.requireNonNull(
        runtimeMinutes, "runtimeMinutes must not be null (use Optional.empty())");
    Objects.requireNonNull(rating, "rating must not be null (use Optional.empty())");
    Objects.requireNonNull(capacity, "capacity must not be null");
    Objects.requireNonNull(creditId, "creditId must not be null");
  }
}
