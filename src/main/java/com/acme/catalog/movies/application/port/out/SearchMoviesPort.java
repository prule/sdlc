package com.acme.catalog.movies.application.port.out;

import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;

/**
 * Outbound port for the movie search query (design D5). {@code criteria.genres()} are already the
 * canonical vocabulary names resolved by {@link
 * com.acme.catalog.movies.application.service.SearchMoviesService} — the port never re-checks them
 * against the vocabulary.
 */
public interface SearchMoviesPort {

  Page<MovieSummary> search(
      MovieSearchCriteria criteria, MovieSortOrder order, PageRequest pageRequest);
}
