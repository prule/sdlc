package com.acme.catalog.movies.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the sealed {@link Credit} interface's two shapes, {@link Credit.Cast} and {@link
 * Credit.Crew}.
 */
class CreditTest {

  private static final Person PERSON = new Person(UUID.randomUUID(), "Ava Solano");

  @Test
  void cast_of_withCharacter_carriesItAsPresent() {
    Credit.Cast cast = Credit.Cast.of(PERSON, "Dana Whitfield", 1);

    assertThat(cast.character()).contains("Dana Whitfield");
    assertThat(cast.billingOrder()).isEqualTo(1);
    assertThat(cast.person()).isEqualTo(PERSON);
  }

  @Test
  void cast_of_withNullCharacter_carriesItAsAbsent() {
    Credit.Cast cast = Credit.Cast.of(PERSON, null, 2);

    assertThat(cast.character()).isEmpty();
  }

  @Test
  void cast_withBillingOrderBelowOne_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> Credit.Cast.of(PERSON, "Dana Whitfield", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("billingOrder");
  }

  @Test
  void cast_withNullPerson_throwsNullPointerException() {
    assertThatThrownBy(() -> Credit.Cast.of(null, "Dana Whitfield", 1))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void crew_withDepartmentAndJob_isAccepted() {
    Credit.Crew crew = new Credit.Crew(PERSON, "Directing", "Director");

    assertThat(crew.department()).isEqualTo("Directing");
    assertThat(crew.job()).isEqualTo("Director");
    assertThat(crew.person()).isEqualTo(PERSON);
  }

  @Test
  void crew_withBlankDepartment_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new Credit.Crew(PERSON, "  ", "Director"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("department");
  }

  @Test
  void crew_withBlankJob_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new Credit.Crew(PERSON, "Directing", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("job");
  }

  @Test
  void crew_withNullPerson_throwsNullPointerException() {
    assertThatThrownBy(() -> new Credit.Crew(null, "Directing", "Director"))
        .isInstanceOf(NullPointerException.class);
  }
}
