package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.application.port.out.GenreVocabularyPort;
import com.acme.catalog.movies.application.port.out.SearchMoviesPort;
import com.acme.catalog.movies.application.service.SearchMoviesService;
import com.acme.platform.web.PlatformWebTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
 * Web-slice coverage for refused searches and the internal-fault outcome ({@code catalog/movies}
 * "Disallowed search is refused before searching" and "Search internal fault is reported
 * generically"; design D3/D4). The real {@link SearchMoviesService} runs, so domain refusals are
 * exercised end to end through the web layer; its outbound ports are mocked so it is visible that
 * nothing is searched.
 */
@PlatformWebTest(controllers = MovieController.class)
@Import(SearchMoviesService.class)
class MovieSearchControllerFailureTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesPort searchMoviesPort;
  @MockitoBean private GenreVocabularyPort genreVocabularyPort;

  static Stream<Arguments> disallowedSearches() {
    return Stream.of(
        Arguments.of("sort=popularity", "popularity", List.of("sort")),
        Arguments.of("size=0", null, List.of("size")),
        Arguments.of("size=101", "101", List.of("size")),
        Arguments.of("page=-1", null, List.of("page")),
        Arguments.of("page=abc", "abc", List.of("page")),
        Arguments.of("page=99999999999", "99999999999", List.of("page")),
        Arguments.of("minRating=5.5", "5.5", List.of("minRating")),
        Arguments.of("minRating=-1", null, List.of("minRating")),
        Arguments.of("minRating=high", "high", List.of("minRating")),
        Arguments.of("releaseYearFrom=1e3", "1e3", List.of("releaseYearFrom")),
        Arguments.of("title=" + "x".repeat(201), "x".repeat(201), List.of("title")),
        Arguments.of("genre=", null, List.of("genre")),
        Arguments.of(
            "releaseYearFrom=2000&releaseYearTo=1990",
            null,
            List.of("releaseYearFrom", "releaseYearTo")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("disallowedSearches")
  void aDisallowedSearchIsABadRequestNamingTheParameterWithoutSearching(
      String query, String echoed, List<String> fields) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(URI.create("http://localhost/api/v1/movies?" + query)).contextPath("/api/v1"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    String raw = result.getResponse().getContentAsString();
    JsonNode body = objectMapper.readTree(raw);
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    List<String> reported = new ArrayList<>();
    body.get("errors").forEach(error -> reported.add(error.get("field").asText()));
    assertThat(reported).containsExactlyInAnyOrderElementsOf(fields);
    body.get("errors").forEach(error -> assertThat(error.has("message")).isTrue());
    if (echoed != null) {
      assertThat(raw).doesNotContain(echoed);
    }
    assertThat(raw).doesNotContain("java.", "Integer", "BigDecimal", "NumberFormat");
    Mockito.verifyNoInteractions(searchMoviesPort, genreVocabularyPort);
  }

  @Test
  void aGenreOutsideTheVocabularyIsABadRequestWithoutSearching() throws Exception {
    BDDMockito.given(genreVocabularyPort.unknownGenres(any()))
        .willReturn(java.util.Set.of("telenovela"));

    MvcResult result =
        mockMvc
            .perform(
                get(URI.create("http://localhost/api/v1/movies?genre=drama&genre=Telenovela"))
                    .contextPath("/api/v1"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    String raw = result.getResponse().getContentAsString();
    assertThat(objectMapper.readTree(raw).at("/errors/0/field").asText()).isEqualTo("genre");
    assertThat(raw).doesNotContainIgnoringCase("telenovela");
    Mockito.verifyNoInteractions(searchMoviesPort);
  }

  @Test
  void anUnexpectedFaultIsAGenericInternalError() throws Exception {
    BDDMockito.given(searchMoviesPort.search(any(), any(), any()))
        .willThrow(new RuntimeException("secret-db-host:5432 refused"));

    MvcResult result =
        mockMvc
            .perform(get(URI.create("http://localhost/api/v1/movies")).contextPath("/api/v1"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    String raw = result.getResponse().getContentAsString();
    assertThat(raw).doesNotContain("secret-db-host");
    JsonNode body = objectMapper.readTree(raw);
    assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
    assertThat(body.get("detail").asText()).isEqualTo("An unexpected error occurred.");
    assertThat(body.has("errors")).isFalse();
  }

  /** Keeps the import of the use-case port meaningful: the real service is the one wired. */
  @Autowired private SearchMoviesUseCase searchMoviesUseCase;

  @Test
  void theRealServiceIsWired() {
    assertThat(searchMoviesUseCase).isInstanceOf(SearchMoviesService.class);
  }
}
