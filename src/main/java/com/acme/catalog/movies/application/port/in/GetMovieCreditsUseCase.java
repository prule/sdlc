package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.MovieCredits;
import java.util.UUID;

/** Inbound port: retrieve one movie's full cast and crew credits by its stable identifier. */
public interface GetMovieCreditsUseCase {

  /**
   * Returns the credits of the movie identified by {@code movieId}.
   *
   * @throws com.acme.common.error.ResourceNotFoundException if no movie matches {@code movieId}
   */
  MovieCredits getMovieCredits(UUID movieId);
}
