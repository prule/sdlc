package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.GetMovieDetailUseCase;
import com.acme.catalog.movies.application.port.out.LoadMoviePort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Implements the get-movie-detail use case. A well-formed identifier that matches no movie is
 * reported as a {@link ResourceNotFoundException} with the stable code {@code MOVIE_NOT_FOUND}
 * (flow 4a of UC-001), handled by the existing global exception handler — no bespoke exception, no
 * new handler.
 */
@Service
public class GetMovieDetailService implements GetMovieDetailUseCase {

  private final LoadMoviePort loadMoviePort;

  public GetMovieDetailService(LoadMoviePort loadMoviePort) {
    this.loadMoviePort = loadMoviePort;
  }

  @Override
  public Movie getMovieDetail(UUID id) {
    return loadMoviePort
        .loadById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("MOVIE_NOT_FOUND", "No movie found with id " + id));
  }
}
