package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovieTest {

  private final MovieId id = new MovieId(UUID.randomUUID());

  @Test
  void rejectsABlankTitle() {
    assertThatThrownBy(
            () ->
                new Movie(
                    id,
                    "   ",
                    2016,
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sortsGenresAlphabetically() {
    Movie movie =
        new Movie(
            id,
            "Arrival",
            2016,
            List.of("Thriller", "Drama", "Sci-Fi"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThat(movie.genres()).containsExactly("Drama", "Sci-Fi", "Thriller");
  }

  @Test
  void ordersMixedCaseNamesIgnoringCaseWithATieBrokenByExactName() {
    Movie movie =
        new Movie(
            id,
            "Arrival",
            2016,
            List.of("drama", "Drama", "Comedy"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThat(movie.genres()).containsExactly("Comedy", "Drama", "drama");
  }

  @Test
  void removesDuplicateGenres() {
    Movie movie =
        new Movie(
            id,
            "Arrival",
            2016,
            List.of("Drama", "Drama"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThat(movie.genres()).containsExactly("Drama");
  }

  @Test
  void allowsAnEmptyGenreList() {
    Movie movie =
        new Movie(
            id, "Arrival", 2016, List.of(), Optional.empty(), Optional.empty(), Optional.empty());

    assertThat(movie.genres()).isEmpty();
  }

  @Test
  void allAbsentOptionalFieldsGiveEmptyOptionals() {
    Movie movie =
        new Movie(
            id, "Arrival", 2016, List.of(), Optional.empty(), Optional.empty(), Optional.empty());

    assertThat(movie.runtime()).isEmpty();
    assertThat(movie.synopsis()).isEmpty();
    assertThat(movie.rating()).isEmpty();
  }
}
