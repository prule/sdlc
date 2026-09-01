package com.acme.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.common.test.PostgresIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Full-flow integration test: proves the Spring context loads against a real Postgres database (via
 * Testcontainers, no H2) and that the Flyway baseline migration actually executes — the "walking"
 * part of the walking skeleton.
 */
class ApplicationIntegrationTest extends PostgresIntegrationTest {

  @Autowired private ApplicationContext context;
  @Autowired private DataSource dataSource;

  @Test
  void contextLoads() {
    assertThat(context).isNotNull();
  }

  @Test
  void flywayBaselineMigrationHasRun() throws Exception {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var resultSet =
            statement.executeQuery(
                "select version from flyway_schema_history where version = '1'")) {
      assertThat(resultSet.next()).isTrue();
      assertThat(resultSet.getString("version")).isEqualTo("1");
    }
  }
}
