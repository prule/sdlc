package com.acme.catalog.people.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.people.application.port.in.GetPersonByIdUseCase;
import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.domain.model.ActingCapacity;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.NonActingCapacity;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link PersonController#getPersonFilmography}. {@link
 * GetPersonFilmographyUseCase} is mocked at the port seam.
 */
@WebMvcTest(PersonController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class PersonFilmographyControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetPersonByIdUseCase getPersonByIdUseCase;
  @MockitoBean private GetPersonFilmographyUseCase getPersonFilmographyUseCase;

  private static FilmographyEntry actingEntry(int releaseYear, String title) {
    return new FilmographyEntry(
        new MovieId(UUID.randomUUID()),
        title,
        releaseYear,
        List.of(new Genre("Sci-Fi")),
        Optional.of(120),
        Optional.empty(),
        new ActingCapacity("Neo", 1),
        UUID.randomUUID());
  }

  private static FilmographyEntry nonActingEntry(int releaseYear, String title) {
    return new FilmographyEntry(
        new MovieId(UUID.randomUUID()),
        title,
        releaseYear,
        List.of(new Genre("Drama")),
        Optional.empty(),
        Optional.empty(),
        new NonActingCapacity("Directing", "Director"),
        UUID.randomUUID());
  }

  private static FilmographyPage pageOf(
      int page, int size, long totalElements, List<FilmographyEntry> items) {
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return new FilmographyPage(items, page, size, totalElements, totalPages);
  }

  @Test
  void getPersonFilmography_existingPerson_returns200EnvelopedHalCollection() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(eq(new PersonId(id)), eq(0), eq(20)))
        .willReturn(pageOf(0, 20, 1, List.of(actingEntry(1999, "The Matrix"))));

    mockMvc
        .perform(get("/people/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.data._embedded.filmography.length()").value(1))
        .andExpect(jsonPath("$.data._embedded.cast").doesNotExist())
        .andExpect(jsonPath("$.data._embedded.crew").doesNotExist())
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists())
        .andExpect(jsonPath("$.meta.pagination.page").value(0))
        .andExpect(jsonPath("$.meta.pagination.size").value(20))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(1))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(1));
  }

  @Test
  void getPersonFilmography_actingItem_exposesMovieSummaryAndActingCapacity() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), anyInt(), anyInt()))
        .willReturn(pageOf(0, 20, 1, List.of(actingEntry(1999, "The Matrix"))));

    mockMvc
        .perform(get("/people/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography[0].id").exists())
        .andExpect(jsonPath("$.data._embedded.filmography[0].title").value("The Matrix"))
        .andExpect(jsonPath("$.data._embedded.filmography[0].releaseYear").value(1999))
        .andExpect(
            jsonPath("$.data._embedded.filmography[0].genres", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.data._embedded.filmography[0].runtimeMinutes").value(120))
        .andExpect(jsonPath("$.data._embedded.filmography[0].rating").doesNotExist())
        .andExpect(jsonPath("$.data._embedded.filmography[0]._links.self.href").exists())
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.type").value("acting"))
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.character").value("Neo"))
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.billingOrder").value(1))
        .andExpect(jsonPath("$.data._embedded.filmography[0]._embedded").doesNotExist())
        .andExpect(jsonPath("$.data._embedded.filmography[0]._templates").doesNotExist());
  }

  @Test
  void getPersonFilmography_nonActingItem_exposesNonActingCapacity() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), anyInt(), anyInt()))
        .willReturn(pageOf(0, 20, 1, List.of(nonActingEntry(2003, "Some Film"))));

    mockMvc
        .perform(get("/people/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.type").value("nonActing"))
        .andExpect(
            jsonPath("$.data._embedded.filmography[0].capacity.department").value("Directing"))
        .andExpect(jsonPath("$.data._embedded.filmography[0].capacity.job").value("Director"));
  }

  @Test
  void getPersonFilmography_personWithNoCredits_returns200WithEmptyArray() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), anyInt(), anyInt()))
        .willReturn(pageOf(0, 20, 0, List.of()));

    mockMvc
        .perform(get("/people/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography.length()").value(0))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(0));
  }

  @Test
  void getPersonFilmography_middlePage_hasAllNavigationLinks() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), eq(1), eq(5)))
        .willReturn(pageOf(1, 5, 25, List.of(actingEntry(1999, "The Matrix"))));

    mockMvc
        .perform(get("/people/{id}/credits", id).param("page", "1").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.first.href").exists())
        .andExpect(jsonPath("$.data._links.last.href").exists())
        .andExpect(jsonPath("$.data._links.prev.href").exists())
        .andExpect(jsonPath("$.data._links.next.href").exists());
  }

  @Test
  void getPersonFilmography_firstPage_omitsPrev() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), eq(0), eq(5)))
        .willReturn(pageOf(0, 5, 25, List.of(actingEntry(1999, "The Matrix"))));

    mockMvc
        .perform(get("/people/{id}/credits", id).param("page", "0").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.next.href").exists())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist());
  }

  @Test
  void getPersonFilmography_lastPage_omitsNext() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), eq(4), eq(5)))
        .willReturn(pageOf(4, 5, 25, List.of(actingEntry(1999, "The Matrix"))));

    mockMvc
        .perform(get("/people/{id}/credits", id).param("page", "4").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.prev.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void getPersonFilmography_pageBeyondLast_isOkWithEmptyEmbeddedNotAnError() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), eq(9), eq(5)))
        .willReturn(pageOf(9, 5, 25, List.of()));

    mockMvc
        .perform(get("/people/{id}/credits", id).param("page", "9").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.filmography.length()").value(0))
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void getPersonFilmography_negativePage_isRejectedWith400() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", id).param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getPersonFilmography_sizeZero_isRejectedWith400() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", id).param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getPersonFilmography_sizeAboveMaximum_isRejectedWith400() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc
        .perform(get("/people/{id}/credits", id).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getPersonFilmography_unknownId_returns404ProblemJsonNoLinks() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), anyInt(), anyInt()))
        .willThrow(
            new ResourceNotFoundException("PERSON_NOT_FOUND", "No person found for id: " + id));

    mockMvc
        .perform(get("/people/{id}/credits", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$._links").doesNotExist())
        .andExpect(jsonPath("$._embedded").doesNotExist());
  }

  @Test
  void getPersonFilmography_malformedUuid_returns400ProblemJsonNotServerError() throws Exception {
    mockMvc
        .perform(get("/people/{id}/credits", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getPersonFilmography_noAuthorizationHeader_isNever401Or403() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPersonFilmographyUseCase.getPersonFilmography(any(), anyInt(), anyInt()))
        .willReturn(pageOf(0, 20, 0, List.of()));

    mockMvc.perform(get("/people/{id}/credits", id)).andExpect(status().isOk());
  }
}
