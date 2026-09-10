package com.acme.catalog.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.acme.catalog.people.application.port.out.LoadPersonPort;
import com.acme.catalog.people.domain.model.Person;
import com.acme.common.error.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit test for {@link GetPersonDetailService}, with {@link LoadPersonPort} mocked. */
class GetPersonDetailServiceTest {

  private final LoadPersonPort loadPersonPort = mock(LoadPersonPort.class);
  private final GetPersonDetailService service = new GetPersonDetailService(loadPersonPort);

  @Test
  void getPersonDetail_whenPortReturnsAPerson_returnsIt() {
    UUID id = UUID.randomUUID();
    Person person = new Person(id, "Ava Solano");
    given(loadPersonPort.loadPerson(id)).willReturn(Optional.of(person));

    Person result = service.getPersonDetail(id);

    assertThat(result).isEqualTo(person);
  }

  @Test
  void
      getPersonDetail_whenPortReturnsEmpty_throwsResourceNotFoundExceptionWithPersonNotFoundCode() {
    UUID id = UUID.randomUUID();
    given(loadPersonPort.loadPerson(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPersonDetail(id))
        .isInstanceOf(ResourceNotFoundException.class)
        .satisfies(
            e -> assertThat(((ResourceNotFoundException) e).code()).isEqualTo("PERSON_NOT_FOUND"));
  }
}
