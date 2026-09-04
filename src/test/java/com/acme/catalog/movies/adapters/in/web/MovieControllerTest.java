package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.credits.application.port.in.GetMovieCreditsUseCase;
import com.acme.catalog.movies.application.port.in.GetMovieByIdUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link MovieController}. {@link GetMovieByIdUseCase} is mocked at the port
 * seam.
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

  @MockitoBean private GetMovieByIdUseCase getMovieByIdUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;
  @MockitoBean private GetMovieCreditsUseCase getMovieCreditsUseCase;

  private static Movie fullMovie(UUID id) {
    return new Movie(
        new MovieId(id),
        "The Matrix",
        1999,
        List.of(new Genre("Sci-Fi"), new Genre("Action")),
        Optional.of(136),
        Optional.of("A hacker discovers the truth about his reality."),
        Optional.of(new Rating(BigDecimal.valueOf(4.5))));
  }

  private static Movie sparseMovie(UUID id) {
    return new Movie(
        new MovieId(id),
        "Obscure Film",
        2001,
        List.of(new Genre("Drama")),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  @Test
  void getMovieById_existingMovie_returns200EnvelopedDetailWithSelfLink() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieByIdUseCase.getMovieById(new MovieId(id))).willReturn(fullMovie(id));

    mockMvc
        .perform(get("/movies/{id}", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.data.id").value(id.toString()))
        .andExpect(jsonPath("$.data.title").value("The Matrix"))
        .andExpect(jsonPath("$.data.releaseYear").value(1999))
        .andExpect(jsonPath("$.data.genres", org.hamcrest.Matchers.hasSize(2)))
        .andExpect(jsonPath("$.data.runtimeMinutes").value(136))
        .andExpect(jsonPath("$.data.synopsis").exists())
        .andExpect(jsonPath("$.data.rating").value(4.5))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._links.credits.href").exists())
        .andExpect(
            jsonPath("$.data._links.credits.href")
                .value(org.hamcrest.Matchers.endsWith("/movies/" + id + "/credits")))
        .andExpect(jsonPath("$.data._embedded").doesNotExist())
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists());
  }

  @Test
  void getMovieById_movieMissingOptionalFields_returns200WithFieldsOmittedNotNull()
      throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieByIdUseCase.getMovieById(new MovieId(id))).willReturn(sparseMovie(id));

    String body =
        mockMvc
            .perform(get("/movies/{id}", id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode data = new ObjectMapper().readTree(body).path("data");

    assertThat(data.has("runtimeMinutes")).as("runtimeMinutes is present").isFalse();
    assertThat(data.has("synopsis")).as("synopsis is present").isFalse();
    assertThat(data.has("rating")).as("rating is present").isFalse();
    assertThat(data.path("id").asText()).isEqualTo(id.toString());
    assertThat(data.path("title").asText()).isEqualTo("Obscure Film");
    assertThat(data.path("genres")).hasSize(1);
  }

  @Test
  void getMovieById_onlySelfAndCreditsLinksPresent_noEmbeddedNoTemplates() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieByIdUseCase.getMovieById(new MovieId(id))).willReturn(fullMovie(id));

    var result = mockMvc.perform(get("/movies/{id}", id)).andExpect(status().isOk()).andReturn();
    JsonNode data =
        new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");
    JsonNode links = data.path("_links");

    assertThat(links.fieldNames()).toIterable().containsExactlyInAnyOrder("self", "credits");
    assertThat(data.has("_embedded")).isFalse();
    assertThat(data.has("_templates")).isFalse();
  }

  @Test
  void getMovieById_unknownId_returns404ProblemJsonNoLinks() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieByIdUseCase.getMovieById(new MovieId(id)))
        .willThrow(
            new ResourceNotFoundException("MOVIE_NOT_FOUND", "No movie found for id: " + id));

    mockMvc
        .perform(get("/movies/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$._links").doesNotExist())
        .andExpect(jsonPath("$._embedded").doesNotExist());
  }

  @Test
  void getMovieById_malformedUuid_returns400ProblemJsonNotServerError() throws Exception {
    mockMvc
        .perform(get("/movies/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getMovieById_noAuthorizationHeader_isNever401Or403() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieByIdUseCase.getMovieById(new MovieId(id))).willReturn(fullMovie(id));

    mockMvc.perform(get("/movies/{id}", id)).andExpect(status().isOk());
  }
}
