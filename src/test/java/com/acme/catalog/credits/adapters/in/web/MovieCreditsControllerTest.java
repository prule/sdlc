package com.acme.catalog.credits.adapters.in.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.catalog.credits.application.port.in.GetMovieCreditsUseCase;
import com.acme.catalog.credits.domain.model.CastCredit;
import com.acme.catalog.credits.domain.model.CrewCredit;
import com.acme.catalog.credits.domain.model.MovieCredits;
import com.acme.catalog.credits.domain.model.Person;
import com.acme.catalog.credits.domain.model.PersonId;
import com.acme.catalog.movies.adapters.in.web.MovieController;
import com.acme.catalog.movies.application.port.in.GetMovieByIdUseCase;
import com.acme.catalog.movies.application.port.in.SearchMoviesUseCase;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.error.GlobalExceptionHandler;
import com.acme.common.error.ResourceNotFoundException;
import com.acme.common.security.ProblemAccessDeniedHandler;
import com.acme.common.security.ProblemAuthenticationEntryPoint;
import com.acme.common.security.SecurityConfig;
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
 * Web-slice test for {@code MovieController.getMovieCredits}. {@link GetMovieCreditsUseCase} is
 * mocked at the port seam.
 */
@WebMvcTest(MovieController.class)
@Import({
  SecurityConfig.class,
  ProblemAuthenticationEntryPoint.class,
  ProblemAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
class MovieCreditsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetMovieByIdUseCase getMovieByIdUseCase;
  @MockitoBean private SearchMoviesUseCase searchMoviesUseCase;
  @MockitoBean private GetMovieCreditsUseCase getMovieCreditsUseCase;

  private static Person person(String name) {
    return new Person(new PersonId(UUID.randomUUID()), name);
  }

  @Test
  void getMovieCredits_existingMovieWithCastAndCrew_returns200EnvelopedCollection()
      throws Exception {
    UUID id = UUID.randomUUID();
    Person actor = person("Keanu Reeves");
    Person director = person("Lana Wachowski");
    MovieCredits credits =
        new MovieCredits(
            List.of(new CastCredit(actor, "Neo", 1)),
            List.of(new CrewCredit(director, "Directing", "Director")));
    given(getMovieCreditsUseCase.getMovieCredits(new MovieId(id))).willReturn(credits);

    mockMvc
        .perform(get("/movies/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().exists("X-Correlation-Id"))
        .andExpect(jsonPath("$.data._embedded.cast", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$.data._embedded.crew", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(
            jsonPath("$.data._embedded.cast[0].person.id").value(actor.id().value().toString()))
        .andExpect(jsonPath("$.data._embedded.cast[0].person.name").value("Keanu Reeves"))
        .andExpect(jsonPath("$.data._embedded.cast[0].character").value("Neo"))
        .andExpect(jsonPath("$.data._embedded.cast[0].billingOrder").value(1))
        .andExpect(
            jsonPath("$.data._embedded.crew[0].person.id").value(director.id().value().toString()))
        .andExpect(jsonPath("$.data._embedded.crew[0].person.name").value("Lana Wachowski"))
        .andExpect(jsonPath("$.data._embedded.crew[0].department").value("Directing"))
        .andExpect(jsonPath("$.data._embedded.crew[0].job").value("Director"))
        .andExpect(jsonPath("$.data._links.self.href").exists())
        .andExpect(jsonPath("$.data._embedded.cast[0]._links").doesNotExist())
        .andExpect(jsonPath("$.data._embedded.crew[0]._links").doesNotExist())
        .andExpect(jsonPath("$.meta.correlationId").exists())
        .andExpect(jsonPath("$.meta.timestamp").exists())
        .andExpect(jsonPath("$.meta.pagination").doesNotExist());
  }

  @Test
  void getMovieCredits_onlySelfLinkPresent_noTemplates() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieCreditsUseCase.getMovieCredits(new MovieId(id)))
        .willReturn(new MovieCredits(List.of(), List.of()));

    var result = mockMvc.perform(get("/movies/{id}/credits", id)).andExpect(status().isOk());

    result
        .andExpect(jsonPath("$.data._links.self").exists())
        .andExpect(jsonPath("$.data._templates").doesNotExist());
  }

  @Test
  void getMovieCredits_movieWithNoCredits_returns200WithEmptyArrays() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieCreditsUseCase.getMovieCredits(new MovieId(id)))
        .willReturn(new MovieCredits(List.of(), List.of()));

    mockMvc
        .perform(get("/movies/{id}/credits", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data._embedded.cast", org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.data._embedded.crew", org.hamcrest.Matchers.hasSize(0)))
        .andExpect(jsonPath("$.data._links.self").exists());
  }

  @Test
  void getMovieCredits_unknownId_returns404ProblemJsonNoLinks() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieCreditsUseCase.getMovieCredits(new MovieId(id)))
        .willThrow(
            new ResourceNotFoundException("MOVIE_NOT_FOUND", "No movie found for id: " + id));

    mockMvc
        .perform(get("/movies/{id}/credits", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"))
        .andExpect(jsonPath("$.correlationId").exists())
        .andExpect(jsonPath("$._links").doesNotExist())
        .andExpect(jsonPath("$._embedded").doesNotExist());
  }

  @Test
  void getMovieCredits_malformedUuid_returns400ProblemJsonNotServerError() throws Exception {
    mockMvc
        .perform(get("/movies/{id}/credits", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void getMovieCredits_noAuthorizationHeader_isNever401Or403() throws Exception {
    UUID id = UUID.randomUUID();
    given(getMovieCreditsUseCase.getMovieCredits(new MovieId(id)))
        .willReturn(new MovieCredits(List.of(), List.of()));

    mockMvc.perform(get("/movies/{id}/credits", id)).andExpect(status().isOk());
  }
}
