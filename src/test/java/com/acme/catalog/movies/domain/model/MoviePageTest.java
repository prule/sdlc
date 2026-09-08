package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MoviePage}'s derived {@code totalPages}. */
class MoviePageTest {

  @Test
  void totalPages_evenDivision_isExact() {
    MoviePage page = new MoviePage(List.of(), 0, 20, 40);

    assertThat(page.totalPages()).isEqualTo(2);
  }

  @Test
  void totalPages_unevenDivision_roundsUp() {
    MoviePage page = new MoviePage(List.of(), 0, 20, 41);

    assertThat(page.totalPages()).isEqualTo(3);
  }

  @Test
  void totalPages_zeroTotalElements_isZero() {
    MoviePage page = new MoviePage(List.of(), 0, 20, 0);

    assertThat(page.totalPages()).isEqualTo(0);
  }

  @Test
  void content_isImmutable() {
    MoviePage page = new MoviePage(new java.util.ArrayList<>(), 0, 20, 0);

    assertThat(page.content()).isEmpty();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                page.content()
                    .add(
                        Movie.of(
                            java.util.UUID.randomUUID(),
                            "Title",
                            2020,
                            List.of(Genre.DRAMA),
                            null,
                            null,
                            null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
