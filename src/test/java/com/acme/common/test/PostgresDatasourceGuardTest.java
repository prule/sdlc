package com.acme.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Guards against the in-memory H2 default runtime datasource ever leaking into the test suite.
 * Every DB-backed test extends {@link PostgresIntegrationTest} and gets its datasource overridden
 * via {@code @DynamicPropertySource} to point at the shared Testcontainers PostgreSQL instance —
 * this test asserts that override actually took effect by inspecting the live connection's product
 * name, so any accidental fallback to H2 fails the build loudly rather than silently.
 */
class PostgresDatasourceGuardTest extends PostgresIntegrationTest {

  @Autowired private DataSource dataSource;

  @Test
  void activeTestDatasourceIsPostgres() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metaData = connection.getMetaData();
      assertThat(metaData.getDatabaseProductName()).isEqualTo("PostgreSQL");
    }
  }
}
