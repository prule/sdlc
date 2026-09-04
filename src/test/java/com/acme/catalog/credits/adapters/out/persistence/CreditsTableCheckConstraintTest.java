package com.acme.catalog.credits.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.catalog.movies.adapters.out.persistence.GenreJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaRepository;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaRepository;
import com.acme.common.test.PostgresIntegrationTest;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres, no H2) proof that {@code chk_credits_cast_fields} (V4) is enforced
 * by the database itself, not just by the {@link CreditJpaEntity} factory methods. Bypasses the
 * domain/factory guards entirely with raw {@link JdbcTemplate} inserts, so a violating row can only
 * be rejected by the DB CHECK constraint.
 */
@Transactional
class CreditsTableCheckConstraintTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private PersonJpaRepository personJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void insert_rejectsACastRowThatCarriesADepartment() {
    UUID movieId = saveMovie();
    UUID personId = savePerson();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO credits (id, movie_id, person_id, credit_type, character_name,"
                        + " billing_order, department, job) VALUES (?, ?, ?, 'CAST', ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    movieId,
                    personId,
                    "Neo",
                    1,
                    "Directing",
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void insert_rejectsACastRowMissingBillingOrder() {
    UUID movieId = saveMovie();
    UUID personId = savePerson();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO credits (id, movie_id, person_id, credit_type, character_name,"
                        + " billing_order, department, job) VALUES (?, ?, ?, 'CAST', ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    movieId,
                    personId,
                    "Neo",
                    null,
                    null,
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void insert_rejectsACrewRowThatCarriesACharacterAndBillingOrder() {
    UUID movieId = saveMovie();
    UUID personId = savePerson();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO credits (id, movie_id, person_id, credit_type, character_name,"
                        + " billing_order, department, job) VALUES (?, ?, ?, 'CREW', ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    movieId,
                    personId,
                    "Neo",
                    1,
                    "Directing",
                    "Director"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void insert_rejectsACrewRowMissingDepartmentAndJob() {
    UUID movieId = saveMovie();
    UUID personId = savePerson();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO credits (id, movie_id, person_id, credit_type, character_name,"
                        + " billing_order, department, job) VALUES (?, ?, ?, 'CREW', ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    movieId,
                    personId,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID saveMovie() {
    UUID movieId = UUID.randomUUID();
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    genres.add(genreJpaRepository.save(new GenreJpaEntity(UUID.randomUUID(), "Genre-" + movieId)));
    movieJpaRepository.save(
        new MovieJpaEntity(movieId, "Check Constraint Movie", 2000, null, null, null, genres));
    return movieId;
  }

  private UUID savePerson() {
    UUID personId = UUID.randomUUID();
    personJpaRepository.save(new PersonJpaEntity(personId, "Check Constraint Person"));
    return personId;
  }
}
