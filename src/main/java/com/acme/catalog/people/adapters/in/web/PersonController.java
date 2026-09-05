package com.acme.catalog.people.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.movies.domain.model.Genre;
import com.acme.catalog.movies.domain.model.Rating;
import com.acme.catalog.people.application.port.in.GetPersonByIdUseCase;
import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.domain.model.ActingCapacity;
import com.acme.catalog.people.domain.model.FilmographyCapacity;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyPage;
import com.acme.catalog.people.domain.model.NonActingCapacity;
import com.acme.catalog.people.domain.model.Person;
import com.acme.catalog.people.domain.model.PersonId;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.MoviesApi;
import com.acme.generated.api.PeopleApi;
import com.acme.generated.model.FilmographyItem;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieSummaryLinks;
import com.acme.generated.model.Pagination;
import com.acme.generated.model.PersonDetailEnvelope;
import com.acme.generated.model.PersonFilmographyData;
import com.acme.generated.model.PersonFilmographyDataEmbedded;
import com.acme.generated.model.PersonFilmographyEnvelope;
import com.acme.generated.model.PersonFilmographyLinks;
import com.acme.generated.model.PersonLinks;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link PeopleApi}. Thin: calls {@link GetPersonByIdUseCase}/{@link
 * GetPersonFilmographyUseCase}, maps domain types to the generated DTOs, and assembles HAL links
 * via {@link org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. No business logic lives
 * here; no HATEOAS import in domain/application. {@code @Validated} enforces the generated
 * {@code @Min}/{@code @Max} query-param constraints on {@code getPersonFilmography} (same rationale
 * as {@code MovieController}).
 */
@RestController
@Validated
public class PersonController implements PeopleApi {

  private final GetPersonByIdUseCase getPersonByIdUseCase;
  private final GetPersonFilmographyUseCase getPersonFilmographyUseCase;

  public PersonController(
      GetPersonByIdUseCase getPersonByIdUseCase,
      GetPersonFilmographyUseCase getPersonFilmographyUseCase) {
    this.getPersonByIdUseCase = getPersonByIdUseCase;
    this.getPersonFilmographyUseCase = getPersonFilmographyUseCase;
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
    // Pure link assembly — no port call, no additional SQL statement (D12, mirrors D-F on movies).
    // .toUri() (not .withSelfRel().getHref()) so the unset optional page/size query params are
    // omitted rather than left as unresolved {?page,size} URI-template placeholders.
    URI creditsHref =
        linkTo(methodOn(PeopleApi.class).getPersonFilmography(id, null, null, null)).toUri();

    com.acme.generated.model.Person data =
        new com.acme.generated.model.Person(
            person.id().value(),
            person.name(),
            new PersonLinks(new Link(selfHref), new Link(creditsHref)));

    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new PersonDetailEnvelope(data, meta));
  }

  @Override
  public ResponseEntity<PersonFilmographyEnvelope> getPersonFilmography(
      UUID id, UUID xCorrelationId, Integer page, Integer size) {
    FilmographyPage filmographyPage =
        getPersonFilmographyUseCase.getPersonFilmography(new PersonId(id), page, size);
    String correlationId = CorrelationId.current();

    List<FilmographyItem> items =
        filmographyPage.items().stream().map(PersonController::toFilmographyItem).toList();

    PersonFilmographyData data =
        new PersonFilmographyData(
            new PersonFilmographyDataEmbedded(items), collectionLinks(id, filmographyPage));

    Meta meta =
        new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId))
            .pagination(
                new Pagination(
                    filmographyPage.page(),
                    filmographyPage.size(),
                    filmographyPage.totalElements(),
                    filmographyPage.totalPages()));

    return ResponseEntity.ok(new PersonFilmographyEnvelope(data, meta));
  }

  private static FilmographyItem toFilmographyItem(FilmographyEntry entry) {
    UUID movieId = entry.movieId().value();
    List<String> genres = entry.genres().stream().map(Genre::label).toList();

    FilmographyItem item =
        new FilmographyItem(
                movieId,
                entry.movieTitle(),
                entry.releaseYear(),
                genres,
                new MovieSummaryLinks(movieSelfLink(movieId)),
                toGeneratedCapacity(entry.capacity()))
            .runtimeMinutes(entry.runtimeMinutes().orElse(null))
            .rating(entry.rating().map(Rating::score).orElse(null));
    return item;
  }

  private static Link movieSelfLink(UUID movieId) {
    URI href =
        URI.create(
            linkTo(methodOn(MoviesApi.class).getMovieById(movieId, null)).withSelfRel().getHref());
    return new Link(href);
  }

  private static com.acme.generated.model.FilmographyCapacity toGeneratedCapacity(
      FilmographyCapacity capacity) {
    return switch (capacity) {
      case ActingCapacity acting ->
          new com.acme.generated.model.ActingCapacity(
              "acting", acting.character(), acting.billingOrder());
      case NonActingCapacity nonActing ->
          new com.acme.generated.model.NonActingCapacity(
              "nonActing", nonActing.department(), nonActing.job());
    };
  }

  private static PersonFilmographyLinks collectionLinks(UUID id, FilmographyPage filmographyPage) {
    int page = filmographyPage.page();
    int size = filmographyPage.size();
    int totalPages = filmographyPage.totalPages();

    PersonFilmographyLinks links = new PersonFilmographyLinks(pageLink(id, page, size));
    links.first(pageLink(id, 0, size));
    if (totalPages > 0) {
      links.last(pageLink(id, totalPages - 1, size));
    } else {
      links.last(pageLink(id, 0, size));
    }
    if (page > 0) {
      links.prev(pageLink(id, page - 1, size));
    }
    if (page < totalPages - 1) {
      links.next(pageLink(id, page + 1, size));
    }
    return links;
  }

  private static Link pageLink(UUID id, int page, int size) {
    return new Link(
        linkTo(methodOn(PeopleApi.class).getPersonFilmography(id, null, page, size)).toUri());
  }
}
