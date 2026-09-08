package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;

/** Outbound port: fetch a page of movies matching criteria, ordered and paged. */
public interface SearchMoviesPort {

  MoviePage search(MovieSearchCriteria criteria, MoviePageRequest pageRequest, MovieSort sort);
}
