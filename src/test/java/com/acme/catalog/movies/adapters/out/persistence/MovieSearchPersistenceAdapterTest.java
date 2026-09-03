package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.MovieSortField;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.movies.domain.model.SortDirection;
import com.acme.common.test.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres, no H2) test for {@link MovieSearchPersistenceAdapter}. Inserts its
 * own fixtures and runs inside a rolled-back transaction (see {@code MoviePersistenceAdapterTest}
 * precedent) so nothing leaks into the shared {@link PostgresIntegrationTest} container.
 */
@Transactional
class MovieSearchPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private MovieSearchPersistenceAdapter searchAdapter;

  private final Map<String, UUID> idsByTitle = new HashMap<>();

  @Test
  void search_titleFilter_matchesCaseInsensitiveSubstringOnly() {
    UUID matrixId = saveMovie("The Matrix", 1999, null, null, Set.of("Sci-Fi"));
    saveMovie("Unrelated Film", 2001, null, null, Set.of("Drama"));

    MoviePage page = search(criteriaWithTitle("matrix"));

    assertThat(ids(page)).containsExactly(matrixId);
  }

  @Test
  void search_genreAndFilter_matchesMovieWithExtraGenresButNotSubset() {
    UUID extraGenresId =
        saveMovie("Extra Genres Movie", 2000, null, null, Set.of("Drama", "Crime", "Thriller"));
    saveMovie("Subset Genre Movie", 2000, null, null, Set.of("Drama"));

    MoviePage page = search(criteriaWithGenres("Drama", "Crime"));

    assertThat(ids(page)).containsExactly(extraGenresId);
  }

  @Test
  void search_unknownGenre_isNotAnErrorAndReturnsEmpty() {
    saveMovie("Some Movie", 2000, null, null, Set.of("Drama"));

    MoviePage page = search(criteriaWithGenres("Nonexistent"));

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  @Test
  void search_yearRangeFilter_isInclusiveWithIndependentBounds() {
    UUID inRange = saveMovie("In Range", 1995, null, null, Set.of("Drama"));
    UUID tooEarly = saveMovie("Too Early", 1985, null, null, Set.of("Drama"));
    UUID tooLate = saveMovie("Too Late", 2005, null, null, Set.of("Drama"));

    MovieSearchCriteria bothBounds =
        new MovieSearchCriteria(
            Optional.empty(), List.of(), Optional.of(1990), Optional.of(1999), Optional.empty());
    assertThat(ids(search(bothBounds))).containsExactly(inRange);

    MovieSearchCriteria fromOnly =
        new MovieSearchCriteria(
            Optional.empty(), List.of(), Optional.of(1990), Optional.empty(), Optional.empty());
    assertThat(ids(search(fromOnly))).containsExactlyInAnyOrder(inRange, tooLate);

    MovieSearchCriteria toOnly =
        new MovieSearchCriteria(
            Optional.empty(), List.of(), Optional.empty(), Optional.of(1999), Optional.empty());
    assertThat(ids(search(toOnly))).containsExactlyInAnyOrder(inRange, tooEarly);
  }

  @Test
  void search_minRatingFilter_excludesUnratedAndBelowThreshold() {
    UUID highlyRated =
        saveMovie("High Rated", 2000, null, BigDecimal.valueOf(4.5), Set.of("Drama"));
    saveMovie("Low Rated", 2000, null, BigDecimal.valueOf(2.0), Set.of("Drama"));
    saveMovie("Unrated", 2000, null, null, Set.of("Drama"));

    MovieSearchCriteria criteria =
        new MovieSearchCriteria(
            Optional.empty(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(new Rating(BigDecimal.valueOf(4))));

    assertThat(ids(search(criteria))).containsExactly(highlyRated);
  }

  @Test
  void search_combinedFilters_intersectAndReportCorrectTotals() {
    UUID matches =
        saveMovie(
            "The Great Crime Drama", 1995, 120, BigDecimal.valueOf(4.5), Set.of("Drama", "Crime"));
    saveMovie("The Great Crime Drama Sequel", 1995, 120, BigDecimal.valueOf(4.5), Set.of("Drama"));
    saveMovie("Unrelated Title", 1995, 120, BigDecimal.valueOf(4.5), Set.of("Drama", "Crime"));
    saveMovie(
        "The Great Crime Drama Prequel",
        1980,
        120,
        BigDecimal.valueOf(4.5),
        Set.of("Drama", "Crime"));

    MovieSearchCriteria combined =
        new MovieSearchCriteria(
            Optional.of("great crime"),
            List.of(new Genre("Drama"), new Genre("Crime")),
            Optional.of(1990),
            Optional.of(1999),
            Optional.of(new Rating(BigDecimal.valueOf(4))));

    MoviePage page = search(combined);

    assertThat(ids(page)).containsExactly(matches);
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  @Test
  void search_ratingSort_placesUnratedMoviesLastInBothDirections() {
    UUID highest = saveMovie("Highest", 2000, null, BigDecimal.valueOf(4.8), Set.of("Drama"));
    UUID mid = saveMovie("Mid", 2000, null, BigDecimal.valueOf(3.0), Set.of("Drama"));
    UUID unrated = saveMovie("Unrated", 2000, null, null, Set.of("Drama"));

    MoviePage desc =
        search(MovieSearchCriteria.NONE, new MovieSort(MovieSortField.RATING, SortDirection.DESC));
    assertThat(ids(desc)).containsExactly(highest, mid, unrated);

    MoviePage asc =
        search(MovieSearchCriteria.NONE, new MovieSort(MovieSortField.RATING, SortDirection.ASC));
    assertThat(ids(asc)).containsExactly(mid, highest, unrated);
  }

  @Test
  void search_titleSort_ordersAscending() {
    UUID b = saveMovie("Beta", 2000, null, null, Set.of("Drama"));
    UUID a = saveMovie("Alpha", 2000, null, null, Set.of("Drama"));
    UUID c = saveMovie("Charlie", 2000, null, null, Set.of("Drama"));

    MoviePage page =
        search(MovieSearchCriteria.NONE, new MovieSort(MovieSortField.TITLE, SortDirection.ASC));

    assertThat(ids(page)).containsExactly(a, b, c);
  }

  @Test
  void search_defaultSort_isReleaseYearDescendingWithTitleTiebreak() {
    UUID newer = saveMovie("Zeta", 2010, null, null, Set.of("Drama"));
    UUID olderA = saveMovie("Alpha", 2000, null, null, Set.of("Drama"));
    UUID olderB = saveMovie("Beta", 2000, null, null, Set.of("Drama"));

    MoviePage page = search(MovieSearchCriteria.NONE, MovieSort.DEFAULT);

    assertThat(ids(page)).containsExactly(newer, olderA, olderB);
  }

  @Test
  void search_pagination_returnsTheRequestedPageAndCorrectTotals() {
    saveMovie("Alpha", 2000, null, null, Set.of("Drama"));
    saveMovie("Beta", 2000, null, null, Set.of("Drama"));
    saveMovie("Charlie", 2000, null, null, Set.of("Drama"));
    MovieSort titleAsc = new MovieSort(MovieSortField.TITLE, SortDirection.ASC);

    MoviePage firstPage = searchAdapter.search(MovieSearchCriteria.NONE, 0, 2, titleAsc);
    assertThat(firstPage.items()).hasSize(2);
    assertThat(firstPage.totalElements()).isEqualTo(3);
    assertThat(firstPage.totalPages()).isEqualTo(2);

    MoviePage secondPage = searchAdapter.search(MovieSearchCriteria.NONE, 1, 2, titleAsc);
    assertThat(secondPage.items()).hasSize(1);
  }

  @Test
  void search_pageBeyondLastPage_isEmptyNotAnError() {
    saveMovie("Alpha", 2000, null, null, Set.of("Drama"));

    MoviePage page = searchAdapter.search(MovieSearchCriteria.NONE, 9, 5, MovieSort.DEFAULT);

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(1);
  }

  /**
   * Regression test for a non-deterministic-pagination bug: two rows sharing the same (sort field,
   * title) have an undefined relative order under {@code LIMIT}/{@code OFFSET} unless the {@code
   * ORDER BY} ends in a unique terminal key ({@code m.id}). Without it, paging one row at a time
   * across two same-titled rows can skip one and repeat the other.
   */
  @Test
  void search_pagesOfMoviesSharingTheSameTitle_areDeterministicWithNoSkipOrDuplicate() {
    UUID first = saveMovie("Same Title", 2000, null, null, Set.of("Drama"));
    UUID second = saveMovie("Same Title", 2000, null, null, Set.of("Drama"));

    MoviePage page0 = searchAdapter.search(MovieSearchCriteria.NONE, 0, 1, MovieSort.DEFAULT);
    MoviePage page1 = searchAdapter.search(MovieSearchCriteria.NONE, 1, 1, MovieSort.DEFAULT);

    List<UUID> seenIds = new java.util.ArrayList<>();
    seenIds.addAll(ids(page0));
    seenIds.addAll(ids(page1));

    assertThat(seenIds).as("no skip, no duplicate across the two pages").hasSize(2);
    assertThat(seenIds).containsExactlyInAnyOrder(first, second);
    assertThat(page0.items()).hasSize(1);
    assertThat(page1.items()).hasSize(1);
  }

  @Test
  void search_duplicateGenreParameter_behavesLikeASingleGenre() {
    UUID movieId = saveMovie("Drama Movie", 2000, null, null, Set.of("Drama"));

    MoviePage page = search(criteriaWithGenres("Drama", "Drama"));

    assertThat(ids(page)).containsExactly(movieId);
  }

  /**
   * Regression test for a missing {@code ESCAPE} clause on the title {@code LIKE} filter: a search
   * term containing a literal {@code %} must be matched literally, not treated as a SQL wildcard.
   */
  @Test
  void search_titleContainingALiteralPercent_matchesOnlyTitlesContainingThatLiteralPercent() {
    UUID literalPercent = saveMovie("100% Guaranteed", 2000, null, null, Set.of("Drama"));
    saveMovie("100 Percent Guaranteed", 2000, null, null, Set.of("Drama"));

    MoviePage page = search(criteriaWithTitle("100%"));

    assertThat(ids(page)).containsExactly(literalPercent);
  }

  private UUID saveMovie(
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      BigDecimal rating,
      Set<String> genreNames) {
    UUID movieId = UUID.randomUUID();
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    for (String name : genreNames) {
      genres.add(findOrCreateGenre(name));
    }
    MovieJpaEntity entity =
        new MovieJpaEntity(movieId, title, releaseYear, runtimeMinutes, null, rating, genres);
    movieJpaRepository.save(entity);
    idsByTitle.put(title, movieId);
    return movieId;
  }

  private GenreJpaEntity findOrCreateGenre(String name) {
    return genreJpaRepository.findAll().stream()
        .filter(g -> g.getName().equals(name))
        .findFirst()
        .orElseGet(() -> genreJpaRepository.save(new GenreJpaEntity(UUID.randomUUID(), name)));
  }

  private static MovieSearchCriteria criteriaWithTitle(String title) {
    return new MovieSearchCriteria(
        Optional.of(title), List.of(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static MovieSearchCriteria criteriaWithGenres(String... genreNames) {
    List<Genre> genres = List.of(genreNames).stream().map(Genre::new).toList();
    return new MovieSearchCriteria(
        Optional.empty(), genres, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private MoviePage search(MovieSearchCriteria criteria) {
    return search(criteria, MovieSort.DEFAULT);
  }

  private MoviePage search(MovieSearchCriteria criteria, MovieSort sort) {
    return searchAdapter.search(criteria, 0, 20, sort);
  }

  private static List<UUID> ids(MoviePage page) {
    return page.items().stream().map(Movie::id).map(id -> id.value()).toList();
  }
}
