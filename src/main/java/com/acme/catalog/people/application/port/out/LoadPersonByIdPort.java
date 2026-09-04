package com.acme.catalog.people.application.port.out;

import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import java.util.Optional;

/** Outbound port: load a Person by their stable opaque id, from wherever it is persisted. */
public interface LoadPersonByIdPort {

  Optional<Person> load(PersonId id);
}
