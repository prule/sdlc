package com.acme.catalog.movies.application.port.in;

import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;

/** Inbound port: search/browse the movie catalog as an ordered, paged result. */
public interface SearchMoviesUseCase {

  /**
   * @param criteria optional, conjunctive search criteria
   * @param pageRequest the requested page (index + size)
   * @param sort the requested (or default) ordering
   * @return the requested page of matching movies plus page metadata
   */
  MoviePage search(MovieSearchCriteria criteria, MoviePageRequest pageRequest, MovieSort sort);
}
