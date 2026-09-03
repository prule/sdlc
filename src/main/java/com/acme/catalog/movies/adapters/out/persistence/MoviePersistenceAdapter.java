package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.application.port.out.LoadMovieByIdPort;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implements {@link LoadMovieByIdPort} against Postgres via {@link MovieJpaRepository}, mapping the
 * JPA entity to/from the domain {@link Movie}. The domain is never annotated {@code @Entity}.
 */
@Component
public class MoviePersistenceAdapter implements LoadMovieByIdPort {

  private final MovieJpaRepository movieJpaRepository;

  public MoviePersistenceAdapter(MovieJpaRepository movieJpaRepository) {
    this.movieJpaRepository = movieJpaRepository;
  }

  @Override
  public Optional<Movie> load(MovieId id) {
    return movieJpaRepository.findById(id.value()).map(MoviePersistenceAdapter::toDomain);
  }

  static Movie toDomain(MovieJpaEntity entity) {
    List<Genre> genres = entity.getGenres().stream().map(g -> new Genre(g.getName())).toList();

    return new Movie(
        new MovieId(entity.getId()),
        entity.getTitle(),
        entity.getReleaseYear(),
        genres,
        Optional.ofNullable(entity.getRuntimeMinutes()),
        Optional.ofNullable(entity.getSynopsis()),
        Optional.ofNullable(entity.getRating()).map(Rating::new));
  }
}
