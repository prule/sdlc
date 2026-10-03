package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.ResultPage;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import com.acme.platform.web.PlatformWebTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Web-slice coverage for the {@code searchMovies} 200 mapping and navigation links ({@code
 * catalog/movies} "Search and browse movies", "Search results are movie summaries" and "Pages link
 * to themselves and their neighbours with the same criteria"; design D6). The use case is mocked.
 */
@PlatformWebTest(controllers = MovieController.class)
class MovieSearchControllerTest {

  private static final String BASE = "http://localhost/api/v1/movies";
  private static final UUID ARRIVAL = UUID.fromString("6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b");
  private static final UUID REEL = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  private static MovieSummary arrival() {
    return new MovieSummary(
        new MovieId(ARRIVAL),
        "Arrival",
        2016,
        List.of("Sci-Fi", "Drama"),
        Optional.of(new RuntimeMinutes(116)),
        Optional.of(new Rating(new BigDecimal("4.5"))));
  }

  private static MovieSummary reel() {
    return new MovieSummary(
        new MovieId(REEL), "Untitled Reel", 1974, List.of(), Optional.empty(), Optional.empty());
  }

  private void answers(List<MovieSummary> items, int page, int size, long total) {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(items, page, size, total));
  }

  private static MockHttpServletRequestBuilder search(String query) {
    return get(URI.create(query.isEmpty() ? BASE : BASE + "?" + query)).contextPath("/api/v1");
  }

  private JsonNode body(MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result = mockMvc.perform(request).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static String href(JsonNode body, String rel) {
    JsonNode link = body.path("data").path("_links").path(rel);
    return link.isMissingNode() ? null : link.get("href").asText();
  }

  @Test
  void browseIsTheSuccessEnvelopeWithSummariesAndPagination() throws Exception {
    answers(List.of(arrival(), reel()), 0, 20, 2);

    MvcResult result =
        mockMvc
            .perform(search("").accept(MediaType.parseMediaType("application/problem+json")))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.properties()).extracting(e -> e.getKey()).containsExactly("data", "meta");
    assertThat(body.at("/meta/correlationId").asText())
        .isEqualTo(result.getResponse().getHeader("X-Correlation-Id"));
    assertThat(body.at("/meta/pagination").toString())
        .isEqualTo("{\"page\":0,\"size\":20,\"totalElements\":2,\"totalPages\":1}");
    assertThat(body.at("/data/_embedded/movies")).hasSize(2);
  }

  @Test
  void passesTheRawRequestToTheUseCase() throws Exception {
    answers(List.of(), 1, 5, 0);

    body(
        search(
            "title=heist&genre=Drama&genre=sci-fi&releaseYearFrom=1990&releaseYearTo=1999"
                + "&minRating=3.5&sort=-rating&page=1&size=5"));

    ArgumentCaptor<SearchMoviesQuery> query = ArgumentCaptor.forClass(SearchMoviesQuery.class);
    BDDMockito.then(searchMoviesUseCase).should().search(query.capture());
    assertThat(query.getValue())
        .isEqualTo(
            new SearchMoviesQuery(
                "heist",
                List.of("Drama", "sci-fi"),
                1990,
                1999,
                new BigDecimal("3.5"),
                "-rating",
                1,
                5));
  }

  @Test
  void summariesHoldExactlyTheirMembersAndLinkToTheDetails() throws Exception {
    answers(List.of(arrival(), reel()), 0, 20, 2);

    JsonNode movies = body(search("")).at("/data/_embedded/movies");

    JsonNode full = movies.get(0);
    List<String> members = new ArrayList<>();
    full.fieldNames().forEachRemaining(members::add);
    assertThat(members)
        .containsExactlyInAnyOrder(
            "id", "title", "releaseYear", "genres", "runtimeMinutes", "rating", "_links");
    assertThat(full.get("id").asText()).isEqualTo(ARRIVAL.toString());
    assertThat(full.get("genres").toString()).isEqualTo("[\"Drama\",\"Sci-Fi\"]");
    assertThat(full.get("rating").toString()).isEqualTo("4.5");
    assertThat(full.at("/_links/self/href").asText()).isEqualTo(BASE + "/" + ARRIVAL);
    assertThat(full.get("_links").size()).isEqualTo(1);

    JsonNode minimal = movies.get(1);
    assertThat(minimal.has("runtimeMinutes")).isFalse();
    assertThat(minimal.has("rating")).isFalse();
    assertThat(minimal.has("synopsis")).isFalse();
    assertThat(minimal.get("genres").toString()).isEqualTo("[]");
  }

  @Test
  void ratingUsesItsShortestExactForm() throws Exception {
    MovieSummary five =
        new MovieSummary(
            new MovieId(ARRIVAL),
            "Five",
            2000,
            List.of(),
            Optional.empty(),
            Optional.of(new Rating(new BigDecimal("5.0"))));
    answers(List.of(five), 0, 20, 1);

    String raw = mockMvc.perform(search("")).andReturn().getResponse().getContentAsString();

    assertThat(raw).contains("\"rating\":5,").doesNotContain("5.0");
  }

  @Test
  void summaryLinksHonourForwardedHeaders() throws Exception {
    answers(List.of(arrival()), 0, 20, 1);

    JsonNode body =
        body(
            search("")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "api.example.test"));

    assertThat(body.at("/data/_embedded/movies/0/_links/self/href").asText())
        .isEqualTo("https://api.example.test/api/v1/movies/" + ARRIVAL);
    assertThat(href(body, "self")).isEqualTo("https://api.example.test/api/v1/movies");
  }

  // --- navigation links ---

  private static final String CRITERIA =
      "title=heist&genre=drama&genre=Sci-Fi&minRating=3.5&sort=-rating&size=20";

  @Test
  void firstPageLinksKeepTheCriteria() throws Exception {
    answers(List.of(), 0, 20, 45);

    JsonNode body = body(search(CRITERIA + "&page=0"));

    assertThat(href(body, "self")).isEqualTo(BASE + "?" + CRITERIA + "&page=0");
    assertThat(href(body, "first")).isEqualTo(BASE + "?" + CRITERIA + "&page=0");
    assertThat(href(body, "next")).isEqualTo(BASE + "?" + CRITERIA + "&page=1");
    assertThat(href(body, "last")).isEqualTo(BASE + "?" + CRITERIA + "&page=2");
    assertThat(href(body, "prev")).isNull();
  }

  @Test
  void middlePageHasBothNeighbours() throws Exception {
    answers(List.of(), 1, 20, 45);

    JsonNode body = body(search(CRITERIA + "&page=1"));

    assertThat(href(body, "prev")).isEqualTo(BASE + "?" + CRITERIA + "&page=0");
    assertThat(href(body, "next")).isEqualTo(BASE + "?" + CRITERIA + "&page=2");
    assertThat(href(body, "first")).isEqualTo(BASE + "?" + CRITERIA + "&page=0");
    assertThat(href(body, "last")).isEqualTo(BASE + "?" + CRITERIA + "&page=2");
  }

  @Test
  void lastPageHasNoNext() throws Exception {
    answers(List.of(), 2, 20, 45);

    JsonNode body = body(search(CRITERIA + "&page=2"));

    assertThat(href(body, "prev")).isEqualTo(BASE + "?" + CRITERIA + "&page=1");
    assertThat(href(body, "next")).isNull();
  }

  @Test
  void pageAfterTheLastStillPointsToTheFirstAndLast() throws Exception {
    answers(List.of(), 7, 20, 45);

    JsonNode body = body(search(CRITERIA + "&page=7"));

    assertThat(href(body, "self")).isEqualTo(BASE + "?" + CRITERIA + "&page=7");
    assertThat(href(body, "first")).isEqualTo(BASE + "?" + CRITERIA + "&page=0");
    assertThat(href(body, "last")).isEqualTo(BASE + "?" + CRITERIA + "&page=2");
    assertThat(href(body, "next")).isNull();
    assertThat(href(body, "prev")).isNull();
    assertThat(body.at("/data/_embedded/movies")).isEmpty();
  }

  @Test
  void anEmptyResultPointsToItselfAndPageZero() throws Exception {
    answers(List.of(), 0, 20, 0);

    JsonNode body = body(search("title=zzzz-no-such-title"));

    assertThat(href(body, "self")).isEqualTo(BASE + "?title=zzzz-no-such-title");
    assertThat(href(body, "first")).isEqualTo(BASE + "?title=zzzz-no-such-title&page=0");
    assertThat(href(body, "last")).isEqualTo(BASE + "?title=zzzz-no-such-title&page=0");
    assertThat(href(body, "next")).isNull();
    assertThat(href(body, "prev")).isNull();
    assertThat(body.at("/meta/pagination/totalPages").asInt()).isZero();
  }

  @Test
  void defaultsAreNeverAddedToLinks() throws Exception {
    answers(List.of(), 0, 20, 45);

    JsonNode body = body(search(""));

    assertThat(href(body, "self")).isEqualTo(BASE);
    assertThat(href(body, "next")).isEqualTo(BASE + "?page=1");
    assertThat(href(body, "last")).isEqualTo(BASE + "?page=2");
  }

  @Test
  void undeclaredParametersAreDroppedAndValuesRoundTripExactly() throws Exception {
    answers(List.of(), 0, 20, 45);

    JsonNode body = body(search("utm_source=mail&title=100%25%20Love%2B&genre=b&genre=a"));

    assertThat(href(body, "next"))
        .isEqualTo(BASE + "?title=100%25%20Love%2B&genre=b&genre=a&page=1");
  }

  @Test
  void theLinkParameterListMatchesTheInterfaceDescription() throws Exception {
    assertThat(MovieController.SEARCH_PARAMETERS)
        .containsExactlyElementsOf(SearchOperationParameters.fromBundledSpec());
  }
}
