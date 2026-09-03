package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.GetMovieByIdUseCase;
import com.acme.catalog.movies.application.port.out.LoadMovieByIdPort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/** Implements the get-movie-by-id use case. Not found is a domain-level failure, not a 500. */
@Service
public class GetMovieByIdService implements GetMovieByIdUseCase {

  private final LoadMovieByIdPort loadMovieByIdPort;

  public GetMovieByIdService(LoadMovieByIdPort loadMovieByIdPort) {
    this.loadMovieByIdPort = loadMovieByIdPort;
  }

  @Override
  public Movie getMovieById(MovieId id) {
    return loadMovieByIdPort
        .load(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "MOVIE_NOT_FOUND", "No movie found for id: " + id.value()));
  }
}
