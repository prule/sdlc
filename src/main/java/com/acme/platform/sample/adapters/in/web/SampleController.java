package com.acme.platform.sample.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.common.web.CorrelationId;
import com.acme.generated.api.SampleApi;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.Pagination;
import com.acme.generated.model.SampleCollectionData;
import com.acme.generated.model.SampleCollectionDataEmbedded;
import com.acme.generated.model.SampleCollectionEnvelope;
import com.acme.generated.model.SampleCollectionLinks;
import com.acme.generated.model.SampleItem;
import com.acme.generated.model.SampleItemLinks;
import com.acme.platform.sample.application.port.in.ListSamplesUseCase;
import com.acme.platform.sample.domain.model.SamplePage;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Implements the generated {@link SampleApi}. Thin: calls the {@link ListSamplesUseCase}, maps the
 * domain page to the generated envelope DTO, and builds {@code _embedded}/{@code _links} via {@link
 * org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. This is a demonstrative, throwaway
 * endpoint proving the HAL pagination-link convention — not a real catalog capability (see {@code
 * openspec/changes/adopt-hal-hypermedia}). {@code @Validated} enforces the generated
 * {@code @Min}/{@code @Max} query-param constraints, since the interface-level {@code @Validated}
 * on {@link SampleApi} is not, by itself, honoured on the implementing bean.
 */
@RestController
@Validated
public class SampleController implements SampleApi {

  private final ListSamplesUseCase listSamplesUseCase;

  public SampleController(ListSamplesUseCase listSamplesUseCase) {
    this.listSamplesUseCase = listSamplesUseCase;
  }

  @Override
  public ResponseEntity<SampleCollectionEnvelope> listSamples(
      UUID xCorrelationId, Integer page, Integer size) {
    SamplePage samplePage = listSamplesUseCase.listSamples(page, size);
    String correlationId = CorrelationId.current();

    List<SampleItem> items =
        samplePage.items().stream()
            .map(
                item ->
                    new SampleItem(item.id(), item.label())
                        .links(new SampleItemLinks(itemSelfLink(item.id()))))
            .toList();

    SampleCollectionData data =
        new SampleCollectionData(
            new SampleCollectionDataEmbedded(items), collectionLinks(samplePage));

    Meta meta =
        new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId))
            .pagination(
                new Pagination(
                    samplePage.page(),
                    samplePage.size(),
                    samplePage.totalElements(),
                    samplePage.totalPages()));

    return ResponseEntity.ok(new SampleCollectionEnvelope(data, meta));
  }

  private static Link itemSelfLink(UUID id) {
    URI collectionUri = linkTo(methodOn(SampleApi.class).listSamples(null, null, null)).toUri();
    URI href =
        UriComponentsBuilder.fromUri(collectionUri).pathSegment(id.toString()).build().toUri();
    return new Link(href);
  }

  private static SampleCollectionLinks collectionLinks(SamplePage samplePage) {
    int page = samplePage.page();
    int size = samplePage.size();
    int totalPages = samplePage.totalPages();

    SampleCollectionLinks links = new SampleCollectionLinks(pageLink(page, size));
    links.first(pageLink(0, size));
    if (totalPages > 0) {
      links.last(pageLink(totalPages - 1, size));
    } else {
      links.last(pageLink(0, size));
    }
    if (page > 0) {
      links.prev(pageLink(page - 1, size));
    }
    if (page < totalPages - 1) {
      links.next(pageLink(page + 1, size));
    }
    return links;
  }

  private static Link pageLink(int page, int size) {
    return new Link(linkTo(methodOn(SampleApi.class).listSamples(null, page, size)).toUri());
  }
}
