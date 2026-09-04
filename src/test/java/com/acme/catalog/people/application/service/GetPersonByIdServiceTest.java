package com.acme.catalog.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.people.application.port.out.LoadPersonByIdPort;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.error.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetPersonByIdServiceTest {

  private final LoadPersonByIdPort loadPersonByIdPort = mock(LoadPersonByIdPort.class);
  private final GetPersonByIdService service = new GetPersonByIdService(loadPersonByIdPort);

  @Test
  void getPersonById_returnsThePersonWhenFound() {
    PersonId id = new PersonId(UUID.randomUUID());
    Person person = new Person(id, "Keanu Reeves");
    given(loadPersonByIdPort.load(id)).willReturn(Optional.of(person));

    Person result = service.getPersonById(id);

    assertThat(result).isEqualTo(person);
  }

  @Test
  void getPersonById_throwsResourceNotFoundWhenMissing() {
    PersonId id = new PersonId(UUID.randomUUID());
    given(loadPersonByIdPort.load(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPersonById(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("PERSON_NOT_FOUND"));
  }
}
