package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.platform.web.CollectionLinksFactory;
import com.acme.platform.web.PlatformWebTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end (MockMvc, real routing) coverage for the {@code StrictUuidWebConfig} deviation from
 * design D4: it must enforce BR-1 for identifier values that carry whitespace, including a
 * `%20`-encoded value, and never invoke {@link GetMovieUseCase} when it rejects. An empty path
 * segment (a trailing or doubled slash) never matches `/movies/{id}` at all, so it is reported as
 * the platform's "no such path" 404 rather than reaching the converter; that is verified here too,
 * so the distinction from the 400 "malformed identifier" outcome is explicit.
 */
@PlatformWebTest(controllers = MovieController.class)
@org.springframework.context.annotation.Import(CollectionLinksFactory.class)
class MovieControllerEdgeIdTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  @Test
  void percentEncodedSpaceIsBadRequestAndNeverInvokesTheUseCase() throws Exception {
    MvcResult result =
        mockMvc.perform(get(URI.create("/api/v1/movies/%20")).contextPath("/api/v1")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    Mockito.verifyNoInteractions(getMovieUseCase);
  }

  @Test
  void leadingWhitespaceOnAnOtherwiseCanonicalIdIsBadRequestAndNeverInvokesTheUseCase()
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(URI.create("/api/v1/movies/%206f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b"))
                    .contextPath("/api/v1"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    Mockito.verifyNoInteractions(getMovieUseCase);
  }

  @Test
  void emptyPathSegmentIsNotFoundNotBadRequestAndNeverInvokesTheUseCase() throws Exception {
    MvcResult trailingSlash =
        mockMvc.perform(get("/api/v1/movies/").contextPath("/api/v1")).andReturn();
    MvcResult doubleSlash =
        mockMvc.perform(get("/api/v1/movies//").contextPath("/api/v1")).andReturn();

    // No path segment is present at all, so this is the platform's "nothing exists at the
    // path" outcome (404 NOT_FOUND), never the converter's 400 BAD_REQUEST: there is no value
    // for StrictUuidWebConfig to reject.
    assertThat(trailingSlash.getResponse().getStatus()).isEqualTo(404);
    assertThat(doubleSlash.getResponse().getStatus()).isEqualTo(404);
    Mockito.verifyNoInteractions(getMovieUseCase);
  }
}
