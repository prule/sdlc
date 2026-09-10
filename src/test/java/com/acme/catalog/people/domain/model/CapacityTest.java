package com.acme.catalog.people.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for the sealed {@link Capacity} interface's two shapes. */
class CapacityTest {

  @Test
  void acting_of_withCharacter_carriesItAsPresentAndTypeIsActing() {
    Capacity.Acting acting = Capacity.Acting.of("Dana Whitfield");

    assertThat(acting.character()).contains("Dana Whitfield");
    assertThat(acting.type()).isEqualTo(Capacity.Type.ACTING);
  }

  @Test
  void acting_of_withNullCharacter_carriesItAsAbsent() {
    Capacity.Acting acting = Capacity.Acting.of(null);

    assertThat(acting.character()).isEmpty();
  }

  @Test
  void nonActing_withDepartmentAndJob_carriesThemAndTypeIsNonActing() {
    Capacity.NonActing nonActing = new Capacity.NonActing("Directing", "Director");

    assertThat(nonActing.department()).isEqualTo("Directing");
    assertThat(nonActing.job()).isEqualTo("Director");
    assertThat(nonActing.type()).isEqualTo(Capacity.Type.NON_ACTING);
  }

  @Test
  void nonActing_withBlankDepartment_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new Capacity.NonActing("  ", "Director"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("department");
  }

  @Test
  void nonActing_withBlankJob_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new Capacity.NonActing("Directing", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("job");
  }
}
