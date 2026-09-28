package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.InvalidSearchCriterionException;
import com.acme.catalog.movies.domain.model.SearchCriterion;
import com.acme.platform.web.CollectionLinksFactory;
import com.acme.platform.web.PlatformWebTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-slice coverage for {@code /movies} search refusals and the fault, through the real {@link
 * MovieController} behind its {@code @Validated} proxy (design D2/D6, task 6.3b).
 */
@PlatformWebTest(controllers = MovieController.class)
@Import(CollectionLinksFactory.class)
class MovieSearchRefusalTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  private static Stream<Arguments> boundsViolations() {
    return Stream.of(
        Arguments.of("page", "-1", "page"),
        Arguments.of("size", "0", "size"),
        Arguments.of("size", "101", "size"),
        Arguments.of("minRating", "5.1", "minRating"),
        Arguments.of("minRating", "-0.1", "minRating"));
  }

  @ParameterizedTest
  @MethodSource("boundsViolations")
  void boundsViolationIsBadRequestNamingTheParameterAndNeverInvokesTheUseCase(
      String param, String value, String expectedName) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies").contextPath("/api/v1").param(param, value))
            .andReturn();

    JsonNode body = assertBadRequestNaming(result, expectedName);
    Mockito.verifyNoInteractions(searchMoviesUseCase);
  }

  private static Stream<Arguments> typeMismatches() {
    return Stream.of(
        Arguments.of("page", "abc", "page"),
        Arguments.of("page", "99999999999", "page"),
        Arguments.of("size", "1.5", "size"),
        Arguments.of("minRating", "abc", "minRating"),
        Arguments.of("releaseYearTo", "abc", "releaseYearTo"));
  }

  @ParameterizedTest
  @MethodSource("typeMismatches")
  void typeMismatchIsBadRequestNamingTheParameter(String param, String value, String expectedName)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies").contextPath("/api/v1").param(param, value))
            .andReturn();

    assertBadRequestNaming(result, expectedName);
  }

  private static Stream<Arguments> domainDetectedFailures() {
    return Stream.of(
        Arguments.of("sort", "", "sort"),
        Arguments.of("sort", "Title", "sort"),
        Arguments.of("sort", "synopsis", "sort"));
  }

  @ParameterizedTest
  @MethodSource("domainDetectedFailures")
  void domainDetectedSortFailureIsBadRequestNamingSortAndNeverInvokesTheUseCase(
      String param, String value, String expectedName) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies").contextPath("/api/v1").param(param, value))
            .andReturn();

    assertBadRequestNaming(result, expectedName);
    Mockito.verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void repeatedSortIsBadRequestNamingSort() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/movies")
                    .contextPath("/api/v1")
                    .param("sort", "title")
                    .param("sort", "rating"))
            .andReturn();

    assertBadRequestNaming(result, "sort");
    Mockito.verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void reversedYearRangeIsBadRequestNamingReleaseYearFromAndNeverInvokesTheUseCase()
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/movies")
                    .contextPath("/api/v1")
                    .param("releaseYearFrom", "2010")
                    .param("releaseYearTo", "2000"))
            .andReturn();

    assertBadRequestNaming(result, "releaseYearFrom");
    Mockito.verifyNoInteractions(searchMoviesUseCase);
  }

  @Test
  void unknownGenreFromTheUseCaseIsBadRequestNamingGenreNotInternalError() throws Exception {
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willThrow(new InvalidSearchCriterionException(SearchCriterion.GENRE));

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies").contextPath("/api/v1").param("genre", "Western"))
            .andReturn();

    assertBadRequestNaming(result, "genre");
  }

  @Test
  void anEmptyGenreFromTheUseCaseIsBadRequestNamingGenre() throws Exception {
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willThrow(new InvalidSearchCriterionException(SearchCriterion.GENRE));

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies").contextPath("/api/v1").param("genre", ""))
            .andReturn();

    assertBadRequestNaming(result, "genre");
  }

  @Test
  void unexpectedFaultIsInternalErrorRevealingNothing() throws Exception {
    BDDMockito.given(
            searchMoviesUseCase.search(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willThrow(new RuntimeException("secret-db-host:5432 refused"));

    MvcResult result = mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    String raw = result.getResponse().getContentAsString();
    assertThat(raw).doesNotContain("secret-db-host");
    JsonNode body = objectMapper.readTree(raw);
    assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
    assertThat(body.get("detail").asText()).isEqualTo("An unexpected error occurred.");
  }

  private JsonNode assertBadRequestNaming(MvcResult result, String parameterName) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    assertThat(body.get("detail").asText())
        .isEqualTo("Query parameter '" + parameterName + "' is invalid.");
    return body;
  }
}
