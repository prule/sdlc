package com.acme.catalog.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.people.application.port.out.PersonFilmographyPort;
import com.acme.catalog.people.domain.model.ActingCapacity;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetPersonFilmographyServiceTest {

  private final PersonFilmographyPort personFilmographyPort = mock(PersonFilmographyPort.class);
  private final GetPersonFilmographyService service =
      new GetPersonFilmographyService(personFilmographyPort);

  private static FilmographyEntry entry() {
    return new FilmographyEntry(
        new MovieId(UUID.randomUUID()),
        "The Matrix",
        1999,
        List.of(new Genre("Sci-Fi")),
        Optional.empty(),
        Optional.empty(),
        new ActingCapacity("Neo", 1),
        UUID.randomUUID());
  }

  @Test
  void getPersonFilmography_returnsThePageWhenPresentWithItems() {
    PersonId id = new PersonId(UUID.randomUUID());
    FilmographyPage page = new FilmographyPage(List.of(entry()), 0, 20, 1, 1);
    given(personFilmographyPort.loadFilmography(id, 0, 20)).willReturn(Optional.of(page));

    FilmographyPage result = service.getPersonFilmography(id, 0, 20);

    assertThat(result).isEqualTo(page);
  }

  @Test
  void getPersonFilmography_returnsPresentButEmptyPageForAPersonWithNoCredits() {
    PersonId id = new PersonId(UUID.randomUUID());
    FilmographyPage emptyPage = new FilmographyPage(List.of(), 0, 20, 0, 0);
    given(personFilmographyPort.loadFilmography(id, 0, 20)).willReturn(Optional.of(emptyPage));

    FilmographyPage result = service.getPersonFilmography(id, 0, 20);

    assertThat(result.items()).isEmpty();
    assertThat(result.totalElements()).isZero();
  }

  @Test
  void getPersonFilmography_throwsResourceNotFoundWhenThePersonIsUnknown() {
    PersonId id = new PersonId(UUID.randomUUID());
    given(personFilmographyPort.loadFilmography(id, 0, 20)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPersonFilmography(id, 0, 20))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("PERSON_NOT_FOUND"));
  }
}
