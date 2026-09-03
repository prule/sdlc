package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.movies.application.port.out.LoadMovieByIdPort;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetMovieByIdServiceTest {

  private final LoadMovieByIdPort loadMovieByIdPort = mock(LoadMovieByIdPort.class);
  private final GetMovieByIdService service = new GetMovieByIdService(loadMovieByIdPort);

  @Test
  void getMovieById_returnsTheMovieWhenFound() {
    MovieId id = new MovieId(UUID.randomUUID());
    Movie movie =
        new Movie(
            id,
            "Arrival",
            2016,
            List.of(new Genre("Sci-Fi")),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    given(loadMovieByIdPort.load(id)).willReturn(Optional.of(movie));

    Movie result = service.getMovieById(id);

    assertThat(result).isEqualTo(movie);
  }

  @Test
  void getMovieById_throwsResourceNotFoundWhenMissing() {
    MovieId id = new MovieId(UUID.randomUUID());
    given(loadMovieByIdPort.load(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMovieById(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("MOVIE_NOT_FOUND"));
  }
}
