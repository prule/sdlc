package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.platform.web.GlobalExceptionHandler;
import com.acme.platform.web.PlatformWebTest;
import com.acme.shared.domain.InvalidRequestException;
import com.acme.testsupport.LogCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-slice coverage for the {@code searchMovies} failure outcomes ({@code catalog/movies} "Search
 * asked in a way that isn't allowed is refused before searching" and "Search internal fault is
 * reported generically").
 */
@PlatformWebTest(controllers = MovieController.class)
class MovieControllerSearchFailureTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  /** Refusals the framework detects (bounds and types): the use case is never invoked. */
  private static Stream<Arguments> frameworkRefusals() {
    return Stream.of(
        Arguments.of("size", "101", "size"),
        Arguments.of("size", "0", "size"),
        Arguments.of("size", "-3", "size"),
        Arguments.of("size", "ten", "size"),
        Arguments.of("page", "-1", "page"),
        Arguments.of("page", "first", "page"),
        Arguments.of("page", "1.5", "page"),
        Arguments.of("minRating", "5.5", "minRating"),
        Arguments.of("minRating", "-1", "minRating"),
        Arguments.of("minRating", "high", "minRating"),
        Arguments.of("releaseYearFrom", "nineteen", "releaseYearFrom"),
        Arguments.of("releaseYearTo", "20x0", "releaseYearTo"));
  }

  @ParameterizedTest(name = "{0}={1}")
  @MethodSource("frameworkRefusals")
  void frameworkDetectedRefusalIsANamedBadRequestAndNeverSearches(
      String parameter, String value, String named) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies").contextPath("/api/v1").param(parameter, value))
            .andReturn();

    assertNamedBadRequest(result, named, value);
    Mockito.verifyNoInteractions(searchMoviesUseCase);
  }

  /** Refusals the domain detects: the use case raises the named refusal before searching. */
  private static Stream<Arguments> domainRefusals() {
    return Stream.of(
        Arguments.of(List.of("genre", "Spaghetti"), "genre"),
        Arguments.of(List.of("sort", "popularity"), "sort"),
        Arguments.of(List.of("sort", "TITLE"), "sort"),
        Arguments.of(
            List.of("releaseYearFrom", "2010", "releaseYearTo", "2000"), "releaseYearFrom"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("domainRefusals")
  void domainDetectedRefusalIsANamedBadRequest(List<String> params, String named) throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willThrow(new InvalidRequestException(named));
    var request = get("/api/v1/movies").contextPath("/api/v1");
    for (int i = 0; i < params.size(); i += 2) {
      request.param(params.get(i), params.get(i + 1));
    }

    MvcResult result = mockMvc.perform(request).andReturn();

    assertNamedBadRequest(result, named, params.get(1));
  }

  @Test
  void unexpectedFaultIsAGenericInternalErrorLoggedWithTheCorrelationId() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willThrow(new RuntimeException("secret-db-host:5432 refused"));

    try (LogCaptor logCaptor = LogCaptor.forClass(GlobalExceptionHandler.class)) {
      MvcResult result = mockMvc.perform(get("/api/v1/movies").contextPath("/api/v1")).andReturn();

      assertThat(result.getResponse().getStatus()).isEqualTo(500);
      assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
      String raw = result.getResponse().getContentAsString();
      assertThat(raw).doesNotContain("secret-db-host");
      JsonNode body = objectMapper.readTree(raw);
      assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
      assertThat(body.get("detail").asText()).isEqualTo("An unexpected error occurred.");
      String correlationId = result.getResponse().getHeader("X-Correlation-Id");
      assertThat(body.get("correlationId").asText()).isEqualTo(correlationId);

      List<ILoggingEvent> events = logCaptor.events();
      assertThat(events)
          .anySatisfy(
              event -> {
                assertThat(event.getLevel().toString()).isEqualTo("ERROR");
                assertThat(event.getFormattedMessage()).contains(correlationId);
              });
    }
  }

  private void assertNamedBadRequest(MvcResult result, String named, String suppliedValue)
      throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("type").asText()).isEqualTo("urn:problem-type:bad-request");
    assertThat(body.hasNonNull("title")).isTrue();
    assertThat(body.get("status").asInt()).isEqualTo(400);
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    assertThat(body.hasNonNull("correlationId")).isTrue();
    assertThat(body.has("instance")).isFalse();
    assertThat(body.get("detail").asText())
        .isEqualTo("The request parameter '" + named + "' is not valid.");
    // Every value used here is distinct from every parameter name, so this check is meaningful.
    assertThat(body.get("detail").asText()).doesNotContain(suppliedValue);
  }
}
