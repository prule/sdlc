package com.acme.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.generated.model.Meta;
import com.acme.generated.model.Pagination;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ResponseMetaFactoryTest {

  private final ResponseMetaFactory factory =
      new ResponseMetaFactory(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void aSingleResourceMetaHasNoPagination() {
    assertThat(factory.create().getPagination()).isNull();
  }

  @Test
  void aPagedListMetaCarriesItsCounts() {
    Pagination pagination = new Pagination(1, 20, 45L, 3);

    Meta meta = factory.create(pagination);

    assertThat(meta.getPagination()).isEqualTo(pagination);
    assertThat(meta.getTimestamp()).isNotNull();
    assertThat(meta.getCorrelationId()).isNotNull();
  }
}
