package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.GetMovieCreditsUseCase;
import com.acme.catalog.movies.application.port.out.LoadMovieCreditsPort;
import com.acme.catalog.movies.domain.model.MovieCredits;
import com.acme.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Implements the get-movie-credits use case. A well-formed identifier that matches no movie is
 * reported as a {@link ResourceNotFoundException} with the stable code {@code MOVIE_NOT_FOUND}
 * (reusing the UC-001 outcome, per design.md decision 3) — handled by the existing global exception
 * handler, no bespoke exception, no new handler.
 */
@Service
public class GetMovieCreditsService implements GetMovieCreditsUseCase {

  private final LoadMovieCreditsPort loadMovieCreditsPort;

  public GetMovieCreditsService(LoadMovieCreditsPort loadMovieCreditsPort) {
    this.loadMovieCreditsPort = loadMovieCreditsPort;
  }

  @Override
  public MovieCredits getMovieCredits(UUID movieId) {
    return loadMovieCreditsPort
        .loadCreditsForMovie(movieId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "MOVIE_NOT_FOUND", "No movie found with id " + movieId));
  }
}
