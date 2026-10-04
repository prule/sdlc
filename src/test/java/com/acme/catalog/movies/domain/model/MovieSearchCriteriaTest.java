package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MovieSearchCriteria} (design D3, task 3.3): title normalization and the
 * release-year range invariant.
 */
class MovieSearchCriteriaTest {

  @Test
  void blankTitleBecomesEmpty() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.of("   "), Set.of(), Optional.empty(), Optional.empty(), Optional.empty());

    assertThat(criteria.titleTerm()).isEmpty();
  }

  @Test
  void whitespaceOnlyTitleBecomesEmpty() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.of("\t\n "), Set.of(), Optional.empty(), Optional.empty(), Optional.empty());

    assertThat(criteria.titleTerm()).isEmpty();
  }

  @Test
  void nonBlankTitleIsKeptUntrimmed() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.of("  heist  "),
            Set.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    assertThat(criteria.titleTerm()).contains("  heist  ");
  }

  @Test
  void fromGreaterThanToThrows() {
    assertThatThrownBy(
            () ->
                MovieSearchCriteria.of(
                    Optional.empty(),
                    Set.of(),
                    Optional.of(2010),
                    Optional.of(2000),
                    Optional.empty()))
        .isInstanceOf(InvalidSearchCriterionException.class)
        .extracting(ex -> ((InvalidSearchCriterionException) ex).criterion())
        .isEqualTo(SearchCriterion.RELEASE_YEAR_RANGE);
  }

  @Test
  void fromEqualToToIsAllowed() {
    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(
            Optional.empty(), Set.of(), Optional.of(2005), Optional.of(2005), Optional.empty());

    assertThat(criteria.releaseYearFrom()).contains(2005);
    assertThat(criteria.releaseYearTo()).contains(2005);
  }

  @Test
  void eitherBoundMayBeAbsent() {
    MovieSearchCriteria fromOnly =
        MovieSearchCriteria.of(
            Optional.empty(), Set.of(), Optional.of(2005), Optional.empty(), Optional.empty());
    MovieSearchCriteria toOnly =
        MovieSearchCriteria.of(
            Optional.empty(), Set.of(), Optional.empty(), Optional.of(2005), Optional.empty());

    assertThat(fromOnly.releaseYearTo()).isEmpty();
    assertThat(toOnly.releaseYearFrom()).isEmpty();
  }
}
