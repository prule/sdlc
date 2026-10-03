package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovieSummaryTest {

  private final MovieId id = new MovieId(UUID.randomUUID());

  @Test
  void rejectsABlankTitle() {
    assertThatThrownBy(
            () -> new MovieSummary(id, " ", 2016, List.of(), Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ordersAndDeduplicatesGenresLikeAMoviesDetails() {
    MovieSummary summary =
        new MovieSummary(
            id,
            "Arrival",
            2016,
            List.of("Sci-Fi", "drama", "Sci-Fi", "Action"),
            Optional.empty(),
            Optional.empty());

    assertThat(summary.genres()).containsExactly("Action", "drama", "Sci-Fi");
  }

  @Test
  void holdsUnrecordedDetailsAsEmpty() {
    MovieSummary summary =
        new MovieSummary(id, "Untitled", 1974, List.of(), Optional.empty(), Optional.empty());

    assertThat(summary.genres()).isEmpty();
    assertThat(summary.runtime()).isEmpty();
    assertThat(summary.rating()).isEmpty();
  }
}
