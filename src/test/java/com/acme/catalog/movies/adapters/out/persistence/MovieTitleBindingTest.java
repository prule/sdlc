package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acme.testsupport.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pins that the title search term reaches PostgreSQL as a bind parameter, never written into the
 * SQL text (design D1). A quote alone does not prove binding, because Hibernate escapes quotes when
 * it inlines a literal, so the test also inspects the SQL Hibernate sends through a test-scoped
 * {@link StatementInspector} (observation only; no framework bean is replaced).
 */
@AutoConfigureMockMvc
@TestPropertySource(
    properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.acme.catalog.movies.adapters.out.persistence."
            + "MovieTitleBindingTest$RecordingStatementInspector")
class MovieTitleBindingTest extends PostgresIntegrationTest {

  private static final Pattern BOUND_TITLE_PATTERN =
      Pattern.compile("like\\s+lower\\s*\\(\\s*\\?\\s*\\)", Pattern.CASE_INSENSITIVE);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearRecordedStatements() {
    RecordingStatementInspector.STATEMENTS.clear();
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  private void insertMovie(String title, int releaseYear) {
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year) VALUES (?, ?, ?)",
        UUID.randomUUID(),
        title,
        releaseYear);
  }

  @Test
  void aQuoteInTheTitleTermIsMatchedLiterallyAndSentAsABindParameter() throws Exception {
    insertMovie("Ocean's Eleven", 2001);
    insertMovie("Arrival", 2016);

    MvcResult result =
        mockMvc
            // A URI (not a template) so the already-encoded %20 is sent as-is, not re-encoded.
            .perform(get(URI.create("/api/v1/movies?title=n's%20e")).contextPath("/api/v1"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("data").get("_embedded").get("movies"))
        .extracting(movie -> movie.get("title").asText())
        .containsExactly("Ocean's Eleven");

    List<String> statements = List.copyOf(RecordingStatementInspector.STATEMENTS);
    assertThat(statements).isNotEmpty();
    assertThat(statements).noneMatch(sql -> sql.contains("n's e") || sql.contains("n''s e"));
    assertThat(statements).anyMatch(sql -> BOUND_TITLE_PATTERN.matcher(sql).find());
  }

  /**
   * Records every SQL statement Hibernate prepares, unchanged. Registered by class name through the
   * {@code hibernate.session_factory.statement_inspector} property, so Hibernate instantiates it.
   */
  public static class RecordingStatementInspector implements StatementInspector {

    static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
      STATEMENTS.add(sql);
      return sql;
    }
  }
}
