package com.acme.platform.health.adapters.in.web;

import com.acme.common.web.CorrelationId;
import com.acme.generated.api.HealthApi;
import com.acme.generated.model.Meta;
import com.acme.generated.model.PingData;
import com.acme.generated.model.PingEnvelope;
import com.acme.platform.health.application.port.in.PingUseCase;
import com.acme.platform.health.domain.model.PingStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link HealthApi}. Thin: calls the {@link PingUseCase}, reads the
 * request's correlation id, and maps the domain result to the generated envelope DTO. No business
 * logic lives here.
 */
@RestController
public class PingController implements HealthApi {

  private final PingUseCase pingUseCase;

  public PingController(PingUseCase pingUseCase) {
    this.pingUseCase = pingUseCase;
  }

  @Override
  public ResponseEntity<PingEnvelope> ping(UUID xCorrelationId) {
    PingStatus status = pingUseCase.ping();
    String correlationId = CorrelationId.current();

    PingData data =
        new PingData(
            PingData.StatusEnum.fromValue(status.status()),
            status.timestamp().atOffset(ZoneOffset.UTC));

    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new PingEnvelope(data, meta));
  }
}
