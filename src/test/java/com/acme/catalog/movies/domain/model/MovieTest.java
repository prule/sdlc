package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovieTest {

  private static MovieId id() {
    return new MovieId(UUID.randomUUID());
  }

  @Test
  void constructor_createsAValidMovieWithAllOptionalsPresent() {
    Movie movie =
        new Movie(
            id(),
            "The Matrix",
            1999,
            List.of(new Genre("Sci-Fi"), new Genre("Action")),
            Optional.of(136),
            Optional.of("A hacker discovers the truth."),
            Optional.of(new Rating(BigDecimal.valueOf(4.5))));

    assertThat(movie.title()).isEqualTo("The Matrix");
    assertThat(movie.releaseYear()).isEqualTo(1999);
    assertThat(movie.genres()).hasSize(2);
    assertThat(movie.runtimeMinutes()).contains(136);
    assertThat(movie.synopsis()).isPresent();
    assertThat(movie.rating()).contains(new Rating(BigDecimal.valueOf(4.5)));
  }

  @Test
  void constructor_allowsAllOptionalFieldsAbsent() {
    Movie movie =
        new Movie(
            id(),
            "Obscure Film",
            2001,
            List.of(new Genre("Drama")),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThat(movie.runtimeMinutes()).isEmpty();
    assertThat(movie.synopsis()).isEmpty();
    assertThat(movie.rating()).isEmpty();
  }

  @Test
  void constructor_rejectsZeroGenres() {
    assertThatThrownBy(
            () ->
                new Movie(
                    id(),
                    "No Genre",
                    2001,
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("genres");
  }

  @Test
  void constructor_rejectsARatingOutsideZeroToFive() {
    assertThatThrownBy(() -> new Rating(BigDecimal.valueOf(5.1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Rating(BigDecimal.valueOf(-0.1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_rejectsABlankTitle() {
    assertThatThrownBy(
            () ->
                new Movie(
                    id(),
                    " ",
                    2001,
                    List.of(new Genre("Drama")),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
