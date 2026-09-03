package com.acme.platform.sample.adapters.out.inmemory;

import com.acme.platform.sample.application.port.out.LoadSampleItemsPort;
import com.acme.platform.sample.domain.model.SampleItem;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Static, in-memory source of demonstrative sample items — no persistence, no Flyway migration.
 * Exists solely to prove the HAL pagination-link convention (see {@code
 * openspec/changes/adopt-hal-hypermedia}); a real catalog capability would replace this with a
 * persistence adapter.
 */
@Component
public class InMemorySampleItemsAdapter implements LoadSampleItemsPort {

  /** Fixed, deterministic ids so tests can assert on stable content across pages. */
  private static final List<SampleItem> SAMPLE_ITEMS =
      IntStream.rangeClosed(1, 25)
          .mapToObj(
              i ->
                  new SampleItem(
                      UUID.nameUUIDFromBytes(("sample-" + i).getBytes()), "Sample item " + i))
          .toList();

  @Override
  public List<SampleItem> findAll() {
    return SAMPLE_ITEMS;
  }
}
