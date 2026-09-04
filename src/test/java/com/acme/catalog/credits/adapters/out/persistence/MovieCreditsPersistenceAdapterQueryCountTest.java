package com.acme.catalog.credits.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.adapters.out.persistence.GenreJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaRepository;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaRepository;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.test.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
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
 * openspec/changes/add-movie-credits/design.md}, D-D): loading a movie's credits — each credit
 * joining to a Person — must issue a bounded, credit-count-independent number of SQL statements (an
 * existence probe + a single fetch-join query), not one Person query per credit row.
 */
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MovieCreditsPersistenceAdapterQueryCountTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private PersonJpaRepository personJpaRepository;
  @Autowired private CreditJpaRepository creditJpaRepository;
  @Autowired private MovieCreditsPersistenceAdapter adapter;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @PersistenceContext private EntityManager entityManager;

  @Test
  void load_statementCountIsBoundedAndIndependentOfCreditCount() {
    UUID smallMovieId = seedMovieWithCredits(3, 3);
    entityManager.flush();
    Statistics statistics = statistics();
    statistics.clear();

    var smallResult = adapter.load(new MovieId(smallMovieId));

    long smallStatementCount = statistics.getPrepareStatementCount();
    assertThat(smallResult).isPresent();
    assertThat(smallResult.orElseThrow().cast()).hasSize(3);
    assertThat(smallResult.orElseThrow().crew()).hasSize(3);
    assertThat(smallStatementCount)
        .as("statement count for a movie with 3 cast + 3 crew credits")
        .isLessThanOrEqualTo(2);

    UUID largeMovieId = seedMovieWithCredits(20, 20);
    entityManager.flush();
    statistics.clear();

    var largeResult = adapter.load(new MovieId(largeMovieId));

    long largeStatementCount = statistics.getPrepareStatementCount();
    assertThat(largeResult.orElseThrow().cast()).hasSize(20);
    assertThat(largeResult.orElseThrow().crew()).hasSize(20);
    assertThat(largeStatementCount)
        .as("statement count does not grow with credit count")
        .isEqualTo(smallStatementCount);
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private UUID seedMovieWithCredits(int castCount, int crewCount) {
    UUID movieId = UUID.randomUUID();
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    genres.add(genreJpaRepository.save(new GenreJpaEntity(UUID.randomUUID(), "Genre-" + movieId)));
    movieJpaRepository.save(
        new MovieJpaEntity(movieId, "Movie " + movieId, 2000, null, null, null, genres));

    for (int i = 0; i < castCount; i++) {
      PersonJpaEntity person =
          personJpaRepository.save(new PersonJpaEntity(UUID.randomUUID(), "Actor " + i));
      creditJpaRepository.save(
          CreditJpaEntity.cast(UUID.randomUUID(), movieId, person, "Character " + i, i + 1));
    }
    for (int i = 0; i < crewCount; i++) {
      PersonJpaEntity person =
          personJpaRepository.save(new PersonJpaEntity(UUID.randomUUID(), "Crew " + i));
      creditJpaRepository.save(
          CreditJpaEntity.crew(UUID.randomUUID(), movieId, person, "Department " + i, "Job " + i));
    }
    return movieId;
  }
}
