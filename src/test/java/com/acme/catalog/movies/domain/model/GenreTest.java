package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Confirms the domain {@link Genre} vocabulary matches the OpenAPI contract enum exactly. */
class GenreTest {

  @Test
  void matchesTheOpenApiContractEnumOfEighteenGenres() {
    assertThat(Genre.values()).hasSize(18);
  }

  @Test
  void everyGenreNameMatchesTheGeneratedContractEnumConstantName() {
    for (Genre genre : Genre.values()) {
      assertThat(com.acme.generated.model.Genre.valueOf(genre.name())).isNotNull();
    }
  }
}
