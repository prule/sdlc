package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link MovieCredits} aggregate's ordering rules (BR-5, BR-6, BR-8). */
class MovieCreditsTest {

  private static Person person(String name) {
    return new Person(UUID.randomUUID(), name);
  }

  @Test
  void of_ordersCastByBillingOrderAscendingRegardlessOfInputOrder() {
    Credit.Cast third = Credit.Cast.of(person("Third"), null, 3);
    Credit.Cast first = Credit.Cast.of(person("First"), "Lead", 1);
    Credit.Cast second = Credit.Cast.of(person("Second"), null, 2);

    MovieCredits credits = MovieCredits.of(List.of(third, first, second), List.of());

    assertThat(credits.cast()).containsExactly(first, second, third);
  }

  @Test
  void of_ordersCrewByDepartmentThenJobCaseInsensitively() {
    Credit.Crew soundComposer = new Credit.Crew(person("A"), "sound", "composer");
    Credit.Crew directingProducer = new Credit.Crew(person("B"), "Directing", "Producer");
    Credit.Crew directingDirector = new Credit.Crew(person("C"), "DIRECTING", "director");

    List<Credit.Crew> unordered = List.of(soundComposer, directingProducer, directingDirector);
    MovieCredits credits = MovieCredits.of(List.of(), unordered);

    assertThat(credits.crew()).containsExactly(directingDirector, directingProducer, soundComposer);
  }

  @Test
  void of_withEmptyCastAndCrew_isValid() {
    MovieCredits credits = MovieCredits.of(List.of(), List.of());

    assertThat(credits.cast()).isEmpty();
    assertThat(credits.crew()).isEmpty();
  }

  @Test
  void of_withOnlyCast_leavesCrewEmpty() {
    Credit.Cast cast = Credit.Cast.of(person("Solo"), "Lead", 1);

    MovieCredits credits = MovieCredits.of(List.of(cast), List.of());

    assertThat(credits.cast()).containsExactly(cast);
    assertThat(credits.crew()).isEmpty();
  }

  @Test
  void canonicalConstructor_storesImmutableCopies() {
    MovieCredits credits = new MovieCredits(List.of(), List.of());

    assertThat(credits.cast()).isEmpty();
    assertThat(credits.crew()).isEmpty();
  }
}
