package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Filmography}'s derived {@code totalPages}. */
class FilmographyTest {

  @Test
  void totalPages_evenDivision_isExact() {
    Filmography filmography = new Filmography(List.of(), 0, 20, 40);

    assertThat(filmography.totalPages()).isEqualTo(2);
  }

  @Test
  void totalPages_unevenDivision_roundsUp() {
    Filmography filmography = new Filmography(List.of(), 0, 20, 41);

    assertThat(filmography.totalPages()).isEqualTo(3);
  }

  @Test
  void totalPages_zeroTotalElements_isZero() {
    Filmography filmography = new Filmography(List.of(), 0, 20, 0);

    assertThat(filmography.totalPages()).isEqualTo(0);
  }

  @Test
  void content_isImmutable() {
    Filmography filmography = new Filmography(new java.util.ArrayList<>(), 0, 20, 0);

    assertThat(filmography.content()).isEmpty();
    assertThatThrownBy(
            () ->
                filmography
                    .content()
                    .add(
                        new FilmographyEntry(
                            FilmographyMovieSummary.of(
                                java.util.UUID.randomUUID(),
                                "Title",
                                2020,
                                List.of(Genre.DRAMA),
                                null,
                                null),
                            Capacity.Acting.of(null))))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
