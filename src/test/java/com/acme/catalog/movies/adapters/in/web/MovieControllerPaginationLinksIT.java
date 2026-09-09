package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.adapters.out.persistence.MovieJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaRepository;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.common.test.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Full-stack (real Postgres, Testcontainers, no H2) round-trip test proving that a filtered,
 * non-default-sorted request's pagination links carry those filters/sort forward and backward:
 * following {@code first}/{@code prev}/{@code next}/{@code last} returns the same result set (same
 * {@code totalElements}/{@code totalPages}, same item ordering) as the originating request. Asserts
 * semantic target-query equality (params/values as a set, ignoring ordering) rather than exact
 * query-string equality, per the design's stated risk.
 */
@AutoConfigureMockMvc
class MovieControllerPaginationLinksIT extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private MovieJpaRepository movieJpaRepository;

  private static String uniqueTag() {
    return "PLTAG" + UUID.randomUUID().toString().replace("-", "");
  }

  private static MovieJpaEntity movie(String tag, String titleSuffix, int year, double rating) {
    return new MovieJpaEntity(
        UUID.randomUUID(),
        tag + " " + titleSuffix,
        year,
        null,
        null,
        BigDecimal.valueOf(rating),
        Set.of(Genre.DRAMA, Genre.CRIME));
  }

  @Test
  void followingEveryNavLink_preservesFiltersAndSort_forwardAndBackward() throws Exception {
    String tag = uniqueTag();
    // Six matching movies, all DRAMA+CRIME, rating >= 3.0, released 2000-2025, sorted by
    // title ascending -> Alpha, Bravo, Charlie, Delta, Echo, Foxtrot.
    List<MovieJpaEntity> matching =
        List.of(
            movie(tag, "Alpha", 2010, 4.0),
            movie(tag, "Bravo", 2011, 4.0),
            movie(tag, "Charlie", 2012, 4.0),
            movie(tag, "Delta", 2013, 4.0),
            movie(tag, "Echo", 2014, 4.0),
            movie(tag, "Foxtrot", 2015, 4.0));
    // Movies that must NOT match the filters below (wrong genre, wrong year, wrong rating).
    MovieJpaEntity wrongGenre =
        new MovieJpaEntity(
            UUID.randomUUID(),
            tag + " WrongGenre",
            2012,
            null,
            null,
            BigDecimal.valueOf(4.0),
            Set.of(Genre.COMEDY));
    MovieJpaEntity wrongYear = movie(tag, "WrongYear", 1990, 4.0);
    MovieJpaEntity wrongRating = movie(tag, "WrongRating", 2012, 1.0);
    movieJpaRepository.saveAll(matching);
    movieJpaRepository.saveAll(List.of(wrongGenre, wrongYear, wrongRating));

    int size = 2;
    // Page 1 (middle page: 0-indexed) of a 6-item, size=2 result -> 3 total pages.
    String body =
        mockMvc
            .perform(
                get("/movies")
                    .param("title", tag)
                    .param("genre", "DRAMA", "CRIME")
                    .param("releaseYearFrom", "2000")
                    .param("releaseYearTo", "2025")
                    .param("minRating", "3.0")
                    .param("sort", "title,asc")
                    .param("page", "1")
                    .param("size", String.valueOf(size)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ObjectMapper mapper = new ObjectMapper();
    JsonNode originating = mapper.readTree(body);

    assertThat(originating.path("meta").path("pagination").path("totalElements").asInt())
        .isEqualTo(6);
    assertThat(originating.path("meta").path("pagination").path("totalPages").asInt()).isEqualTo(3);
    assertThat(titles(originating)).containsExactly("Charlie", "Delta");

    JsonNode links = originating.path("data").path("_links");
    assertSemanticallyEquivalentExceptPage(links.path("self"), links.path("self"));

    for (String rel : List.of("first", "prev", "next", "last")) {
      JsonNode linkNode = links.path(rel);
      assertThat(linkNode.has("href")).as("link %s is present", rel).isTrue();

      // Assert semantic query equality with self (same params/values, only page differs).
      assertSemanticallyEquivalentExceptPage(links.path("self"), linkNode);

      String followBody =
          mockMvc
              .perform(get(URI.create(linkNode.path("href").asText())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode followed = mapper.readTree(followBody);

      assertThat(followed.path("meta").path("pagination").path("totalElements").asInt())
          .as("totalElements following %s", rel)
          .isEqualTo(6);
      assertThat(followed.path("meta").path("pagination").path("totalPages").asInt())
          .as("totalPages following %s", rel)
          .isEqualTo(3);
    }

    // first -> page 0 -> Alpha, Bravo
    String firstBody =
        mockMvc
            .perform(get(URI.create(links.path("first").path("href").asText())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(titles(mapper.readTree(firstBody))).containsExactly("Alpha", "Bravo");

    // prev (page 0) -> Alpha, Bravo
    String prevBody =
        mockMvc
            .perform(get(URI.create(links.path("prev").path("href").asText())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(titles(mapper.readTree(prevBody))).containsExactly("Alpha", "Bravo");

    // next (page 2) -> Echo, Foxtrot
    String nextBody =
        mockMvc
            .perform(get(URI.create(links.path("next").path("href").asText())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(titles(mapper.readTree(nextBody))).containsExactly("Echo", "Foxtrot");

    // last (page 2) -> Echo, Foxtrot
    String lastBody =
        mockMvc
            .perform(get(URI.create(links.path("last").path("href").asText())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(titles(mapper.readTree(lastBody))).containsExactly("Echo", "Foxtrot");
  }

  private static List<String> titles(JsonNode envelope) {
    JsonNode movies = envelope.path("data").path("_embedded").path("movies");
    return java.util.stream.StreamSupport.stream(movies.spliterator(), false)
        .map(m -> m.path("title").asText())
        .map(t -> t.substring(t.lastIndexOf(' ') + 1))
        .toList();
  }

  /**
   * Asserts two link nodes target the same query params/values (order-independent, multi-valued
   * aware) except for {@code page}, which may legitimately differ.
   */
  private static void assertSemanticallyEquivalentExceptPage(JsonNode selfLink, JsonNode other) {
    var selfParams =
        UriComponentsBuilder.fromUriString(selfLink.path("href").asText()).build().getQueryParams();
    var otherParams =
        UriComponentsBuilder.fromUriString(other.path("href").asText()).build().getQueryParams();

    var selfWithoutPage = new org.springframework.util.LinkedMultiValueMap<>(selfParams);
    selfWithoutPage.remove("page");
    var otherWithoutPage = new org.springframework.util.LinkedMultiValueMap<>(otherParams);
    otherWithoutPage.remove("page");

    assertThat(toComparableMap(otherWithoutPage))
        .as("query params (excluding page) of %s vs %s", other, selfLink)
        .isEqualTo(toComparableMap(selfWithoutPage));
  }

  private static java.util.Map<String, Set<String>> toComparableMap(
      org.springframework.util.MultiValueMap<String, String> params) {
    java.util.Map<String, Set<String>> result = new java.util.HashMap<>();
    params.forEach((key, values) -> result.put(key, Set.copyOf(values)));
    return result;
  }
}
