package com.acme.catalog.people.adapters.in.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.acme.catalog.people.application.port.in.GetPersonDetailUseCase;
import com.acme.catalog.people.application.port.in.GetPersonFilmographyUseCase;
import com.acme.catalog.people.domain.model.Capacity;
import com.acme.catalog.people.domain.model.Filmography;
import com.acme.catalog.people.domain.model.FilmographyCriteria;
import com.acme.catalog.people.domain.model.FilmographyEntry;
import com.acme.catalog.people.domain.model.FilmographyMovieSummary;
import com.acme.catalog.people.domain.model.FilmographyPageRequest;
import com.acme.catalog.people.domain.model.Person;
import com.acme.common.web.CorrelationId;
import com.acme.generated.api.PeopleApi;
import com.acme.generated.model.CapacityType;
import com.acme.generated.model.Genre;
import com.acme.generated.model.Link;
import com.acme.generated.model.Meta;
import com.acme.generated.model.MovieLinks;
import com.acme.generated.model.MovieSummary;
import com.acme.generated.model.Pagination;
import com.acme.generated.model.PersonDetail;
import com.acme.generated.model.PersonDetailEnvelope;
import com.acme.generated.model.PersonFilmographyData;
import com.acme.generated.model.PersonFilmographyDataEmbedded;
import com.acme.generated.model.PersonFilmographyEnvelope;
import com.acme.generated.model.PersonFilmographyLinks;
import com.acme.generated.model.PersonLinks;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link PeopleApi}. Thin: calls the use case, maps the domain {@link
 * Person}/{@link Filmography} to the generated DTOs, and builds HAL links via {@link
 * org.springframework.hateoas.server.mvc.WebMvcLinkBuilder}. Link assembly is web-adapter-only —
 * the domain and application layers know nothing about hypermedia. {@code @Validated} enforces the
 * generated {@code @Min}/{@code @Max} query-param constraints (see {@code
 * com.acme.catalog.movies.adapters.in.web.MovieController} for the same note).
 */
@RestController
@Validated
public class PersonController implements PeopleApi {

  private final GetPersonDetailUseCase getPersonDetailUseCase;
  private final GetPersonFilmographyUseCase getPersonFilmographyUseCase;

  public PersonController(
      GetPersonDetailUseCase getPersonDetailUseCase,
      GetPersonFilmographyUseCase getPersonFilmographyUseCase) {
    this.getPersonDetailUseCase = getPersonDetailUseCase;
    this.getPersonFilmographyUseCase = getPersonFilmographyUseCase;
  }

  @Override
  public ResponseEntity<PersonDetailEnvelope> getPersonById(UUID id, UUID xCorrelationId) {
    Person person = getPersonDetailUseCase.getPersonDetail(id);
    String correlationId = CorrelationId.current();

    PersonDetail data = toPersonDetail(person);
    Meta meta = new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId));

    return ResponseEntity.ok(new PersonDetailEnvelope(data, meta));
  }

  @Override
  public ResponseEntity<PersonFilmographyEnvelope> getPersonFilmography(
      UUID id,
      UUID xCorrelationId,
      Integer page,
      Integer size,
      CapacityType capacity,
      Integer releaseYearFrom,
      Integer releaseYearTo) {
    FilmographyCriteria criteria =
        FilmographyCriteria.of(toDomainCapacityType(capacity), releaseYearFrom, releaseYearTo);
    FilmographyPageRequest pageRequest = new FilmographyPageRequest(page, size);

    Filmography filmography = getPersonFilmographyUseCase.getFilmography(id, criteria, pageRequest);
    String correlationId = CorrelationId.current();

    List<com.acme.generated.model.FilmographyEntry> entries =
        filmography.content().stream().map(PersonController::toFilmographyEntry).toList();

    PersonFilmographyData data =
        new PersonFilmographyData(
            new PersonFilmographyDataEmbedded(entries),
            collectionLinks(id, filmography, capacity, releaseYearFrom, releaseYearTo));

    Meta meta =
        new Meta(OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(correlationId))
            .pagination(
                new Pagination(
                    filmography.page(),
                    filmography.size(),
                    filmography.totalElements(),
                    filmography.totalPages()));

    return ResponseEntity.ok(new PersonFilmographyEnvelope(data, meta));
  }

  private static PersonDetail toPersonDetail(Person person) {
    PersonLinks links = new PersonLinks(selfLink(person.id()), creditsLink(person.id()));
    return new PersonDetail(person.id(), person.name(), links);
  }

  private static com.acme.generated.model.FilmographyEntry toFilmographyEntry(
      FilmographyEntry entry) {
    return new com.acme.generated.model.FilmographyEntry(
        toMovieSummary(entry.movie()), toCapacity(entry.capacity()));
  }

  private static MovieSummary toMovieSummary(FilmographyMovieSummary movie) {
    MovieSummary summary =
        new MovieSummary(
            movie.id(),
            movie.title(),
            movie.releaseYear(),
            movie.genres().stream().map(genre -> Genre.valueOf(genre.name())).toList(),
            new MovieLinks(movieSelfLink(movie.id())));

    movie.runtimeMinutes().ifPresent(summary::setRuntimeMinutes);
    movie.rating().ifPresent(rating -> summary.setRating(rating.value()));

    return summary;
  }

  private static com.acme.generated.model.Capacity toCapacity(Capacity capacity) {
    if (capacity instanceof Capacity.Acting acting) {
      com.acme.generated.model.Capacity dto =
          new com.acme.generated.model.Capacity(CapacityType.ACTING);
      acting.character().ifPresent(dto::setCharacter);
      return dto;
    }
    Capacity.NonActing nonActing = (Capacity.NonActing) capacity;
    com.acme.generated.model.Capacity dto =
        new com.acme.generated.model.Capacity(CapacityType.NON_ACTING);
    dto.setDepartment(nonActing.department());
    dto.setJob(nonActing.job());
    return dto;
  }

  private static Capacity.Type toDomainCapacityType(CapacityType capacity) {
    if (capacity == null) {
      return null;
    }
    return capacity == CapacityType.ACTING ? Capacity.Type.ACTING : Capacity.Type.NON_ACTING;
  }

  private static Link selfLink(UUID id) {
    return new Link(linkTo(methodOn(PeopleApi.class).getPersonById(id, null)).toUri());
  }

  private static Link movieSelfLink(UUID movieId) {
    return new Link(
        linkTo(methodOn(com.acme.generated.api.MoviesApi.class).getMovieById(movieId, null))
            .toUri());
  }

  private static Link creditsLink(UUID id) {
    return new Link(
        linkTo(methodOn(PeopleApi.class).getPersonById(id, null)).slash("credits").toUri());
  }

  private static PersonFilmographyLinks collectionLinks(
      UUID personId,
      Filmography filmography,
      CapacityType capacity,
      Integer releaseYearFrom,
      Integer releaseYearTo) {
    int page = filmography.page();
    int size = filmography.size();
    int totalPages = filmography.totalPages();

    PersonFilmographyLinks links =
        new PersonFilmographyLinks(
            pageLink(personId, page, size, capacity, releaseYearFrom, releaseYearTo));
    links.setFirst(pageLink(personId, 0, size, capacity, releaseYearFrom, releaseYearTo));
    links.setLast(
        pageLink(
            personId, Math.max(totalPages - 1, 0), size, capacity, releaseYearFrom, releaseYearTo));
    if (page > 0) {
      links.setPrev(pageLink(personId, page - 1, size, capacity, releaseYearFrom, releaseYearTo));
    }
    if (page < totalPages - 1) {
      links.setNext(pageLink(personId, page + 1, size, capacity, releaseYearFrom, releaseYearTo));
    }
    return links;
  }

  private static Link pageLink(
      UUID personId,
      int page,
      int size,
      CapacityType capacity,
      Integer releaseYearFrom,
      Integer releaseYearTo) {
    return new Link(
        linkTo(
                methodOn(PeopleApi.class)
                    .getPersonFilmography(
                        personId, null, page, size, capacity, releaseYearFrom, releaseYearTo))
            .toUri());
  }
}
