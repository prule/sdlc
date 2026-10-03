package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.platform.web.PageLinkBuilder.PageHrefs;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriUtils;

/** Unit coverage for the generic page-link assembly (BR-7, add-movie-search design D6). */
class PageLinkBuilderTest {

  private static final List<String> RECOGNISED = List.of("title", "genre", "sort", "size");

  private final PageLinkBuilder builder = new PageLinkBuilder();

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/movies");
    request.setContextPath("/api/v1");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(80);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return request;
  }

  /** The query string as an ordered multimap of decoded names to decoded values. */
  private static Map<String, List<String>> query(URI uri) {
    Map<String, List<String>> params = new LinkedHashMap<>();
    String raw = uri.getRawQuery();
    if (raw == null) {
      return params;
    }
    for (String pair : raw.split("&")) {
      int eq = pair.indexOf('=');
      String name = UriUtils.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
      String value = UriUtils.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
      params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }
    return params;
  }

  private static List<String> names(URI uri) {
    List<String> names = new ArrayList<>();
    String raw = uri.getRawQuery();
    if (raw != null) {
      for (String pair : raw.split("&")) {
        names.add(pair.substring(0, pair.indexOf('=')));
      }
    }
    return names;
  }

  @Test
  void recognisedParametersAreKeptVerbatimInAFixedOrderAndUnrecognisedAreDropped() {
    MockHttpServletRequest request = request();
    request.addParameter("size", "10");
    request.addParameter("foo", "bar");
    request.addParameter("genre", "Drama", "sci-fi", "");
    request.addParameter("title", "the");
    request.addParameter("page", "1");

    PageHrefs hrefs = builder.build(RECOGNISED, 1, 4, true, true);

    Map<String, List<String>> next = query(hrefs.next().orElseThrow());
    assertThat(next)
        .containsExactly(
            Map.entry("title", List.of("the")),
            Map.entry("genre", List.of("Drama", "sci-fi", "")),
            Map.entry("size", List.of("10")),
            Map.entry("page", List.of("2")));
    assertThat(names(hrefs.self()))
        .containsExactly("title", "genre", "genre", "genre", "size", "page");
    for (URI uri : List.of(hrefs.self(), hrefs.first(), hrefs.last())) {
      assertThat(query(uri)).doesNotContainKey("foo");
    }
  }

  @Test
  void absentParametersStayAbsentSoDefaultsAreNeverWritten() {
    MockHttpServletRequest request = request();
    request.addParameter("genre", "Drama");

    PageHrefs hrefs = builder.build(RECOGNISED, 0, 1, false, true);

    assertThat(query(hrefs.self())).containsOnlyKeys("genre");
    assertThat(query(hrefs.next().orElseThrow()))
        .containsOnlyKeys("genre", "page")
        .containsEntry("page", List.of("1"));
    assertThat(query(hrefs.last())).containsEntry("page", List.of("1"));
  }

  @Test
  void selfKeepsTheRawPageValue() {
    MockHttpServletRequest request = request();
    request.addParameter("page", "01");

    PageHrefs hrefs = builder.build(RECOGNISED, 1, 4, true, true);

    assertThat(query(hrefs.self())).containsEntry("page", List.of("01"));
  }

  @Test
  void firstPageOfSeveral() {
    request();

    PageHrefs hrefs = builder.build(RECOGNISED, 0, 4, false, true);

    assertThat(query(hrefs.first())).containsEntry("page", List.of("0"));
    assertThat(query(hrefs.last())).containsEntry("page", List.of("4"));
    assertThat(hrefs.prev()).isEmpty();
    assertThat(query(hrefs.next().orElseThrow())).containsEntry("page", List.of("1"));
  }

  @Test
  void middlePage() {
    request().addParameter("page", "2");

    PageHrefs hrefs = builder.build(RECOGNISED, 2, 4, true, true);

    assertThat(query(hrefs.prev().orElseThrow())).containsEntry("page", List.of("1"));
    assertThat(query(hrefs.next().orElseThrow())).containsEntry("page", List.of("3"));
  }

  @Test
  void lastPage() {
    request().addParameter("page", "4");

    PageHrefs hrefs = builder.build(RECOGNISED, 4, 4, true, false);

    assertThat(query(hrefs.prev().orElseThrow())).containsEntry("page", List.of("3"));
    assertThat(hrefs.next()).isEmpty();
  }

  @Test
  void pageAfterTheLastStillPointsToFirstAndLastOnly() {
    request().addParameter("page", "9");

    PageHrefs hrefs = builder.build(RECOGNISED, 9, 2, false, false);

    assertThat(query(hrefs.self())).containsEntry("page", List.of("9"));
    assertThat(query(hrefs.first())).containsEntry("page", List.of("0"));
    assertThat(query(hrefs.last())).containsEntry("page", List.of("2"));
    assertThat(hrefs.prev()).isEmpty();
    assertThat(hrefs.next()).isEmpty();
  }

  @Test
  void emptyResultPointsFirstAndLastAtPageZero() {
    request();

    PageHrefs hrefs = builder.build(RECOGNISED, 0, 0, false, false);

    assertThat(query(hrefs.first())).containsEntry("page", List.of("0"));
    assertThat(query(hrefs.last())).containsEntry("page", List.of("0"));
    assertThat(hrefs.prev()).isEqualTo(Optional.empty());
    assertThat(hrefs.next()).isEqualTo(Optional.empty());
  }

  @Test
  void hrefsAreAbsoluteAndTargetTheCurrentPath() {
    request();

    PageHrefs hrefs = builder.build(RECOGNISED, 0, 0, false, false);

    assertThat(hrefs.self().toString()).isEqualTo("http://localhost/api/v1/movies");
    assertThat(hrefs.first().toString()).isEqualTo("http://localhost/api/v1/movies?page=0");
  }

  @Test
  void forwardedSchemeAndHostAreHonoured() {
    MockHttpServletRequest request = request();
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "api.example.test");

    PageHrefs hrefs = builder.build(RECOGNISED, 0, 0, false, false);

    assertThat(hrefs.self().getScheme()).isEqualTo("https");
    assertThat(hrefs.self().getHost()).isEqualTo("api.example.test");
    assertThat(hrefs.first().toString()).startsWith("https://api.example.test/api/v1/movies?");
  }

  @Test
  void aTitleWithReservedCharactersRoundTrips() {
    String title = "Tom & Jerry 100% +1";
    request().addParameter("title", title);

    PageHrefs hrefs = builder.build(RECOGNISED, 0, 0, false, false);

    assertThat(query(hrefs.self())).containsEntry("title", List.of(title));
    assertThat(query(hrefs.first())).containsEntry("title", List.of(title));
    assertThat(hrefs.self().getRawQuery()).doesNotContain(" ").doesNotContain("+");
  }
}
