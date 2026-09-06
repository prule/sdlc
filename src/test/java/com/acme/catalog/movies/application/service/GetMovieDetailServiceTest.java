package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.movies.application.port.out.LoadMoviePort;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit test for {@link GetMovieDetailService}, with {@link LoadMoviePort} mocked. */
class GetMovieDetailServiceTest {

  private final LoadMoviePort loadMoviePort = mock(LoadMoviePort.class);
  private final GetMovieDetailService service = new GetMovieDetailService(loadMoviePort);

  @Test
  void getMovieDetail_whenPortReturnsAMovie_returnsIt() {
    UUID id = UUID.randomUUID();
    Movie movie = Movie.of(id, "Silent Harbor", 2021, List.of(Genre.MYSTERY), null, null, null);
    given(loadMoviePort.loadById(id)).willReturn(Optional.of(movie));

    Movie result = service.getMovieDetail(id);

    assertThat(result).isEqualTo(movie);
  }

  @Test
  void getMovieDetail_whenPortReturnsEmpty_throwsResourceNotFoundExceptionWithMovieNotFoundCode() {
    UUID id = UUID.randomUUID();
    given(loadMoviePort.loadById(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMovieDetail(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("MOVIE_NOT_FOUND"));
  }
}
