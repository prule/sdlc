package com.acme.catalog.people.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.people.domain.model.Capacity;
import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyPageRequest;
import com.acme.common.test.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence integration test against real Postgres (Testcontainers, never H2). Own fixtures per
 * test, seeded via raw SQL through {@link JdbcTemplate} directly against the shared {@code
 * movies}/{@code movie_genres}/{@code credits} tables (rather than importing the {@code
 * catalog.movies} slice's JPA entities), keeping this slice's test suite as decoupled as its
 * production adapter (design.md decision 3; the human-approved implementation constraint). The
 * person row itself is seeded via this slice's own {@link PersonDetailJpaRepository}.
 */
class PersonFilmographyJpaAdapterTest extends PostgresIntegrationTest {

  @Autowired private PersonDetailJpaRepository personDetailJpaRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PersonFilmographyJpaAdapter adapter;

  private static UUID seedPerson(PersonDetailJpaRepository repository, String name) {
    UUID id = UUID.randomUUID();
    repository.save(new PersonDetailJpaEntity(id, name));
    return id;
  }

  private UUID seedMovie(String title, int year, Integer runtimeMinutes, Double rating) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO movies (id, title, release_year, runtime_minutes, synopsis, rating) VALUES"
            + " (?,?,?,?,?,?)",
        id,
        title,
        year,
        runtimeMinutes,
        null,
        rating == null ? null : BigDecimal.valueOf(rating));
    jdbcTemplate.update("INSERT INTO movie_genres (movie_id, genre) VALUES (?, 'DRAMA')", id);
    return id;
  }

  private UUID seedCastCredit(UUID movieId, UUID personId, String character, int billingOrder) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO credits (id, movie_id, person_id, kind, character, billing_order) VALUES"
            + " (?,?,?,'CAST',?,?)",
        id,
        movieId,
        personId,
        character,
        billingOrder);
    return id;
  }

  private UUID seedCrewCredit(UUID movieId, UUID personId, String department, String job) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO credits (id, movie_id, person_id, kind, department, job) VALUES"
            + " (?,?,?,'CREW',?,?)",
        id,
        movieId,
        personId,
        department,
        job);
    return id;
  }

  @Test
  void loadFilmography_unknownPerson_returnsEmptyOptional() {
    Optional<Filmography> result =
        adapter.loadFilmography(
            UUID.randomUUID(), FilmographyCriteria.none(), new FilmographyPageRequest(0, 20));

    assertThat(result).isEmpty();
  }

  @Test
  void loadFilmography_existingPersonWithNoCredits_returnsPresentButEmptyWithTotalZero() {
    UUID personId = seedPerson(personDetailJpaRepository, "Uncredited Person");

    Optional<Filmography> result =
        adapter.loadFilmography(
            personId, FilmographyCriteria.none(), new FilmographyPageRequest(0, 20));

    assertThat(result).isPresent();
    Filmography filmography = result.orElseThrow();
    assertThat(filmography.content()).isEmpty();
    assertThat(filmography.totalElements()).isZero();
  }

  @Test
  void loadFilmography_personActingAndDirectingSameMovie_yieldsTwoEntries() {
    UUID personId = seedPerson(personDetailJpaRepository, "Multi Capacity");
    UUID movieId = seedMovie("Harbor Lights", 2020, 105, 4.0);
    seedCastCredit(movieId, personId, "Dana Whitfield", 1);
    seedCrewCredit(movieId, personId, "Directing", "Director");

    Filmography filmography =
        adapter
            .loadFilmography(
                personId, FilmographyCriteria.none(), new FilmographyPageRequest(0, 20))
            .orElseThrow();

    assertThat(filmography.totalElements()).isEqualTo(2);
    assertThat(filmography.content()).hasSize(2);
    assertThat(filmography.content())
        .allSatisfy(entry -> assertThat(entry.movie().id()).isEqualTo(movieId));
    Set<Capacity.Type> types =
        filmography.content().stream().map(e -> e.capacity().type()).collect(Collectors.toSet());
    assertThat(types).containsExactlyInAnyOrder(Capacity.Type.ACTING, Capacity.Type.NON_ACTING);
  }

  @Test
  void loadFilmography_orderedNewestFirstThenTitleAscending() {
    UUID personId = seedPerson(personDetailJpaRepository, "Ordering Person");
    UUID a2020 = seedMovie("Alpha", 2020, null, null);
    UUID b2020 = seedMovie("Bravo", 2020, null, null);
    UUID a2019 = seedMovie("Charlie", 2019, null, null);
    seedCastCredit(a2020, personId, null, 1);
    seedCastCredit(b2020, personId, null, 1);
    seedCastCredit(a2019, personId, null, 1);

    Filmography filmography =
        adapter
            .loadFilmography(
                personId, FilmographyCriteria.none(), new FilmographyPageRequest(0, 20))
            .orElseThrow();

    assertThat(filmography.content())
        .extracting(e -> e.movie().id())
        .containsExactly(a2020, b2020, a2019);
  }

  @Test
  void loadFilmography_pagingAcrossTies_everyEntryAppearsExactlyOnceWithNoGapsOrDuplicates() {
    UUID personId = seedPerson(personDetailJpaRepository, "Tied Person");
    List<UUID> creditIds = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      UUID movieId = seedMovie("Tied Movie", 2020, null, null);
      creditIds.add(seedCastCredit(movieId, personId, null, 1));
    }

    int pageSize = 3;
    List<UUID> seenMovieIds = new ArrayList<>();
    for (int pageIndex = 0; pageIndex * pageSize < creditIds.size(); pageIndex++) {
      Filmography filmography =
          adapter
              .loadFilmography(
                  personId,
                  FilmographyCriteria.none(),
                  new FilmographyPageRequest(pageIndex, pageSize))
              .orElseThrow();
      filmography.content().forEach(e -> seenMovieIds.add(e.movie().id()));
    }

    assertThat(seenMovieIds).hasSize(creditIds.size());
    assertThat(new HashSet<>(seenMovieIds)).hasSize(creditIds.size());
  }

  @Test
  void loadFilmography_capacityFilter_narrowsToOneType() {
    UUID personId = seedPerson(personDetailJpaRepository, "Filter Person");
    UUID movieId = seedMovie("Harbor Lights", 2020, null, null);
    seedCastCredit(movieId, personId, "Dana Whitfield", 1);
    seedCrewCredit(movieId, personId, "Directing", "Director");

    Filmography actingOnly =
        adapter
            .loadFilmography(
                personId,
                FilmographyCriteria.of(Capacity.Type.ACTING, null, null),
                new FilmographyPageRequest(0, 20))
            .orElseThrow();

    assertThat(actingOnly.content()).hasSize(1);
    assertThat(actingOnly.content().get(0).capacity().type()).isEqualTo(Capacity.Type.ACTING);
    assertThat(actingOnly.totalElements()).isEqualTo(1);
  }

  @Test
  void loadFilmography_releaseYearRange_eachBoundWorksAloneAndTogether() {
    UUID personId = seedPerson(personDetailJpaRepository, "Range Person");
    UUID y2000 = seedMovie("Y2000", 2000, null, null);
    UUID y2010 = seedMovie("Y2010", 2010, null, null);
    UUID y2020 = seedMovie("Y2020", 2020, null, null);
    seedCastCredit(y2000, personId, null, 1);
    seedCastCredit(y2010, personId, null, 1);
    seedCastCredit(y2020, personId, null, 1);

    Filmography fromOnly =
        adapter
            .loadFilmography(
                personId,
                FilmographyCriteria.of(null, 2010, null),
                new FilmographyPageRequest(0, 20))
            .orElseThrow();
    assertThat(fromOnly.content())
        .extracting(e -> e.movie().id())
        .containsExactlyInAnyOrder(y2010, y2020);

    Filmography toOnly =
        adapter
            .loadFilmography(
                personId,
                FilmographyCriteria.of(null, null, 2010),
                new FilmographyPageRequest(0, 20))
            .orElseThrow();
    assertThat(toOnly.content())
        .extracting(e -> e.movie().id())
        .containsExactlyInAnyOrder(y2000, y2010);

    Filmography both =
        adapter
            .loadFilmography(
                personId,
                FilmographyCriteria.of(null, 2005, 2015),
                new FilmographyPageRequest(0, 20))
            .orElseThrow();
    assertThat(both.content()).extracting(e -> e.movie().id()).containsExactly(y2010);
  }

  @Test
  void loadFilmography_invertedReleaseYearRange_isValidAndMatchesNothing() {
    UUID personId = seedPerson(personDetailJpaRepository, "Inverted Range Person");
    UUID movieId = seedMovie("Y2010", 2010, null, null);
    seedCastCredit(movieId, personId, null, 1);

    Filmography filmography =
        adapter
            .loadFilmography(
                personId,
                FilmographyCriteria.of(null, 2020, 2000),
                new FilmographyPageRequest(0, 20))
            .orElseThrow();

    assertThat(filmography.content()).isEmpty();
    assertThat(filmography.totalElements()).isZero();
  }

  @Test
  void loadFilmography_filterMatchingNothing_returnsEmptyPageWithTotalZero() {
    UUID personId = seedPerson(personDetailJpaRepository, "No Match Person");
    UUID movieId = seedMovie("Harbor Lights", 2020, null, null);
    seedCastCredit(movieId, personId, "Dana Whitfield", 1);

    Filmography filmography =
        adapter
            .loadFilmography(
                personId,
                FilmographyCriteria.of(Capacity.Type.NON_ACTING, null, null),
                new FilmographyPageRequest(0, 20))
            .orElseThrow();

    assertThat(filmography.content()).isEmpty();
    assertThat(filmography.totalElements()).isZero();
  }

  @Test
  void loadFilmography_pageBeyondLast_returnsEmptyPageWithTrueTotal() {
    UUID personId = seedPerson(personDetailJpaRepository, "Beyond Last Person");
    UUID movieId = seedMovie("Harbor Lights", 2020, null, null);
    seedCastCredit(movieId, personId, "Dana Whitfield", 1);

    Filmography filmography =
        adapter
            .loadFilmography(
                personId, FilmographyCriteria.none(), new FilmographyPageRequest(5, 20))
            .orElseThrow();

    assertThat(filmography.content()).isEmpty();
    assertThat(filmography.totalElements()).isEqualTo(1);
  }

  @Test
  void loadFilmography_totalElementsAgreesWithRows_underCapacityFilter() {
    UUID personId = seedPerson(personDetailJpaRepository, "Count Guard Person");
    UUID movie1 = seedMovie("Movie One", 2020, null, null);
    UUID movie2 = seedMovie("Movie Two", 2021, null, null);
    seedCastCredit(movie1, personId, "Lead", 1);
    seedCrewCredit(movie1, personId, "Directing", "Director");
    seedCastCredit(movie2, personId, "Support", 1);

    Filmography acting =
        adapter
            .loadFilmography(
                personId,
                FilmographyCriteria.of(Capacity.Type.ACTING, null, null),
                new FilmographyPageRequest(0, 20))
            .orElseThrow();

    assertThat(acting.totalElements()).isEqualTo(acting.content().size());
    assertThat(acting.totalElements()).isEqualTo(2);
  }

  @Test
  void loadFilmography_actingEntryWithNoCharacter_omitsIt() {
    UUID personId = seedPerson(personDetailJpaRepository, "No Character Person");
    UUID movieId = seedMovie("Harbor Lights", 2020, null, null);
    seedCastCredit(movieId, personId, null, 2);

    FilmographyEntry entry =
        adapter
            .loadFilmography(
                personId, FilmographyCriteria.none(), new FilmographyPageRequest(0, 20))
            .orElseThrow()
            .content()
            .get(0);

    Capacity.Acting acting = (Capacity.Acting) entry.capacity();
    assertThat(acting.character()).isEmpty();
  }
}
