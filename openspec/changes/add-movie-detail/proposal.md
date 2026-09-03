## Why

The platform foundation (health-check, api-codegen, HAL hypermedia) is complete, but no
consumer-facing catalog data exists yet. CAT-001 delivers the first real `catalog` capability —
`GET /api/v1/movies/{id}` — proving the end-to-end path from a public HTTP endpoint down to real,
persisted domain data in Postgres, so API consumers can retrieve a single movie's detail.

## What Changes

- Add a public, read-only endpoint `GET /api/v1/movies/{id}` returning a single **Movie** by its
  stable opaque UUID.
- Author the OpenAPI contract for the operation and its `MovieDetail` `data` schema (with inline
  genre labels and a `self`-only HAL `_links`) in the split spec **before** any controller code;
  regenerate stubs; the controller implements the generated interface.
- Introduce the first persisted catalog data: a **Movie** domain aggregate (genres as a value on the
  aggregate), a `GetMovieDetail` use case + `LoadMovieByIdPort`, and a Postgres persistence adapter
  mapping a JPA entity ↔ domain.
- Add the first application Flyway schema migration for `movies` + `genres` (+ join).
- Ship a small **demo dataset** via a dev/demo-profile seed loader (NOT a production Flyway data
  migration), plus **independent** Testcontainers fixtures so tests never depend on the demo seed.
- Success is the standard Envelope `{data, meta}` as `application/json` with HAL `self` inside
  `data`; unknown id → `404 problem+json`; malformed UUID → `400 problem+json`; optional fields
  (runtime, synopsis, rating) omitted when absent.
- Reconcile `domain/` TODOs the ticket settles: record the 0–5 star aggregate Rating in
  `glossary.md` and `business-rules.md`.
- No breaking changes: purely additive (new endpoint, new schema, new table, existing shared
  `Problem`/`Link`/`Envelope`/`Meta` components reused).

## Capabilities

### New Capabilities
- `catalog/movies`: retrieve a single Movie's detail by id — the Movie aggregate's own data
  (id, title, release year, genre labels, and optional runtime, synopsis, 0–5 star aggregate
  rating) with a `self` HAL link, served publicly and read-only.

### Modified Capabilities
<!-- None: no existing capability's requirements change. Shared platform components are reused,
     not modified. -->

## Impact

- **API:** new operation `getMovieById` under `/movies/{id}`; new `MovieDetail`/`MovieDetailData`/
  `MovieDetailEnvelope`/`MovieLinks` schemas in the authored OpenAPI spec. Regenerates stubs.
- **Persistence:** new Flyway migration `V2__movies.sql` (tables `movies`, `genres`, `movie_genre`).
  First real application tables.
- **Code:** new `com.acme.catalog.movies` package tree (domain / application / adapters in+out),
  reusing the existing global `@RestControllerAdvice`, correlation-id filter, and success envelope.
- **Config:** a demo-seed loader active only under a `demo`/dev Spring profile (never `prod`).
- **Docs:** updates to `domain/glossary.md` and `domain/business-rules.md` (Rating scale).
- **Dependencies:** none new; existing Spring Web, Data JPA, Flyway, Testcontainers.
