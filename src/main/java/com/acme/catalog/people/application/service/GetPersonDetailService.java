package com.acme.catalog.people.application.service;

import com.acme.catalog.people.application.port.in.GetPersonDetailUseCase;
import com.acme.catalog.people.application.port.out.LoadPersonPort;
import com.acme.catalog.people.domain.model.Person;
import com.acme.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Implements the get-person-detail use case. A well-formed identifier that matches no person is
 * reported as a {@link ResourceNotFoundException} with the stable code {@code PERSON_NOT_FOUND},
 * handled by the existing global exception handler — no bespoke exception, no new handler.
 */
@Service
public class GetPersonDetailService implements GetPersonDetailUseCase {

  private final LoadPersonPort loadPersonPort;

  public GetPersonDetailService(LoadPersonPort loadPersonPort) {
    this.loadPersonPort = loadPersonPort;
  }

  @Override
  public Person getPersonDetail(UUID personId) {
    return loadPersonPort
        .loadPerson(personId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "PERSON_NOT_FOUND", "No person found with id " + personId));
  }
}
