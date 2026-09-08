package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MoviePageRequest;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.common.test.PostgresIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Guards {@link MovieSearchPersistenceAdapter} against a per-row genre N+1: asserts, via Hibernate
 * statistics, that the number of database statements issued for one search request is a small,
 * exact constant that does not grow with the page size (see {@code
 * openspec/changes/add-movie-search/design.md}).
 */
class MovieSearchNPlusOneGuardTest extends PostgresIntegrationTest {

  @DynamicPropertySource
  static void hibernateStatistics(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
  }

  @Autowired private MovieJpaRepository movieJpaRepository;

  @Autowired private MovieSearchPersistenceAdapter adapter;

  @Autowired private EntityManagerFactory entityManagerFactory;

  private static final int EXPECTED_STATEMENT_COUNT = 3;

  private String uniqueTag() {
    return "NPLUSONE" + UUID.randomUUID().toString().replace("-", "");
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  @Test
  void search_statementCountIsTheSameConstant_forAOneMoviePageAndAFullMultiGenrePage() {
    String tag = uniqueTag();
    seedMovies(tag, 1);
    long oneMoviePageCount = statementCountForFullPageSearch(tag, 1);

    String otherTag = uniqueTag();
    seedMovies(otherTag, 100);
    long hundredMoviePageCount = statementCountForFullPageSearch(otherTag, 100);

    assertThat(oneMoviePageCount).isEqualTo(EXPECTED_STATEMENT_COUNT);
    assertThat(hundredMoviePageCount).isEqualTo(EXPECTED_STATEMENT_COUNT);
    assertThat(hundredMoviePageCount).isEqualTo(oneMoviePageCount);
  }

  private void seedMovies(String tag, int count) {
    List<MovieJpaEntity> movies = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      movies.add(
          new MovieJpaEntity(
              UUID.randomUUID(),
              tag + " Movie " + i,
              2020,
              120,
              null,
              BigDecimal.valueOf(4),
              Set.of(Genre.DRAMA, Genre.CRIME, Genre.ACTION)));
    }
    movieJpaRepository.saveAll(movies);
  }

  private long statementCountForFullPageSearch(String tag, int size) {
    statistics().clear();

    MovieSearchCriteria criteria = MovieSearchCriteria.of(tag, null, null, null, null);
    MoviePage page =
        adapter.search(criteria, new MoviePageRequest(0, size), MovieSort.defaultSort());
    assertThat(page.content()).hasSize(size);

    return statistics().getPrepareStatementCount();
  }
}
