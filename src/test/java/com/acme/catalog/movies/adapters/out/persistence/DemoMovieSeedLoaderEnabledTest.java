package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.common.test.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.ActiveProfiles;

/** {@code demo} profile active — the seed loader runs at startup and is idempotent. */
@ActiveProfiles("demo")
class DemoMovieSeedLoaderEnabledTest extends PostgresIntegrationTest {

  @Autowired private DemoMovieSeedLoader demoMovieSeedLoader;
  @Autowired private MovieJpaRepository movieJpaRepository;

  @Test
  void demoSeedLoaderInsertsMoviesIdempotently() throws Exception {
    long countAfterStartup = movieJpaRepository.count();
    assertThat(countAfterStartup)
        .as("movies inserted by the seed loader at context startup")
        .isPositive();

    demoMovieSeedLoader.run(new DefaultApplicationArguments());

    assertThat(movieJpaRepository.count())
        .as("re-running the seed loader must not insert duplicates")
        .isEqualTo(countAfterStartup);
  }
}
