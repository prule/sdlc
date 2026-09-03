package com.acme.platform.sample.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A page of {@link SampleItem}s plus page metadata (page/size/totalElements/totalPages). No
 * link/HAL concept here — that is a web-adapter concern (see {@code
 * standards/clean-architecture.md} and {@code openspec/changes/adopt-hal-hypermedia}).
 */
public record SamplePage(
    List<SampleItem> items, int page, int size, long totalElements, int totalPages) {

  public SamplePage {
    Objects.requireNonNull(items, "items must not be null");
    items = List.copyOf(items);
  }
}
