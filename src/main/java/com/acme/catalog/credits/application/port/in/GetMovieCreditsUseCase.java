package com.acme.catalog.credits.application.port.in;

import com.acme.catalog.credits.domain.model.MovieCredits;
import com.acme.catalog.movies.domain.model.MovieId;

/** Inbound port: retrieve a single Movie's cast and crew by its stable opaque id. */
public interface GetMovieCreditsUseCase {

  /**
   * @throws com.acme.common.error.ResourceNotFoundException if no Movie exists for {@code id}. A
   *     Movie that exists but has no credits returns a present, empty {@link MovieCredits}, not an
   *     exception.
   */
  MovieCredits getMovieCredits(MovieId id);
}
