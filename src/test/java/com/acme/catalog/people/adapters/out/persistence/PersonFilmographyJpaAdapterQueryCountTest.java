package com.acme.catalog.people.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.credits.adapters.out.persistence.CreditJpaEntity;
import com.acme.catalog.credits.adapters.out.persistence.CreditJpaRepository;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaRepository;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaRepository;
import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.PersonId;
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
 * openspec/changes/add-person-filmography/design.md}, D-B): loading a Person's filmography — each
 * credit joining to its Movie and that Movie's genres — must issue a bounded, filmography-size-
 * independent number of SQL statements, not one Movie query per credit row nor one genre query per
 * Movie.
 */
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PersonFilmographyJpaAdapterQueryCountTest extends PostgresIntegrationTest {

  @Autowired
  private com.acme.catalog.credits.adapters.out.persistence.PersonJpaRepository
      creditsPersonJpaRepository;

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private CreditJpaRepository creditJpaRepository;
  @Autowired private PersonFilmographyJpaAdapter adapter;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @PersistenceContext private EntityManager entityManager;

  @Test
  void loadFilmography_statementCountIsBoundedAndIndependentOfFilmographySize() {
    UUID smallPersonId = seedPersonWithCredits(3);
    entityManager.flush();
    Statistics statistics = statistics();
    statistics.clear();

    FilmographyPage smallResult =
        adapter.loadFilmography(new PersonId(smallPersonId), 0, 100).orElseThrow();

    long smallStatementCount = statistics.getPrepareStatementCount();
    assertThat(smallResult.items()).hasSize(3);
    assertThat(smallStatementCount)
        .as("statement count for a person with 3 credits across 3 movies")
        .isLessThanOrEqualTo(4);

    UUID largePersonId = seedPersonWithCredits(20);
    entityManager.flush();
    statistics.clear();

    FilmographyPage largeResult =
        adapter.loadFilmography(new PersonId(largePersonId), 0, 100).orElseThrow();

    long largeStatementCount = statistics.getPrepareStatementCount();
    assertThat(largeResult.items()).hasSize(20);
    assertThat(largeStatementCount)
        .as("statement count does not grow with filmography size")
        .isEqualTo(smallStatementCount);
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private UUID seedPersonWithCredits(int movieCount) {
    UUID personId = UUID.randomUUID();
    creditsPersonJpaRepository.save(
        new com.acme.catalog.credits.adapters.out.persistence.PersonJpaEntity(
            personId, "Actor " + personId));

    for (int i = 0; i < movieCount; i++) {
      UUID movieId = UUID.randomUUID();
      Set<GenreJpaEntity> genres = new LinkedHashSet<>();
      genres.add(
          genreJpaRepository.save(new GenreJpaEntity(UUID.randomUUID(), "Genre-" + movieId)));
      movieJpaRepository.save(
          new MovieJpaEntity(movieId, "Movie " + movieId, 2000 + i, null, null, null, genres));

      com.acme.catalog.credits.adapters.out.persistence.PersonJpaEntity person =
          creditsPersonJpaRepository.findById(personId).orElseThrow();
      creditJpaRepository.save(
          CreditJpaEntity.cast(UUID.randomUUID(), movieId, person, "Character " + i, i + 1));
    }
    return personId;
  }
}
