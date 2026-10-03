package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.shared.domain.InvalidRequestException;
import com.acme.shared.domain.ResultPage;

/** Inbound port for searching and browsing the catalog a page at a time (UC-002). */
public interface SearchMoviesUseCase {

  /**
   * @throws InvalidRequestException naming the first request element at fault, before any search is
   *     performed
   */
  ResultPage<Movie> search(SearchMoviesQuery query);
}
