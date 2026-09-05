## Context

The catalog is reset to the platform foundation: shared OpenAPI components (Envelope/Meta/Problem/
Link), the redocly-bundle → openapi-generator pipeline (controllers implement generated interfaces,
`GeneratedApiCodegenTest` guards against per-operation `<Operation><Status>Response*` duplicates), the
HAL convention (`platform/hypermedia-links`), the `PublicEndpoints.PATTERNS` public-surface source of
truth + its consistency test, the global `@RestControllerAdvice` error handler, and the H2-default /
Postgres-profile datasource. This change adds the first real catalog capability against that
foundation. See proposal.md — Why. Requirements are in `specs/catalog/movies/spec.md`.

## Goals / Non-Goals

**Goals:**
- Build `catalog/movies` clean/hexagonal, contract-first, reusing every platform primitive above.
- Encode the two distinct exception flows (malformed → 400 before lookup; not-found → 404) with the
  least new machinery, leaning on the existing handlers.

**Non-Goals (design-level):**
- No `credits` / cast-crew navigation link on movie detail (UC-001 non-goal), even though
  `domain/business-rules.md` records a future `_links.credits` from CAT-003 — it belongs to a later
  capability, not this one. `MovieLinks` carries `self` only for now.
- No search/list, no caching/rate-limiting, no write paths.

## Decisions

### D1 — Identifier is a UUID; malformed vs not-found fall out of the type system
The path variable is typed `UUID`. A non-UUID path value (e.g. `not-a-uuid`) fails Spring MVC path
binding and raises `MethodArgumentTypeMismatchException`, which the existing global handler already
maps to `400 BAD_REQUEST` — satisfying flow 3a (rejected before any lookup) for free. A well-formed
UUID that no row carries raises `ResourceNotFoundException("MOVIE_NOT_FOUND", …)` from the
application layer → `404`. This gives the two distinct outcomes (BR-3) with no custom validation code.
_Alternatives:_ a custom string id with a `pattern` + manual parse (more code, no benefit); a single
"not found" for both (rejected — violates BR-3's distinctness).

### D2 — OpenAPI operation (added)
One operation added, public (`security: []`), path added to `openapi.yaml` `paths` as
`/movies/{id}: $ref: 'paths/movies.yaml'`. Contract snippet (`paths/movies.yaml`):

```yaml
get:
  operationId: getMovie
  tags: [movie]
  summary: Retrieve a movie's detail by its identifier
  security: []
  parameters:
    - $ref: '../openapi.yaml#/components/parameters/CorrelationId'
    - name: id
      in: path
      required: true
      schema: { type: string, format: uuid }
  responses:
    '200':
      description: The movie's detail
      headers:
        X-Correlation-Id: { $ref: '../openapi.yaml#/components/headers/CorrelationId' }
      content:
        application/json:
          schema: { $ref: '../openapi.yaml#/components/schemas/MovieEnvelope' }
    '400': { $ref: '../openapi.yaml#/components/responses/BadRequest' }
    '404': { $ref: '../openapi.yaml#/components/responses/NotFound' }
    '500': { $ref: '../openapi.yaml#/components/responses/InternalError' }
```

`components/schemas/movie.yaml` defines `MovieData` (required `id`,`title`,`releaseYear`,`genres`,
`_links`; optional `runtimeMinutes`,`synopsis`,`rating`) and `MovieEnvelope` (`data`+`meta`).
`_links` is a **required** property of `MovieData` (mirroring `SampleItem`/`SampleItemLinks`), so
`data._links.self` is contract-enforced always-present, not just test-enforced. `genres` is an
array (`minItems: 1`) of a `Genre` enum. `rating` is `number` with `minimum: 0`, `maximum: 5`.
`MovieLinks { self }` is added to `common.yaml` (per §2a fixed-relation `_links`, `$ref`ed named
component). Optional fields are omitted from the DTO when absent (Jackson non-null), matching BR-5.
New root `$ref`s registered under `openapi.yaml#/components/schemas`: `MovieData`, `MovieEnvelope`,
`MovieLinks`, `Genre`. `getMovie` returns the shared `MovieEnvelope` — the codegen test invariant.

### D3 — Clean/hexagonal layering (dependencies inward-only)
`com.acme.catalog.movies`:
- **domain/model**: `Movie` (record: `MovieId id`, `String title`, `int releaseYear`,
  `Set<Genre> genres` (≥1), `Optional<Integer> runtimeMinutes`, `Optional<String> synopsis`,
  `Optional<Rating> rating`); `Genre` enum (controlled vocabulary); `Rating` value object enforcing
  0–5; `MovieId` wrapper over UUID. No Spring/JPA imports.
- **application**: `GetMovieDetailUseCase` (in port), `LoadMoviePort`
  (out port: `Optional<Movie> findById(MovieId)`), `GetMovieDetailService` implementing the use case,
  throwing `ResourceNotFoundException("MOVIE_NOT_FOUND", …)` on empty. Depends only on domain.
- **adapters/out/persistence**: `MovieJpaEntity` + `MovieGenreJpaEntity` (or `@ElementCollection`),
  `MovieSpringDataRepository`, `MoviePersistenceAdapter` implementing `LoadMoviePort`, mapping JPA →
  domain. JPA annotations live here only, never on domain.
- **adapters/in/web**: `MovieController implements MovieApi` (generated). `@Validated`. Maps domain
  `Movie` → generated `MovieEnvelope`, builds `data._links.self` via `WebMvcLinkBuilder`, sets `Meta`
  from `CorrelationId.current()` (mirrors `SampleController`).

### D4 — Persistence: Flyway V2 (Postgres)
New migration `V2__create_movies.sql` (never edit V1):
```sql
CREATE TABLE movies (
  id             UUID PRIMARY KEY,
  title          TEXT NOT NULL,
  release_year   INTEGER NOT NULL,
  runtime_minutes INTEGER,           -- nullable (optional)
  synopsis       TEXT,               -- nullable (optional)
  rating         NUMERIC(2,1),       -- nullable; CHECK 0..5
  CONSTRAINT movies_rating_range CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);
CREATE TABLE movie_genres (
  movie_id UUID NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
  genre    TEXT NOT NULL,
  PRIMARY KEY (movie_id, genre)
);
```
Genre is a controlled vocabulary enforced in the domain (`Genre` enum) and stored as text; the
child-table rows give the ≥1-genre invariant its storage. _Rollback:_ additive migration on a
foundation with no movie tables; roll back by reverting the deploy (drop tables in a follow-up
migration if ever needed) — no data migration risk.

### D5 — Public surface + demo seed
Add `/movies/{id}` (verbatim) to `PublicEndpoints.PATTERNS` (relative to context path `/api/v1`) so
the endpoint is permitted and the `security: []` consistency test agrees. The pattern must be the
literal OpenAPI path key `/movies/{id}`, not `/movies/*`: `PublicEndpointsConsistencyTest` string-
matches `PATTERNS` against the path keys carrying `security: []`, so `/movies/*` ≠ `/movies/{id}`
would fail the build. `/movies/{id}` is a valid Spring Security `requestMatchers` pattern and still
matches a non-UUID segment (e.g. `/movies/not-a-uuid`), so a malformed id stays public and reaches
the controller to yield 400 (flow 3a), not 401. Add a small demo-profile seed (following the
platform's demo-profile pattern) so the H2 default runtime serves at least one movie; the
default-runtime smoke test may be extended to assert `GET /movies/{seededId}`. All unit/web/persistence
tests use their own fixtures and never depend on the demo seed; DB tests run on Testcontainers-Postgres.

## Risks / Trade-offs

- **Genre vocabulary is not specified by the use case** → choose a conventional fixed enum now (see
  Open Questions); storing genre as text keeps the vocabulary extensible without a schema change.
- **`rating` as `NUMERIC(2,1)` vs OpenAPI `number`** → JSON serializes as a number; the 0–5 bound is
  enforced in domain (`Rating`) and by the DB `CHECK`, so contract and storage agree.
- **Demo seed drift with H2** → keep the seed tiny and demo-profile-scoped; tests own their fixtures
  so seed changes never break the suite.
- **Optional-field omission** → rely on Jackson non-null serialization of the generated DTO; the
  web test asserts absent keys (not null) for the no-optionals case to catch regressions.

## Open Questions

- **Genre controlled vocabulary values.** UC-001 says "controlled-vocabulary categories" without
  listing them. Assumption for implementation: a conventional set (e.g. ACTION, ADVENTURE, ANIMATION,
  COMEDY, CRIME, DOCUMENTARY, DRAMA, FANTASY, HORROR, MYSTERY, ROMANCE, SCIENCE_FICTION, THRILLER,
  WESTERN). This is deferrable — it does not change the specs, the approach, or the task breakdown,
  and the enum can be extended later without an API-breaking change. Confirm with a domain owner
  before archive if a canonical list exists.
