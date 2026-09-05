package com.acme.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for integration tests that need a real PostgreSQL database. The container is a
 * singleton, static instance shared across the whole test suite (started once, never stopped by
 * this class — reaped by Ryuk/JVM exit) so Flyway migrations run once against a real engine — no H2
 * or other in-memory substitute is used anywhere in this codebase.
 *
 * <p>Deliberately does NOT use {@code @Testcontainers}/{@code @Container}: that JUnit Jupiter
 * extension manages the lifecycle of an annotated static field per test class and stops it after
 * the first subclass finishes, which tears the container down out from under every subsequent
 * DB-backed test class sharing this base. Starting the container in a plain static initializer
 * instead, with no {@code @Container} annotation on the field, is the correct pattern for a
 * container meant to be a true cross-class singleton.
 *
 * <p>Activates the {@code test} profile as an explicit marker distinguishing the test suite from
 * the H2 default runtime (there is no {@code application-test.yml}; the datasource always comes
 * from {@link #datasourceProperties}, not from this profile) — see {@code DemoMovieSeedLoader}'s
 * profile gating, which is off under {@code test}.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
  }
}
