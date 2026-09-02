package com.acme.platform.sample.application.service;

import com.acme.platform.sample.application.port.in.ListSamplesUseCase;
import com.acme.platform.sample.application.port.out.LoadSampleItemsPort;
import com.acme.platform.sample.domain.model.SampleItem;
import com.acme.platform.sample.domain.model.SamplePage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Implements the list-samples use case. Pages a static/in-memory data set; a page index beyond the
 * last page yields an empty item list, not an error (see {@code
 * openspec/changes/adopt-hal-hypermedia}, D5b).
 */
@Service
public class ListSamplesService implements ListSamplesUseCase {

  private final LoadSampleItemsPort loadSampleItemsPort;

  public ListSamplesService(LoadSampleItemsPort loadSampleItemsPort) {
    this.loadSampleItemsPort = loadSampleItemsPort;
  }

  @Override
  public SamplePage listSamples(int page, int size) {
    List<SampleItem> all = loadSampleItemsPort.findAll();
    long totalElements = all.size();
    int totalPages = (int) Math.ceil((double) totalElements / size);

    int fromIndex = Math.min(page * size, all.size());
    int toIndex = Math.min(fromIndex + size, all.size());
    List<SampleItem> pageItems = all.subList(fromIndex, toIndex);

    return new SamplePage(pageItems, page, size, totalElements, totalPages);
  }
}
