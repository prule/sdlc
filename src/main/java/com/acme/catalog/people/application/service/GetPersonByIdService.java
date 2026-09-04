package com.acme.catalog.people.application.service;

import com.acme.catalog.people.application.port.in.GetPersonByIdUseCase;
import com.acme.catalog.people.application.port.out.LoadPersonByIdPort;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/** Implements the get-person-by-id use case. Not found is a domain-level failure, not a 500. */
@Service
public class GetPersonByIdService implements GetPersonByIdUseCase {

  private final LoadPersonByIdPort loadPersonByIdPort;

  public GetPersonByIdService(LoadPersonByIdPort loadPersonByIdPort) {
    this.loadPersonByIdPort = loadPersonByIdPort;
  }

  @Override
  public Person getPersonById(PersonId id) {
    return loadPersonByIdPort
        .load(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "PERSON_NOT_FOUND", "No person found for id: " + id.value()));
  }
}
