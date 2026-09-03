package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.common.test.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Default profile (no {@code demo}, no {@code prod}) — the demo seed loader bean must not even be
 * registered, so it never loads under production and never affects the default test suite.
 */
class DemoMovieSeedLoaderDisabledTest extends PostgresIntegrationTest {

  @Autowired private ApplicationContext context;

  @Test
  void demoSeedLoaderIsNotRegisteredWithoutTheDemoProfile() {
    assertThat(context.getBeanNamesForType(DemoMovieSeedLoader.class)).isEmpty();
  }
}
