package com.acme.catalog.movies.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.acme.catalog.movies.application.port.in.GetMovieUseCase;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.platform.web.GlobalExceptionHandler;
import com.acme.platform.web.PlatformWebTest;
import com.acme.shared.domain.ResourceNotFoundException;
import com.acme.testsupport.LogCaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-slice coverage for the {@code getMovie} failure outcomes ({@code catalog/movies} "Malformed
 * identifier is refused before any lookup", "Unknown movie is reported as not found" and "Internal
 * fault is reported generically").
 */
@PlatformWebTest(controllers = MovieController.class)
class MovieControllerFailureTest {

  private static final String WELL_FORMED_ID = "6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private GetMovieUseCase getMovieUseCase;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not-a-movie-id",
        "1-1-1-1-1",
        "6f1c2a3b4d5e4f608a7b9c0d1e2f3a4b",
        "123",
      })
  void malformedIdentifierIsBadRequestAndNeverInvokesTheUseCase(String malformedId)
      throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/v1/movies/{id}", malformedId).contextPath("/api/v1")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("BAD_REQUEST");
    Mockito.verifyNoInteractions(getMovieUseCase);
  }

  @Test
  void unknownMovieIsNotFound() throws Exception {
    BDDMockito.given(
            getMovieUseCase.getMovie(new MovieId(java.util.UUID.fromString(WELL_FORMED_ID))))
        .willThrow(new ResourceNotFoundException("no movie with that id"));

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/movies/{id}", WELL_FORMED_ID).contextPath("/api/v1"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("code").asText()).isEqualTo("NOT_FOUND");
  }

  @Test
  void unexpectedFaultIsAGenericInternalErrorLoggedWithTheCorrelationId() throws Exception {
    BDDMockito.given(
            getMovieUseCase.getMovie(new MovieId(java.util.UUID.fromString(WELL_FORMED_ID))))
        .willThrow(new RuntimeException("secret-db-host:5432 refused"));

    try (LogCaptor logCaptor = LogCaptor.forClass(GlobalExceptionHandler.class)) {
      MvcResult result =
          mockMvc
              .perform(get("/api/v1/movies/{id}", WELL_FORMED_ID).contextPath("/api/v1"))
              .andReturn();

      assertThat(result.getResponse().getStatus()).isEqualTo(500);
      String raw = result.getResponse().getContentAsString();
      assertThat(raw).doesNotContain("secret-db-host");
      JsonNode body = objectMapper.readTree(raw);
      assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
      assertThat(body.get("detail").asText()).isEqualTo("An unexpected error occurred.");

      String correlationId = result.getResponse().getHeader("X-Correlation-Id");
      List<ILoggingEvent> events = logCaptor.events();
      assertThat(events)
          .anySatisfy(
              event -> {
                assertThat(event.getLevel().toString()).isEqualTo("ERROR");
                assertThat(event.getFormattedMessage()).contains(correlationId);
              });
    }
  }
}
