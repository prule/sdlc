## Why

UC-001: an API consumer who already holds a movie's catalog identifier needs that movie's curated details so they can show it to their own end users. This is the first `catalog` capability. It is built on the UC-000 platform (uniform results and failures, correlation id, self links, read-only/public access). The current tree has no movie code, contract or schema; earlier movie work was removed in the "clean branch" reset.

## What Changes

- Add a public, read-only operation, `GET /api/v1/movies/{id}`, that returns one movie's details in the standard success envelope with a `self` link.
- Movie details always contain the identifier, title, release year and genres. Runtime (minutes), synopsis and rating (0–5 stars, score only) are included only when the curator has recorded them. When one is not recorded it is omitted, never shown as `0` or `null`. Keywords, credits and reviews are not included.
- Genres are shown by name. They are sorted alphabetically by name. A movie with no genres has an empty list.
- There are three distinct failure outcomes. A malformed identifier gets `400 BAD_REQUEST` before any lookup. A well-formed identifier that matches no movie gets `404 NOT_FOUND`. An internal fault gets `500 INTERNAL_ERROR`. All three use the uniform problem form.
- Write methods on `/movies/{id}` are refused with `405` and change nothing.
- Add the first Flyway migration. It creates the movie, genre and movie–genre tables, starting empty; curation is out-of-band.
- Add a small set of sample movies, loaded only in standalone mode (Gate 1, Q1). One movie has every optional detail and one has none and no genres, so evaluators can see BR-3 and flow 4b.
- Make identifier binding strict. Only the canonical 8-4-4-4-12 hexadecimal UUID form is well-formed. The JDK's lenient forms, such as `1-1-1-1-1`, are rejected.
- Update two platform scenarios that used `/movies` paths as examples of paths the service does not offer. Once this change lands, `/movies/{id}` is offered.

No breaking API change: the operation is additive. The DB schema change is additive: new tables only.

## Non-goals

- Movie search and browse by title, genre, year or rating (a separate use case).
- Cast and crew, keywords, reviews, and any links to them. Those links are added when the capabilities exist.
- Retrieving several movies in one request, and localised titles or synopses.
- Rate limiting (still a TODO in `domain/business-rules.md`).
- Creating, correcting or removing movies or genres, and any curation or ingestion tooling.
- Genre identity or links. Genres are shown by name alone until the genres-keywords capability exists.
- Sample movies in persistent mode. The sample set exists only in standalone mode.

## Capabilities

### New Capabilities
- `catalog/movies`: retrieve one movie's curated details by its stable, opaque identifier. Covers the content rules (BR-2 to BR-5), the distinct failure outcomes, read-only and public access, and identical behaviour in both runtime modes.

### Modified Capabilities
- `platform/uniform-responses`: in the requirement "Service is read-only and public", the unknown-path write example moves from `/api/v1/movies/123` to a path that is still not offered. `/movies/{id}` is added to the list of offered paths that refuse writes with `405`. In "Distinct failure kinds", the not-found example moves from `GET /api/v1/movies` to `GET /api/v1/no-such-thing`.

## Impact

- **API:** new `paths/movies.yaml` and `components/schemas/movie.yaml`. The shared `BadRequest` and `NotFound` responses are added to `components/responses/common.yaml`, defined once. There is a new `Movies` tag, so the generator produces a new `MoviesApi` interface.
- **Code:** new `com.acme.catalog.movies.**` slice (domain, application, persistence adapter, web adapter). There is a small shared kernel: `DomainException` and `ResourceNotFoundException`. `GlobalExceptionHandler` gets a handler that maps it to 404. The platform web config gets a strict UUID converter.
- **DB:** `V1__create_movie_catalog.sql`. It is applied in both standalone (H2) and persistent (PostgreSQL) modes. The standalone-only seed is `db/demo/R__demo_movies.sql`.
- **Config:** `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`, and per-profile `spring.flyway.locations`. The `PostgresIntegrationTest` base class pins the Flyway locations to exclude the seed.
- **Domain docs:** `domain/business-rules.md` credits wording (Gate 1).
- **Tests:** existing `ReadOnlyRefusalTest`, `InterfaceDescriptionContractTest`, `GeneratedApiCodegenTest` and the shared runtime-mode assertions are extended.
