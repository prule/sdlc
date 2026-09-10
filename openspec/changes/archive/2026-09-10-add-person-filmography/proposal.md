## Why

UC-004 gave every person a `data._links.credits` link that points at `GET /people/{id}/credits`, but that sub-resource does not exist yet — the link currently 404s. UC-005 fills it in: given a known person identifier, a consumer can obtain the movies that person is credited in (from the *person* side), each entry annotated with the single capacity in which they contributed, optionally narrowed by capacity or release year, one ordered page at a time — so consumers can present a person's filmography.

## What Changes

- Add a public, read-only `GET /api/v1/people/{id}/credits` operation (`operationId: getPersonFilmography`) returning an **ordered, paged** collection of filmography entries wrapped in the standard success Envelope, with `meta.pagination` and HAL `self`/`first`/`last`/`prev`/`next` collection links (the UC-002 paging convention).
- Each entry is a **movie summary** (the UC-002 `MovieSummary` shape — id, title, releaseYear, genres, optional runtimeMinutes/rating, with a `self` link to the movie's detail, UC-001) annotated with **exactly one capacity** (BR-3, BR-4). A capacity carries a coarse type (`acting`/`non-acting`) plus a display role label — `character` for acting (omitted when not recorded), `department` + `job` for non-acting. A person credited in N capacities on one movie yields **N entries**, never one entry listing several capacities.
- Present as a **single flat list** (not split into acting/non-acting groups), ordered **newest movie first** (release year desc), then **title asc**, then a **stable terminal tiebreak** (the credit's own identifier) so the total order is strict and unchanging across page boundaries — no entry skipped or duplicated when paging (BR-3, 5a).
- Two optional, conjunctive filters (BR-9): **capacity** (`acting`/`non-acting`, the coarse type) and a **release-year range** (`releaseYearFrom`/`releaseYearTo`, either bound alone, same inclusive semantics as UC-002). Supplying none returns the whole filmography.
- Distinguish outcomes exactly as UC-004 (BR-5): a **malformed** identifier is `400 application/problem+json` before any lookup (framework UUID path binding); a **well-formed** identifier matching no person is `404` (`code: PERSON_NOT_FOUND`). Invalid paging (`page < 0`, `size < 1` or `> 100`) is `400` via the shared `@Min`/`@Max` params (BR-7); an unrecognised `capacity` value is `400` via enum bind failure (flow 3d).
- Empty results are **success, not error** (BR-8): an existing person with no credits, a filter that matches nothing, or a page beyond the last all return `200` with an empty page and the correct total (zero when nothing matches; greater than zero for a valid page beyond the last).
- Add OpenAPI schemas (`Capacity`, `FilmographyEntry`, `PersonFilmographyData`, `PersonFilmographyEnvelope`) and a `PersonFilmographyLinks` collection-links object; register `paths/people-filmography.yaml` and the new schemas in `openapi.yaml`, reusing the shared `Envelope`/`Meta`/`Pagination`/`Link`/`Page`/`Size` components and the existing `MovieSummary`/`Genre`/`MovieLinks`; regenerate stubs.
- Extend the existing `com.acme.catalog.people` slice: new domain filmography value objects, a `GetPersonFilmographyUseCase` + service, a `LoadPersonFilmographyPort` + `PersonFilmographyJpaAdapter`, and a new operation on `PersonController`. Register `/people/{id}/credits` in `PublicEndpoints.PATTERNS`.

**No new database migration.** The `credits`, `people`, `movies`, and `movie_genres` tables already exist (`V2__movies.sql`, `V3__credits.sql`). This change only reads them — each `credits` row is already exactly one capacity for one person on one movie, so one row maps to one filmography entry. No applied migration is edited.

**No BREAKING changes.** Purely additive: a new operation on an existing capability, new schemas. No existing contract, schema, or behavior changes; the previously-404ing `credits` link now resolves.

## Capabilities

### New Capabilities
<!-- None. This extends the existing catalog/people capability. -->

### Modified Capabilities
- `catalog/people`: Add the person-side filmography sub-resource — a public, read-only, paged, filterable, ordered list of a person's movie credits, one entry per capacity, that the existing person-detail `credits` link resolves to.

## Impact

- **API:** new operation `getPersonFilmography` (`GET /api/v1/people/{id}/credits`, `security: []`, public). New `paths/people-filmography.yaml`; new schemas in `components/schemas/person.yaml` (`Capacity`, `FilmographyEntry`, `PersonFilmographyData`, `PersonFilmographyEnvelope`) and `PersonFilmographyLinks` in `components/schemas/common.yaml`, all `$ref`ed from `openapi.yaml`. Reuse shared `Page`/`Size`, `Envelope`/`Meta`/`Pagination`, and the existing `MovieSummary`/`Genre`/`MovieLinks`. Regenerate stubs (`openApiGenerate`); keep `GeneratedApiCodegenTest` green.
- **Code:** extend `com.acme.catalog.people` — domain `FilmographyEntry`, `Capacity` (sealed/record with `acting`/`non-acting` type + label fields), `FilmographyCriteria` (optional capacity + year bounds), page-request/page value objects (or reuse a shared page shape); inbound `GetPersonFilmographyUseCase` + `GetPersonFilmographyService`; outbound `LoadPersonFilmographyPort` + `PersonFilmographyJpaAdapter` (two-phase native query: page-of-credit-ids + count, then hydrate in order, with `CAST(id AS varchar)` + `UUID.fromString` for H2/Postgres portability). Add the operation to `PersonController implements PeopleApi` with web-only HAL link assembly. Reuse the existing `ResourceNotFoundException` + global `@RestControllerAdvice`. Register `/people/{id}/credits` in `PublicEndpoints.PATTERNS`.
- **Data:** none — reads the existing `credits`/`people`/`movies`/`movie_genres` tables. No Flyway migration. The existing demo seed already populates people and credits with fixed UUIDs, so the H2 default runtime already has a person with a retrievable filmography.
- **Tests:** per-layer happy/edge/failure — filmography returned ordered and paged; one entry per capacity (a person acting and directing one movie yields two entries); capacity and release-year filters (including a filter matching nothing → empty `200`, total zero); page beyond last → empty `200`, total greater than zero; unknown person → `404 PERSON_NOT_FOUND`; malformed id → `400` (no lookup); invalid `page`/`size` → `400`; unknown `capacity` value → `400`. Persistence tests use Testcontainers/Postgres (never H2). Optionally extend `H2DefaultRuntimeSmokeTest` to hit `/people/{seededPersonId}/credits`.

## Non-goals

- Presenting a movie's credits from the **movie side** (its cast & crew) — that is UC-003, the inverse view, already implemented.
- Retrieving the person's own core details (UC-004) or searching/listing people (UC-006).
- Retrieving a listed movie's full detail — navigable *from* each entry via its `self` link, but performed by UC-001.
- Filtering by anything **other than** capacity and release-year range (e.g. genre or title) — out of scope for now (BR-9).
- Configurable sort orders — only the single default order (newest-first, title, terminal tiebreak) is offered (see design Open Questions).
- Billing order, or any credit facet beyond the coarse capacity type and its display role label — the full credit detail stays reachable from the movie side (UC-003).
- Any create/update/delete of catalog data — the API stays read-only.
