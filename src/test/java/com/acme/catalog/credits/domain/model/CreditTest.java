package com.acme.catalog.credits.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreditTest {

  private static Person person() {
    return new Person(new PersonId(UUID.randomUUID()), "Keanu Reeves");
  }

  @Test
  void castCredit_constructor_createsAValidCastCredit() {
    Person person = person();

    CastCredit castCredit = new CastCredit(person, "Neo", 1);

    assertThat(castCredit.person()).isEqualTo(person);
    assertThat(castCredit.character()).isEqualTo("Neo");
    assertThat(castCredit.billingOrder()).isEqualTo(1);
    assertThat((Credit) castCredit).isInstanceOf(Credit.class);
  }

  @Test
  void castCredit_constructor_rejectsANonPositiveBillingOrder() {
    Person person = person();

    assertThatThrownBy(() -> new CastCredit(person, "Neo", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("billingOrder");
    assertThatThrownBy(() -> new CastCredit(person, "Neo", -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void castCredit_constructor_rejectsABlankCharacter() {
    Person person = person();

    assertThatThrownBy(() -> new CastCredit(person, " ", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("character");
  }

  @Test
  void crewCredit_constructor_createsAValidCrewCredit() {
    Person person = person();

    CrewCredit crewCredit = new CrewCredit(person, "Directing", "Director");

    assertThat(crewCredit.person()).isEqualTo(person);
    assertThat(crewCredit.department()).isEqualTo("Directing");
    assertThat(crewCredit.job()).isEqualTo("Director");
  }

  @Test
  void crewCredit_constructor_rejectsABlankDepartmentOrJob() {
    Person person = person();

    assertThatThrownBy(() -> new CrewCredit(person, " ", "Director"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("department");
    assertThatThrownBy(() -> new CrewCredit(person, "Directing", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("job");
  }

  @Test
  void castAndCrewCredits_areSeparateSealedSubtypes() {
    Credit cast = new CastCredit(person(), "Neo", 1);
    Credit crew = new CrewCredit(person(), "Directing", "Director");

    assertThat(cast).isInstanceOf(CastCredit.class).isNotInstanceOf(CrewCredit.class);
    assertThat(crew).isInstanceOf(CrewCredit.class).isNotInstanceOf(CastCredit.class);
  }

  @Test
  void castCredit_equality_isValueBased() {
    Person person = person();

    assertThat(new CastCredit(person, "Neo", 1)).isEqualTo(new CastCredit(person, "Neo", 1));
  }

  @Test
  void movieCredits_constructor_defensivelyCopiesLists() {
    var mutableCast = new java.util.ArrayList<>(List.of(new CastCredit(person(), "Neo", 1)));
    MovieCredits movieCredits = new MovieCredits(mutableCast, List.of());
    mutableCast.clear();

    assertThat(movieCredits.cast()).hasSize(1);
  }

  @Test
  void movieCredits_allowsEmptyCastAndCrew() {
    MovieCredits movieCredits = new MovieCredits(List.of(), List.of());

    assertThat(movieCredits.cast()).isEmpty();
    assertThat(movieCredits.crew()).isEmpty();
  }
}
