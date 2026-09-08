package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit test for {@link SearchMoviesService}, with {@link SearchMoviesPort} mocked. */
class SearchMoviesServiceTest {

  private final SearchMoviesPort searchMoviesPort = mock(SearchMoviesPort.class);
  private final SearchMoviesService service = new SearchMoviesService(searchMoviesPort);

  @Test
  void search_delegatesToThePortAndReturnsItsResult() {
    MovieSearchCriteria criteria = MovieSearchCriteria.none();
    MoviePageRequest pageRequest = new MoviePageRequest(0, 20);
    MovieSort sort = MovieSort.defaultSort();
    MoviePage expected = new MoviePage(List.of(), 0, 20, 0);
    given(searchMoviesPort.search(criteria, pageRequest, sort)).willReturn(expected);

    MoviePage result = service.search(criteria, pageRequest, sort);

    assertThat(result).isEqualTo(expected);
  }
}
