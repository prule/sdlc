package com.acme.catalog.people.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.people.application.port.in.GetPersonByIdUseCase;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.PeopleApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.PersonDetailEnvelope;
import com.acme.generated.model.PersonLinks;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link PeopleApi}. Thin: calls {@link GetPersonByIdUseCase}, maps the
 * domain {@link Person} to the generated DTO, and assembles the HAL {@code self} link via {@link
 * org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. No business logic lives here; no
 * HATEOAS import in domain/application.
 */
@RestController
public class PersonController implements PeopleApi {

  private final GetPersonByIdUseCase getPersonByIdUseCase;

  public PersonController(GetPersonByIdUseCase getPersonByIdUseCase) {
    this.getPersonByIdUseCase = getPersonByIdUseCase;
  }

  @Override
  public ResponseEntity<PersonDetailEnvelope> getPersonById(UUID id, UUID xCorrelationId) {
    Person person = getPersonByIdUseCase.getPersonById(new PersonId(id));
    String correlationId = CorrelationId.current();

    URI selfHref =
        URI.create(
            linkTo(methodOn(PeopleApi.class).getPersonById(id, xCorrelationId))
                .withSelfRel()
                .getHref());

    com.acme.generated.model.Person data =
        new com.acme.generated.model.Person(
            person.id().value(), person.name(), new PersonLinks(new Link(selfHref)));

    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new PersonDetailEnvelope(data, meta));
  }
}
