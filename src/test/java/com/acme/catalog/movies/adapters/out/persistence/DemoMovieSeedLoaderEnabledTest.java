package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.credits.adapters.out.persistence.CreditJpaRepository;
import com.acme.catalog.credits.adapters.out.persistence.PersonJpaRepository;
import com.acme.common.test.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code demo} profile active — the seed loader runs at startup and is idempotent.
 *
 * <p><b>Shared-singleton contract:</b> {@link PostgresIntegrationTest}'s container is a single
 * JVM-wide instance reused by every DB-backed test class (see its javadoc) — unlike {@link
 * MoviePersistenceAdapterTest}, this test is not {@code @Transactional} (the seed runs as an {@code
 * ApplicationRunner} during context startup, outside any test-managed transaction), so any row it
 * inserts would otherwise persist for the rest of the suite. This class is the only place in the
 * suite that leaves durable rows in {@code movies}/{@code genres}/{@code movie_genre}/{@code
 * credits}/{@code people}, so it cleans them up itself afterward, respecting FK order ({@code
 * credits} first — it references both {@code movies} and {@code people} — then the join table, then
 * {@code movies}/{@code genres}/{@code people}) — leaving the shared container exactly as it found
 * it. Copy this cleanup if you add another non-transactional, DB-writing test against the shared
 * container.
 */
@ActiveProfiles("demo")
class DemoMovieSeedLoaderEnabledTest extends PostgresIntegrationTest {

  @Autowired private DemoMovieSeedLoader demoMovieSeedLoader;
  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private PersonJpaRepository personJpaRepository;
  @Autowired private CreditJpaRepository creditJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUpSeededRows() {
    jdbcTemplate.update("DELETE FROM credits");
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movies");
    jdbcTemplate.update("DELETE FROM genres");
    jdbcTemplate.update("DELETE FROM people");
  }

  /**
   * A single test method: the seed only runs once (at context startup, before any
   * {@code @AfterEach} cleanup) per JVM, so a second {@code @Test} method would see an empty DB
   * (the first method's cleanup already ran) rather than the startup-seeded state.
   */
  @Test
  void demoSeedLoaderInsertsMoviesPeopleAndCreditsIdempotently() throws Exception {
    long movieCountAfterStartup = movieJpaRepository.count();
    long personCountAfterStartup = personJpaRepository.count();
    long creditCountAfterStartup = creditJpaRepository.count();
    assertThat(movieCountAfterStartup)
        .as("movies inserted by the seed loader at context startup")
        .isPositive();
    assertThat(personCountAfterStartup)
        .as("people inserted by the seed loader at context startup")
        .isPositive();
    assertThat(creditCountAfterStartup)
        .as("credits inserted by the seed loader at context startup")
        .isPositive();

    demoMovieSeedLoader.run(new DefaultApplicationArguments());

    assertThat(movieJpaRepository.count())
        .as("re-running the seed loader must not insert duplicate movies")
        .isEqualTo(movieCountAfterStartup);
    assertThat(personJpaRepository.count())
        .as("re-running the seed loader must not insert duplicate people")
        .isEqualTo(personCountAfterStartup);
    assertThat(creditJpaRepository.count())
        .as("re-running the seed loader must not insert duplicate credits")
        .isEqualTo(creditCountAfterStartup);
  }
}
