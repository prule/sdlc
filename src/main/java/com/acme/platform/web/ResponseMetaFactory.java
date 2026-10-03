package com.acme.platform.web;

import com.acme.generated.model.Meta;
import com.acme.generated.model.Pagination;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code meta} member every success envelope carries, from the clock and the current
 * correlation id. Only a paged list's {@code meta} carries {@code pagination}.
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

  /** The {@code meta} of a paged list: as {@link #create()}, plus the paging counts. */
  public Meta create(Pagination pagination) {
    return create().pagination(pagination);
  }
}
