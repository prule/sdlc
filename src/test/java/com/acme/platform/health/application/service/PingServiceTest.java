package com.acme.platform.health.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.platform.health.domain.model.PingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PingServiceTest {

  @Test
  void ping_returnsOkWithTheCurrentTimeFromTheInjectedClock() {
    Instant fixedInstant = Instant.parse("2026-06-15T10:30:00Z");
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    PingService service = new PingService(fixedClock);

    PingStatus status = service.ping();

    assertThat(status.status()).isEqualTo("ok");
    assertThat(status.timestamp()).isEqualTo(fixedInstant);
  }
}
