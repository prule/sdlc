package com.acme.platform.availability.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AvailabilityTest {

  @Test
  void rejectsANullStatus() {
    assertThatThrownBy(() -> new Availability(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("status");
  }

  @Test
  void holdsTheGivenStatus() {
    Availability availability = new Availability(AvailabilityStatus.UP);

    assertThat(availability.status()).isEqualTo(AvailabilityStatus.UP);
  }
}
