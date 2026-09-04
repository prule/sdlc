package com.acme.catalog.credits.application.service;

import com.acme.catalog.credits.application.port.in.GetMovieCreditsUseCase;
import com.acme.catalog.credits.application.port.out.LoadMovieCreditsPort;
import com.acme.catalog.credits.domain.model.MovieCredits;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implements the get-movie-credits use case. An unknown movie is a domain-level 404, reusing the
 * same {@code MOVIE_NOT_FOUND} code as {@code GetMovieByIdService}; a movie with no credits is a
 * normal, present, empty result.
 */
@Service
public class GetMovieCreditsService implements GetMovieCreditsUseCase {

  private final LoadMovieCreditsPort loadMovieCreditsPort;

  public GetMovieCreditsService(LoadMovieCreditsPort loadMovieCreditsPort) {
    this.loadMovieCreditsPort = loadMovieCreditsPort;
  }

  @Override
  public MovieCredits getMovieCredits(MovieId id) {
    return loadMovieCreditsPort
        .load(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "MOVIE_NOT_FOUND", "No movie found for id: " + id.value()));
  }
}
