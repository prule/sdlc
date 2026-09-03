package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MoviePageTest {

  private static Movie movie() {
    return new Movie(
        new MovieId(UUID.randomUUID()),
        "Arrival",
        2016,
        List.of(new Genre("Sci-Fi")),
        Optional.empty(),
        Optional.empty(),
        Optional.of(new Rating(BigDecimal.valueOf(4))));
  }

  @Test
  void constructor_createsAValidPage() {
    MoviePage page = new MoviePage(List.of(movie()), 0, 20, 1, 1);

    assertThat(page.items()).hasSize(1);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  @Test
  void constructor_allowsEmptyItems() {
    MoviePage page = new MoviePage(List.of(), 0, 20, 0, 0);

    assertThat(page.items()).isEmpty();
  }

  @Test
  void constructor_rejectsNullItems() {
    assertThatThrownBy(() -> new MoviePage(null, 0, 20, 0, 0))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_defensivelyCopiesItems() {
    List<Movie> mutable = new java.util.ArrayList<>();
    mutable.add(movie());
    MoviePage page = new MoviePage(mutable, 0, 20, 1, 1);

    mutable.add(movie());

    assertThat(page.items()).hasSize(1);
  }
}
