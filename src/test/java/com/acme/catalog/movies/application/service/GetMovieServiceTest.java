package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.acme.catalog.movies.application.port.out.LoadMoviePort;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.shared.domain.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMovieServiceTest {

  @Mock private LoadMoviePort loadMoviePort;

  private final MovieId id = new MovieId(UUID.randomUUID());

  @Test
  void returnsTheMovieWhenFound() {
    Movie movie =
        new Movie(
            id,
            "Arrival",
            2016,
            List.of("Drama"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    given(loadMoviePort.loadMovie(id)).willReturn(Optional.of(movie));
    GetMovieService service = new GetMovieService(loadMoviePort);

    Movie result = service.getMovie(id);

    assertThat(result).isEqualTo(movie);
  }

  @Test
  void throwsResourceNotFoundWhenTheMovieIsAbsent() {
    given(loadMoviePort.loadMovie(id)).willReturn(Optional.empty());
    GetMovieService service = new GetMovieService(loadMoviePort);

    assertThatThrownBy(() -> service.getMovie(id)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void propagatesAPortExceptionUnchanged() {
    RuntimeException portFailure = new RuntimeException("secret-db-host:5432 refused");
    given(loadMoviePort.loadMovie(id)).willThrow(portFailure);
    GetMovieService service = new GetMovieService(loadMoviePort);

    assertThatThrownBy(() -> service.getMovie(id)).isSameAs(portFailure);
  }
}
