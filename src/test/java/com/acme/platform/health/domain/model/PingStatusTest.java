package com.acme.platform.health.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PingStatusTest {

  @Test
  void ok_createsAStatusWithTheGivenTimestamp() {
    Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

    PingStatus status = PingStatus.ok(timestamp);

    assertThat(status.status()).isEqualTo("ok");
    assertThat(status.timestamp()).isEqualTo(timestamp);
  }

  @Test
  void constructor_rejectsAStatusOtherThanOk() {
    Instant timestamp = Instant.now();

    assertThatThrownBy(() -> new PingStatus("degraded", timestamp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("degraded");
  }

  @Test
  void constructor_rejectsANullTimestamp() {
    assertThatThrownBy(() -> new PingStatus("ok", null)).isInstanceOf(NullPointerException.class);
  }
}
