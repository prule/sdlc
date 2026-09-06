package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.LoadMoviePort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.Rating;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implements {@link LoadMoviePort}, mapping the JPA entity to/from the domain {@link Movie}. */
@Component
public class MoviePersistenceAdapter implements LoadMoviePort {

  private final MovieJpaRepository movieJpaRepository;

  public MoviePersistenceAdapter(MovieJpaRepository movieJpaRepository) {
    this.movieJpaRepository = movieJpaRepository;
  }

  @Override
  public Optional<Movie> loadById(UUID id) {
    return movieJpaRepository.findById(id).map(MoviePersistenceAdapter::toDomain);
  }

  private static Movie toDomain(MovieJpaEntity entity) {
    return Movie.of(
        entity.getId(),
        entity.getTitle(),
        entity.getReleaseYear(),
        List.copyOf(entity.getGenres()),
        entity.getRuntimeMinutes(),
        entity.getSynopsis(),
        entity.getRating() == null ? null : new Rating(entity.getRating()));
  }
}
