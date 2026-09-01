package com.acme.platform.health.application.service;

import com.acme.platform.health.application.port.in.PingUseCase;
import com.acme.platform.health.domain.model.PingStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Implements the ping use case. Has no outbound ports; ping touches no persistence. */
@Service
public class PingService implements PingUseCase {

  private final Clock clock;

  public PingService(Clock clock) {
    this.clock = clock;
  }

  @Override
  public PingStatus ping() {
    return PingStatus.ok(Instant.now(clock));
  }
}
