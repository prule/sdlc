package com.acme.platform.health.adapters.in.web;

import com.acme.common.web.CorrelationId;
import com.acme.generated.api.HealthApi;
import com.acme.generated.model.Ping200Response;
import com.acme.generated.model.Ping200ResponseData;
import com.acme.generated.model.Ping200ResponseMeta;
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
  public ResponseEntity<Ping200Response> ping(UUID xCorrelationId) {
    PingStatus status = pingUseCase.ping();
    String correlationId = CorrelationId.current();

    Ping200ResponseData data =
        new Ping200ResponseData(
            Ping200ResponseData.StatusEnum.fromValue(status.status()),
            status.timestamp().atOffset(ZoneOffset.UTC));

    Ping200ResponseMeta meta =
        new Ping200ResponseMeta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new Ping200Response(data, meta));
  }
}
