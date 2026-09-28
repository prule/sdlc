package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.out.LoadMoviePort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/** Application service implementing {@link GetMovieUseCase} (UC-001). */
@Service
public class GetMovieService implements GetMovieUseCase {

  private final LoadMoviePort loadMoviePort;

  public GetMovieService(LoadMoviePort loadMoviePort) {
    this.loadMoviePort = loadMoviePort;
  }

  @Override
  public Movie getMovie(MovieId id) {
    return loadMoviePort
        .loadMovie(id)
        .orElseThrow(() -> new ResourceNotFoundException("No movie found for id " + id));
  }
}
