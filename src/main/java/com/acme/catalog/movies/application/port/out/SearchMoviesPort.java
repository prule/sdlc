package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.shared.domain.PageSpec;
import com.acme.shared.domain.ResultPage;

/**
 * Outbound port returning one page of the movies that match validated criteria, in a complete,
 * stable order (UC-002). Genre names in the criteria are already canonical.
 */
public interface SearchMoviesPort {

  ResultPage<Movie> search(MovieSearchCriteria criteria, MovieSortOrder order, PageSpec page);
}
