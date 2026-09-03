package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;

/** Inbound port: retrieve a single Movie's detail by its stable opaque id. */
public interface GetMovieByIdUseCase {

  /**
   * @throws com.acme.common.error.ResourceNotFoundException if no Movie exists for {@code id}
   */
  Movie getMovieById(MovieId id);
}
