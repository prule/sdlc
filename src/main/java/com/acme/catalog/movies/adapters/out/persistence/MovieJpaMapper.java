package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import java.util.Optional;

/** Maps a {@link MovieJpaEntity} (with its genres loaded) to the domain {@link Movie}. */
final class MovieJpaMapper {

  private MovieJpaMapper() {}

  static Movie toDomain(MovieJpaEntity entity) {
    return new Movie(
        new MovieId(entity.getId()),
        entity.getTitle(),
        entity.getReleaseYear(),
        entity.getGenres().stream().map(GenreJpaEntity::getName).toList(),
        Optional.ofNullable(entity.getRuntimeMinutes()).map(RuntimeMinutes::new),
        Optional.ofNullable(entity.getSynopsis()).filter(s -> !s.isBlank()),
        Optional.ofNullable(entity.getRating()).map(Rating::new));
  }
}
