package com.acme.catalog.people.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.people.application.port.in.GetPersonDetailUseCase;
import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.domain.model.Capacity;
import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyMovieSummary;
import com.acme.catalog.people.domain.model.Genre;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link PersonController#getPersonFilmography} (UC-005). {@link
 * GetPersonFilmographyUseCase} is mocked at the port seam.
 */
@WebMvcTest(PersonController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class PersonControllerFilmographyTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetPersonDetailUseCase getPersonDetailUseCase;

  @MockitoBean private GetPersonFilmographyUseCase getPersonFilmographyUseCase;

  private static FilmographyEntry actingEntry(UUID movieId, String title, int year) {
    FilmographyMovieSummary movie =
        FilmographyMovieSummary.of(movieId, title, year, List.of(Genre.DRAMA), 118, null);
    return new FilmographyEntry(movie, Capacity.Acting.of("Dana Whitfield"));
  }

  private static FilmographyEntry nonActingEntry(UUID movieId, String title, int year) {
    FilmographyMovieSummary movie =
        FilmographyMovieSummary.of(movieId, title, year, List.of(Genre.DRAMA), null, null);
    return new FilmographyEntry(movie, new Capacity.NonActing("Directing", "Director"));
  }

  @Test
  void getPersonFilmography_existingPersonWithCredits_returns200WithEnvelopeShapeAndPagination()
      throws Exception {
    UUID personId = UUID.randomUUID();
    UUID movieId = UUID.randomUUID();
    FilmographyEntry entry = actingEntry(movieId, "The Wandering Reel", 2019);
    Filmography filmography = new Filmography(List.of(entry), 0, 20, 1);
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(filmography);

    mockMvc
        .perform(get("/people/{id}/credits", personId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data._embedded.filmography.length()").value(1))
        .andExpect(jsonPath("$.data._embedded.filmography[0].movie.id").value(movieId.toString()))
        .andExpect(
            jsonPath("$.data._embedded.filmography[0].movie.title").value("The Wandering Reel"))
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.type").value("acting"))
        .andExpect(
            jsonPath("$.data._embedded.filmography[0].capacity.character").value("Dana Whitfield"))
        .andExpect(
            jsonPath(
                "$.data._embedded.filmography[0].movie._links.self.href",
                Matchers.endsWith("/movies/" + movieId)))
        .andExpect(
            jsonPath(
                "$.data._links.self.href",
                Matchers.containsString("/people/" + personId + "/credits")))
        .andExpect(jsonPath("$.meta.pagination.page").value(0))
        .andExpect(jsonPath("$.meta.pagination.size").value(20))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(1))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(1))
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists());
  }

  @Test
  void getPersonFilmography_nonActingEntry_carriesDepartmentAndJobNoCharacter() throws Exception {
    UUID personId = UUID.randomUUID();
    UUID movieId = UUID.randomUUID();
    FilmographyEntry entry = nonActingEntry(movieId, "Harbor Lights", 2020);
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(entry), 0, 20, 1));

    mockMvc
        .perform(get("/people/{id}/credits", personId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.type").value("non-acting"))
        .andExpect(
            jsonPath("$.data._embedded.filmography[0].capacity.department").value("Directing"))
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.job").value("Director"))
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.character").doesNotExist());
  }

  @Test
  void getPersonFilmography_defaultPaging_usesPageZeroSizeTwenty() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 0, 20, 0));

    mockMvc
        .perform(get("/people/{id}/credits", personId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.pagination.page").value(0))
        .andExpect(jsonPath("$.meta.pagination.size").value(20));
  }

  @Test
  void getPersonFilmography_pageWithNextAndPrev_carriesNavigationLinks() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 1, 2, 6));

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.self").exists())
        .andExpect(jsonPath("$.data._links.first").exists())
        .andExpect(jsonPath("$.data._links.last").exists())
        .andExpect(jsonPath("$.data._links.prev").exists())
        .andExpect(jsonPath("$.data._links.next").exists());
  }

  @Test
  void getPersonFilmography_firstPage_hasNoPrevAndLastPage_hasNoNext() throws Exception {
    UUID personId = UUID.randomUUID();

    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 0, 2, 6));
    mockMvc
        .perform(get("/people/{id}/credits", personId).param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist())
        .andExpect(jsonPath("$.data._links.next").exists());

    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 2, 2, 6));
    mockMvc
        .perform(get("/people/{id}/credits", personId).param("page", "2").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.prev").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void getPersonFilmography_existingPersonWithNoCredits_returns200EmptyPageTotalZero()
      throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 0, 20, 0));

    mockMvc
        .perform(get("/people/{id}/credits", personId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography.length()").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(0));
  }

  @Test
  void getPersonFilmography_pageBeyondLast_returns200EmptyPageWithTrueTotal() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 5, 20, 3));

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("page", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography.length()").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(3));
  }

  @Test
  void getPersonFilmography_filterMatchingNothing_returns200EmptyPageTotalZero() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 0, 20, 0));

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("capacity", "non-acting"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography.length()").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(0));
  }

  @Test
  void getPersonFilmography_capacityFilter_isPassedThroughToTheUseCase() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 0, 20, 0));

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("capacity", "acting"))
        .andExpect(status().isOk());

    org.mockito.ArgumentCaptor<FilmographyCriteria> captor =
        org.mockito.ArgumentCaptor.forClass(FilmographyCriteria.class);
    org.mockito.Mockito.verify(getPersonFilmographyUseCase)
        .getFilmography(org.mockito.ArgumentMatchers.eq(personId), captor.capture(), any());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().capacity())
        .contains(Capacity.Type.ACTING);
  }

  @Test
  void getPersonFilmography_releaseYearRange_isPassedThroughToTheUseCase() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willReturn(new Filmography(List.of(), 0, 20, 0));

    mockMvc
        .perform(
            get("/people/{id}/credits", personId)
                .param("releaseYearFrom", "2000")
                .param("releaseYearTo", "2020"))
        .andExpect(status().isOk());

    org.mockito.ArgumentCaptor<FilmographyCriteria> captor =
        org.mockito.ArgumentCaptor.forClass(FilmographyCriteria.class);
    org.mockito.Mockito.verify(getPersonFilmographyUseCase)
        .getFilmography(org.mockito.ArgumentMatchers.eq(personId), captor.capture(), any());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().yearFrom()).contains(2000);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().yearTo()).contains(2020);
  }

  @Test
  void getPersonFilmography_wellFormedUnknownId_returns404ProblemJson() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(eqPersonId(personId), any(), any()))
        .willThrow(
            new ResourceNotFoundException(
                "PERSON_NOT_FOUND", "No person found with id " + personId));

    mockMvc
        .perform(get("/people/{id}/credits", personId))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getPersonFilmography_malformedId_returns400WithoutInvokingTheUseCase() throws Exception {
    mockMvc
        .perform(get("/people/{id}/credits", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(getPersonFilmographyUseCase);
  }

  @Test
  void getPersonFilmography_negativePage_returns400WithoutInvokingTheUseCase() throws Exception {
    UUID personId = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(getPersonFilmographyUseCase);
  }

  @Test
  void getPersonFilmography_sizeZero_returns400WithoutInvokingTheUseCase() throws Exception {
    UUID personId = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("size", "0"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(getPersonFilmographyUseCase);
  }

  @Test
  void getPersonFilmography_sizeAboveMax_returns400WithoutInvokingTheUseCase() throws Exception {
    UUID personId = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("size", "101"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(getPersonFilmographyUseCase);
  }

  @Test
  void getPersonFilmography_unrecognisedCapacityValue_returns400WithoutInvokingTheUseCase()
      throws Exception {
    UUID personId = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("capacity", "not-a-capacity"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(getPersonFilmographyUseCase);
  }

  @Test
  void getPersonFilmography_malformedReleaseYearFrom_returns400WithoutInvokingTheUseCase()
      throws Exception {
    UUID personId = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("releaseYearFrom", "not-a-year"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(getPersonFilmographyUseCase);
  }

  @Test
  void getPersonFilmography_malformedReleaseYearTo_returns400WithoutInvokingTheUseCase()
      throws Exception {
    UUID personId = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", personId).param("releaseYearTo", "not-a-year"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(getPersonFilmographyUseCase);
  }

  @Test
  void getPersonFilmography_requiresNoAuthentication() throws Exception {
    UUID personId = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getFilmography(any(), any(), any()))
        .willReturn(new Filmography(List.of(), 0, 20, 0));

    mockMvc.perform(get("/people/{id}/credits", personId)).andExpect(status().isOk());
  }

  private static UUID eqPersonId(UUID personId) {
    return org.mockito.ArgumentMatchers.eq(personId);
  }
}
