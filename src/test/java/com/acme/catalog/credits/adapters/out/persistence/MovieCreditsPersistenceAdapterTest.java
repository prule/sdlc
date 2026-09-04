package com.acme.catalog.credits.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.credits.domain.model.CastCredit;
import com.acme.catalog.credits.domain.model.CrewCredit;
import com.acme.catalog.credits.domain.model.MovieCredits;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaRepository;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaRepository;
import com.acme.catalog.movies.domain.model.MovieId;
import com.acme.common.test.PostgresIntegrationTest;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres, no H2) test for {@link MovieCreditsPersistenceAdapter}. Inserts
 * its own fixtures — never relies on the demo seed.
 */
@Transactional
class MovieCreditsPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private PersonJpaRepository personJpaRepository;
  @Autowired private CreditJpaRepository creditJpaRepository;
  @Autowired private MovieCreditsPersistenceAdapter adapter;

  @Test
  void load_returnsEmptyOptional_whenMovieDoesNotExist() {
    Optional<MovieCredits> result = adapter.load(new MovieId(UUID.randomUUID()));

    assertThat(result).isEmpty();
  }

  @Test
  void load_returnsPresentButEmptyCredits_whenMovieExistsWithNoCredits() {
    UUID movieId = saveMovie("No Credits Movie");

    Optional<MovieCredits> result = adapter.load(new MovieId(movieId));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().cast()).isEmpty();
    assertThat(result.orElseThrow().crew()).isEmpty();
  }

  @Test
  void load_ordersCastByBillingOrderThenNameThenId_andCrewByDepartmentThenJobThenNameThenId() {
    UUID movieId = saveMovie("The Matrix");
    UUID zoe = savePerson("Zoe Actor");
    UUID amy = savePerson("Amy Actor");
    UUID bob = savePerson("Bob Actor");
    UUID zaraDirector = savePerson("Zara Director");
    UUID aaronDirector = savePerson("Aaron Director");

    // Cast: two rows share billingOrder 1, tiebreak by person name.
    saveCastCredit(movieId, zoe, "Lead A", 1);
    saveCastCredit(movieId, amy, "Lead B", 1);
    saveCastCredit(movieId, bob, "Support", 2);

    // Crew: two rows share department + job, tiebreak by person name.
    saveCrewCredit(movieId, zaraDirector, "Directing", "Director");
    saveCrewCredit(movieId, aaronDirector, "Directing", "Director");
    saveCrewCredit(movieId, bob, "Writing", "Writer");

    MovieCredits credits = adapter.load(new MovieId(movieId)).orElseThrow();

    assertThat(credits.cast())
        .extracting(CastCredit::person)
        .extracting(p -> p.id().value())
        .containsExactly(amy, zoe, bob);
    assertThat(credits.cast()).extracting(CastCredit::billingOrder).containsExactly(1, 1, 2);

    assertThat(credits.crew())
        .extracting(CrewCredit::person)
        .extracting(p -> p.id().value())
        .containsExactly(aaronDirector, zaraDirector, bob);
    assertThat(credits.crew())
        .extracting(CrewCredit::department, CrewCredit::job)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Directing", "Director"),
            org.assertj.core.groups.Tuple.tuple("Directing", "Director"),
            org.assertj.core.groups.Tuple.tuple("Writing", "Writer"));
  }

  @Test
  void load_movieWithOnlyCast_returnsPopulatedOrderedCastAndAPresentEmptyCrewArray() {
    UUID movieId = saveMovie("Cast Only Movie");
    UUID zoe = savePerson("Zoe Actor");
    UUID amy = savePerson("Amy Actor");
    saveCastCredit(movieId, zoe, "Lead A", 2);
    saveCastCredit(movieId, amy, "Lead B", 1);

    MovieCredits credits = adapter.load(new MovieId(movieId)).orElseThrow();

    assertThat(credits.cast())
        .extracting(CastCredit::person)
        .extracting(p -> p.id().value())
        .containsExactly(amy, zoe);
    assertThat(credits.crew()).isNotNull().isEmpty();
  }

  @Test
  void load_movieWithOnlyCrew_returnsPopulatedOrderedCrewAndAPresentEmptyCastArray() {
    UUID movieId = saveMovie("Crew Only Movie");
    UUID zara = savePerson("Zara Director");
    UUID aaron = savePerson("Aaron Director");
    saveCrewCredit(movieId, zara, "Directing", "Director");
    saveCrewCredit(movieId, aaron, "Directing", "Director");

    MovieCredits credits = adapter.load(new MovieId(movieId)).orElseThrow();

    assertThat(credits.crew())
        .extracting(CrewCredit::person)
        .extracting(p -> p.id().value())
        .containsExactly(aaron, zara);
    assertThat(credits.cast()).isNotNull().isEmpty();
  }

  @Test
  void load_castAndCrewOrderingIsStableAcrossRepeatedCalls() {
    UUID movieId = saveMovie("Stable Order Movie");
    UUID personA = savePerson("Person A");
    UUID personB = savePerson("Person B");
    saveCastCredit(movieId, personA, "Role A", 1);
    saveCastCredit(movieId, personB, "Role B", 1);

    MovieCredits first = adapter.load(new MovieId(movieId)).orElseThrow();
    MovieCredits second = adapter.load(new MovieId(movieId)).orElseThrow();

    assertThat(first.cast()).isEqualTo(second.cast());
  }

  private UUID saveMovie(String title) {
    UUID movieId = UUID.randomUUID();
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    genres.add(genreJpaRepository.save(new GenreJpaEntity(UUID.randomUUID(), "Genre-" + movieId)));
    movieJpaRepository.save(new MovieJpaEntity(movieId, title, 2000, null, null, null, genres));
    return movieId;
  }

  private UUID savePerson(String name) {
    UUID personId = UUID.randomUUID();
    personJpaRepository.save(new PersonJpaEntity(personId, name));
    return personId;
  }

  private void saveCastCredit(UUID movieId, UUID personId, String character, int billingOrder) {
    PersonJpaEntity person = personJpaRepository.getReferenceById(personId);
    creditJpaRepository.save(
        CreditJpaEntity.cast(UUID.randomUUID(), movieId, person, character, billingOrder));
  }

  private void saveCrewCredit(UUID movieId, UUID personId, String department, String job) {
    PersonJpaEntity person = personJpaRepository.getReferenceById(personId);
    creditJpaRepository.save(
        CreditJpaEntity.crew(UUID.randomUUID(), movieId, person, department, job));
  }
}
