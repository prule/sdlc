package com.acme.catalog.movies.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.movies.domain.model.Credit;
import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.MovieCredits;
import com.acme.common.test.PostgresIntegrationTest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence adapter test against real Postgres (Testcontainers, never H2). Own fixtures,
 * independent of {@link MovieDemoSeed}.
 */
class MovieCreditsPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired private MovieJpaRepository movieJpaRepository;

  @Autowired private PersonJpaRepository personJpaRepository;

  @Autowired private CreditJpaRepository creditJpaRepository;

  @Autowired private MovieCreditsPersistenceAdapter adapter;

  @Test
  void loadCreditsForMovie_seededMovieWithCastAndCrew_mapsRowsToDomain() {
    UUID movieId = UUID.randomUUID();
    movieJpaRepository.save(
        new MovieJpaEntity(movieId, "Harbor Lights", 2020, null, null, null, Set.of(Genre.DRAMA)));

    PersonJpaEntity lead = personJpaRepository.save(new PersonJpaEntity(UUID.randomUUID(), "Ava"));
    PersonJpaEntity supporting =
        personJpaRepository.save(new PersonJpaEntity(UUID.randomUUID(), "Marcus"));
    PersonJpaEntity director =
        personJpaRepository.save(new PersonJpaEntity(UUID.randomUUID(), "Priya"));

    creditJpaRepository.save(
        CreditJpaEntity.cast(UUID.randomUUID(), movieId, lead, "Dana Whitfield", 1));
    creditJpaRepository.save(CreditJpaEntity.cast(UUID.randomUUID(), movieId, supporting, null, 2));
    creditJpaRepository.save(
        CreditJpaEntity.crew(UUID.randomUUID(), movieId, director, "Directing", "Director"));

    Optional<MovieCredits> result = adapter.loadCreditsForMovie(movieId);

    assertThat(result).isPresent();
    MovieCredits credits = result.orElseThrow();
    assertThat(credits.cast()).hasSize(2);
    assertThat(credits.cast().get(0).person().name()).isEqualTo("Ava");
    assertThat(credits.cast().get(0).character()).contains("Dana Whitfield");
    assertThat(credits.cast().get(1).character()).isEmpty();
    assertThat(credits.crew()).hasSize(1);
    Credit.Crew crew = credits.crew().get(0);
    assertThat(crew.person().name()).isEqualTo("Priya");
    assertThat(crew.department()).isEqualTo("Directing");
    assertThat(crew.job()).isEqualTo("Director");
  }

  @Test
  void loadCreditsForMovie_movieWithCastButNoCrew_presentsCastAndEmptyCrew() {
    UUID movieId = UUID.randomUUID();
    movieJpaRepository.save(
        new MovieJpaEntity(movieId, "Cast Only", 2021, null, null, null, Set.of(Genre.DRAMA)));
    PersonJpaEntity lead = personJpaRepository.save(new PersonJpaEntity(UUID.randomUUID(), "Ava"));
    creditJpaRepository.save(
        CreditJpaEntity.cast(UUID.randomUUID(), movieId, lead, "Dana Whitfield", 1));

    Optional<MovieCredits> result = adapter.loadCreditsForMovie(movieId);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().cast()).hasSize(1);
    assertThat(result.orElseThrow().crew()).isEmpty();
  }

  @Test
  void loadCreditsForMovie_movieWithCrewButNoCast_presentsCrewAndEmptyCast() {
    UUID movieId = UUID.randomUUID();
    movieJpaRepository.save(
        new MovieJpaEntity(movieId, "Crew Only", 2021, null, null, null, Set.of(Genre.DRAMA)));
    PersonJpaEntity director =
        personJpaRepository.save(new PersonJpaEntity(UUID.randomUUID(), "Priya"));
    creditJpaRepository.save(
        CreditJpaEntity.crew(UUID.randomUUID(), movieId, director, "Directing", "Director"));

    Optional<MovieCredits> result = adapter.loadCreditsForMovie(movieId);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().cast()).isEmpty();
    assertThat(result.orElseThrow().crew()).hasSize(1);
  }

  @Test
  void loadCreditsForMovie_existingMovieWithNoCredits_returnsPresentButEmptyGroups() {
    UUID movieId = UUID.randomUUID();
    movieJpaRepository.save(
        new MovieJpaEntity(movieId, "Uncredited", 2022, null, null, null, Set.of(Genre.DRAMA)));

    Optional<MovieCredits> result = adapter.loadCreditsForMovie(movieId);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().cast()).isEmpty();
    assertThat(result.orElseThrow().crew()).isEmpty();
  }

  @Test
  void loadCreditsForMovie_unknownMovieId_returnsEmpty() {
    Optional<MovieCredits> result = adapter.loadCreditsForMovie(UUID.randomUUID());

    assertThat(result).isEmpty();
  }
}
