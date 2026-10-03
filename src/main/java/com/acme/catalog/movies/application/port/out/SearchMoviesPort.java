package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.MovieOrder;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.PageRequest;
import com.acme.catalog.movies.domain.model.ResultPage;

/** Outbound port finding one page of the movies matching already-validated criteria (UC-002). */
public interface SearchMoviesPort {

  ResultPage<MovieSummary> search(
      MovieSearchCriteria criteria, MovieOrder order, PageRequest pageRequest);
}
