package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.shared.domain.InvalidRequestException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MovieSearchCriteriaTest {

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  \t "})
  void blankTitleBecomesAbsent(String title) {
    assertThat(MovieSearchCriteria.of(title, null, null, null, null).titleTerm()).isEmpty();
  }

  @Test
  void titleTermIsKeptAsGivenWithoutTrimming() {
    assertThat(MovieSearchCriteria.of(" arrival", null, null, null, null).titleTerm())
        .contains(" arrival");
  }

  @Test
  void blankGenresAreDropped() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(null, Arrays.asList("Drama", "", "  ", null), null, null, null);

    assertThat(criteria.genres()).containsExactly("Drama");
  }

  @Test
  void noCriteriaAtAll() {
    MovieSearchCriteria criteria = MovieSearchCriteria.of(null, null, null, null, null);

    assertThat(criteria.titleTerm()).isEmpty();
    assertThat(criteria.genres()).isEmpty();
    assertThat(criteria.releaseYearFrom()).isEmpty();
    assertThat(criteria.releaseYearTo()).isEmpty();
    assertThat(criteria.minRating()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "5", "5.0", "4.5"})
  void minRatingWithinTheScaleIsAccepted(String rating) {
    assertThat(
            MovieSearchCriteria.of(null, List.of(), null, null, new BigDecimal(rating)).minRating())
        .contains(new BigDecimal(rating));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.1", "5.1", "-1", "5.5"})
  void minRatingOutsideTheScaleIsRefusedNamingMinRating(String rating) {
    assertThatThrownBy(
            () -> MovieSearchCriteria.of(null, List.of(), null, null, new BigDecimal(rating)))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("minRating"));
  }

  @Test
  void reversedRangeIsRefusedNamingReleaseYearFrom() {
    assertThatThrownBy(() -> MovieSearchCriteria.of(null, List.of(), 2010, 2000, null))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("releaseYearFrom"));
  }

  @Test
  void equalBoundsAreAllowed() {
    MovieSearchCriteria criteria = MovieSearchCriteria.of(null, List.of(), 2016, 2016, null);

    assertThat(criteria.releaseYearFrom()).contains(2016);
    assertThat(criteria.releaseYearTo()).contains(2016);
  }

  @Test
  void eitherBoundAloneIsAllowed() {
    assertThat(MovieSearchCriteria.of(null, null, 2000, null, null).releaseYearFrom())
        .contains(2000);
    assertThat(MovieSearchCriteria.of(null, null, null, 1999, null).releaseYearTo()).contains(1999);
  }

  @Test
  void minRatingIsCheckedBeforeTheRange() {
    assertThatThrownBy(
            () -> MovieSearchCriteria.of(null, List.of(), 2010, 2000, new BigDecimal("6")))
        .isInstanceOfSatisfying(
            InvalidRequestException.class, e -> assertThat(e.field()).isEqualTo("minRating"));
  }

  @Test
  void withGenresReplacesTheGenreNames() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of("a", List.of("drama"), 1990, 2000, BigDecimal.ONE)
            .withGenres(List.of("Drama"));

    assertThat(criteria.genres()).containsExactly("Drama");
    assertThat(criteria.titleTerm()).contains("a");
    assertThat(criteria.releaseYearFrom()).contains(1990);
  }
}
