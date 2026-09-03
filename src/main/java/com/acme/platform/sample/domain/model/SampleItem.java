package com.acme.platform.sample.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A trivial, demonstrative sample item — id + label. Exists solely to prove the HAL pagination link
 * convention (see {@code openspec/changes/adopt-hal-hypermedia}); not a real catalog entity.
 */
public record SampleItem(UUID id, String label) {

  public SampleItem {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(label, "label must not be null");
  }
}
