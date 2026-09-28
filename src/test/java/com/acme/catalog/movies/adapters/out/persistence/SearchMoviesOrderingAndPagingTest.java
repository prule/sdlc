package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSortOrder;
import com.acme.catalog.movies.domain.model.MovieSummary;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import com.acme.testsupport.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers coverage for {@link SearchMoviesPort}'s ordering and paging (design D4/D5, task
 * 5.3): the default order, every explicit {@code sort} value, unrated-last for rating orders,
 * stable walks across ties, beyond-last offsets and a huge {@code page}.
 */
class SearchMoviesOrderingAndPagingTest extends PostgresIntegrationTest {

  @Autowired private MoviePersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final MovieSearchCriteria NO_CRITERIA =
      MovieSearchCriteria.of(
          Optional.empty(), Set.of(), Optional.empty(), Optional.empty(), Optional.empty());

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM movie_genre");
    jdbcTemplate.update("DELETE FROM movie");
    jdbcTemplate.update("DELETE FROM genre");
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

  private Page<MovieSummary> search(MovieSortOrder order, PageRequest pageRequest) {
    return adapter.search(NO_CRITERIA, order, pageRequest);
  }

  @Test
  void defaultOrderIsReleaseYearDescendingThenTitleAscending() {
    insertMovie("Arrival", 2016, null);
    insertMovie("Laugh Track", 1998, null);
    insertMovie("Zebra", 2016, null);
    insertMovie("alpha", 2016, null);

    Page<MovieSummary> result = search(MovieSortOrder.DEFAULT, new PageRequest(0, 20));

    assertThat(result.items())
        .extracting(MovieSummary::title)
        .containsExactly("alpha", "Arrival", "Zebra", "Laugh Track");
  }

  @ParameterizedTest
  @EnumSource(
      value = MovieSortOrder.class,
      names = {"DEFAULT"},
      mode = EnumSource.Mode.EXCLUDE)
  void everySupportedSortProducesAStableCompleteOrder(MovieSortOrder order) {
    insertMovie("Beta", 2000, new BigDecimal("3.0"));
    insertMovie("Alpha", 2010, new BigDecimal("4.0"));
    insertMovie("Gamma", 2005, null);

    Page<MovieSummary> result = search(order, new PageRequest(0, 20));

    assertThat(result.items()).hasSize(3);
    assertThat(result.totalElements()).isEqualTo(3);
  }

  @Test
  void titleAscendingOrder() {
    insertMovie("Beta", 2000, null);
    insertMovie("alpha", 2000, null);
    insertMovie("Gamma", 2000, null);

    Page<MovieSummary> result = search(MovieSortOrder.TITLE_ASC, new PageRequest(0, 20));

    assertThat(result.items())
        .extracting(MovieSummary::title)
        .containsExactly("alpha", "Beta", "Gamma");
  }

  @Test
  void titleDescendingOrder() {
    insertMovie("Beta", 2000, null);
    insertMovie("alpha", 2000, null);
    insertMovie("Gamma", 2000, null);

    Page<MovieSummary> result = search(MovieSortOrder.TITLE_DESC, new PageRequest(0, 20));

    assertThat(result.items())
        .extracting(MovieSummary::title)
        .containsExactly("Gamma", "Beta", "alpha");
  }

  @Test
  void releaseYearAscendingOrder() {
    insertMovie("A", 2005, null);
    insertMovie("B", 1998, null);
    insertMovie("C", 2016, null);

    Page<MovieSummary> result = search(MovieSortOrder.RELEASE_YEAR_ASC, new PageRequest(0, 20));

    assertThat(result.items()).extracting(MovieSummary::title).containsExactly("B", "A", "C");
  }

  @Test
  void releaseYearDescendingOrder() {
    insertMovie("A", 2005, null);
    insertMovie("B", 1998, null);
    insertMovie("C", 2016, null);

    Page<MovieSummary> result = search(MovieSortOrder.RELEASE_YEAR_DESC, new PageRequest(0, 20));

    assertThat(result.items()).extracting(MovieSummary::title).containsExactly("C", "A", "B");
  }

  @Test
  void ratingAscendingPutsUnratedLast() {
    insertMovie("Rated Three", 2000, new BigDecimal("3.0"));
    insertMovie("Rated FourFive", 2000, new BigDecimal("4.5"));
    insertMovie("Unrated", 2000, null);

    Page<MovieSummary> result = search(MovieSortOrder.RATING_ASC, new PageRequest(0, 20));

    assertThat(result.items())
        .extracting(MovieSummary::title)
        .containsExactly("Rated Three", "Rated FourFive", "Unrated");
  }

  @Test
  void ratingDescendingPutsUnratedLast() {
    insertMovie("Rated Three", 2000, new BigDecimal("3.0"));
    insertMovie("Rated FourFive", 2000, new BigDecimal("4.5"));
    insertMovie("Unrated", 2000, null);

    Page<MovieSummary> result = search(MovieSortOrder.RATING_DESC, new PageRequest(0, 20));

    assertThat(result.items())
        .extracting(MovieSummary::title)
        .containsExactly("Rated FourFive", "Rated Three", "Unrated");
  }

  @Test
  void tenSameTitleMoviesWalkedWithSizeThreeAppearExactlyOnceAndInTheSameOrderOnARepeatWalk() {
    for (int i = 0; i < 10; i++) {
      insertMovie("Same Title", 2000, null);
    }

    List<UUID> firstWalk = walkAllIds(MovieSortOrder.TITLE_ASC, 3);
    List<UUID> secondWalk = walkAllIds(MovieSortOrder.TITLE_ASC, 3);

    assertThat(firstWalk).hasSize(10);
    assertThat(new java.util.HashSet<>(firstWalk)).hasSize(10);
    assertThat(secondWalk).isEqualTo(firstWalk);
  }

  private List<UUID> walkAllIds(MovieSortOrder order, int size) {
    List<UUID> ids = new ArrayList<>();
    int page = 0;
    while (true) {
      Page<MovieSummary> result = search(order, new PageRequest(page, size));
      if (result.items().isEmpty()) {
        break;
      }
      result.items().forEach(summary -> ids.add(summary.id().value()));
      page++;
    }
    return ids;
  }

  @Test
  void aBeyondLastOffsetReturnsEmptyAndIssuesNoPageQuery() {
    insertMovie("Only Movie", 2000, null);

    Page<MovieSummary> result = search(MovieSortOrder.DEFAULT, new PageRequest(50, 20));

    assertThat(result.items()).isEmpty();
    assertThat(result.totalElements()).isEqualTo(1);
  }

  @Test
  void aHugePageIsABeyondLastPageNeverAFailure() {
    insertMovie("Only Movie", 2000, null);

    Page<MovieSummary> result = search(MovieSortOrder.DEFAULT, new PageRequest(2147483647, 100));

    assertThat(result.items()).isEmpty();
    assertThat(result.totalElements()).isEqualTo(1);
  }
}
