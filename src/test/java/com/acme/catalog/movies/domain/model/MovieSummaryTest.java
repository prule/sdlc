package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MovieSummary}: genre normalization (shared with {@link Movie} via {@link
 * GenreNames}) and required fields (design D3, task 3.2).
 */
class MovieSummaryTest {

  private static final MovieId ID = new MovieId(UUID.randomUUID());

  @Test
  void genresAreOrderedAlphabeticallyIgnoringCaseLikeMovie() {
    MovieSummary summary =
        new MovieSummary(
            ID,
            "Untitled",
            2000,
            List.of("Thriller", "drama", "Comedy"),
            Optional.empty(),
            Optional.empty());

    assertThat(summary.genres()).containsExactly("Comedy", "drama", "Thriller");
  }

  @Test
  void removesExactDuplicateGenres() {
    MovieSummary summary =
        new MovieSummary(
            ID, "Untitled", 2000, List.of("Drama", "Drama"), Optional.empty(), Optional.empty());

    assertThat(summary.genres()).containsExactly("Drama");
  }

  @Test
  void requiresANonNullId() {
    assertThatThrownBy(
            () ->
                new MovieSummary(
                    null, "Untitled", 2000, List.of(), Optional.empty(), Optional.empty()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void requiresANonBlankTitle() {
    assertThatThrownBy(
            () -> new MovieSummary(ID, "  ", 2000, List.of(), Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void holdsOptionalRuntimeAndRating() {
    MovieSummary summary =
        new MovieSummary(
            ID,
            "Arrival",
            2016,
            List.of(),
            Optional.of(new RuntimeMinutes(116)),
            Optional.of(new Rating(new BigDecimal("4.5"))));

    assertThat(summary.runtime()).contains(new RuntimeMinutes(116));
    assertThat(summary.rating()).contains(new Rating(new BigDecimal("4.5")));
  }
}
