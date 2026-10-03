package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.MovieOrder;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.catalog.movies.domain.model.PageRequest;
import com.acme.catalog.movies.domain.model.ResultPage;
import com.acme.testsupport.MovieCatalogFixture;
import com.acme.testsupport.PostgresIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Testcontainers coverage for {@link MovieSearchAdapter} against real PostgreSQL (design D5). */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MovieSearchAdapterTest extends PostgresIntegrationTest {

  @Autowired private MovieSearchAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private MovieCatalogFixture catalog;

  @BeforeEach
  void setUp() {
    catalog = new MovieCatalogFixture(jdbcTemplate);
  }

  @AfterEach
  void cleanUp() {
    catalog.clear();
  }

  private ResultPage<MovieSummary> search(MovieSearchCriteria criteria) {
    return adapter.search(criteria, MovieOrder.DEFAULT, PageRequest.of(0, 100));
  }

  private List<String> titles(MovieSearchCriteria criteria) {
    return titles(search(criteria));
  }

  private List<String> titles(MovieSearchCriteria criteria, MovieOrder order) {
    return titles(adapter.search(criteria, order, PageRequest.of(0, 100)));
  }

  private static List<String> titles(ResultPage<MovieSummary> page) {
    return page.items().stream().map(MovieSummary::title).toList();
  }

  private static MovieSearchCriteria title(String term) {
    return MovieSearchCriteria.of(term, null, null, null, null);
  }

  private static MovieSearchCriteria none() {
    return MovieSearchCriteria.of(null, null, null, null, null);
  }

  // --- genre vocabulary ---

  @Test
  void reportsOnlyTheGenresMissingFromTheVocabularyIgnoringCase() {
    catalog.genre("Drama");
    catalog.genre("Sci-Fi");

    assertThat(adapter.unknownGenres(Set.of("drama", "sci-fi"))).isEmpty();
    assertThat(adapter.unknownGenres(Set.of("drama", "telenovela"))).containsExactly("telenovela");
  }

  // --- title ---

  @Test
  void titleMatchesAnyPartIgnoringCase() {
    catalog.movie("The Grand Heist", 2005, null);
    catalog.movie("Heist Night", 2001, null);
    catalog.movie("Arrival", 2016, null);

    assertThat(titles(title("heist"))).containsExactlyInAnyOrder("The Grand Heist", "Heist Night");
    assertThat(titles(title("HEIST"))).containsExactlyInAnyOrder("The Grand Heist", "Heist Night");
    assertThat(titles(title("rand h"))).containsExactly("The Grand Heist");
  }

  @Test
  void likeWildcardsAndTheEscapeCharacterMatchLiterally() {
    catalog.movie("100% Love", 2010, null);
    catalog.movie("Snake_Eyes", 1998, null);
    catalog.movie("Back\\Slash", 2000, null);
    catalog.movie("Arrival", 2016, null);

    assertThat(titles(title("%"))).containsExactly("100% Love");
    assertThat(titles(title("_"))).containsExactly("Snake_Eyes");
    assertThat(titles(title("\\"))).containsExactly("Back\\Slash");
  }

  // --- genres ---

  @Test
  void severalGenresMeanAllOfThem() {
    catalog.movie("A", 2000, null, "Drama");
    catalog.movie("B", 2000, null, "Drama", "Sci-Fi");
    catalog.movie("C", 2000, null, "Sci-Fi");

    assertThat(titles(MovieSearchCriteria.of(null, List.of("drama"), null, null, null)))
        .containsExactly("A", "B");
    assertThat(titles(MovieSearchCriteria.of(null, List.of("Drama", "sci-fi"), null, null, null)))
        .containsExactly("B");
    assertThat(titles(MovieSearchCriteria.of(null, List.of("DRAMA", "drama"), null, null, null)))
        .containsExactly("A", "B");
  }

  // --- release year ---

  @Test
  void releaseYearBoundsAreInclusive() {
    catalog.movie("y1989", 1989, null);
    catalog.movie("y1990", 1990, null);
    catalog.movie("y1999", 1999, null);
    catalog.movie("y2000", 2000, null);

    assertThat(titles(MovieSearchCriteria.of(null, null, 1990, 1999, null)))
        .containsExactly("y1999", "y1990");
    assertThat(titles(MovieSearchCriteria.of(null, null, 1999, null, null)))
        .containsExactly("y2000", "y1999");
    assertThat(titles(MovieSearchCriteria.of(null, null, null, 1990, null)))
        .containsExactly("y1990", "y1989");
    assertThat(titles(MovieSearchCriteria.of(null, null, 1990, 1990, null)))
        .containsExactly("y1990");
  }

  // --- minimum rating ---

  @Test
  void minimumRatingIsInclusiveAndLeavesOutUnratedMovies() {
    catalog.movie("r35", 2000, "3.5");
    catalog.movie("r40", 2000, "4.0");
    catalog.movie("r45", 2000, "4.5");
    catalog.movie("unrated", 2000, null);

    assertThat(titles(MovieSearchCriteria.of(null, null, null, null, new BigDecimal("4"))))
        .containsExactly("r40", "r45");
    assertThat(titles(MovieSearchCriteria.of(null, null, null, null, BigDecimal.ZERO)))
        .containsExactly("r35", "r40", "r45");
    assertThat(titles(MovieSearchCriteria.of(null, null, null, null, new BigDecimal("4.25"))))
        .containsExactly("r45");
  }

  @Test
  void allCriteriaCombine() {
    catalog.movie("Heist Night", 2001, "4.0", "Drama", "Thriller");
    catalog.movie("Heist Day", 2001, "3.0", "Drama", "Thriller");
    catalog.movie("Heist Morning", 2001, "4.0", "Drama");

    assertThat(
            titles(
                MovieSearchCriteria.of(
                    "heist", List.of("drama", "thriller"), 2000, 2005, new BigDecimal("3.5"))))
        .containsExactly("Heist Night");
  }

  // --- ordering ---

  @Test
  void defaultOrderIsNewestFirstThenTitleIgnoringCase() {
    catalog.movie("Zodiac", 2007, null);
    catalog.movie("Arrival", 2016, null);
    catalog.movie("atonement", 2007, null);

    assertThat(titles(none())).containsExactly("Arrival", "atonement", "Zodiac");
    assertThat(titles(none(), MovieOrder.RELEASE_YEAR_ASC))
        .containsExactly("atonement", "Zodiac", "Arrival");
  }

  @Test
  void titleOrderIgnoresCase() {
    catalog.movie("alien", 1979, null);
    catalog.movie("Arrival", 2016, null);
    catalog.movie("Brazil", 1985, null);

    assertThat(titles(none(), MovieOrder.TITLE_ASC)).containsExactly("alien", "Arrival", "Brazil");
    assertThat(titles(none(), MovieOrder.TITLE_DESC)).containsExactly("Brazil", "Arrival", "alien");
  }

  @Test
  void unratedMoviesComeLastInEitherRatingDirectionWithTiesByTitle() {
    catalog.movie("r3", 2000, "3.0");
    catalog.movie("r5b", 2000, "5.0");
    catalog.movie("r5a", 2000, "5.0");
    catalog.movie("unrated", 2000, null);

    assertThat(titles(none(), MovieOrder.RATING_ASC))
        .containsExactly("r3", "r5a", "r5b", "unrated");
    assertThat(titles(none(), MovieOrder.RATING_DESC))
        .containsExactly("r5a", "r5b", "r3", "unrated");
  }

  @Test
  void titlesDifferingOnlyInCaseAreStillTotallyOrdered() {
    catalog.movie("Heist", 2000, null);
    catalog.movie("heist", 2000, null);
    catalog.movie("HEIST", 2000, null);

    List<String> first = titles(none(), MovieOrder.TITLE_ASC);
    assertThat(first).hasSize(3);
    for (int i = 0; i < 5; i++) {
      assertThat(titles(none(), MovieOrder.TITLE_ASC)).isEqualTo(first);
    }
  }

  // --- paging ---

  @Test
  void pagesCarryTheRequestedSliceAndTheTotal() {
    for (int i = 0; i < 45; i++) {
      catalog.movie("Movie %02d".formatted(i), 2000, null);
    }

    ResultPage<MovieSummary> first =
        adapter.search(none(), MovieOrder.DEFAULT, PageRequest.of(0, 20));
    ResultPage<MovieSummary> last =
        adapter.search(none(), MovieOrder.DEFAULT, PageRequest.of(2, 20));
    ResultPage<MovieSummary> afterLast =
        adapter.search(none(), MovieOrder.DEFAULT, PageRequest.of(7, 20));

    assertThat(first.items()).hasSize(20);
    assertThat(first.totalElements()).isEqualTo(45);
    assertThat(first.totalPages()).isEqualTo(3);
    assertThat(last.items()).hasSize(5);
    assertThat(afterLast.items()).isEmpty();
    assertThat(afterLast.totalElements()).isEqualTo(45);
  }

  @Test
  void walkingThePagesListsEveryMatchExactlyOnceInOrder() {
    for (int i = 0; i < 5; i++) {
      catalog.movie("Same Title", 2000, null);
    }
    catalog.movie("Same title", 2000, "4.0");

    for (MovieOrder order : MovieOrder.values()) {
      List<Object> walked = new ArrayList<>();
      for (int page = 0; page < 3; page++) {
        adapter.search(none(), order, PageRequest.of(page, 2)).items().stream()
            .map(MovieSummary::id)
            .forEach(walked::add);
      }
      List<Object> whole =
          adapter.search(none(), order, PageRequest.of(0, 100)).items().stream()
              .map(MovieSummary::id)
              .map(Object.class::cast)
              .toList();

      assertThat(walked).as(order.name()).doesNotHaveDuplicates().isEqualTo(whole);
    }
  }

  @Test
  void anEmptyCatalogIsAnEmptyPage() {
    ResultPage<MovieSummary> page = search(none());

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  // --- summary mapping and query count ---

  @Test
  void mapsSummariesWithGenresInOrderAndAbsentDetails() {
    catalog.movieWithDetails("Arrival", 2016, 116, "A linguist…", "4.5", "Sci-Fi", "Drama");
    catalog.movie("Untitled Reel", 1974, null);

    List<MovieSummary> items = search(none()).items();

    assertThat(items.getFirst().genres()).containsExactly("Drama", "Sci-Fi");
    assertThat(items.getFirst().runtime()).isPresent();
    assertThat(items.getFirst().rating()).isPresent();
    assertThat(items.get(1).genres()).isEmpty();
    assertThat(items.get(1).runtime()).isEmpty();
    assertThat(items.get(1).rating()).isEmpty();
  }

  @Test
  void aPageOfTwentyRunsNoMoreThanThreeQueries() {
    for (int i = 0; i < 25; i++) {
      catalog.movie("Movie %02d".formatted(i), 2000, null, "Drama", "Sci-Fi");
    }
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();

    ResultPage<MovieSummary> page =
        adapter.search(
            MovieSearchCriteria.of(null, List.of("drama"), null, null, null),
            MovieOrder.DEFAULT,
            PageRequest.of(0, 20));

    assertThat(page.items()).hasSize(20).allSatisfy(m -> assertThat(m.genres()).hasSize(2));
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
  }
}
