package com.acme.platform.health.application.port.in;

import com.acme.platform.health.domain.model.PingStatus;

/** Inbound port: check that the service is alive. */
public interface PingUseCase {

  /** Returns the current liveness status of the service. */
  PingStatus ping();
}
