package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import org.springframework.stereotype.Service;

/**
 * Implements the search-movies use case by delegating straight through to {@link SearchMoviesPort}.
 */
@Service
public class SearchMoviesService implements SearchMoviesUseCase {

  private final SearchMoviesPort searchMoviesPort;

  public SearchMoviesService(SearchMoviesPort searchMoviesPort) {
    this.searchMoviesPort = searchMoviesPort;
  }

  @Override
  public MoviePage searchMovies(MovieSearchCriteria criteria, int page, int size, MovieSort sort) {
    return searchMoviesPort.search(criteria, page, size, sort);
  }
}
