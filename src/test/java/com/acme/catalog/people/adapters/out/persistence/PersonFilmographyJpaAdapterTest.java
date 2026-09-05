package com.acme.catalog.people.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.catalog.credits.adapters.out.persistence.CreditJpaEntity;
import com.acme.catalog.credits.adapters.out.persistence.CreditJpaRepository;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.GenreJpaRepository;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaEntity;
import com.acme.catalog.movies.adapters.out.persistence.MovieJpaRepository;
import com.acme.catalog.people.domain.model.ActingCapacity;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.NonActingCapacity;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.test.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testcontainers (real Postgres, no H2) test for {@link PersonFilmographyJpaAdapter}. Inserts its
 * own fixtures — never relies on the demo seed (see {@code
 * openspec/changes/add-person-filmography/design.md}).
 */
@Transactional
class PersonFilmographyJpaAdapterTest extends PostgresIntegrationTest {

  @Autowired
  private com.acme.catalog.credits.adapters.out.persistence.PersonJpaRepository
      creditsPersonJpaRepository;

  @Autowired private MovieJpaRepository movieJpaRepository;
  @Autowired private GenreJpaRepository genreJpaRepository;
  @Autowired private CreditJpaRepository creditJpaRepository;
  @Autowired private PersonFilmographyJpaAdapter adapter;

  @Test
  void loadFilmography_returnsEmpty_whenNoPersonForId() {
    Optional<FilmographyPage> result =
        adapter.loadFilmography(new PersonId(UUID.randomUUID()), 0, 20);

    assertThat(result).isEmpty();
  }

  @Test
  void loadFilmography_returnsPresentButEmptyPage_forAnExistingPersonWithNoCredits() {
    UUID personId = seedPerson("No Credits Person");

    Optional<FilmographyPage> result = adapter.loadFilmography(new PersonId(personId), 0, 20);

    assertThat(result).isPresent();
    FilmographyPage page = result.orElseThrow();
    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  void loadFilmography_returnsOneItemPerMovieAndCapacity_forActingAndNonActingCredits() {
    UUID personId = seedPerson("Multi Capacity Person");
    UUID movieId = seedMovie("One Movie", 2020, "Drama");
    seedCastCredit(movieId, personId, "Neo", 1);
    seedCrewCredit(movieId, personId, "Directing", "Director");

    FilmographyPage page = adapter.loadFilmography(new PersonId(personId), 0, 20).orElseThrow();

    assertThat(page.items()).hasSize(2);
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.items())
        .allSatisfy(item -> assertThat(item.movieId().value()).isEqualTo(movieId));
    assertThat(page.items().stream().map(FilmographyEntry::capacity))
        .anySatisfy(
            capacity ->
                assertThat(capacity)
                    .isInstanceOf(ActingCapacity.class)
                    .satisfies(c -> assertThat(((ActingCapacity) c).character()).isEqualTo("Neo")))
        .anySatisfy(
            capacity ->
                assertThat(capacity)
                    .isInstanceOf(NonActingCapacity.class)
                    .satisfies(
                        c ->
                            assertThat(((NonActingCapacity) c).department())
                                .isEqualTo("Directing")));
  }

  @Test
  void loadFilmography_ordersByReleaseYearDescThenTitleAsc() {
    UUID personId = seedPerson("Ordering Person");
    UUID older = seedMovie("Zeta Movie", 2010, "Drama");
    UUID newer = seedMovie("Alpha Movie", 2020, "Drama");
    seedCastCredit(older, personId, "Role1", 1);
    seedCastCredit(newer, personId, "Role2", 1);

    FilmographyPage page = adapter.loadFilmography(new PersonId(personId), 0, 20).orElseThrow();

    assertThat(page.items()).hasSize(2);
    assertThat(page.items().get(0).movieId().value()).isEqualTo(newer);
    assertThat(page.items().get(1).movieId().value()).isEqualTo(older);
  }

  @Test
  void loadFilmography_tiedOnReleaseYearAndTitle_resolvesByCreditIdTerminalTiebreak() {
    UUID personId = seedPerson("Tiebreak Person");
    UUID movieId = seedMovie("Same Title", 2020, "Drama");
    UUID firstCreditId = seedCastCreditWithId(movieId, personId, "Role1", 1);
    UUID secondCreditId = seedCrewCreditWithId(movieId, personId, "Directing", "Director");

    FilmographyPage page = adapter.loadFilmography(new PersonId(personId), 0, 20).orElseThrow();

    assertThat(page.items()).hasSize(2);
    List<UUID> creditIdOrder = page.items().stream().map(FilmographyEntry::creditId).toList();
    // Postgres orders uuid values byte-wise (equivalently, by canonical hex-string form), which
    // differs from java.util.UUID#compareTo's signed-long comparison whenever the top bit of
    // either 64-bit half is set — so the expected order below is computed the same way SQL orders
    // them (string comparison), not via UUID#compareTo.
    List<UUID> expectedOrder =
        firstCreditId.toString().compareTo(secondCreditId.toString()) < 0
            ? List.of(firstCreditId, secondCreditId)
            : List.of(secondCreditId, firstCreditId);
    assertThat(creditIdOrder).isEqualTo(expectedOrder);
  }

  @Test
  void loadFilmography_pagesWithoutCrossPageSkipOrDuplicate() {
    UUID personId = seedPerson("Paging Person");
    List<UUID> movieIds = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      UUID movieId = seedMovie("Movie " + i, 2000 + i, "Drama");
      movieIds.add(movieId);
      seedCastCredit(movieId, personId, "Role" + i, 1);
    }

    FilmographyPage page0 = adapter.loadFilmography(new PersonId(personId), 0, 2).orElseThrow();
    FilmographyPage page1 = adapter.loadFilmography(new PersonId(personId), 1, 2).orElseThrow();
    FilmographyPage page2 = adapter.loadFilmography(new PersonId(personId), 2, 2).orElseThrow();

    assertThat(page0.items()).hasSize(2);
    assertThat(page1.items()).hasSize(2);
    assertThat(page2.items()).hasSize(1);
    assertThat(page0.totalElements()).isEqualTo(5);
    assertThat(page0.totalPages()).isEqualTo(3);

    List<UUID> allMovieIds = new ArrayList<>();
    page0.items().forEach(item -> allMovieIds.add(item.movieId().value()));
    page1.items().forEach(item -> allMovieIds.add(item.movieId().value()));
    page2.items().forEach(item -> allMovieIds.add(item.movieId().value()));

    assertThat(allMovieIds).hasSize(5).doesNotHaveDuplicates();
    // Newest first: Movie 4 (2004) ... Movie 0 (2000).
    assertThat(allMovieIds)
        .containsExactly(
            movieIds.get(4), movieIds.get(3), movieIds.get(2), movieIds.get(1), movieIds.get(0));
  }

  @Test
  void loadFilmography_aPageBeyondTheLast_isPresentButEmpty() {
    UUID personId = seedPerson("Beyond Last Page Person");
    UUID movieId = seedMovie("Only Movie", 2020, "Drama");
    seedCastCredit(movieId, personId, "Role", 1);

    FilmographyPage page = adapter.loadFilmography(new PersonId(personId), 5, 20).orElseThrow();

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(1);
  }

  private UUID seedPerson(String name) {
    UUID id = UUID.randomUUID();
    creditsPersonJpaRepository.save(
        new com.acme.catalog.credits.adapters.out.persistence.PersonJpaEntity(id, name));
    return id;
  }

  private UUID seedMovie(String title, int releaseYear, String genreName) {
    UUID movieId = UUID.randomUUID();
    Set<GenreJpaEntity> genres = new LinkedHashSet<>();
    genres.add(findOrCreateGenre(genreName));
    movieJpaRepository.save(
        new MovieJpaEntity(movieId, title, releaseYear, null, null, null, genres));
    return movieId;
  }

  private GenreJpaEntity findOrCreateGenre(String name) {
    return genreJpaRepository.save(
        new GenreJpaEntity(UUID.randomUUID(), name + "-" + UUID.randomUUID()));
  }

  private void seedCastCredit(UUID movieId, UUID personId, String character, int billingOrder) {
    seedCastCreditWithId(movieId, personId, character, billingOrder);
  }

  private UUID seedCastCreditWithId(
      UUID movieId, UUID personId, String character, int billingOrder) {
    UUID creditId = UUID.randomUUID();
    com.acme.catalog.credits.adapters.out.persistence.PersonJpaEntity person =
        creditsPersonJpaRepository.findById(personId).orElseThrow();
    creditJpaRepository.save(
        CreditJpaEntity.cast(creditId, movieId, person, character, billingOrder));
    return creditId;
  }

  private void seedCrewCredit(UUID movieId, UUID personId, String department, String job) {
    seedCrewCreditWithId(movieId, personId, department, job);
  }

  private UUID seedCrewCreditWithId(UUID movieId, UUID personId, String department, String job) {
    UUID creditId = UUID.randomUUID();
    com.acme.catalog.credits.adapters.out.persistence.PersonJpaEntity person =
        creditsPersonJpaRepository.findById(personId).orElseThrow();
    creditJpaRepository.save(CreditJpaEntity.crew(creditId, movieId, person, department, job));
    return creditId;
  }
}
