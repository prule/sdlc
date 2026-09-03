package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MovieSearchCriteriaTest {

  @Test
  void constructor_acceptsAllFiltersAbsent() {
    MovieSearchCriteria criteria = MovieSearchCriteria.NONE;

    assertThat(criteria.title()).isEmpty();
    assertThat(criteria.genres()).isEmpty();
    assertThat(criteria.yearFrom()).isEmpty();
    assertThat(criteria.yearTo()).isEmpty();
    assertThat(criteria.minRating()).isEmpty();
  }

  @Test
  void constructor_acceptsAllFiltersPresent() {
    MovieSearchCriteria criteria =
        new MovieSearchCriteria(
            Optional.of("matrix"),
            List.of(new Genre("Sci-Fi"), new Genre("Action")),
            Optional.of(1990),
            Optional.of(1999),
            Optional.of(new Rating(BigDecimal.valueOf(4))));

    assertThat(criteria.title()).contains("matrix");
    assertThat(criteria.genres()).hasSize(2);
    assertThat(criteria.yearFrom()).contains(1990);
    assertThat(criteria.yearTo()).contains(1999);
    assertThat(criteria.minRating()).isPresent();
  }

  @Test
  void constructor_rejectsNullTitleOptional() {
    assertThatThrownBy(
            () ->
                new MovieSearchCriteria(
                    null, List.of(), Optional.empty(), Optional.empty(), Optional.empty()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_rejectsNullGenresList() {
    assertThatThrownBy(
            () ->
                new MovieSearchCriteria(
                    Optional.empty(), null, Optional.empty(), Optional.empty(), Optional.empty()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructor_defensivelyCopiesGenres() {
    List<Genre> mutable = new java.util.ArrayList<>();
    mutable.add(new Genre("Drama"));
    MovieSearchCriteria criteria =
        new MovieSearchCriteria(
            Optional.empty(), mutable, Optional.empty(), Optional.empty(), Optional.empty());

    mutable.add(new Genre("Crime"));

    assertThat(criteria.genres()).hasSize(1);
  }
}
