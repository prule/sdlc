package com.acme.catalog.people.application.port.out;

import com.acme.catalog.people.domain.model.Person;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: load one person by their stable identifier. */
public interface LoadPersonPort {

  /**
   * Loads the person identified by {@code personId}.
   *
   * @return {@link Optional#empty()} only when no person matches {@code personId}
   */
  Optional<Person> loadPerson(UUID personId);
}
