package com.acme.catalog.movies.application.service;

import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import org.springframework.stereotype.Service;

/** Implements the search/browse use case. Depends only on domain + ports. */
@Service
public class SearchMoviesService implements SearchMoviesUseCase {

  private final SearchMoviesPort searchMoviesPort;

  public SearchMoviesService(SearchMoviesPort searchMoviesPort) {
    this.searchMoviesPort = searchMoviesPort;
  }

  @Override
  public MoviePage search(
      MovieSearchCriteria criteria, MoviePageRequest pageRequest, MovieSort sort) {
    return searchMoviesPort.search(criteria, pageRequest, sort);
  }
}
