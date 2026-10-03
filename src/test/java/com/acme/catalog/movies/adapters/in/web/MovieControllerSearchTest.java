package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesQuery;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.RuntimeMinutes;
import com.acme.platform.web.PlatformWebTest;
import com.acme.shared.domain.ResultPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Web-slice coverage for the {@code searchMovies} 200 mapping ({@code catalog/movies} "Search and
 * browse movies a page at a time", "Search results are movie summaries" and "Page links keep the
 * criteria, order and size"). {@link SearchMoviesUseCase} is mocked.
 */
@PlatformWebTest(controllers = MovieController.class)
class MovieControllerSearchTest {

  private static final UUID ARRIVAL_ID = UUID.fromString("6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b");
  private static final UUID REEL_ID = UUID.fromString("7f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  private static Movie arrival() {
    return new Movie(
        new MovieId(ARRIVAL_ID),
        "Arrival",
        2016,
        List.of("Sci-Fi", "Drama"),
        Optional.of(new RuntimeMinutes(116)),
        Optional.of("A linguist is recruited..."),
        Optional.of(new Rating(new BigDecimal("4.5"))));
  }

  private static Movie reel(String rating) {
    return new Movie(
        new MovieId(REEL_ID),
        "Untitled Reel",
        1974,
        List.of(),
        Optional.empty(),
        Optional.empty(),
        rating == null ? Optional.empty() : Optional.of(new Rating(new BigDecimal(rating))));
  }

  private static MockHttpServletRequestBuilder search() {
    return get("/api/v1/movies").contextPath("/api/v1");
  }

  private JsonNode body(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static Map<String, List<String>> query(String href) {
    Map<String, List<String>> params = new LinkedHashMap<>();
    String raw = URI.create(href).getRawQuery();
    if (raw == null) {
      return params;
    }
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      params
          .computeIfAbsent(
              UriUtils.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
              k -> new ArrayList<>())
          .add(UriUtils.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
    }
    return params;
  }

  @Test
  void envelopeHasEmbeddedMoviesLinksAndPagination() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(arrival(), reel(null)), 0, 20, 25));

    MvcResult result =
        mockMvc
            .perform(search())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    JsonNode body = body(result);
    assertThat(body.fieldNames()).toIterable().containsExactly("data", "meta");
    assertThat(body.get("data").fieldNames()).toIterable().containsExactly("_embedded", "_links");
    assertThat(body.get("data").get("_embedded").fieldNames())
        .toIterable()
        .containsExactly("movies");
    assertThat(body.get("data").get("_embedded").get("movies")).hasSize(2);
    JsonNode meta = body.get("meta");
    assertThat(meta.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("timestamp", "correlationId", "pagination");
    assertThat(meta.get("correlationId").asText())
        .isEqualTo(result.getResponse().getHeader("X-Correlation-Id"));
    JsonNode pagination = meta.get("pagination");
    assertThat(pagination.get("page").asInt()).isZero();
    assertThat(pagination.get("size").asInt()).isEqualTo(20);
    assertThat(pagination.get("totalElements").asLong()).isEqualTo(25);
    assertThat(pagination.get("totalPages").asInt()).isEqualTo(2);
    JsonNode links = body.get("data").get("_links");
    assertThat(links.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("self", "first", "next", "last");
    assertThat(links.get("self").get("href").asText()).isEqualTo("http://localhost/api/v1/movies");
  }

  @Test
  void summaryHasNoSynopsisAndAbsentOptionalFieldsAreAbsentKeys() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(arrival(), reel(null)), 0, 20, 2));

    JsonNode movies =
        body(mockMvc.perform(search()).andReturn()).get("data").get("_embedded").get("movies");

    JsonNode full = movies.get(0);
    assertThat(full.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "title", "releaseYear", "genres", "runtimeMinutes", "rating", "_links");
    assertThat(full.get("id").asText()).isEqualTo(ARRIVAL_ID.toString());
    assertThat(full.get("runtimeMinutes").asInt()).isEqualTo(116);
    assertThat(full.get("genres")).map(JsonNode::asText).containsExactly("Drama", "Sci-Fi");
    assertThat(full.get("_links").fieldNames()).toIterable().containsExactly("self");
    assertThat(full.get("_links").get("self").get("href").asText())
        .isEqualTo("http://localhost/api/v1/movies/" + ARRIVAL_ID);

    JsonNode minimal = movies.get(1);
    assertThat(minimal.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("id", "title", "releaseYear", "genres", "_links");
    assertThat(minimal.get("genres").isArray()).isTrue();
    assertThat(minimal.get("genres").isEmpty()).isTrue();
  }

  @Test
  void ratingsAreWrittenInTheirShortestExactDecimalForm() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(arrival(), reel("5.0")), 0, 20, 2));

    JsonNode movies =
        body(mockMvc.perform(search()).andReturn()).get("data").get("_embedded").get("movies");

    assertThat(movies.get(0).get("rating").asText()).isEqualTo("4.5");
    assertThat(movies.get(1).get("rating").asText()).isEqualTo("5");
  }

  @Test
  void entrySelfLinksHonourForwardedHeaders() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(arrival()), 0, 20, 1));

    JsonNode data =
        body(mockMvc
                .perform(
                    search()
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "api.example.test"))
                .andReturn())
            .get("data");

    assertThat(
            data.get("_embedded")
                .get("movies")
                .get(0)
                .get("_links")
                .get("self")
                .get("href")
                .asText())
        .isEqualTo("https://api.example.test/api/v1/movies/" + ARRIVAL_ID);
    assertThat(data.get("_links").get("self").get("href").asText())
        .isEqualTo("https://api.example.test/api/v1/movies");
  }

  @Test
  void problemJsonAcceptHeaderStillGetsA200SuccessEnvelopeAsJson() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(), 0, 20, 0));

    mockMvc
        .perform(search().accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void defaultsArePassedToTheUseCase() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(), 0, 20, 0));

    mockMvc.perform(search()).andExpect(status().isOk());

    ArgumentCaptor<SearchMoviesQuery> captor = ArgumentCaptor.forClass(SearchMoviesQuery.class);
    Mockito.verify(searchMoviesUseCase).search(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(new SearchMoviesQuery(null, List.of(), null, null, null, null, 0, 20));
  }

  @Test
  void explicitValuesArePassedToTheUseCase() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(), 2, 5, 0));

    mockMvc
        .perform(
            search()
                .param("title", " arr")
                .param("genre", "drama", "Sci-Fi")
                .param("releaseYearFrom", "1990")
                .param("releaseYearTo", "2020")
                .param("minRating", "4.5")
                .param("sort", "-rating")
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk());

    ArgumentCaptor<SearchMoviesQuery> captor = ArgumentCaptor.forClass(SearchMoviesQuery.class);
    Mockito.verify(searchMoviesUseCase).search(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(
            new SearchMoviesQuery(
                " arr",
                List.of("drama", "Sci-Fi"),
                1990,
                2020,
                new BigDecimal("4.5"),
                "-rating",
                2,
                5));
  }

  @Test
  void commaSeparatedGenresBindLikeRepeatedOnes() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(), 0, 20, 0));

    mockMvc.perform(search().param("genre", "Drama,Sci-Fi")).andExpect(status().isOk());

    ArgumentCaptor<SearchMoviesQuery> captor = ArgumentCaptor.forClass(SearchMoviesQuery.class);
    Mockito.verify(searchMoviesUseCase).search(captor.capture());
    assertThat(captor.getValue().genres()).containsExactly("Drama", "Sci-Fi");
  }

  @Test
  void linksPreserveCriteriaOrderAndSize() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(arrival()), 1, 10, 45));

    JsonNode links =
        body(mockMvc
                .perform(
                    search()
                        .param("title", "the")
                        .param("genre", "Drama", "Sci-Fi")
                        .param("sort", "-rating")
                        .param("size", "10")
                        .param("page", "1"))
                .andReturn())
            .get("data")
            .get("_links");

    Map<String, String> expectedPages =
        Map.of("self", "1", "first", "0", "prev", "0", "next", "2", "last", "4");
    expectedPages.forEach(
        (rel, page) -> {
          Map<String, List<String>> query = query(links.get(rel).get("href").asText());
          assertThat(query)
              .as(rel)
              .containsOnlyKeys("title", "genre", "sort", "size", "page")
              .containsEntry("title", List.of("the"))
              .containsEntry("genre", List.of("Drama", "Sci-Fi"))
              .containsEntry("sort", List.of("-rating"))
              .containsEntry("size", List.of("10"))
              .containsEntry("page", List.of(page));
        });
  }

  @Test
  void defaultsAreNotWrittenIntoLinks() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(arrival()), 0, 20, 25));

    JsonNode links =
        body(mockMvc.perform(search().param("genre", "Drama")).andReturn())
            .get("data")
            .get("_links");

    links
        .properties()
        .forEach(
            link ->
                assertThat(query(link.getValue().get("href").asText()))
                    .as(link.getKey())
                    .doesNotContainKeys("size", "sort"));
    assertThat(query(links.get("self").get("href").asText())).doesNotContainKey("page");
    assertThat(query(links.get("next").get("href").asText())).containsEntry("page", List.of("1"));
    assertThat(query(links.get("last").get("href").asText())).containsEntry("page", List.of("1"));
  }

  @Test
  void unrecognisedParametersAreDroppedFromLinks() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(), 0, 20, 0));

    MvcResult result =
        mockMvc.perform(search().param("foo", "bar").param("title", "a")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    body(result)
        .get("data")
        .get("_links")
        .properties()
        .forEach(link -> assertThat(link.getValue().get("href").asText()).doesNotContain("foo"));
  }

  @Test
  void emptyResultIsStillNavigable() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(), 0, 20, 0));

    JsonNode body = body(mockMvc.perform(search().param("title", "zzzz")).andReturn());

    JsonNode links = body.get("data").get("_links");
    assertThat(links.fieldNames()).toIterable().containsExactlyInAnyOrder("self", "first", "last");
    assertThat(query(links.get("first").get("href").asText())).containsEntry("page", List.of("0"));
    assertThat(query(links.get("last").get("href").asText())).containsEntry("page", List.of("0"));
    assertThat(body.get("data").get("_embedded").get("movies").isEmpty()).isTrue();
    assertThat(body.get("meta").get("pagination").get("totalPages").asInt()).isZero();
  }

  @Test
  void pageAfterTheLastHasFirstAndLastButNoPrevOrNext() throws Exception {
    BDDMockito.given(searchMoviesUseCase.search(any()))
        .willReturn(new ResultPage<>(List.of(), 9, 2, 5));

    JsonNode body =
        body(mockMvc.perform(search().param("size", "2").param("page", "9")).andReturn());

    assertThat(body.get("data").get("_links").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("self", "first", "last");
    assertThat(body.get("meta").get("pagination").get("totalElements").asLong()).isEqualTo(5);
  }
}
