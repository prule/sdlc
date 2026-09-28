package com.acme.platform.web;

import com.acme.generated.model.Meta;
import com.acme.generated.model.Pagination;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code meta} member every success envelope carries, from the clock and the current
 * correlation id.
 */
@Component
public class ResponseMetaFactory {

  private final Clock clock;

  public ResponseMetaFactory(Clock clock) {
    this.clock = clock;
  }

  public Meta create() {
    OffsetDateTime timestamp = OffsetDateTime.now(clock);
    UUID correlationId = CorrelationId.current().map(UUID::fromString).orElseGet(UUID::randomUUID);
    return new Meta(timestamp, correlationId);
  }

  /** The {@code meta} member for a collection response, carrying {@code pagination} (design D6). */
  public Meta create(Pagination pagination) {
    return create().pagination(pagination);
  }
}
