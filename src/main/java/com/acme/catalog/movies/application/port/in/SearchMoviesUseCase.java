package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.InvalidSearchCriterionException;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;

/** Inbound port for searching and browsing movies (UC-002). */
public interface SearchMoviesUseCase {

  /**
   * @throws InvalidSearchCriterionException if a genre in {@code criteria} is not in the curated
   *     vocabulary
   */
  Page<MovieSummary> search(
      MovieSearchCriteria criteria, MovieSortOrder order, PageRequest pageRequest);
}
