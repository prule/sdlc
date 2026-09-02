package com.acme.platform.sample.application.port.out;

import com.acme.platform.sample.domain.model.SampleItem;
import java.util.List;

/** Outbound port: load the full, ordered set of demonstrative sample items. */
public interface LoadSampleItemsPort {

  List<SampleItem> findAll();
}
