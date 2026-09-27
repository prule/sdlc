package com.acme.platform.availability.application.port.in;

import com.acme.platform.availability.domain.model.Availability;

/** Inbound port: check whether the service is available (liveness only). */
public interface CheckAvailabilityUseCase {

  Availability checkAvailability();
}
