package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSortOrder.SortField;
import com.acme.shared.domain.PageSpec;
import com.acme.shared.domain.ResultPage;
import com.acme.testsupport.PostgresIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers coverage for {@link MovieSearchAdapter} against real PostgreSQL (design D5): every
 * filter, every order (asserted as the full id sequence), paging, and the vocabulary read. Ordering
 * fixtures use titles of letters only, distinct at their first letter, so no assertion hinges on
 * how the database collation treats spaces, punctuation or accents.
 */
class MovieSearchAdapterTest extends PostgresIntegrationTest {

  private static final PageSpec FIRST_PAGE = new PageSpec(0, 100);

  @Autowired private MovieSearchAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
  }

  private UUID genre(String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", id, name);
    return id;
  }

  private UUID movie(String title, int releaseYear, String rating, UUID... genres) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        title,
        releaseYear,
        null,
        null,
        rating == null ? null : new BigDecimal(rating));
    for (UUID genre : genres) {
      jdbcTemplate.update("INSERT INTO movie_genre (movie_id, genre_id) VALUES (?, ?)", id, genre);
    }
    return id;
  }

  private static MovieSearchCriteria none() {
    return MovieSearchCriteria.of(null, List.of(), null, null, null);
  }

  private List<UUID> ids(MovieSearchCriteria criteria) {
    return ids(adapter.search(criteria, MovieSortOrder.DEFAULT, FIRST_PAGE));
  }

  private static List<UUID> ids(ResultPage<Movie> page) {
    return page.items().stream().map(m -> m.id().value()).toList();
  }

  @Nested
  class Filters {

    @Test
    void titleMatchesAnySubstringIgnoringCase() {
      UUID arrival = movie("Arrival", 2016, null);
      UUID train = movie("The Arrival of a Train", 1896, null);
      movie("Contact", 1997, null);

      assertThat(ids(MovieSearchCriteria.of("ARRIV", List.of(), null, null, null)))
          .containsExactlyInAnyOrder(arrival, train);
    }

    @Test
    void percentInTheTermIsLiteral() {
      UUID wolf = movie("100% Wolf", 2020, null);
      movie("1000 Wolves", 2010, null);

      assertThat(ids(MovieSearchCriteria.of("0% W", List.of(), null, null, null)))
          .containsExactly(wolf);
    }

    @Test
    void underscoreInTheTermIsLiteral() {
      UUID underscored = movie("File_Name", 2020, null);
      movie("FileXName", 2010, null);

      assertThat(ids(MovieSearchCriteria.of("e_n", List.of(), null, null, null)))
          .containsExactly(underscored);
    }

    @Test
    void backslashInTheTermIsLiteral() {
      UUID slashed = movie("Back\\Slash", 2020, null);
      movie("BackSlash", 2010, null);

      assertThat(ids(MovieSearchCriteria.of("k\\s", List.of(), null, null, null)))
          .containsExactly(slashed);
    }

    @Test
    void quoteInTheTermIsLiteral() {
      UUID quoted = movie("Schindler's List", 1993, null);
      movie("Schindlers Ark", 1982, null);

      assertThat(ids(MovieSearchCriteria.of("r's l", List.of(), null, null, null)))
          .containsExactly(quoted);
    }

    @Test
    void severalGenresMeanAllOfThem() {
      UUID drama = genre("Drama");
      UUID sciFi = genre("Sci-Fi");
      UUID arrival = movie("Arrival", 2016, null, drama, sciFi);
      movie("Contact", 1997, null, sciFi);
      movie("Atonement", 2007, null, drama);

      assertThat(ids(MovieSearchCriteria.of(null, List.of("Drama", "Sci-Fi"), null, null, null)))
          .containsExactly(arrival);
    }

    @Test
    void movieWithoutGenresNeverMatchesAGenreFilter() {
      UUID drama = genre("Drama");
      UUID atonement = movie("Atonement", 2007, null, drama);
      movie("Untitled Reel", 1974, null);

      assertThat(ids(MovieSearchCriteria.of(null, List.of("Drama"), null, null, null)))
          .containsExactly(atonement);
    }

    @Test
    void releaseYearRangesAreInclusiveAndMayBeOpenEnded() {
      UUID y1997 = movie("Contact", 1997, null);
      UUID y1999 = movie("Matrix", 1999, null);
      UUID y2000 = movie("Gladiator", 2000, null);
      UUID y2016 = movie("Arrival", 2016, null);

      assertThat(ids(MovieSearchCriteria.of(null, List.of(), 1999, 2000, null)))
          .containsExactlyInAnyOrder(y1999, y2000);
      assertThat(ids(MovieSearchCriteria.of(null, List.of(), 2016, 2016, null)))
          .containsExactly(y2016);
      assertThat(ids(MovieSearchCriteria.of(null, List.of(), null, 1999, null)))
          .containsExactlyInAnyOrder(y1997, y1999);
      assertThat(ids(MovieSearchCriteria.of(null, List.of(), 2000, null, null)))
          .containsExactlyInAnyOrder(y2000, y2016);
    }

    @Test
    void minRatingIsInclusiveAndExcludesUnratedMovies() {
      UUID four = movie("Four", 2001, "4.0");
      UUID fourHalf = movie("FourHalf", 2002, "4.5");
      UUID threeHalf = movie("ThreeHalf", 2003, "3.5");
      movie("Unrated", 2004, null);

      assertThat(ids(MovieSearchCriteria.of(null, List.of(), null, null, new BigDecimal("4"))))
          .containsExactlyInAnyOrder(four, fourHalf);
      assertThat(ids(MovieSearchCriteria.of(null, List.of(), null, null, BigDecimal.ZERO)))
          .containsExactlyInAnyOrder(four, fourHalf, threeHalf);
    }

    @Test
    void criteriaCombineWithAnd() {
      UUID drama = genre("Drama");
      UUID sciFi = genre("Sci-Fi");
      UUID arrival = movie("Arrival", 2016, "4.5", drama, sciFi);
      movie("Arrival Point", 1999, "4.5", drama);
      movie("Contact", 1997, "4.0", drama, sciFi);

      assertThat(
              ids(
                  MovieSearchCriteria.of(
                      "arrival", List.of("Sci-Fi"), 2000, null, new BigDecimal("4"))))
          .containsExactly(arrival);
    }

    @Test
    void emptyCatalogIsAnEmptyPageWithZeroTotals() {
      ResultPage<Movie> page = adapter.search(none(), MovieSortOrder.DEFAULT, new PageSpec(0, 20));

      assertThat(page.items()).isEmpty();
      assertThat(page.totalElements()).isZero();
      assertThat(page.totalPages()).isZero();
    }

    @Test
    void hydratedMoviesCarryTheirGenresAndOptionalDetails() {
      UUID drama = genre("Drama");
      UUID sciFi = genre("Sci-Fi");
      movie("Arrival", 2016, "4.5", sciFi, drama);

      Movie movie = adapter.search(none(), MovieSortOrder.DEFAULT, FIRST_PAGE).items().getFirst();

      assertThat(movie.genres()).containsExactly("Drama", "Sci-Fi");
      assertThat(movie.rating()).isPresent();
    }

    @Test
    void loadGenreNamesReturnsTheWholeVocabulary() {
      genre("Drama");
      genre("Sci-Fi");
      genre("Comedy");

      assertThat(adapter.loadGenreNames()).containsExactlyInAnyOrder("Drama", "Sci-Fi", "Comedy");
    }
  }

  @Nested
  class OrderingAndPaging {

    private UUID alpha;
    private UUID bravo;
    private UUID charlie;
    private UUID delta;
    private UUID echo;

    private void orderingFixture() {
      alpha = movie("Alpha", 2000, "3.0");
      bravo = movie("Bravo", 2010, "4.5");
      charlie = movie("Charlie", 2000, null);
      delta = movie("delta", 2010, "3.0");
      echo = movie("Echo", 1990, "4.5");
    }

    private List<UUID> sorted(SortField field, boolean descending) {
      return ids(adapter.search(none(), new MovieSortOrder(field, descending), FIRST_PAGE));
    }

    @Test
    void titleOrdersIgnoreLetterCase() {
      orderingFixture();

      assertThat(sorted(SortField.TITLE, false))
          .containsExactly(alpha, bravo, charlie, delta, echo);
      assertThat(sorted(SortField.TITLE, true)).containsExactly(echo, delta, charlie, bravo, alpha);
    }

    @Test
    void releaseYearOrdersBreakTiesByTitle() {
      orderingFixture();

      assertThat(sorted(SortField.RELEASE_YEAR, false))
          .containsExactly(echo, alpha, charlie, bravo, delta);
      assertThat(sorted(SortField.RELEASE_YEAR, true))
          .containsExactly(bravo, delta, alpha, charlie, echo);
    }

    @Test
    void ratingOrdersPutUnratedMoviesLastInBothDirections() {
      orderingFixture();

      assertThat(sorted(SortField.RATING, false))
          .containsExactly(alpha, delta, bravo, echo, charlie);
      assertThat(sorted(SortField.RATING, true))
          .containsExactly(bravo, echo, alpha, delta, charlie);
    }

    @Test
    void defaultOrderIsNewestFirstThenTitle() {
      UUID zodiac = movie("Zodiac", 2007, null);
      UUID arrival = movie("Arrival", 2016, null);
      UUID atonement = movie("Atonement", 2007, null);

      assertThat(ids(none())).containsExactly(arrival, atonement, zodiac);
    }

    private static Stream<Arguments> everyOrder() {
      return Stream.of(SortField.values())
          .flatMap(f -> Stream.of(Arguments.of(f, false), Arguments.of(f, true)));
    }

    @ParameterizedTest
    @MethodSource("everyOrder")
    void walkingSmallPagesOverTiesListsEveryMovieOnceInTheSameOrder(
        SortField field, boolean descending) {
      for (int i = 0; i < 4; i++) {
        movie("Same", 2000, "4.0");
      }
      movie("Same", 2000, null);
      movie("Same", 2001, null);
      movie("Other", 2000, "4.0");
      movie("Other", 1999, "2.5");
      movie("Kilo", 2000, "4.0");
      MovieSortOrder order = new MovieSortOrder(field, descending);

      List<UUID> single = ids(adapter.search(none(), order, FIRST_PAGE));
      List<UUID> walked = new ArrayList<>();
      ResultPage<Movie> page;
      int index = 0;
      do {
        page = adapter.search(none(), order, new PageSpec(index++, 2));
        walked.addAll(ids(page));
      } while (page.hasNext());

      assertThat(single).hasSize(9).doesNotHaveDuplicates();
      assertThat(walked).isEqualTo(single);
      assertThat(index).isEqualTo(5);
    }

    @Test
    void lastPartialPageHoldsTheRemainder() {
      for (int i = 0; i < 5; i++) {
        movie("Movie" + (char) ('A' + i), 2000 + i, null);
      }

      ResultPage<Movie> page = adapter.search(none(), MovieSortOrder.DEFAULT, new PageSpec(2, 2));

      assertThat(page.items()).hasSize(1);
      assertThat(page.page()).isEqualTo(2);
      assertThat(page.size()).isEqualTo(2);
      assertThat(page.totalElements()).isEqualTo(5);
      assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void pageAfterTheLastIsEmptyKeepsTheTotalAndRunsOnlyTheCount() {
      for (int i = 0; i < 5; i++) {
        movie("Movie" + (char) ('A' + i), 2000 + i, null);
      }
      Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
      boolean wasEnabled = statistics.isStatisticsEnabled();
      statistics.setStatisticsEnabled(true);
      statistics.clear();
      try {
        ResultPage<Movie> page = adapter.search(none(), MovieSortOrder.DEFAULT, new PageSpec(9, 2));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
      } finally {
        statistics.setStatisticsEnabled(wasEnabled);
      }
    }

    @Test
    void aHugePageNumberIsJustAnEmptyPage() {
      movie("Alone", 2000, null);

      ResultPage<Movie> page =
          adapter.search(none(), MovieSortOrder.DEFAULT, new PageSpec(Integer.MAX_VALUE, 100));

      assertThat(page.items()).isEmpty();
      assertThat(page.totalElements()).isEqualTo(1);
    }
  }
}
