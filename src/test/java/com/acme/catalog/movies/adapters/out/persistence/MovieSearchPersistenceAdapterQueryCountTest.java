package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Movie;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.catalog.movies.domain.model.MoviePage;
import com.acme.catalog.movies.domain.model.MovieSearchCriteria;
import com.acme.catalog.movies.domain.model.MovieSort;
import com.acme.catalog.movies.domain.model.MovieSortField;
import com.acme.catalog.movies.domain.model.SortDirection;
import com.acme.common.test.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres) guard for the N+1 avoidance decision (see {@code
 * openspec/changes/add-movie-search/design.md}, D2): loading a page of movies each carrying
 * multiple genres must issue a SQL statement count that is bounded and independent of the page size
 * or the genres-per-movie, and the CAT-001 single-movie detail path must not regress.
 */
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MovieSearchPersistenceAdapterQueryCountTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private MovieSearchPersistenceAdapter searchAdapter;
  @Autowired private MoviePersistenceAdapter moviePersistenceAdapter;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private EntityManager entityManager;

  @Test
  void search_pageLoad_statementCountIsBoundedAndIndependentOfPageSizeAndGenreCount() {
    seedMovies(5, 2);
    entityManager.flush();
    Statistics statistics = statistics();
    statistics.clear();

    MoviePage smallPage = searchAdapter.search(MovieSearchCriteria.NONE, 0, 5, MovieSort.DEFAULT);
    long smallPageStatementCount = statistics.getPrepareStatementCount();

    assertThat(smallPage.items()).hasSize(5);
    assertThat(smallPageStatementCount)
        .as("statement count for a 5-row, 2-genre-per-movie page")
        .isLessThanOrEqualTo(3);

    seedMovies(10, 5);
    entityManager.flush();
    statistics.clear();

    MoviePage largerPage = searchAdapter.search(MovieSearchCriteria.NONE, 0, 15, MovieSort.DEFAULT);
    long largerPageStatementCount = statistics.getPrepareStatementCount();

    assertThat(largerPage.items()).hasSize(15);
    assertThat(largerPageStatementCount)
        .as("statement count does not grow with page size or genres-per-movie")
        .isEqualTo(smallPageStatementCount);
  }

  /**
   * Hardens the N+1 guard: a naive single {@code JOIN FETCH ... LIMIT} query would lower the
   * statement count (passing the guard above) while reintroducing Hibernate in-memory pagination
   * over the to-many genre join (HHH000104), which mis-paginates — wrong row count and/or wrong
   * order. Asserting exact ordered content, not just the statement count, catches that regression.
   */
  @Test
  void search_multiGenreMultiRowSortedPage_returnsCorrectSizeAndOrder() {
    UUID alpha = saveMovieWithGenres("Alpha", 2000, Set.of("Drama", "Crime", "Thriller"));
    UUID bravo = saveMovieWithGenres("Bravo", 2000, Set.of("Sci-Fi", "Action"));
    UUID charlie = saveMovieWithGenres("Charlie", 2000, Set.of("Comedy", "Romance", "Drama"));
    saveMovieWithGenres("Delta", 2000, Set.of("Horror"));
    entityManager.flush();

    MoviePage page =
        searchAdapter.search(
            MovieSearchCriteria.NONE, 0, 3, new MovieSort(MovieSortField.TITLE, SortDirection.ASC));

    assertThat(page.items()).hasSize(3);
    assertThat(page.items().stream().map(Movie::id).map(MovieId::value).toList())
        .as(
            "page size respected and rows in the requested sort order (no in-memory-pagination"
                + " mis-pagination)")
        .containsExactly(alpha, bravo, charlie);
    assertThat(page.items().get(0).genres()).hasSize(3);
    assertThat(page.items().get(2).genres()).hasSize(3);
  }

  private UUID saveMovieWithGenres(String title, int releaseYear, Set<String> genreNames) {
    UUID movieId = UUID.randomUUID();
    saveMovie(movieId, title, releaseYear, genreNames);
    return movieId;
  }

  @Test
  void getMovieById_queryBehaviourDoesNotRegress() {
    UUID movieId = UUID.randomUUID();
    saveMovie(movieId, "Detail Movie", 2000, Set.of("Drama", "Crime", "Thriller"));
    entityManager.flush();
    Statistics statistics = statistics();
    statistics.clear();

    moviePersistenceAdapter.load(new MovieId(movieId));

    assertThat(statistics.getPrepareStatementCount())
        .as("statement count for the single-movie detail path")
        .isLessThanOrEqualTo(2);
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private void seedMovies(int count, int genresPerMovie) {
    for (int i = 0; i < count; i++) {
      Set<String> genreNames = new LinkedHashSet<>();
      for (int g = 0; g < genresPerMovie; g++) {
        genreNames.add("Genre-" + UUID.randomUUID());
      }
      saveMovie(UUID.randomUUID(), "Movie " + UUID.randomUUID(), 2000 + i, genreNames);
    }
  }

  private void saveMovie(UUID movieId, String title, int releaseYear, Set<String> genreNames) {
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    for (String name : genreNames) {
      genres.add(findOrCreateGenre(name));
    }
    MovieJpaEntity entity =
        new MovieJpaEntity(movieId, title, releaseYear, null, null, BigDecimal.valueOf(4), genres);
    movieJpaRepository.save(entity);
  }

  private GenreJpaEntity findOrCreateGenre(String name) {
    return genreJpaRepository.findAll().stream()
        .filter(g -> g.getName().equals(name))
        .findFirst()
        .orElseGet(() -> genreJpaRepository.save(new GenreJpaEntity(UUID.randomUUID(), name)));
  }
}
