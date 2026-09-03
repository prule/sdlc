package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.MovieSortField;
import com.acme.catalog.movies.domain.model.SortDirection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchMoviesServiceTest {

  private final SearchMoviesPort searchMoviesPort = mock(SearchMoviesPort.class);
  private final SearchMoviesService service = new SearchMoviesService(searchMoviesPort);

  @Test
  void searchMovies_delegatesCriteriaPageSizeSortAndReturnsThePort_result() {
    MovieSearchCriteria criteria =
        new MovieSearchCriteria(
            Optional.of("matrix"),
            List.of(new Genre("Sci-Fi")),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    MovieSort sort = new MovieSort(MovieSortField.TITLE, SortDirection.ASC);
    Movie movie =
        new Movie(
            new MovieId(UUID.randomUUID()),
            "The Matrix",
            1999,
            List.of(new Genre("Sci-Fi")),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    MoviePage expectedPage = new MoviePage(List.of(movie), 2, 10, 1, 1);
    given(searchMoviesPort.search(criteria, 2, 10, sort)).willReturn(expectedPage);

    MoviePage result = service.searchMovies(criteria, 2, 10, sort);

    assertThat(result).isEqualTo(expectedPage);
  }
}
