package com.acme.catalog.movies.adapters.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.movies.application.port.in.GetMovieCreditsUseCase;
import com.acme.catalog.movies.application.port.in.GetMovieDetailUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.Credit;
import com.acme.catalog.movies.domain.model.MovieCredits;
import com.acme.catalog.movies.domain.model.Person;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Web-slice test for {@link MovieController#getMovieCredits} (UC-003). {@link
 * GetMovieCreditsUseCase} is mocked at the port seam.
 */
@WebMvcTest(MovieController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class MovieControllerCreditsTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetMovieDetailUseCase getMovieDetailUseCase;

  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;

  @MockitoBean private GetMovieCreditsUseCase getMovieCreditsUseCase;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void getMovieCredits_existingMovie_returns200WithCastAndCrewOrderedAndSelfLink()
      throws Exception {
    UUID id = UUID.randomUUID();
    Person lead = new Person(UUID.randomUUID(), "Ava Solano");
    Person supporting = new Person(UUID.randomUUID(), "Marcus Reyes");
    Person director = new Person(UUID.randomUUID(), "Priya Nandan");
    Person composer = new Person(UUID.randomUUID(), "Tomas Berg");

    MovieCredits credits =
        MovieCredits.of(
            List.of(Credit.Cast.of(supporting, null, 2), Credit.Cast.of(lead, "Dana Whitfield", 1)),
            List.of(
                new Credit.Crew(composer, "Sound", "Composer"),
                new Credit.Crew(director, "Directing", "Director")));
    given(getMovieCreditsUseCase.getMovieCredits(id)).willReturn(credits);

    String body =
        mockMvc
            .perform(get("/movies/{id}/credits", id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.data._embedded.cast.length()").value(2))
            .andExpect(jsonPath("$.data._embedded.cast[0].billingOrder").value(1))
            .andExpect(jsonPath("$.data._embedded.cast[0].person.id").value(lead.id().toString()))
            .andExpect(jsonPath("$.data._embedded.cast[0].person.name").value("Ava Solano"))
            .andExpect(jsonPath("$.data._embedded.cast[0].character").value("Dana Whitfield"))
            .andExpect(jsonPath("$.data._embedded.cast[1].billingOrder").value(2))
            .andExpect(jsonPath("$.data._embedded.crew.length()").value(2))
            .andExpect(jsonPath("$.data._embedded.crew[0].department").value("Directing"))
            .andExpect(jsonPath("$.data._embedded.crew[0].job").value("Director"))
            .andExpect(jsonPath("$.data._embedded.crew[1].department").value("Sound"))
            .andExpect(
                jsonPath(
                    "$.data._links.self.href",
                    org.hamcrest.Matchers.endsWith("/movies/" + id + "/credits")))
            .andExpect(jsonPath("$.meta.correlationId").exists())
            .andExpect(jsonPath("$.meta.timestamp").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = objectMapper.readTree(body);
    JsonNode castPerson = root.path("data").path("_embedded").path("cast").get(0).path("person");
    org.assertj.core.api.Assertions.assertThat(castPerson.has("_links")).isFalse();
  }

  @Test
  void getMovieCredits_castEntryWithNoCharacter_omitsCharacterKey() throws Exception {
    UUID id = UUID.randomUUID();
    Person supporting = new Person(UUID.randomUUID(), "Marcus Reyes");
    MovieCredits credits = MovieCredits.of(List.of(Credit.Cast.of(supporting, null, 1)), List.of());
    given(getMovieCreditsUseCase.getMovieCredits(id)).willReturn(credits);

    String body =
        mockMvc
            .perform(get("/movies/{id}/credits", id))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode castEntry =
        objectMapper.readTree(body).path("data").path("_embedded").path("cast").get(0);

    org.assertj.core.api.Assertions.assertThat(castEntry.has("character")).isFalse();
  }

  @Test
  void getMovieCredits_movieWithNoCastOrCrew_returns200WithBothGroupsEmpty() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieCreditsUseCase.getMovieCredits(id))
        .willReturn(MovieCredits.of(List.of(), List.of()));

    mockMvc
        .perform(get("/movies/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.cast.length()").value(0))
        .andExpect(jsonPath("$.data._embedded.crew.length()").value(0));
  }

  @Test
  void getMovieCredits_movieWithCastButNoCrew_returns200WithPopulatedCastAndEmptyCrew()
      throws Exception {
    UUID id = UUID.randomUUID();
    Person lead = new Person(UUID.randomUUID(), "Ava Solano");
    given(getMovieCreditsUseCase.getMovieCredits(id))
        .willReturn(MovieCredits.of(List.of(Credit.Cast.of(lead, "Dana Whitfield", 1)), List.of()));

    mockMvc
        .perform(get("/movies/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.cast.length()").value(1))
        .andExpect(jsonPath("$.data._embedded.crew.length()").value(0));
  }

  @Test
  void getMovieCredits_movieWithCrewButNoCast_returns200WithPopulatedCrewAndEmptyCast()
      throws Exception {
    UUID id = UUID.randomUUID();
    Person director = new Person(UUID.randomUUID(), "Priya Nandan");
    given(getMovieCreditsUseCase.getMovieCredits(id))
        .willReturn(
            MovieCredits.of(
                List.of(), List.of(new Credit.Crew(director, "Directing", "Director"))));

    mockMvc
        .perform(get("/movies/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.cast.length()").value(0))
        .andExpect(jsonPath("$.data._embedded.crew.length()").value(1));
  }

  @Test
  void getMovieCredits_wellFormedUnknownId_returns404ProblemJson() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieCreditsUseCase.getMovieCredits(id))
        .willThrow(
            new ResourceNotFoundException("MOVIE_NOT_FOUND", "No movie found with id " + id));

    mockMvc
        .perform(get("/movies/{id}/credits", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getMovieCredits_malformedId_returns400WithoutInvokingTheUseCase() throws Exception {
    mockMvc
        .perform(get("/movies/{id}/credits", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());

    verifyNoInteractions(getMovieCreditsUseCase);
  }

  @Test
  void getMovieCredits_requiresNoAuthentication() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieCreditsUseCase.getMovieCredits(id))
        .willReturn(MovieCredits.of(List.of(), List.of()));

    mockMvc.perform(get("/movies/{id}/credits", id)).andExpect(status().isOk());
  }
}
