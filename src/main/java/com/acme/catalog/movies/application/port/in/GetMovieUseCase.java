package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.shared.domain.ResourceNotFoundException;

/** Inbound port for retrieving a single curated movie's details (UC-001). */
public interface GetMovieUseCase {

  /**
   * @throws ResourceNotFoundException if no movie exists with the given id
   */
  Movie getMovie(MovieId id);
}
