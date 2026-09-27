package com.acme.platform.availability.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.platform.availability.domain.model.Availability;
import com.acme.platform.availability.domain.model.AvailabilityStatus;
import org.junit.jupiter.api.Test;

class AvailabilityServiceTest {

  private final AvailabilityService service = new AvailabilityService();

  @Test
  void alwaysAnswersUpWithNoSpringContext() {
    Availability availability = service.checkAvailability();

    assertThat(availability).isEqualTo(new Availability(AvailabilityStatus.UP));
  }
}
