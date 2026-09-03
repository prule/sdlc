package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;

/** Inbound port: search a page of Movie summaries matching the given filters, sorted and paged. */
public interface SearchMoviesUseCase {

  /**
   * @param criteria the filters to apply (all combine with AND)
   * @param page zero-based page index
   * @param size page size
   * @param sort the field/direction to sort by
   * @return the requested page of matching movies plus page metadata
   */
  MoviePage searchMovies(MovieSearchCriteria criteria, int page, int size, MovieSort sort);
}
