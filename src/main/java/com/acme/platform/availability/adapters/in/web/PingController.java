package com.acme.platform.availability.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.generated.api.HealthApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.PingData;
import com.acme.generated.model.PingEnvelope;
import com.acme.generated.model.PingLinks;
import com.acme.platform.availability.application.port.in.CheckAvailabilityUseCase;
import com.acme.platform.web.ResponseMetaFactory;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public availability check ({@code GET /ping}). Content-Type is always {@code
 * application/json}, never negotiated to {@code application/problem+json}, even when the request's
 * {@code Accept} names only the problem media type: the mapping's {@code produces} check still
 * yields {@code 406} for a genuinely unsatisfiable {@code Accept} (design D3).
 */
@RestController
public class PingController implements HealthApi {

  private final CheckAvailabilityUseCase checkAvailabilityUseCase;
  private final ResponseMetaFactory responseMetaFactory;

  public PingController(
      CheckAvailabilityUseCase checkAvailabilityUseCase, ResponseMetaFactory responseMetaFactory) {
    this.checkAvailabilityUseCase = checkAvailabilityUseCase;
    this.responseMetaFactory = responseMetaFactory;
  }

  @Override
  public ResponseEntity<PingEnvelope> ping() {
    checkAvailabilityUseCase.checkAvailability();

    URI self = linkTo(methodOn(HealthApi.class).ping()).toUri();
    PingLinks links = new PingLinks(new Link(self));
    PingData data = new PingData(PingData.StatusEnum.UP, links);
    PingEnvelope envelope = new PingEnvelope(data, responseMetaFactory.create());

    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(envelope);
  }
}
