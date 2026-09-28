package com.acme.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for every test that boots the application context (standards/testing.md §3): the
 * context runs against a real PostgreSQL, never H2 — only {@code H2DefaultRuntimeSmokeTest} may
 * boot on the H2 default datasource.
 *
 * <p>The container is a JVM-wide singleton, started once in a static initializer and never stopped
 * by JUnit (Testcontainers' Ryuk reaps it at JVM exit). A {@code @Container} field here would
 * instead be started and stopped per subclass, leaving Spring's cached contexts — shared between
 * subclasses with the same configuration — pointing at a stopped container.
 */
@SpringBootTest
public abstract class PostgresIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // The base class always pins this, whatever profile a subclass activates, so no
    // Testcontainers-backed test ever picks up the standalone-only demo seed (design D7).
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
  }
}
