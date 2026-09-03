package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;

/** Outbound port: search a page of Movies matching the given criteria, sorted and paged. */
public interface SearchMoviesPort {

  /**
   * @param criteria the filters to apply (all combine with AND)
   * @param page zero-based page index
   * @param size page size
   * @param sort the field/direction to sort by
   * @return the requested page of matching movies plus page metadata
   */
  MoviePage search(MovieSearchCriteria criteria, int page, int size, MovieSort sort);
}
