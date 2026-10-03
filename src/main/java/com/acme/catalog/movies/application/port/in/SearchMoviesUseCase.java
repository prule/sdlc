package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.ResultPage;
import com.acme.shared.domain.InvalidCriteriaException;

/** Inbound port for searching and browsing the catalog a page at a time (UC-002). */
public interface SearchMoviesUseCase {

  /**
   * @throws InvalidCriteriaException if the search is asked in a way that isn't allowed (BR-10);
   *     nothing is searched in that case
   */
  ResultPage<MovieSummary> search(SearchMoviesQuery query);
}
