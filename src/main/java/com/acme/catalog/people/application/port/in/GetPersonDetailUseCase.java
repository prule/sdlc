package com.acme.catalog.people.application.port.in;

import com.acme.catalog.people.domain.model.Person;
import java.util.UUID;

/** Inbound port: retrieve one person's core details by their stable identifier. */
public interface GetPersonDetailUseCase {

  /**
   * Returns the person identified by {@code personId}.
   *
   * @throws com.acme.common.error.ResourceNotFoundException if no person matches {@code personId}
   */
  Person getPersonDetail(UUID personId);
}
