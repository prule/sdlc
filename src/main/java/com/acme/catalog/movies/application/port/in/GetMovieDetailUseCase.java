package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.Movie;
import java.util.UUID;

/** Inbound port: retrieve one movie's detail by its stable identifier. */
public interface GetMovieDetailUseCase {

  /**
   * Returns the movie identified by {@code id}.
   *
   * @throws com.acme.common.error.ResourceNotFoundException if no movie matches {@code id}
   */
  Movie getMovieDetail(UUID id);
}
