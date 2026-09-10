## Why

Today a consumer can retrieve a movie's detail (UC-001) and search the catalog (UC-002), but there is no way to see *who worked on a movie*. UC-003 requires that, given a known movie identifier, a consumer can obtain that movie's full **cast** (performers + the character they played) and **crew** (non-acting contributors + their role), each in a meaningful order, so they can display a movie's credits. This is the smallest next increment on the catalog and the foundation the later person-detail and filmography features build on.

## What Changes

- Add a public, read-only sub-resource `GET /api/v1/movies/{id}/credits` (`operationId: getMovieCredits`) that returns a known movie's credits **whole, unpaginated** — cast and crew are small per movie (BR-7).
- Return credits as a HAL resource with **two separate embedded relations**: `data._embedded.cast` and `data._embedded.crew` — not one list with a type discriminator, since cast and crew have different shapes and different orderings (BR-4). `data._links.self` points at the credits endpoint.
  - **Cast item:** inline person (`id` + `name`), optional `character` (omitted when absent — BR-4/flow 6a), `billingOrder` (positive integer, `1` = top billing). Cast ordered by `billingOrder` ascending (BR-5).
  - **Crew item:** inline person (`id` + `name`), `department` and `job` (both free text). Crew ordered by `department` then `job` ascending, compared case-insensitively (BR-6).
- **People are named-only in this change (no onward person link).** Each credited person is exposed inline as `id` + `name` only, with **no** person self-link. A standalone person resource does not exist yet; the onward link is an additive follow-up when person detail lands (resolved decision 1, BR-9). This is deliberately the smallest increment.
- Distinguish outcomes (BR-3, BR-8): a well-formed unknown movie id → `404 problem+json` (`code: MOVIE_NOT_FOUND`, reusing the UC-001 outcome); a malformed id → `400` (framework UUID parse, same as UC-001); an existing movie with no cast and/or no crew → a normal `200` with empty group(s), **not** `404`.
- Modify the existing **movie-detail** contract so movie detail gains a `credits` HAL **link** relation (`data._links.credits`) pointing at `GET /movies/{id}/credits`, so a consumer arriving from movie detail can navigate to its credits. This is **additive and non-breaking** — a new optional link relation; no existing field changes.
- Add new OpenAPI schemas (`CreditPerson`, `CastCredit`, `CrewCredit`, `MovieCreditsData`, `MovieCreditsEnvelope`, `CreditsLinks`) and register `paths/movies-credits.yaml`, reusing the existing `Envelope`/`Meta`/`Link` components; regenerate stubs.
- Extend `com.acme.catalog.movies` with a credits domain model (`Person`, a sealed `Credit` with `Cast`/`Crew`, a `MovieCredits` aggregate), a `GetMovieCreditsUseCase` + service, a `LoadMovieCreditsPort`, and a persistence adapter.
- Add a **new Flyway migration** `V3__credits.sql` creating `people` and `credits` tables (never editing the applied `V2`).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `catalog/movies`: ADD requirements for retrieving a movie's credits — the credits sub-resource, the cast/crew embedded shapes and orderings, named-only inline people (no onward link), whole/unpaginated delivery, empty credits as a `200` success (distinct from `404` not-found), and malformed vs unknown id outcomes; and MODIFY the movie-detail representation so its `data._links` additively carries a `credits` link to the credits sub-resource.

## Impact

- **API:** new operation `getMovieCredits` (`GET /api/v1/movies/{id}/credits`), `security: []` (public). New schemas in `openapi/components/schemas/movie.yaml`, a new `CreditsLinks` and `credits` relation added to `MovieLinks` in `components/schemas/common.yaml`, new `paths/movies-credits.yaml`, all registered in `openapi.yaml`. Regenerate stubs (`openApiGenerate`). **Contract change to movie detail:** `MovieLinks` gains an optional `credits` link — additive, non-breaking.
- **Code:** new domain (`Person`, sealed `Credit`/`Cast`/`Crew`, `MovieCredits`), inbound `GetMovieCreditsUseCase` + `GetMovieCreditsService`, outbound `LoadMovieCreditsPort` + persistence adapter (+ JPA entities), and the `getMovieCredits` handler with web-only HAL assembly (plus the new `credits` link on movie detail). Register `/movies/{id}/credits` in `PublicEndpoints.PATTERNS`.
- **Data:** **new schema.** Flyway `V3__credits.sql` adds `people` and `credits` tables (FK to `movies(id)` ON DELETE CASCADE and `people(id)`), kept H2- and Postgres-compatible (single shared migration set). Extend the demo seed for the H2 default runtime (a movie with cast+crew, a movie with empty credits, a cast credit with no character).
- **Tests:** per-layer happy/edge/failure — cast/crew ordering, character omitted when absent, empty-credits `200`, unknown id `404`, malformed id `400`, `data._links.self` present and **no** person onward link, and movie detail now carrying `data._links.credits`. Persistence tests use Testcontainers/Postgres (never H2). Keep `GeneratedApiCodegenTest` and `PublicEndpointsConsistencyTest` green.

## Non-goals

- A standalone person resource / person self-link (`GET /people/{id}`, UC-004/CAT-004) — deferred; the onward person link is added additively later.
- A person's filmography (the person-side inverse view, UC-005) and searching/listing people (UC-006).
- Pagination of credits — credits are returned whole (BR-7).
- Any controlled vocabulary for crew `department`/`job` — both stay free text.
- Any create/update/delete of catalog data — the API stays read-only.
