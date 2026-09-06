package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link Movie} aggregate's factory invariants. */
class MovieTest {

  private static final UUID ID = UUID.randomUUID();

  @Test
  void of_withAllOptionalFields_buildsAMovieCarryingThem() {
    Movie movie =
        Movie.of(
            ID,
            "The Wandering Reel",
            2019,
            List.of(Genre.DRAMA),
            118,
            "A projectionist discovers a film that predicts the news.",
            new Rating(BigDecimal.valueOf(4.5)));

    assertThat(movie.id()).isEqualTo(ID);
    assertThat(movie.title()).isEqualTo("The Wandering Reel");
    assertThat(movie.releaseYear()).isEqualTo(2019);
    assertThat(movie.genres()).containsExactly(Genre.DRAMA);
    assertThat(movie.runtimeMinutes()).contains(118);
    assertThat(movie.synopsis()).isPresent();
    assertThat(movie.rating()).contains(new Rating(BigDecimal.valueOf(4.5)));
  }

  @Test
  void of_withNoOptionalFields_buildsAMovieWithThemAbsent() {
    Movie movie = Movie.of(ID, "Silent Harbor", 2021, List.of(Genre.MYSTERY), null, null, null);

    assertThat(movie.runtimeMinutes()).isEmpty();
    assertThat(movie.synopsis()).isEmpty();
    assertThat(movie.rating()).isEmpty();
  }

  @Test
  void of_withNoGenres_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> Movie.of(ID, "No Genre", 2020, List.of(), null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("genre");
  }

  @Test
  void of_withBlankTitle_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> Movie.of(ID, "  ", 2020, List.of(Genre.DRAMA), null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }

  @Test
  void of_withNullId_throwsNullPointerException() {
    assertThatThrownBy(() -> Movie.of(null, "Title", 2020, List.of(Genre.DRAMA), null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void canonicalConstructor_deduplicatesGenresIntoAnImmutableSet() {
    Movie movie =
        new Movie(
            ID,
            "Title",
            2020,
            Set.of(Genre.DRAMA),
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            java.util.Optional.empty());

    assertThatThrownBy(() -> movie.genres().add(Genre.COMEDY))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
