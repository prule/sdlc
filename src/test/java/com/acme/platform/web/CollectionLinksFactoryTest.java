package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.generated.model.CollectionLinks;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Unit tests for {@link CollectionLinksFactory} (design D6, {@code platform/collection-paging},
 * task 6.2): relation sets across page positions, raw value preservation, unrecognised parameter
 * dropping, and that defaults are never leaked into links.
 */
class CollectionLinksFactoryTest {

  private static final Set<String> RECOGNISED =
      Set.of(
          "title",
          "genre",
          "releaseYearFrom",
          "releaseYearTo",
          "minRating",
          "sort",
          "page",
          "size");

  private final CollectionLinksFactory factory = new CollectionLinksFactory();

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  private void bindRequest(String uri, String queryString) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setQueryString(queryString);
    if (queryString != null) {
      for (String pair : queryString.split("&")) {
        String[] parts = pair.split("=", 2);
        request.addParameter(parts[0], parts.length > 1 ? parts[1] : "");
      }
    }
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private Page<String> pageOf(int page, int size, long totalElements) {
    return new Page<>(List.of(), new PageRequest(page, size), totalElements);
  }

  @Test
  void firstPageHasNoPrevButHasNextAndLast() {
    bindRequest("/api/v1/movies", "size=7");

    CollectionLinks links = factory.create(pageOf(0, 7, 25), RECOGNISED);

    assertThat(links.getPrev()).isNull();
    assertThat(links.getNext()).isNotNull();
    assertThat(links.getSelf().getHref().toString()).endsWith("/api/v1/movies?size=7&page=0");
    assertThat(links.getNext().getHref().toString()).endsWith("page=1");
    assertThat(links.getLast().getHref().toString()).endsWith("page=3");
  }

  @Test
  void middlePageHasEveryRelation() {
    bindRequest("/api/v1/movies", "genre=drama&sort=-rating&size=7&page=1");

    CollectionLinks links = factory.create(pageOf(1, 7, 25), RECOGNISED);

    assertThat(links.getSelf().getHref().toString())
        .endsWith("/api/v1/movies?genre=drama&sort=-rating&size=7&page=1");
    assertThat(links.getFirst().getHref().toString()).endsWith("page=0");
    assertThat(links.getLast().getHref().toString()).endsWith("page=3");
    assertThat(links.getPrev().getHref().toString()).endsWith("page=0");
    assertThat(links.getNext().getHref().toString()).endsWith("page=2");
  }

  @Test
  void lastPageHasPrevButNoNext() {
    bindRequest("/api/v1/movies", "size=7&page=3");

    CollectionLinks links = factory.create(pageOf(3, 7, 25), RECOGNISED);

    assertThat(links.getPrev().getHref().toString()).endsWith("page=2");
    assertThat(links.getNext()).isNull();
  }

  @Test
  void noMatchesHasOnlySelfFirstAndLastAllTargetingPageZero() {
    bindRequest("/api/v1/movies", "title=nomatch");

    CollectionLinks links = factory.create(pageOf(0, 20, 0), RECOGNISED);

    assertThat(links.getPrev()).isNull();
    assertThat(links.getNext()).isNull();
    assertThat(links.getFirst().getHref().toString()).endsWith("page=0");
    assertThat(links.getLast().getHref().toString()).endsWith("page=0");
  }

  @Test
  void beyondLastPageHasOnlySelfFirstAndLast() {
    bindRequest("/api/v1/movies", "page=50");

    CollectionLinks links = factory.create(pageOf(50, 20, 25), RECOGNISED);

    assertThat(links.getPrev()).isNull();
    assertThat(links.getNext()).isNull();
    assertThat(links.getSelf().getHref().toString()).endsWith("page=50");
    assertThat(links.getLast().getHref().toString()).endsWith("page=1");
  }

  @Test
  void rawValuesArePreservedExactlyIncludingRepeatedGenreAndMixedCase() {
    bindRequest("/api/v1/movies", "genre=Drama&genre=SCI-FI&page=0");

    CollectionLinks links = factory.create(pageOf(0, 20, 2), RECOGNISED);

    assertThat(links.getSelf().getHref().toString()).endsWith("?genre=Drama&genre=SCI-FI&page=0");
  }

  @Test
  void unrecognisedParametersAreDropped() {
    bindRequest("/api/v1/movies", "foo=bar&title=heist");

    CollectionLinks links = factory.create(pageOf(0, 20, 1), RECOGNISED);

    assertThat(links.getSelf().getHref().toString()).doesNotContain("foo").contains("title=heist");
  }

  @Test
  void malformedPercentEncodingIsDroppedRatherThanFailingTheLink() {
    bindRequest("/api/v1/movies", "%zz=1&title=%zz&genre=Sci%2DFi");

    CollectionLinks links = factory.create(pageOf(0, 20, 1), RECOGNISED);

    assertThat(links.getSelf().getHref().toString())
        .endsWith("/api/v1/movies?genre=Sci%2DFi&page=0");
  }

  @Test
  void defaultsAreNotLeakedIntoLinks() {
    bindRequest("/api/v1/movies", null);

    CollectionLinks links = factory.create(pageOf(0, 20, 3), RECOGNISED);

    assertThat(links.getSelf().getHref().toString()).endsWith("/api/v1/movies?page=0");
    assertThat(links.getSelf().getHref().toString()).doesNotContain("size").doesNotContain("sort");
  }
}
