package com.acme.catalog.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.people.application.port.out.LoadPersonFilmographyPort;
import com.acme.catalog.people.domain.model.Capacity;
import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyMovieSummary;
import com.acme.catalog.people.domain.model.FilmographyPageRequest;
import com.acme.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link GetPersonFilmographyService}, with {@link LoadPersonFilmographyPort} mocked.
 */
class GetPersonFilmographyServiceTest {

  private final LoadPersonFilmographyPort loadPersonFilmographyPort =
      mock(LoadPersonFilmographyPort.class);
  private final GetPersonFilmographyService service =
      new GetPersonFilmographyService(loadPersonFilmographyPort);

  @Test
  void getFilmography_whenPortReturnsAPage_returnsIt() {
    UUID personId = UUID.randomUUID();
    FilmographyCriteria criteria = FilmographyCriteria.none();
    FilmographyPageRequest pageRequest = new FilmographyPageRequest(0, 20);
    FilmographyEntry entry =
        new FilmographyEntry(
            FilmographyMovieSummary.of(
                UUID.randomUUID(), "The Wandering Reel", 2019, List.of(), null, null),
            Capacity.Acting.of("Dana Whitfield"));
    Filmography filmography = new Filmography(List.of(entry), 0, 20, 1);
    given(loadPersonFilmographyPort.loadFilmography(personId, criteria, pageRequest))
        .willReturn(Optional.of(filmography));

    Filmography result = service.getFilmography(personId, criteria, pageRequest);

    assertThat(result).isEqualTo(filmography);
  }

  @Test
  void getFilmography_whenPortReturnsPresentButEmpty_returnsEmptyPage() {
    UUID personId = UUID.randomUUID();
    FilmographyCriteria criteria = FilmographyCriteria.none();
    FilmographyPageRequest pageRequest = new FilmographyPageRequest(0, 20);
    Filmography emptyFilmography = new Filmography(List.of(), 0, 20, 0);
    given(loadPersonFilmographyPort.loadFilmography(personId, criteria, pageRequest))
        .willReturn(Optional.of(emptyFilmography));

    Filmography result = service.getFilmography(personId, criteria, pageRequest);

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isZero();
  }

  @Test
  void getFilmography_whenPortReturnsEmpty_throwsResourceNotFoundExceptionWithPersonNotFoundCode() {
    UUID personId = UUID.randomUUID();
    FilmographyCriteria criteria = FilmographyCriteria.none();
    FilmographyPageRequest pageRequest = new FilmographyPageRequest(0, 20);
    given(loadPersonFilmographyPort.loadFilmography(personId, criteria, pageRequest))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getFilmography(personId, criteria, pageRequest))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("PERSON_NOT_FOUND"));
  }
}
