package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.LoadMoviePort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter loading a curated movie from PostgreSQL, mapping {@link MovieJpaEntity} to the
 * domain {@link Movie} (design D5).
 */
@Component
public class MoviePersistenceAdapter implements LoadMoviePort {

  private final MovieJpaRepository movieJpaRepository;

  public MoviePersistenceAdapter(MovieJpaRepository movieJpaRepository) {
    this.movieJpaRepository = movieJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Movie> loadMovie(MovieId id) {
    return movieJpaRepository.findById(id.value()).map(this::toDomain);
  }

  private Movie toDomain(MovieJpaEntity entity) {
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
