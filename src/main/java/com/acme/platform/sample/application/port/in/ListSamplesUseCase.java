package com.acme.platform.sample.application.port.in;

import com.acme.platform.sample.domain.model.SamplePage;

/** Inbound port: retrieve a page of demonstrative sample items. */
public interface ListSamplesUseCase {

  /**
   * @param page zero-based page index
   * @param size page size
   * @return the requested page of sample items plus page metadata
   */
  SamplePage listSamples(int page, int size);
}
