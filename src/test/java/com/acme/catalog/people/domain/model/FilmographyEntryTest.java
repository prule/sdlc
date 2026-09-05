package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.catalog.movies.domain.model.MovieId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilmographyEntryTest {

  @Test
  void actingCapacity_constructor_createsAValidActingCapacity() {
    ActingCapacity capacity = new ActingCapacity("Neo", 1);

    assertThat(capacity.character()).isEqualTo("Neo");
    assertThat(capacity.billingOrder()).isEqualTo(1);
    assertThat((FilmographyCapacity) capacity).isInstanceOf(FilmographyCapacity.class);
  }

  @Test
  void actingCapacity_constructor_rejectsANonPositiveBillingOrderOrBlankCharacter() {
    assertThatThrownBy(() -> new ActingCapacity("Neo", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("billingOrder");
    assertThatThrownBy(() -> new ActingCapacity(" ", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("character");
  }

  @Test
  void nonActingCapacity_constructor_createsAValidNonActingCapacity() {
    NonActingCapacity capacity = new NonActingCapacity("Directing", "Director");

    assertThat(capacity.department()).isEqualTo("Directing");
    assertThat(capacity.job()).isEqualTo("Director");
  }

  @Test
  void nonActingCapacity_constructor_rejectsABlankDepartmentOrJob() {
    assertThatThrownBy(() -> new NonActingCapacity(" ", "Director"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("department");
    assertThatThrownBy(() -> new NonActingCapacity("Directing", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("job");
  }

  @Test
  void actingAndNonActingCapacities_areSeparateSealedSubtypes() {
    FilmographyCapacity acting = new ActingCapacity("Neo", 1);
    FilmographyCapacity nonActing = new NonActingCapacity("Directing", "Director");

    assertThat(acting).isInstanceOf(ActingCapacity.class).isNotInstanceOf(NonActingCapacity.class);
    assertThat(nonActing)
        .isInstanceOf(NonActingCapacity.class)
        .isNotInstanceOf(ActingCapacity.class);
  }

  @Test
  void constructor_rejectsNullMovieIdOrBlankTitleOrNullCapacityOrCreditId() {
    assertThatThrownBy(
            () ->
                new FilmographyEntry(
                    null,
                    "Title",
                    2020,
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    new ActingCapacity("A", 1),
                    UUID.randomUUID()))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                new FilmographyEntry(
                    new MovieId(UUID.randomUUID()),
                    " ",
                    2020,
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    new ActingCapacity("A", 1),
                    UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
