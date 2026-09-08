package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.common.test.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence integration tests against real Postgres (Testcontainers, never H2). Own fixtures per
 * test. The shared Postgres container is a suite-wide singleton (see {@link
 * PostgresIntegrationTest}), so every fixture title carries a per-test unique tag and every search
 * includes that tag as its title term — this keeps each test's result set isolated from rows
 * committed by any other test class or method, without relying on table truncation or transaction
 * rollback.
 */
class MovieSearchPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;

  @Autowired private MovieSearchPersistenceAdapter adapter;

  private static String uniqueTag() {
    return "TAG" + UUID.randomUUID().toString().replace("-", "");
  }

  private static MovieJpaEntity movie(
      String tag, String titleSuffix, int year, Integer runtime, Double rating, Genre... genres) {
    return new MovieJpaEntity(
        UUID.randomUUID(),
        tag + " " + titleSuffix,
        year,
        runtime,
        null,
        rating == null ? null : BigDecimal.valueOf(rating),
        Set.of(genres));
  }

  private MoviePage searchByTag(
      String tag, Set<Genre> genres, Integer yearFrom, Integer yearTo, BigDecimal minRating) {
    return searchByTag(tag, genres, yearFrom, yearTo, minRating, 0, 20, MovieSort.defaultSort());
  }

  private MoviePage searchByTag(
      String tag,
      Set<Genre> genres,
      Integer yearFrom,
      Integer yearTo,
      BigDecimal minRating,
      int page,
      int size,
      MovieSort sort) {
    MovieSearchCriteria criteria = MovieSearchCriteria.of(tag, genres, yearFrom, yearTo, minRating);
    return adapter.search(criteria, new MoviePageRequest(page, size), sort);
  }

  @Test
  void search_titleTerm_isCaseInsensitiveSubstringMatch() {
    String tag = uniqueTag();
    MovieJpaEntity match = movie(tag, "The Wandering Reel", 2019, null, null, Genre.DRAMA);
    MovieJpaEntity noMatch = movie(uniqueTag(), "Silent Harbor", 2021, null, null, Genre.MYSTERY);
    movieJpaRepository.saveAll(List.of(match, noMatch));

    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(tag.toLowerCase(java.util.Locale.ROOT), null, null, null, null);
    MoviePage page = adapter.search(criteria, new MoviePageRequest(0, 20), MovieSort.defaultSort());

    assertThat(page.content()).extracting(Movie::id).containsExactly(match.getId());
  }

  @Test
  void search_titleTermWithPercentWildcardCharacter_matchesItLiterally() {
    String tag = uniqueTag();
    MovieJpaEntity match = movie(tag, "100% Reel", 2019, null, null, Genre.DRAMA);
    MovieJpaEntity noMatch = movie(tag, "100X Reel", 2019, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(match, noMatch));

    MovieSearchCriteria criteria = MovieSearchCriteria.of(tag + " 100%", null, null, null, null);
    MoviePage page = adapter.search(criteria, new MoviePageRequest(0, 20), MovieSort.defaultSort());

    assertThat(page.content()).extracting(Movie::id).containsExactly(match.getId());
  }

  @Test
  void search_titleTermWithUnderscoreWildcardCharacter_matchesItLiterally() {
    String tag = uniqueTag();
    MovieJpaEntity match = movie(tag, "Foo_Bar", 2019, null, null, Genre.DRAMA);
    MovieJpaEntity noMatch = movie(tag, "FooXBar", 2019, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(match, noMatch));

    MovieSearchCriteria criteria = MovieSearchCriteria.of(tag + " Foo_Bar", null, null, null, null);
    MoviePage page = adapter.search(criteria, new MoviePageRequest(0, 20), MovieSort.defaultSort());

    assertThat(page.content()).extracting(Movie::id).containsExactly(match.getId());
  }

  @Test
  void search_multipleGenres_requiresAllToBePresent() {
    String tag = uniqueTag();
    MovieJpaEntity both = movie(tag, "Both", 2019, null, null, Genre.DRAMA, Genre.CRIME);
    MovieJpaEntity onlyOne = movie(tag, "Only Drama", 2019, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(both, onlyOne));

    MoviePage page = searchByTag(tag, Set.of(Genre.DRAMA, Genre.CRIME), null, null, null);

    assertThat(page.content()).extracting(Movie::id).containsExactly(both.getId());
  }

  @Test
  void search_movieWithSupersetOfGenres_stillMatches() {
    String tag = uniqueTag();
    MovieJpaEntity superset =
        movie(tag, "Superset", 2019, null, null, Genre.DRAMA, Genre.CRIME, Genre.ACTION);
    movieJpaRepository.save(superset);

    MoviePage page = searchByTag(tag, Set.of(Genre.DRAMA, Genre.CRIME), null, null, null);

    assertThat(page.content()).extracting(Movie::id).containsExactly(superset.getId());
  }

  @Test
  void search_releaseYearRange_eachBoundWorksAloneAndTogether() {
    String tag = uniqueTag();
    MovieJpaEntity y2000 = movie(tag, "Y2000", 2000, null, null, Genre.DRAMA);
    MovieJpaEntity y2010 = movie(tag, "Y2010", 2010, null, null, Genre.DRAMA);
    MovieJpaEntity y2020 = movie(tag, "Y2020", 2020, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(y2000, y2010, y2020));

    MoviePage fromOnly = searchByTag(tag, null, 2010, null, null);
    assertThat(fromOnly.content())
        .extracting(Movie::id)
        .containsExactlyInAnyOrder(y2010.getId(), y2020.getId());

    MoviePage toOnly = searchByTag(tag, null, null, 2010, null);
    assertThat(toOnly.content())
        .extracting(Movie::id)
        .containsExactlyInAnyOrder(y2000.getId(), y2010.getId());

    MoviePage both = searchByTag(tag, null, 2005, 2015, null);
    assertThat(both.content()).extracting(Movie::id).containsExactly(y2010.getId());
  }

  @Test
  void search_invertedReleaseYearRange_isValidAndMatchesNothing() {
    String tag = uniqueTag();
    MovieJpaEntity y2010 = movie(tag, "Y2010", 2010, null, null, Genre.DRAMA);
    movieJpaRepository.save(y2010);

    MoviePage page = searchByTag(tag, null, 2020, 2000, null);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  @Test
  void search_minRating_isInclusiveAndExcludesUnratedMovies() {
    String tag = uniqueTag();
    MovieJpaEntity rated4 = movie(tag, "Rated4", 2019, null, 4.0, Genre.DRAMA);
    MovieJpaEntity rated3 = movie(tag, "Rated3", 2019, null, 3.0, Genre.DRAMA);
    MovieJpaEntity unrated = movie(tag, "Unrated", 2019, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(rated4, rated3, unrated));

    MoviePage page = searchByTag(tag, null, null, null, BigDecimal.valueOf(4));

    assertThat(page.content()).extracting(Movie::id).containsExactly(rated4.getId());
  }

  @Test
  void search_combinedCriteria_returnsOnlyMoviesSatisfyingAllOfThem() {
    String tag = uniqueTag();
    MovieJpaEntity matching = movie(tag, "Match", 2015, null, 4.5, Genre.DRAMA, Genre.CRIME);
    MovieJpaEntity wrongGenre = movie(tag, "WrongGenre", 2015, null, 4.5, Genre.COMEDY);
    MovieJpaEntity wrongYear = movie(tag, "WrongYear", 1990, null, 4.5, Genre.DRAMA, Genre.CRIME);
    MovieJpaEntity wrongRating =
        movie(tag, "WrongRating", 2015, null, 2.0, Genre.DRAMA, Genre.CRIME);
    movieJpaRepository.saveAll(List.of(matching, wrongGenre, wrongYear, wrongRating));

    MoviePage page =
        searchByTag(tag, Set.of(Genre.DRAMA, Genre.CRIME), 2010, 2020, BigDecimal.valueOf(4));

    assertThat(page.content()).extracting(Movie::id).containsExactly(matching.getId());
  }

  @Test
  void search_defaultSort_isReleaseYearDescendingThenTitleAscending() {
    String tag = uniqueTag();
    MovieJpaEntity a2020 = movie(tag, "Alpha", 2020, null, null, Genre.DRAMA);
    MovieJpaEntity b2020 = movie(tag, "Bravo", 2020, null, null, Genre.DRAMA);
    MovieJpaEntity a2019 = movie(tag, "Alpha", 2019, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(a2020, b2020, a2019));

    MoviePage page = searchByTag(tag, null, null, null, null, 0, 20, MovieSort.defaultSort());

    assertThat(page.content())
        .extracting(Movie::id)
        .containsExactly(a2020.getId(), b2020.getId(), a2019.getId());
  }

  @Test
  void search_sortByTitleAscending_ordersAlphabetically() {
    String tag = uniqueTag();
    MovieJpaEntity charlie = movie(tag, "Charlie", 2019, null, null, Genre.DRAMA);
    MovieJpaEntity alpha = movie(tag, "Alpha", 2019, null, null, Genre.DRAMA);
    MovieJpaEntity bravo = movie(tag, "Bravo", 2019, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(charlie, alpha, bravo));

    MoviePage page = searchByTag(tag, null, null, null, null, 0, 20, MovieSort.parse("title,asc"));

    assertThat(page.content())
        .extracting(Movie::id)
        .containsExactly(alpha.getId(), bravo.getId(), charlie.getId());
  }

  @Test
  void search_sortByTitleDescending_ordersReverseAlphabetically() {
    String tag = uniqueTag();
    MovieJpaEntity charlie = movie(tag, "Charlie", 2019, null, null, Genre.DRAMA);
    MovieJpaEntity alpha = movie(tag, "Alpha", 2019, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(charlie, alpha));

    MoviePage page = searchByTag(tag, null, null, null, null, 0, 20, MovieSort.parse("title,desc"));

    assertThat(page.content())
        .extracting(Movie::id)
        .containsExactly(charlie.getId(), alpha.getId());
  }

  @Test
  void search_sortByReleaseYearAscending_ordersOldestFirst() {
    String tag = uniqueTag();
    MovieJpaEntity y2020 = movie(tag, "Y2020", 2020, null, null, Genre.DRAMA);
    MovieJpaEntity y2000 = movie(tag, "Y2000", 2000, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(y2020, y2000));

    MoviePage page =
        searchByTag(tag, null, null, null, null, 0, 20, MovieSort.parse("releaseYear,asc"));

    assertThat(page.content()).extracting(Movie::id).containsExactly(y2000.getId(), y2020.getId());
  }

  @Test
  void search_sortByReleaseYearDescending_ordersNewestFirst() {
    String tag = uniqueTag();
    MovieJpaEntity y2020 = movie(tag, "Y2020", 2020, null, null, Genre.DRAMA);
    MovieJpaEntity y2000 = movie(tag, "Y2000", 2000, null, null, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(y2020, y2000));

    MoviePage page =
        searchByTag(tag, null, null, null, null, 0, 20, MovieSort.parse("releaseYear,desc"));

    assertThat(page.content()).extracting(Movie::id).containsExactly(y2020.getId(), y2000.getId());
  }

  @Test
  void search_sortByRatingAscendingAndDescending_ordersByRating() {
    String tag = uniqueTag();
    MovieJpaEntity low = movie(tag, "Low", 2019, null, 1.0, Genre.DRAMA);
    MovieJpaEntity high = movie(tag, "High", 2019, null, 5.0, Genre.DRAMA);
    movieJpaRepository.saveAll(List.of(low, high));

    MoviePage asc = searchByTag(tag, null, null, null, null, 0, 20, MovieSort.parse("rating,asc"));
    assertThat(asc.content()).extracting(Movie::id).containsExactly(low.getId(), high.getId());

    MoviePage desc =
        searchByTag(tag, null, null, null, null, 0, 20, MovieSort.parse("rating,desc"));
    assertThat(desc.content()).extracting(Movie::id).containsExactly(high.getId(), low.getId());
  }

  @Test
  void search_pagingAcrossTies_everyMovieAppearsExactlyOnceWithNoGapsOrDuplicates() {
    String tag = uniqueTag();
    List<MovieJpaEntity> tied = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      tied.add(movie(tag, "Tied Movie", 2020, null, null, Genre.DRAMA));
    }
    movieJpaRepository.saveAll(tied);
    Set<UUID> expectedIds = tied.stream().map(MovieJpaEntity::getId).collect(Collectors.toSet());

    int pageSize = 3;
    List<UUID> seen = new ArrayList<>();
    for (int pageIndex = 0; pageIndex * pageSize < tied.size(); pageIndex++) {
      MoviePage page =
          searchByTag(tag, null, null, null, null, pageIndex, pageSize, MovieSort.defaultSort());
      page.content().forEach(m -> seen.add(m.id()));
    }

    assertThat(seen).hasSize(tied.size());
    assertThat(new HashSet<>(seen)).isEqualTo(expectedIds);
  }

  @Test
  void search_genreSuperset_isReturnedNotRejectedByTheHavingCount() {
    // Trap guard: a bare HAVING count(distinct genre) = :n over the unrestricted join would count
    // the movie's TOTAL genres and wrongly reject this superset match.
    String tag = uniqueTag();
    MovieJpaEntity superset =
        movie(tag, "Superset Guard", 2019, null, null, Genre.DRAMA, Genre.CRIME, Genre.ACTION);
    movieJpaRepository.save(superset);

    MoviePage page = searchByTag(tag, Set.of(Genre.DRAMA, Genre.CRIME), null, null, null);

    assertThat(page.content()).extracting(Movie::id).containsExactly(superset.getId());
    assertThat(page.totalElements()).isEqualTo(1);
  }

  @Test
  void search_totalElementsAgreesWithRows_underCombinedGenreAndWildcardTitleFilter() {
    // Trap guard: a count query that drops the genre HAVING or the LIKE ESCAPE, or that
    // count(*)-over-counts the genre join, would disagree with the actual matching rows.
    String tag = uniqueTag();
    MovieJpaEntity matching =
        movie(tag, "100% Match", 2019, null, null, Genre.DRAMA, Genre.CRIME, Genre.ACTION);
    MovieJpaEntity wrongGenre = movie(tag, "100% Off", 2019, null, null, Genre.COMEDY);
    MovieJpaEntity wrongTitle =
        movie(tag, "No Percent Here", 2019, null, null, Genre.DRAMA, Genre.CRIME);
    movieJpaRepository.saveAll(List.of(matching, wrongGenre, wrongTitle));

    MovieSearchCriteria criteria =
        MovieSearchCriteria.of(tag + " 100%", Set.of(Genre.DRAMA, Genre.CRIME), null, null, null);
    MoviePage page = adapter.search(criteria, new MoviePageRequest(0, 20), MovieSort.defaultSort());

    assertThat(page.content()).extracting(Movie::id).containsExactly(matching.getId());
    assertThat(page.totalElements()).isEqualTo(1);
  }
}
