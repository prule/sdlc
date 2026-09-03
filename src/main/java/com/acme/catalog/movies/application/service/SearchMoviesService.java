package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the search-movies use case by delegating straight through to {@link SearchMoviesPort}.
 * {@code @Transactional(readOnly = true)}: the port issues the count and id-page as separate
 * queries (see {@code MovieSearchPersistenceAdapter}), so a read-only transaction gives them a
 * consistent snapshot and states the read-only intent for future authors copying this pattern.
 */
@Service
public class SearchMoviesService implements SearchMoviesUseCase {

  private final SearchMoviesPort searchMoviesPort;

  public SearchMoviesService(SearchMoviesPort searchMoviesPort) {
    this.searchMoviesPort = searchMoviesPort;
  }

  @Override
  @Transactional(readOnly = true)
  public MoviePage searchMovies(MovieSearchCriteria criteria, int page, int size, MovieSort sort) {
    return searchMoviesPort.search(criteria, page, size, sort);
  }
}
