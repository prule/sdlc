package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.shared.domain.InvalidCriteriaException;
import com.acme.shared.domain.InvalidCriteriaException.Violation;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MovieSearchCriteriaTest {

  @Test
  void noCriteriaBrowses() {
    MovieSearchCriteria criteria = MovieSearchCriteria.of(null, null, null, null, null);

    assertThat(criteria.titleTerm()).isEmpty();
    assertThat(criteria.genres()).isEmpty();
    assertThat(criteria.releaseYearFrom()).isEmpty();
    assertThat(criteria.releaseYearTo()).isEmpty();
    assertThat(criteria.minRating()).isEmpty();
  }

  @Test
  void aBlankTitleTermIsNoCriterion() {
    assertThat(MovieSearchCriteria.of("", null, null, null, null).titleTerm()).isEmpty();
    assertThat(MovieSearchCriteria.of("  \t", null, null, null, null).titleTerm()).isEmpty();
    assertThat(MovieSearchCriteria.of(" heist", null, null, null, null).titleTerm())
        .contains(" heist");
  }

  @Test
  void genresAreLowerCasedAndDeduplicated() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(null, List.of("Drama", "DRAMA", "sci-fi"), null, null, null);

    assertThat(criteria.genres()).containsExactlyInAnyOrder("drama", "sci-fi");
  }

  @Test
  void aBlankGenreIsRefusedAsGenre() {
    assertThatThrownBy(() -> MovieSearchCriteria.of(null, List.of("Drama", " "), null, null, null))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex ->
                assertThat(ex.violations()).extracting(Violation::field).containsExactly("genre"));
  }

  @Test
  void aSingleYearAndOpenEndedRangesAreAllowed() {
    assertThat(MovieSearchCriteria.of(null, null, 1999, 1999, null).releaseYearFrom())
        .isEqualTo(OptionalInt.of(1999));
    assertThat(MovieSearchCriteria.of(null, null, 1999, null, null).releaseYearTo()).isEmpty();
    assertThat(MovieSearchCriteria.of(null, null, null, 1990, null).releaseYearTo())
        .isEqualTo(OptionalInt.of(1990));
  }

  @Test
  void aReversedRangeNamesBothBounds() {
    assertThatThrownBy(() -> MovieSearchCriteria.of(null, null, 2000, 1990, null))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex ->
                assertThat(ex.violations())
                    .extracting(Violation::field)
                    .containsExactly("releaseYearFrom", "releaseYearTo"));
  }

  @Test
  void minRatingAcceptsTheWholeScaleInclusive() {
    assertThat(MovieSearchCriteria.of(null, null, null, null, BigDecimal.ZERO).minRating())
        .contains(new Rating(BigDecimal.ZERO));
    assertThat(MovieSearchCriteria.of(null, null, null, null, new BigDecimal("4.25")).minRating())
        .contains(new Rating(new BigDecimal("4.25")));
    assertThat(MovieSearchCriteria.of(null, null, null, null, new BigDecimal("5.0")).minRating())
        .isPresent();
  }

  @Test
  void minRatingOutsideTheScaleIsRefusedAsMinRating() {
    for (String value : List.of("-1", "5.5", "-0.1")) {
      assertThatThrownBy(
              () -> MovieSearchCriteria.of(null, null, null, null, new BigDecimal(value)))
          .isInstanceOfSatisfying(
              InvalidCriteriaException.class,
              ex ->
                  assertThat(ex.violations())
                      .extracting(Violation::field)
                      .containsExactly("minRating"));
    }
  }

  @Test
  void collectsEveryViolationAndNeverEchoesAValue() {
    assertThatThrownBy(
            () -> MovieSearchCriteria.of(null, List.of(""), 2000, 1990, new BigDecimal("7.77")))
        .isInstanceOfSatisfying(
            InvalidCriteriaException.class,
            ex -> {
              assertThat(ex.violations())
                  .extracting(Violation::field)
                  .containsExactly("genre", "releaseYearFrom", "releaseYearTo", "minRating");
              assertThat(ex.violations())
                  .extracting(Violation::message)
                  .noneMatch(message -> message.contains("7.77") || message.contains("2000"));
            });
  }
}
