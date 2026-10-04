package com.acme.platform.availability.application.service;

import com.acme.platform.availability.application.port.in.CheckAvailabilityUseCase;
import com.acme.platform.availability.domain.model.Availability;
import com.acme.platform.availability.domain.model.AvailabilityStatus;
import org.springframework.stereotype.Service;

/**
 * Answers UP whenever this process is running and able to handle HTTP requests. Liveness only, no
 * outbound port: it never reaches the catalog data (UC-000, Gate 1 A1).
 */
@Service
public class AvailabilityService implements CheckAvailabilityUseCase {

  @Override
  public Availability checkAvailability() {
    return new Availability(AvailabilityStatus.UP);
  }
}
