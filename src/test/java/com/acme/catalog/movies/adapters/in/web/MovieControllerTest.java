package com.acme.catalog.movies.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.application.port.in.GetMovieCreditsUseCase;
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

  @MockitoBean private GetMovieCreditsUseCase getMovieCreditsUseCase;

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
        .andExpect(
            jsonPath(
                "$.data._links.credits.href",
                org.hamcrest.Matchers.endsWith("/movies/" + id + "/credits")))
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
  void getMovies_filteredAndSortedMiddlePage_everyNavLinkPreservesAllFilterAndSortParams()
      throws Exception {
    Movie movie =
        Movie.of(UUID.randomUUID(), "Middle", 2019, List.of(Genre.DRAMA), null, null, null);
    MoviePage moviePage = new MoviePage(List.of(movie), 1, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    String body =
        mockMvc
            .perform(
                get("/movies")
                    .param("title", "reel")
                    .param("genre", "DRAMA", "CRIME")
                    .param("releaseYearFrom", "2000")
                    .param("releaseYearTo", "2025")
                    .param("minRating", "3.5")
                    .param("sort", "title,asc")
                    .param("page", "1")
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    com.fasterxml.jackson.databind.JsonNode links =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(body)
            .path("data")
            .path("_links");

    java.util.Map<String, java.util.Set<String>> expectedNonPageParams =
        java.util.Map.of(
            "title", java.util.Set.of("reel"),
            "genre", java.util.Set.of("DRAMA", "CRIME"),
            "releaseYearFrom", java.util.Set.of("2000"),
            "releaseYearTo", java.util.Set.of("2025"),
            "minRating", java.util.Set.of("3.5"),
            "sort", java.util.Set.of("title,asc"),
            "size", java.util.Set.of("10"));

    for (String rel : List.of("self", "first", "last", "prev", "next")) {
      assertLinkCarriesParams(links.path(rel).path("href").asText(), expectedNonPageParams);
    }
    assertLinkHasPage(links.path("self").path("href").asText(), 1);
    assertLinkHasPage(links.path("first").path("href").asText(), 0);
    assertLinkHasPage(links.path("last").path("href").asText(), 2);
    assertLinkHasPage(links.path("prev").path("href").asText(), 0);
    assertLinkHasPage(links.path("next").path("href").asText(), 2);
  }

  @Test
  void getMovies_sortOmitted_linksCarryOnlyPageAndSize() throws Exception {
    assertLinksCarryOnlyPageAndSize(get("/movies").param("page", "1").param("size", "10"));
  }

  @Test
  void getMovies_explicitDefaultSort_linksCarryOnlyPageAndSizeSameAsOmitted() throws Exception {
    assertLinksCarryOnlyPageAndSize(
        get("/movies").param("page", "1").param("size", "10").param("sort", "releaseYear,desc"));
  }

  @Test
  void getMovies_nonDefaultSort_linksCarrySortParam() throws Exception {
    Movie movie =
        Movie.of(UUID.randomUUID(), "Middle", 2019, List.of(Genre.DRAMA), null, null, null);
    MoviePage moviePage = new MoviePage(List.of(movie), 1, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    String body =
        mockMvc
            .perform(
                get("/movies").param("page", "1").param("size", "10").param("sort", "title,asc"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    com.fasterxml.jackson.databind.JsonNode links =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(body)
            .path("data")
            .path("_links");

    for (String rel : List.of("self", "first", "last", "prev", "next")) {
      assertLinkCarriesParams(
          links.path(rel).path("href").asText(),
          java.util.Map.of("sort", java.util.Set.of("title,asc")));
    }
  }

  private void assertLinksCarryOnlyPageAndSize(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    Movie movie =
        Movie.of(UUID.randomUUID(), "Middle", 2019, List.of(Genre.DRAMA), null, null, null);
    MoviePage moviePage = new MoviePage(List.of(movie), 1, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(moviePage);

    String body =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    com.fasterxml.jackson.databind.JsonNode links =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(body)
            .path("data")
            .path("_links");

    for (String rel : List.of("self", "first", "last", "prev", "next")) {
      String href = links.path(rel).path("href").asText();
      org.springframework.util.MultiValueMap<String, String> params = queryParams(href);
      org.assertj.core.api.Assertions.assertThat(params.keySet())
          .as("query params of %s link", rel)
          .containsExactlyInAnyOrder("page", "size");
    }
  }

  @Test
  void getMovies_filteredBoundaryPages_omitPrevAndNextCorrectlyWhileKeepingFilterParams()
      throws Exception {
    // First page: 30 total, size 10 -> 3 pages; prev absent, next present.
    Movie movie =
        Movie.of(UUID.randomUUID(), "First", 2019, List.of(Genre.DRAMA), null, null, null);
    MoviePage firstPage = new MoviePage(List.of(movie), 0, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(firstPage);

    String firstBody =
        mockMvc
            .perform(
                get("/movies")
                    .param("genre", "DRAMA")
                    .param("sort", "title,asc")
                    .param("page", "0")
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    com.fasterxml.jackson.databind.JsonNode firstLinks =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(firstBody)
            .path("data")
            .path("_links");
    org.assertj.core.api.Assertions.assertThat(firstLinks.has("prev")).isFalse();
    assertLinkCarriesParams(
        firstLinks.path("next").path("href").asText(),
        java.util.Map.of(
            "genre", java.util.Set.of("DRAMA"), "sort", java.util.Set.of("title,asc")));

    // Last page: page 2 of 3; next absent, prev present.
    MoviePage lastPage = new MoviePage(List.of(movie), 2, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(lastPage);

    String lastBody =
        mockMvc
            .perform(
                get("/movies")
                    .param("genre", "DRAMA")
                    .param("sort", "title,asc")
                    .param("page", "2")
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    com.fasterxml.jackson.databind.JsonNode lastLinks =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(lastBody)
            .path("data")
            .path("_links");
    org.assertj.core.api.Assertions.assertThat(lastLinks.has("next")).isFalse();
    assertLinkCarriesParams(
        lastLinks.path("prev").path("href").asText(),
        java.util.Map.of(
            "genre", java.util.Set.of("DRAMA"), "sort", java.util.Set.of("title,asc")));

    // Page beyond last: 200 with empty embedded, self still carries filter params.
    MoviePage beyondLastPage = new MoviePage(List.of(), 5, 10, 30);
    given(
            searchMoviesUseCase.search(
                any(MovieSearchCriteria.class), any(MoviePageRequest.class), any(MovieSort.class)))
        .willReturn(beyondLastPage);

    String beyondLastBody =
        mockMvc
            .perform(
                get("/movies")
                    .param("genre", "DRAMA")
                    .param("sort", "title,asc")
                    .param("page", "5")
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data._embedded.movies.length()").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    com.fasterxml.jackson.databind.JsonNode beyondLastLinks =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(beyondLastBody)
            .path("data")
            .path("_links");
    assertLinkCarriesParams(
        beyondLastLinks.path("self").path("href").asText(),
        java.util.Map.of(
            "genre", java.util.Set.of("DRAMA"), "sort", java.util.Set.of("title,asc")));
  }

  @Test
  void getMovies_invalidPageWithFiltersPresent_returns400WithoutLinksOrEmbedded() throws Exception {
    mockMvc
        .perform(get("/movies").param("genre", "DRAMA").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$._links").doesNotExist())
        .andExpect(jsonPath("$._embedded").doesNotExist())
        .andExpect(jsonPath("$.data").doesNotExist());

    verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void getMovies_invalidSizeWithFiltersPresent_returns400WithoutLinksOrEmbedded() throws Exception {
    mockMvc
        .perform(get("/movies").param("genre", "DRAMA").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$._links").doesNotExist())
        .andExpect(jsonPath("$._embedded").doesNotExist())
        .andExpect(jsonPath("$.data").doesNotExist());

    verifyNoInteractions(searchMoviesUseCase);
  }

  private static org.springframework.util.MultiValueMap<String, String> queryParams(String href) {
    return org.springframework.web.util.UriComponentsBuilder.fromUriString(href)
        .build()
        .getQueryParams();
  }

  private static void assertLinkCarriesParams(
      String href, java.util.Map<String, java.util.Set<String>> expected) {
    org.springframework.util.MultiValueMap<String, String> actual = queryParams(href);
    expected.forEach(
        (name, values) ->
            org.assertj.core.api.Assertions.assertThat(decodeAll(actual.get(name)))
                .as("param %s on link %s", name, href)
                .isEqualTo(values));
  }

  /**
   * Decodes each raw (percent-encoded) query-param value so comparisons are semantic — e.g. {@code
   * title%2Casc} and {@code title,asc} are equal — rather than raw-string comparisons.
   */
  private static java.util.Set<String> decodeAll(List<String> rawValues) {
    if (rawValues == null) {
      return java.util.Set.of();
    }
    return rawValues.stream()
        .map(
            value ->
                org.springframework.web.util.UriUtils.decode(
                    value, java.nio.charset.StandardCharsets.UTF_8))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static void assertLinkHasPage(String href, int expectedPage) {
    org.springframework.util.MultiValueMap<String, String> actual = queryParams(href);
    org.assertj.core.api.Assertions.assertThat(actual.getFirst("page"))
        .as("page param on link %s", href)
        .isEqualTo(String.valueOf(expectedPage));
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
