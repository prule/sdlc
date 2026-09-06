package com.acme.catalog.movies.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.application.port.in.GetMovieDetailUseCase;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test for {@link MovieController} (UC-001). {@link GetMovieDetailUseCase} is mocked at
 * the port seam.
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

  @MockitoBean private GetMovieDetailUseCase getMovieDetailUseCase;

  @Test
  void getMovieById_existingMovieWithAllOptionalFields_returns200WithAllFieldsAndSelfLink()
      throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie =
        Movie.of(
            id,
            "The Wandering Reel",
            2019,
            List.of(Genre.DRAMA, Genre.MYSTERY),
            118,
            "A projectionist discovers a film that predicts the news.",
            new Rating(BigDecimal.valueOf(4.5)));
    given(getMovieDetailUseCase.getMovieDetail(id)).willReturn(movie);

    mockMvc
        .perform(get("/movies/{id}", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.id").value(id.toString()))
        .andExpect(jsonPath("$.data.title").value("The Wandering Reel"))
        .andExpect(jsonPath("$.data.releaseYear").value(2019))
        .andExpect(jsonPath("$.data.genres.length()").value(2))
        .andExpect(jsonPath("$.data.runtimeMinutes").value(118))
        .andExpect(jsonPath("$.data.synopsis").exists())
        .andExpect(jsonPath("$.data.rating").value(4.5))
        .andExpect(
            jsonPath("$.data._links.self.href", org.hamcrest.Matchers.endsWith("/movies/" + id)))
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists());
  }

  @Test
  void getMovieById_existingMovieWithNoOptionalFields_returns200WithOptionalKeysAbsent()
      throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie = Movie.of(id, "Silent Harbor", 2021, List.of(Genre.MYSTERY), null, null, null);
    given(getMovieDetailUseCase.getMovieDetail(id)).willReturn(movie);

    String body =
        mockMvc.perform(get("/movies/{id}", id)).andReturn().getResponse().getContentAsString();

    com.fasterxml.jackson.databind.JsonNode data =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data");

    org.assertj.core.api.Assertions.assertThat(data.has("runtimeMinutes")).isFalse();
    org.assertj.core.api.Assertions.assertThat(data.has("synopsis")).isFalse();
    org.assertj.core.api.Assertions.assertThat(data.has("rating")).isFalse();
  }

  @Test
  void getMovieById_wellFormedUnknownId_returns404ProblemJson() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieDetailUseCase.getMovieDetail(id))
        .willThrow(
            new ResourceNotFoundException("MOVIE_NOT_FOUND", "No movie found with id " + id));

    mockMvc
        .perform(get("/movies/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getMovieById_malformedId_returns400WithoutInvokingTheUseCase() throws Exception {
    mockMvc
        .perform(get("/movies/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(getMovieDetailUseCase);
  }

  @Test
  void getMovieById_requiresNoAuthentication() throws Exception {
    UUID id = UUID.randomUUID();
    Movie movie = Movie.of(id, "Silent Harbor", 2021, List.of(Genre.MYSTERY), null, null, null);
    given(getMovieDetailUseCase.getMovieDetail(any())).willReturn(movie);

    mockMvc.perform(get("/movies/{id}", id)).andExpect(status().isOk());
  }
}
