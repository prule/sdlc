package com.acme.catalog.movies.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.application.port.in.GetMovieDetailUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link MovieController} (UC-001). {@link GetMovieDetailUseCase} is mocked at
 * the port seam.
 */
@WebMvcTest(MovieController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class MovieControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetMovieDetailUseCase getMovieDetailUseCase;

  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  @Test
  void getMovieById_existingMovieWithAllOptionalFields_returns200WithAllFieldsAndSelfLink()
      throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie =
        Movie.of(
            id,
            "The Wandering Reel",
            2019,
            List.of(Genre.DRAMA, Genre.MYSTERY),
            118,
            "A projectionist discovers a film that predicts the news.",
            new Rating(BigDecimal.valueOf(4.5)));
    given(getMovieDetailUseCase.getMovieDetail(id)).willReturn(movie);

    mockMvc
        .perform(get("/movies/{id}", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.id").value(id.toString()))
        .andExpect(jsonPath("$.data.title").value("The Wandering Reel"))
        .andExpect(jsonPath("$.data.releaseYear").value(2019))
        .andExpect(jsonPath("$.data.genres.length()").value(2))
        .andExpect(jsonPath("$.data.runtimeMinutes").value(118))
        .andExpect(jsonPath("$.data.synopsis").exists())
        .andExpect(jsonPath("$.data.rating").value(4.5))
        .andExpect(
            jsonPath("$.data._links.self.href", org.hamcrest.Matchers.endsWith("/movies/" + id)))
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists());
  }

  @Test
  void getMovieById_existingMovieWithNoOptionalFields_returns200WithOptionalKeysAbsent()
      throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie = Movie.of(id, "Silent Harbor", 2021, List.of(Genre.MYSTERY), null, null, null);
    given(getMovieDetailUseCase.getMovieDetail(id)).willReturn(movie);

    String body =
        mockMvc.perform(get("/movies/{id}", id)).andReturn().getResponse().getContentAsString();

    com.fasterxml.jackson.databind.JsonNode data =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data");

    org.assertj.core.api.Assertions.assertThat(data.has("runtimeMinutes")).isFalse();
    org.assertj.core.api.Assertions.assertThat(data.has("synopsis")).isFalse();
    org.assertj.core.api.Assertions.assertThat(data.has("rating")).isFalse();
  }

  @Test
  void getMovieById_wellFormedUnknownId_returns404ProblemJson() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieDetailUseCase.getMovieDetail(id))
        .willThrow(
            new ResourceNotFoundException("MOVIE_NOT_FOUND", "No movie found with id " + id));

    mockMvc
        .perform(get("/movies/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getMovieById_malformedId_returns400WithoutInvokingTheUseCase() throws Exception {
    mockMvc
        .perform(get("/movies/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(getMovieDetailUseCase);
  }

  @Test
  void getMovieById_requiresNoAuthentication() throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie = Movie.of(id, "Silent Harbor", 2021, List.of(Genre.MYSTERY), null, null, null);
    given(getMovieDetailUseCase.getMovieDetail(any())).willReturn(movie);

    mockMvc.perform(get("/movies/{id}", id)).andExpect(status().isOk());
  }

  @Test
  void getMovies_noCriteria_returns200WithSummaryShapeAndItemSelfLinkAndNoSynopsis()
      throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie =
        Movie.of(
            id,
            "The Wandering Reel",
            2019,
            List.of(Genre.DRAMA, Genre.MYSTERY),
            118,
            "A synopsis that must not appear in the summary.",
            new Rating(BigDecimal.valueOf(4.5)));
    MoviePage moviePage = new MoviePage(List.of(movie), 0, 20, 1);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc
        .perform(get("/movies"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data._embedded.movies.length()").value(1))
        .andExpect(jsonPath("$.data._embedded.movies[0].id").value(id.toString()))
        .andExpect(jsonPath("$.data._embedded.movies[0].title").value("The Wandering Reel"))
        .andExpect(jsonPath("$.data._embedded.movies[0].releaseYear").value(2019))
        .andExpect(jsonPath("$.data._embedded.movies[0].genres.length()").value(2))
        .andExpect(jsonPath("$.data._embedded.movies[0].runtimeMinutes").value(118))
        .andExpect(jsonPath("$.data._embedded.movies[0].rating").value(4.5))
        .andExpect(
            jsonPath(
                "$.data._embedded.movies[0]._links.self.href",
                org.hamcrest.Matchers.endsWith("/movies/" + id)))
        .andExpect(jsonPath("$.data._embedded.movies[0].synopsis").doesNotExist())
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists());
  }

  @Test
  void getMovies_summaryWithNoOptionalFields_omitsRuntimeAndRatingKeys() throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie = Movie.of(id, "Silent Harbor", 2021, List.of(Genre.MYSTERY), null, null, null);
    MoviePage moviePage = new MoviePage(List.of(movie), 0, 20, 1);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    String body = mockMvc.perform(get("/movies")).andReturn().getResponse().getContentAsString();
    com.fasterxml.jackson.databind.JsonNode item =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(body)
            .path("data")
            .path("_embedded")
            .path("movies")
            .get(0);

    org.assertj.core.api.Assertions.assertThat(item.has("runtimeMinutes")).isFalse();
    org.assertj.core.api.Assertions.assertThat(item.has("rating")).isFalse();
    org.assertj.core.api.Assertions.assertThat(item.has("synopsis")).isFalse();
  }

  @Test
  void getMovies_middlePage_reportsPaginationMetadataAndAllNavigationLinks() throws Exception {
    Movie movie =
        Movie.of(UUID.randomUUID(), "Middle", 2019, List.of(Genre.DRAMA), null, null, null);
    MoviePage moviePage = new MoviePage(List.of(movie), 1, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc
        .perform(get("/movies").param("page", "1").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.pagination.page").value(1))
        .andExpect(jsonPath("$.meta.pagination.size").value(10))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(30))
        .andExpect(jsonPath("$.meta.pagination.totalPages").value(3))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(
            jsonPath("$.data._links.first.href", org.hamcrest.Matchers.containsString("page=0")))
        .andExpect(
            jsonPath("$.data._links.last.href", org.hamcrest.Matchers.containsString("page=2")))
        .andExpect(
            jsonPath("$.data._links.prev.href", org.hamcrest.Matchers.containsString("page=0")))
        .andExpect(
            jsonPath("$.data._links.next.href", org.hamcrest.Matchers.containsString("page=2")));
  }

  @Test
  void getMovies_firstPage_omitsPrevButHasNext() throws Exception {
    Movie movie =
        Movie.of(UUID.randomUUID(), "First", 2019, List.of(Genre.DRAMA), null, null, null);
    MoviePage moviePage = new MoviePage(List.of(movie), 0, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc
        .perform(get("/movies").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.prev").doesNotExist())
        .andExpect(jsonPath("$.data._links.next.href").exists());
  }

  @Test
  void getMovies_lastPage_omitsNextButHasPrev() throws Exception {
    Movie movie = Movie.of(UUID.randomUUID(), "Last", 2019, List.of(Genre.DRAMA), null, null, null);
    MoviePage moviePage = new MoviePage(List.of(movie), 2, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc
        .perform(get("/movies").param("page", "2").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._links.next").doesNotExist())
        .andExpect(jsonPath("$.data._links.prev.href").exists());
  }

  @Test
  void getMovies_defaultSize_isTwenty() throws Exception {
    MoviePage moviePage = new MoviePage(List.of(), 0, 20, 0);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc
        .perform(get("/movies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meta.pagination.size").value(20));
  }

  @Test
  void getMovies_criteriaMatchNothing_returns200WithEmptyListAndZeroTotalAndNoNextLink()
      throws Exception {
    MoviePage moviePage = new MoviePage(List.of(), 0, 20, 0);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc
        .perform(get("/movies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.movies.length()").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(0))
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void getMovies_pageBeyondLast_returns200WithEmptyListNonZeroTotalAndSelfLinkButNoNext()
      throws Exception {
    MoviePage moviePage = new MoviePage(List.of(), 5, 20, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc
        .perform(get("/movies").param("page", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.movies.length()").value(0))
        .andExpect(jsonPath("$.meta.pagination.totalElements").value(30))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.next").doesNotExist());
  }

  @Test
  void getMovies_unsupportedSortField_returns400ProblemJsonWithoutInvokingTheUseCase()
      throws Exception {
    mockMvc
        .perform(get("/movies").param("sort", "popularity,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void getMovies_negativePage_returns400ProblemJson() throws Exception {
    mockMvc
        .perform(get("/movies").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void getMovies_sizeZero_returns400ProblemJson() throws Exception {
    mockMvc
        .perform(get("/movies").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void getMovies_sizeAboveMax_returns400ProblemJson() throws Exception {
    mockMvc
        .perform(get("/movies").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void getMovies_minRatingAboveMax_returns400ProblemJson() throws Exception {
    mockMvc
        .perform(get("/movies").param("minRating", "6"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

    verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void getMovies_unknownGenre_returns400ProblemJson() throws Exception {
    mockMvc
        .perform(get("/movies").param("genre", "NotAGenre"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void getMovies_requiresNoAuthentication() throws Exception {
    MoviePage moviePage = new MoviePage(List.of(), 0, 20, 0);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    mockMvc.perform(get("/movies")).andExpect(status().isOk());
  }
}
