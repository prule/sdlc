package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.credits.application.port.in.GetMovieCreditsUseCase;
import com.acme.catalog.movies.application.port.in.GetMovieByIdUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.MovieSortField;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.SortDirection;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
 * Web-slice test for {@link MovieController#listMovies}. {@link SearchMoviesUseCase} is mocked at
 * the port seam.
 */
@WebMvcTest(MovieController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class MovieSearchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetMovieByIdUseCase getMovieByIdUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;
  @MockitoBean private GetMovieCreditsUseCase getMovieCreditsUseCase;

  private static Movie movie(int i) {
    return new Movie(
        new MovieId(UUID.nameUUIDFromBytes(("movie-" + i).getBytes())),
        "Movie " + i,
        2000 + i,
        List.of(new Genre("Drama")),
        Optional.of(100 + i),
        Optional.empty(),
        Optional.of(new Rating(BigDecimal.valueOf(4))));
  }

  private static Movie movieWithoutOptionals(int i) {
    return new Movie(
        new MovieId(UUID.nameUUIDFromBytes(("movie-" + i).getBytes())),
        "Obscure Movie " + i,
        1990 + i,
        List.of(new Genre("Drama")),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static MoviePage pageOf(int page, int size, long totalElements, List<Movie> items) {
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return new MoviePage(items, page, size, totalElements, totalPages);
  }

  @Test
  void listMovies_defaultRequest_returnsHalCollectionShape() throws Exception {
    given(
            searchMoviesUseCase.searchMovies(
                eq(MovieSearchCriteria.NONE), eq(0), eq(20), eq(MovieSort.DEFAULT)))
        .willReturn(pageOf(0, 20, 1, List.of(movie(1))));

    mockMvc
        .perform(get("/movies"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.data._embedded.movies.length()").value(1))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.meta.pagination.page").value(0))
        .andExpect(jsonPath("$.meta.pagination.size").value(20))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(1))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(1));
  }

  @Test
  void listMovies_itemWithAllOptionals_exposesFieldsAndSelfLink() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), anyInt(), anyInt(), any()))
        .willReturn(pageOf(0, 20, 1, List.of(movie(1))));

    mockMvc
        .perform(get("/movies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.movies[0].id").exists())
        .andExpect(jsonPath("$.data._embedded.movies[0].title").value("Movie 1"))
        .andExpect(jsonPath("$.data._embedded.movies[0].releaseYear").value(2001))
        .andExpect(jsonPath("$.data._embedded.movies[0].genres", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.data._embedded.movies[0].runtimeMinutes").value(101))
        .andExpect(jsonPath("$.data._embedded.movies[0].rating").value(4))
        .andExpect(jsonPath("$.data._embedded.movies[0]._links.self.href").exists())
        .andExpect(jsonPath("$.data._embedded.movies[0].synopsis").doesNotExist());
  }

  @Test
  void listMovies_itemWithoutOptionals_omitsRuntimeAndRatingNotNull() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), anyInt(), anyInt(), any()))
        .willReturn(pageOf(0, 20, 1, List.of(movieWithoutOptionals(1))));

    String body =
        mockMvc
            .perform(get("/movies"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode item =
        new ObjectMapper().readTree(body).path("data").path("_embedded").path("movies").get(0);

    assertThat(item.has("runtimeMinutes")).as("runtimeMinutes present").isFalse();
    assertThat(item.has("rating")).as("rating present").isFalse();
  }

  @Test
  void listMovies_middlePage_hasAllNavigationLinks() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), eq(1), eq(5), any()))
        .willReturn(pageOf(1, 5, 25, List.of(movie(1))));

    mockMvc
        .perform(get("/movies").param("page", "1").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.first.href").exists())
        .andExpect(jsonPath("$.data._links.last.href").exists())
        .andExpect(jsonPath("$.data._links.prev.href").exists())
        .andExpect(jsonPath("$.data._links.next.href").exists());
  }

  @Test
  void listMovies_firstPage_omitsPrev() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), eq(0), eq(5), any()))
        .willReturn(pageOf(0, 5, 25, List.of(movie(1))));

    mockMvc
        .perform(get("/movies").param("page", "0").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.next.href").exists())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist());
  }

  @Test
  void listMovies_lastPage_omitsNext() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), eq(4), eq(5), any()))
        .willReturn(pageOf(4, 5, 25, List.of(movie(1))));

    mockMvc
        .perform(get("/movies").param("page", "4").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.prev.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void listMovies_emptyResult_isOkWithNoNextOrPrevAndFirstLastAtPageZero() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), eq(0), eq(20), any()))
        .willReturn(pageOf(0, 20, 0, List.of()));

    mockMvc
        .perform(get("/movies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.movies.length()").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(0))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.first.href").exists())
        .andExpect(jsonPath("$.data._links.last.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist());
  }

  @Test
  void listMovies_pageBeyondLastPage_isOkWithEmptyEmbeddedNotAnError() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), eq(9), eq(5), any()))
        .willReturn(pageOf(9, 5, 25, List.of()));

    mockMvc
        .perform(get("/movies").param("page", "9").param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.movies.length()").value(0))
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void listMovies_filtersAndSort_arePassedToTheUseCase() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), anyInt(), anyInt(), any()))
        .willReturn(pageOf(0, 20, 0, List.of()));

    mockMvc
        .perform(
            get("/movies")
                .param("title", "matrix")
                .param("genre", "Drama", "Crime")
                .param("yearFrom", "1990")
                .param("yearTo", "1999")
                .param("minRating", "4")
                .param("sort", "title,asc"))
        .andExpect(status().isOk());

    MovieSearchCriteria expectedCriteria =
        new MovieSearchCriteria(
            Optional.of("matrix"),
            List.of(new Genre("Drama"), new Genre("Crime")),
            Optional.of(1990),
            Optional.of(1999),
            Optional.of(new Rating(BigDecimal.valueOf(4))));
    verify(searchMoviesUseCase)
        .searchMovies(
            eq(expectedCriteria),
            eq(0),
            eq(20),
            eq(new MovieSort(MovieSortField.TITLE, SortDirection.ASC)));
  }

  @Test
  void listMovies_unknownSortField_isRejectedWith400ProblemJsonNotServerError() throws Exception {
    mockMvc
        .perform(get("/movies").param("sort", "bogus,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void listMovies_unknownSortDirection_isRejectedWith400() throws Exception {
    mockMvc
        .perform(get("/movies").param("sort", "title,sideways"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void listMovies_negativePage_isRejectedWith400() throws Exception {
    mockMvc
        .perform(get("/movies").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void listMovies_sizeZero_isRejectedWith400() throws Exception {
    mockMvc.perform(get("/movies").param("size", "0")).andExpect(status().isBadRequest());
  }

  @Test
  void listMovies_sizeAboveMaximum_isRejectedWith400() throws Exception {
    mockMvc.perform(get("/movies").param("size", "101")).andExpect(status().isBadRequest());
  }

  @Test
  void listMovies_nonNumericSize_isRejectedWith400NotServerError() throws Exception {
    mockMvc
        .perform(get("/movies").param("size", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void listMovies_noAuthorizationHeader_isNever401Or403() throws Exception {
    given(searchMoviesUseCase.searchMovies(any(), anyInt(), anyInt(), any()))
        .willReturn(pageOf(0, 20, 0, List.of()));

    mockMvc.perform(get("/movies")).andExpect(status().isOk());
  }
}
