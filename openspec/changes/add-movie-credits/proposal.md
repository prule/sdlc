## Why

CAT-001 delivered movie detail but deliberately deferred credits, emitting only a `self` link and
promising a navigational `credits` link "once a people/credits endpoint exists". Consumers currently
have no way to retrieve who acted in or made a movie. This change delivers that endpoint — a movie's
cast and crew — and closes the CAT-001 loop. It is also the first real Person/Credit modelling in the
system.

## What Changes

- Add `GET /api/v1/movies/{id}/credits` — returns a movie's full cast + crew as an **unpaginated** HAL
  collection inside the standard Envelope (`{data, meta}`), served publicly (`security: []`).
- Cast items expose Person (`id`+`name`), `character`, and `billingOrder`; crew items expose Person
  (`id`+`name`), `department`, and `job`. Person is exposed **inline** with **no** person `self` link
  (no `/people/{id}` endpoint exists yet — no dangling links).
- Cast/crew are carried as **two separate embedded relations** (`data._embedded.cast` and
  `data._embedded.crew`) — see design for rationale.
- Ordering is total and stable: cast by `billingOrder` ascending; crew grouped by `department` then
  `job`; both end in a deterministic terminal tiebreak (person name, then credit id).
- Empty credits → normal `200` with empty embedded arrays; unknown movie id → `404` problem+json
  (reusing `MOVIE_NOT_FOUND`); malformed UUID → `400` problem+json (existing handler).
- Add the navigational `credits` `_link` to the existing `GET /api/v1/movies/{id}` movie-detail
  response (built in the web adapter; no extra DB query).
- Introduce `Person` and `Credit` domain model, a `GetMovieCredits` use case + outbound port, a
  persistence adapter, and a new Flyway migration (`V4`) for `people` + `credits` tables.
- Extend the `@Profile("demo")` seed with people + credits; tests use their own fixtures.
- Add `/movies/{id}/credits` to `PublicEndpoints`; extend `GeneratedApiCodegenTest` for the new
  operation's shared-envelope conformance and the movie-detail schema now carrying a `credits` link.

No breaking API change: the movie-detail response is additive (a new optional `_links.credits`). No
existing DB schema is altered — `V4` adds new tables only (`V1`–`V3` untouched).

## Capabilities

### New Capabilities
- `catalog/credits`: retrieval of a movie's cast and crew as an unpaginated, public, read-only HAL
  collection sub-resource of a movie; defines the endpoint, cast/crew item shapes, ordering,
  empty/404/400 behaviour, public access, bounded (no-N+1) loading, and demo/test-independence.

### Modified Capabilities
- `catalog/movies`: the "Movie detail carries only a self link and no embedded resources" requirement
  changes to allow (and require) a navigational `credits` `_link` on movie detail, addressing the new
  endpoint; the otherwise `self`-only, no-`_embedded`, no-`_templates` behaviour still holds.

## Impact

- **API contract:** new operation `getMovieCredits` and new Credit/cast/crew `data` item schemas in the
  split OpenAPI spec; movie-detail `_links` schema gains a `credits` relation. Bundle → `openApiGenerate`.
- **Code:** new `catalog/credits` domain/application/adapters packages; `MovieController` movie-detail
  method gains the credits link; `PublicEndpoints.PATTERNS` gains `/movies/{id}/credits`.
- **DB:** new Flyway `V4` (`people`, `credits` with FK to `movies`); demo seed extended.
- **Tests:** `GeneratedApiCodegenTest` and `PublicEndpointsConsistencyTest` extended; new web,
  application, and Testcontainers persistence tests (including an N+1 query-count guard).
- **Dependencies:** builds on `catalog/movies`, `platform/hypermedia-links`, `platform/api-codegen`,
  and the CAT-002 N+1-avoidance / empty-collection patterns.
- **Domain docs:** `domain/` reconciliation (glossary field definitions, `credits` relation name,
  person-addressability rule, bounded-contexts credits/people split, movie-detail representation).
