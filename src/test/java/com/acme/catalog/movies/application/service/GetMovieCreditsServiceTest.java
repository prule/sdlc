package com.acme.catalog.movies.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.movies.application.port.out.LoadMovieCreditsPort;
import com.acme.catalog.movies.domain.model.Credit;
import com.acme.catalog.movies.domain.model.MovieCredits;
import com.acme.catalog.movies.domain.model.Person;
import com.acme.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit test for {@link GetMovieCreditsService}, with {@link LoadMovieCreditsPort} mocked. */
class GetMovieCreditsServiceTest {

  private final LoadMovieCreditsPort loadMovieCreditsPort = mock(LoadMovieCreditsPort.class);
  private final GetMovieCreditsService service = new GetMovieCreditsService(loadMovieCreditsPort);

  @Test
  void getMovieCredits_whenPortReturnsCredits_returnsThem() {
    UUID id = UUID.randomUUID();
    Person person = new Person(UUID.randomUUID(), "Ava Solano");
    MovieCredits credits = MovieCredits.of(List.of(Credit.Cast.of(person, "Lead", 1)), List.of());
    given(loadMovieCreditsPort.loadCreditsForMovie(id)).willReturn(Optional.of(credits));

    MovieCredits result = service.getMovieCredits(id);

    assertThat(result).isEqualTo(credits);
  }

  @Test
  void getMovieCredits_whenPortReturnsPresentButEmpty_returnsEmptyGroups() {
    UUID id = UUID.randomUUID();
    MovieCredits emptyCredits = MovieCredits.of(List.of(), List.of());
    given(loadMovieCreditsPort.loadCreditsForMovie(id)).willReturn(Optional.of(emptyCredits));

    MovieCredits result = service.getMovieCredits(id);

    assertThat(result.cast()).isEmpty();
    assertThat(result.crew()).isEmpty();
  }

  @Test
  void getMovieCredits_whenPortReturnsEmpty_throwsResourceNotFoundExceptionWithMovieNotFoundCode() {
    UUID id = UUID.randomUUID();
    given(loadMovieCreditsPort.loadCreditsForMovie(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMovieCredits(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("MOVIE_NOT_FOUND"));
  }
}
