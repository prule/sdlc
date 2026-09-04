package com.acme.catalog.people.application.port.in;

import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;

/** Inbound port: retrieve a single Person's detail by their stable opaque id. */
public interface GetPersonByIdUseCase {

  /**
   * @throws com.acme.common.error.ResourceNotFoundException if no Person exists for {@code id}
   */
  Person getPersonById(PersonId id);
}
