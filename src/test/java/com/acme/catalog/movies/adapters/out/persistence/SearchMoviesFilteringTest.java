package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import com.acme.testsupport.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers coverage for {@link SearchMoviesPort}'s filtering (design D5, task 5.2): title
 * substring, genre matching, year bounds, minimum rating, combined criteria and totals.
 */
class SearchMoviesFilteringTest extends PostgresIntegrationTest {

  @Autowired private MoviePersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final PageRequest defaultPage = new PageRequest(0, 20);

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  private UUID insertGenre(String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", id, name);
    return id;
  }

  private UUID insertMovie(String title, int releaseYear, BigDecimal rating) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, rating) VALUES (?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        rating);
    return id;
  }

  private void linkGenre(UUID movieId, UUID genreId) {
    jdbcTemplate.update(
        "INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", movieId, genreId);
  }

  private MovieSearchCriteria criteria(
      Optional<String> title,
      Set<String> genres,
      Optional<Integer> from,
      Optional<Integer> to,
      Optional<Rating> minRating) {
    return MovieSearchCriteria.of(title, genres, from, to, minRating);
  }

  private Page<MovieSummary> search(MovieSearchCriteria criteria) {
    return adapter.search(criteria, MovieSortOrder.DEFAULT, defaultPage);
  }

  @Test
  void titleSubstringMatchesIgnoringCase() {
    insertMovie("The Grand Heist", 2005, null);
    insertMovie("Heist Night", 2010, null);
    insertMovie("Arrival", 2016, null);

    Page<MovieSummary> result =
        search(
            criteria(
                Optional.of("HEIST"),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

    assertThat(result.items())
        .extracting(MovieSummary::title)
        .containsExactlyInAnyOrder("The Grand Heist", "Heist Night");
    assertThat(result.totalElements()).isEqualTo(2);
  }

  @Test
  void titleSubstringMatchesWildcardCharactersLiterally() {
    insertMovie("100%_Real", 2001, null);
    insertMovie("Arrival", 2016, null);

    Page<MovieSummary> percent =
        search(
            criteria(
                Optional.of("%"), Set.of(), Optional.empty(), Optional.empty(), Optional.empty()));
    Page<MovieSummary> underscore =
        search(
            criteria(
                Optional.of("_"), Set.of(), Optional.empty(), Optional.empty(), Optional.empty()));

    assertThat(percent.items()).extracting(MovieSummary::title).containsExactly("100%_Real");
    assertThat(underscore.items()).extracting(MovieSummary::title).containsExactly("100%_Real");
  }

  @Test
  void titleSubstringMatchesABackslashLiterally() {
    insertMovie("A\\B", 2001, null);
    insertMovie("Arrival", 2016, null);

    Page<MovieSummary> result =
        search(
            criteria(
                Optional.of("\\"), Set.of(), Optional.empty(), Optional.empty(), Optional.empty()));

    assertThat(result.items()).extracting(MovieSummary::title).containsExactly("A\\B");
  }

  @Test
  void allGivenGenresMustBeCarried() {
    UUID drama = insertGenre("Drama");
    UUID sciFi = insertGenre("Sci-Fi");
    UUID justDrama = insertMovie("Just Drama", 2000, null);
    UUID dramaAndSciFi = insertMovie("Drama And Sci-Fi", 2001, null);
    UUID justSciFi = insertMovie("Just Sci-Fi", 2002, null);
    linkGenre(justDrama, drama);
    linkGenre(dramaAndSciFi, drama);
    linkGenre(dramaAndSciFi, sciFi);
    linkGenre(justSciFi, sciFi);

    Page<MovieSummary> result =
        search(
            criteria(
                Optional.empty(),
                Set.of("Drama", "Sci-Fi"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

    assertThat(result.items()).extracting(MovieSummary::title).containsExactly("Drama And Sci-Fi");
  }

  @Test
  void repeatingTheSameGenreHasTheSameEffectAsGivingItOnce() {
    UUID drama = insertGenre("Drama");
    UUID movie = insertMovie("A Drama", 2000, null);
    linkGenre(movie, drama);

    Page<MovieSummary> result =
        search(
            criteria(
                Optional.empty(),
                Set.of("Drama", "drama"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

    assertThat(result.items()).extracting(MovieSummary::title).containsExactly("A Drama");
  }

  @Test
  void inclusiveYearBoundsAndASingleYear() {
    insertMovie("Nineteen Ninety Eight", 1998, null);
    insertMovie("Two Thousand Five", 2005, null);
    insertMovie("Two Thousand Sixteen", 2016, null);

    Page<MovieSummary> bothBounds =
        search(
            criteria(
                Optional.empty(),
                Set.of(),
                Optional.of(1998),
                Optional.of(2005),
                Optional.empty()));
    Page<MovieSummary> singleYear =
        search(
            criteria(
                Optional.empty(),
                Set.of(),
                Optional.of(2005),
                Optional.of(2005),
                Optional.empty()));

    assertThat(bothBounds.items())
        .extracting(MovieSummary::title)
        .containsExactlyInAnyOrder("Nineteen Ninety Eight", "Two Thousand Five");
    assertThat(singleYear.items())
        .extracting(MovieSummary::title)
        .containsExactly("Two Thousand Five");
  }

  @Test
  void minRatingIsInclusiveAndExcludesUnratedMoviesEvenAtZero() {
    insertMovie("Three Point Oh", 2000, new BigDecimal("3.0"));
    insertMovie("Four Point Five", 2001, new BigDecimal("4.5"));
    insertMovie("Unrated", 2002, null);

    Page<MovieSummary> exact =
        search(
            criteria(
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new Rating(new BigDecimal("4.5")))));
    Page<MovieSummary> atThree =
        search(
            criteria(
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new Rating(new BigDecimal("3")))));
    Page<MovieSummary> zero =
        search(
            criteria(
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new Rating(BigDecimal.ZERO))));

    assertThat(exact.items()).extracting(MovieSummary::title).containsExactly("Four Point Five");
    assertThat(atThree.items())
        .extracting(MovieSummary::title)
        .containsExactlyInAnyOrder("Three Point Oh", "Four Point Five");
    assertThat(zero.items())
        .extracting(MovieSummary::title)
        .containsExactlyInAnyOrder("Three Point Oh", "Four Point Five");
  }

  @Test
  void everyCriterionMustHoldWhenCombined() {
    UUID thriller = insertGenre("Thriller");
    UUID comedy = insertGenre("Comedy");

    // Fails title.
    UUID failsTitle = insertMovie("Day Out", 2005, new BigDecimal("4.0"));
    linkGenre(failsTitle, thriller);
    // Fails genre.
    UUID failsGenre = insertMovie("Night Terror", 2005, new BigDecimal("4.0"));
    linkGenre(failsGenre, comedy);
    // Fails year.
    UUID failsYear = insertMovie("Night Terror Two", 1999, new BigDecimal("4.0"));
    linkGenre(failsYear, thriller);
    // Fails rating.
    UUID failsRating = insertMovie("Night Terror Three", 2005, new BigDecimal("1.0"));
    linkGenre(failsRating, thriller);
    // Meets everything.
    UUID meetsAll = insertMovie("Night Terror Four", 2005, new BigDecimal("4.0"));
    linkGenre(meetsAll, thriller);

    Page<MovieSummary> result =
        search(
            criteria(
                Optional.of("night"),
                Set.of("Thriller"),
                Optional.of(2000),
                Optional.empty(),
                Optional.of(new Rating(new BigDecimal("3")))));

    assertThat(result.items()).extracting(MovieSummary::title).containsExactly("Night Terror Four");
    assertThat(result.totalElements()).isEqualTo(1);
  }
}
