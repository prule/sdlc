package com.acme.catalog.people.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.people.application.port.in.GetPersonDetailUseCase;
import com.acme.catalog.people.domain.model.Person;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.PeopleApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.PersonDetail;
import com.acme.generated.model.PersonDetailEnvelope;
import com.acme.generated.model.PersonLinks;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link PeopleApi}. Thin: calls the use case, maps the domain {@link
 * Person} to the generated DTOs, and builds HAL links via {@link
 * org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. Link assembly is web-adapter-only —
 * the domain and application layers know nothing about hypermedia.
 */
@RestController
public class PersonController implements PeopleApi {

  private final GetPersonDetailUseCase getPersonDetailUseCase;

  public PersonController(GetPersonDetailUseCase getPersonDetailUseCase) {
    this.getPersonDetailUseCase = getPersonDetailUseCase;
  }

  @Override
  public ResponseEntity<PersonDetailEnvelope> getPersonById(UUID id, UUID xCorrelationId) {
    Person person = getPersonDetailUseCase.getPersonDetail(id);
    String correlationId = CorrelationId.current();

    PersonDetail data = toPersonDetail(person);
    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new PersonDetailEnvelope(data, meta));
  }

  private static PersonDetail toPersonDetail(Person person) {
    PersonLinks links = new PersonLinks(selfLink(person.id()), creditsLink(person.id()));
    return new PersonDetail(person.id(), person.name(), links);
  }

  private static Link selfLink(UUID id) {
    return new Link(linkTo(methodOn(PeopleApi.class).getPersonById(id, null)).toUri());
  }

  private static Link creditsLink(UUID id) {
    return new Link(
        linkTo(methodOn(PeopleApi.class).getPersonById(id, null)).slash("credits").toUri());
  }
}
