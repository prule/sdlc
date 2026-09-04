package com.acme.catalog.credits.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.credits.application.port.out.LoadMovieCreditsPort;
import com.acme.catalog.credits.domain.model.CastCredit;
import com.acme.catalog.credits.domain.model.CrewCredit;
import com.acme.catalog.credits.domain.model.MovieCredits;
import com.acme.catalog.credits.domain.model.Person;
import com.acme.catalog.credits.domain.model.PersonId;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetMovieCreditsServiceTest {

  private final LoadMovieCreditsPort loadMovieCreditsPort = mock(LoadMovieCreditsPort.class);
  private final GetMovieCreditsService service = new GetMovieCreditsService(loadMovieCreditsPort);

  private static Person person() {
    return new Person(new PersonId(UUID.randomUUID()), "Keanu Reeves");
  }

  @Test
  void getMovieCredits_returnsTheCreditsWhenPresentWithCastAndCrew() {
    MovieId id = new MovieId(UUID.randomUUID());
    MovieCredits credits =
        new MovieCredits(
            List.of(new CastCredit(person(), "Neo", 1)),
            List.of(new CrewCredit(person(), "Directing", "Director")));
    given(loadMovieCreditsPort.load(id)).willReturn(Optional.of(credits));

    MovieCredits result = service.getMovieCredits(id);

    assertThat(result).isEqualTo(credits);
  }

  @Test
  void getMovieCredits_returnsPresentButEmptyCreditsForAMovieWithNoCredits() {
    MovieId id = new MovieId(UUID.randomUUID());
    MovieCredits emptyCredits = new MovieCredits(List.of(), List.of());
    given(loadMovieCreditsPort.load(id)).willReturn(Optional.of(emptyCredits));

    MovieCredits result = service.getMovieCredits(id);

    assertThat(result.cast()).isEmpty();
    assertThat(result.crew()).isEmpty();
  }

  @Test
  void getMovieCredits_throwsResourceNotFoundWhenTheMovieIsUnknown() {
    MovieId id = new MovieId(UUID.randomUUID());
    given(loadMovieCreditsPort.load(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMovieCredits(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("MOVIE_NOT_FOUND"));
  }
}
