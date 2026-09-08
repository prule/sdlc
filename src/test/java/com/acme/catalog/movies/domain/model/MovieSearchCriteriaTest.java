package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MovieSearchCriteria} invariants. */
class MovieSearchCriteriaTest {

  @Test
  void none_hasNoCriteriaAndMatchesEveryMovie() {
    MovieSearchCriteria criteria = MovieSearchCriteria.none();

    assertThat(criteria.title()).isEmpty();
    assertThat(criteria.genres()).isEmpty();
    assertThat(criteria.releaseYearFrom()).isEmpty();
    assertThat(criteria.releaseYearTo()).isEmpty();
    assertThat(criteria.minRating()).isEmpty();
  }

  @Test
  void of_withRawValues_wrapsThemAsPresentOptionals() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            "reel", Set.of(Genre.DRAMA, Genre.MYSTERY), 2000, 2020, BigDecimal.valueOf(4));

    assertThat(criteria.title()).contains("reel");
    assertThat(criteria.genres()).containsExactlyInAnyOrder(Genre.DRAMA, Genre.MYSTERY);
    assertThat(criteria.releaseYearFrom()).contains(2000);
    assertThat(criteria.releaseYearTo()).contains(2020);
    assertThat(criteria.minRating()).contains(BigDecimal.valueOf(4));
  }

  @Test
  void of_withNullOptionalValues_leavesThemAbsent() {
    MovieSearchCriteria criteria = MovieSearchCriteria.of(null, null, null, null, null);

    assertThat(criteria.title()).isEmpty();
    assertThat(criteria.genres()).isEmpty();
    assertThat(criteria.releaseYearFrom()).isEmpty();
    assertThat(criteria.releaseYearTo()).isEmpty();
    assertThat(criteria.minRating()).isEmpty();
  }

  @Test
  void of_withBlankTitle_treatsItAsAbsent() {
    MovieSearchCriteria criteria = MovieSearchCriteria.of("   ", null, null, null, null);

    assertThat(criteria.title()).isEmpty();
  }

  @Test
  void genres_isImmutable() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(null, Set.of(Genre.DRAMA), null, null, null);

    assertThatThrownByAddingToGenres(criteria);
  }

  private static void assertThatThrownByAddingToGenres(MovieSearchCriteria criteria) {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> criteria.genres().add(Genre.COMEDY))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
