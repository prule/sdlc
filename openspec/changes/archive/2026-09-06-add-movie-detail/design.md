## Context

See proposal.md — Why. The catalog is reset to the walking-skeleton foundation: no movie code, schema, or contract exists. The platform provides the shared success Envelope/Meta/Problem/Link components, the HAL convention (`platform/hypermedia-links`), a global `@RestControllerAdvice`, the redocly-bundle → openapi-generator pipeline, the H2-default / `postgres`-profile datasource, and a single `PublicEndpoints.PATTERNS` source of truth for public paths. This design builds `catalog/movies` on top of those, deriving every technical decision from UC-001, `domain/`, and `standards/`.

## Goals / Non-Goals

**Goals:**
- One public, read-only `GET /movies/{id}` returning movie detail in the standard Envelope with a HAL `self` link.
- Cleanly separate UC-001's two failure outcomes (malformed vs not-found) reusing existing infrastructure only.
- Persist movies + genres in PostgreSQL via a new Flyway migration, with a mapping persistence adapter.
- Keep the H2 default runtime demonstrable via a demo seed, while tests stay seed-independent on Testcontainers-Postgres.

**Non-Goals (design-level):**
- No movie search/list, no credits/cast/crew, no navigation link other than `self` (no `credits` endpoint exists on this branch to link to).
- No caching, rate limiting, or write operations.

## Decisions

### Identifier scheme: UUID path variable
A movie is addressed by a UUID — stable, opaque, and never exposing internal storage (matches `domain/business-rules.md` and `standards/openapi.md` §4). The controller method binds the path variable as `java.util.UUID`.

- **Why this cleanly yields UC-001's two distinct outcomes at zero extra cost**: a value that is not a well-formed UUID fails Spring's path binding, raising `MethodArgumentTypeMismatchException` → the **existing** global handler maps it to `400 BAD_REQUEST` **before** the controller body runs (so no lookup occurs, satisfying flow 3a). A well-formed UUID that matches no row causes the application service to throw the **existing** `ResourceNotFoundException("NOT_FOUND", …)` → `404` (flow 4a). No bespoke exception, no new handler.
- **Alternative considered**: a custom opaque slug/short-code with a bespoke `@Pattern` validator. Rejected — it would require a new validation exception path and a new handler for marginal benefit; UUID already meets "stable, opaque, hides storage" and is the platform convention.

### OpenAPI operation (contract-first, added)
New `paths/movies.yaml` (`GET /movies/{id}`, `security: []`) and `components/schemas/movie.yaml`, `$ref`ed from `openapi.yaml`. Reuses shared `Envelope`/`Meta`/`Link` and the `NotFound`/`BadRequest`/`InternalError` responses. Payload schemas are named components (no per-operation `<Operation><Status>Response*` duplicates), keeping `GeneratedApiCodegenTest` green. Contract snippet (abridged):

```yaml
# paths/movies.yaml
get:
  operationId: getMovieById
  tags: [movies]
  summary: Retrieve a movie's detail by id
  security: []
  parameters:
    - $ref: '../openapi.yaml#/components/parameters/CorrelationId'
    - name: id
      in: path
      required: true
      schema: { type: string, format: uuid }
  responses:
    '200': { description: The movie detail, content: { application/json: { schema: { $ref: '../openapi.yaml#/components/schemas/MovieDetailEnvelope' } } } }
    '400': { $ref: '../openapi.yaml#/components/responses/BadRequest' }
    '404': { $ref: '../openapi.yaml#/components/responses/NotFound' }
    '500': { $ref: '../openapi.yaml#/components/responses/InternalError' }

# components/schemas/movie.yaml
MovieDetail:
  type: object
  required: [id, title, releaseYear, genres, _links]
  properties:
    id: { type: string, format: uuid }
    title: { type: string }
    releaseYear: { type: integer }
    genres: { type: array, minItems: 1, items: { $ref: '#/Genre' } }
    runtimeMinutes: { type: integer, minimum: 1 }   # optional
    synopsis: { type: string }                        # optional
    rating: { type: number, minimum: 0, maximum: 5 }  # optional
    _links: { $ref: '../../openapi.yaml#/components/schemas/MovieLinks' }
Genre:
  type: string
  enum: [Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy, History, Horror, Music, Mystery, Romance, SciFi, Thriller, War, Western]
```
`MovieLinks { self }` is added to `components/schemas/common.yaml` (fixed relations, never `additionalProperties`), mirroring `PingLinks`. Optional fields are omitted when absent via the already-global Jackson `non_null` inclusion.

### Components and dependency direction (inward-only)
- **domain/model** (no Spring/JPA): `Movie` (record aggregate, factory enforcing invariants: ≥1 genre, releaseYear present, `rating` if present within 0–5), `Genre` (enum matching the OpenAPI enum), `Rating` (value object validating 0–5).
- **application/port/in**: `GetMovieDetailUseCase`. **application/port/out**: `LoadMoviePort` returning `Optional<Movie>`. **application/service**: `GetMovieDetailService` (throws `ResourceNotFoundException` when the port returns empty).
- **adapters/out/persistence**: `MovieJpaEntity` (+ genres via `@ElementCollection`), Spring Data `MovieJpaRepository`, `MoviePersistenceAdapter` implementing `LoadMoviePort` and mapping entity ↔ domain.
- **adapters/in/web**: `MovieController implements MoviesApi` (generated), maps domain `Movie` → generated `MovieDetailEnvelope`, builds `self` via `WebMvcLinkBuilder`.

Dependencies point inward only: web/persistence → application → domain; domain imports nothing framework-related. Link assembly stays web-adapter-only.

### Persistence / Flyway migration
New `V2__movies.sql` (next after V1 baseline; V1 is never edited):
- `movies` — `id UUID PRIMARY KEY`, `title TEXT NOT NULL`, `release_year INT NOT NULL`, `runtime_minutes INT NULL`, `synopsis TEXT NULL`, `rating NUMERIC(2,1) NULL CHECK (rating >= 0 AND rating <= 5)`.
- `movie_genres` — `movie_id UUID NOT NULL REFERENCES movies(id) ON DELETE CASCADE`, `genre VARCHAR(32) NOT NULL`, `PRIMARY KEY (movie_id, genre)`. The one-or-more-genres invariant is enforced in the domain factory and by demo/curation data; the schema permits reading it.
- **Rollback**: additive migration; rollback = drop `movie_genres` then `movies` (no data loss risk for other features since these are new tables).

### Demo seed vs test isolation
A demo-seed component (active when the `test` profile is NOT active, e.g. `@Profile("!test")`, idempotent — inserts only if `movies` is empty) seeds a couple of movies: one with all optional fields, one with none, each with ≥1 genre, using fixed UUIDs so the H2 default runtime serves demonstrable detail. Tests run under the `test` profile so the seed is inert; persistence tests supply and assert their own fixtures on Testcontainers-Postgres. The H2 default-runtime smoke test MAY be extended to assert `GET /movies/{seededId}` returns 200; DB/persistence-logic tests stay on Testcontainers.

### Public-endpoint registration
Add `/movies/{id}` **verbatim** (the OpenAPI path key) to `PublicEndpoints.PATTERNS`; the consistency test string-matches path keys against `security: []` operations, so a glob like `/movies/*` would fail it. This honours the deliberate public-surface divergence from `standards/security.md` recorded in `domain/business-rules.md`.

## Risks / Trade-offs

- **Malformed-id detection depends on UUID binding** → If a future non-UUID identifier scheme were adopted, the malformed→400 path would need an explicit validator. Mitigation: documented here; UUID is the settled platform convention.
- **`rating` as `NUMERIC(2,1)` maps to a decimal on the wire** → the OpenAPI `number` (0–5) and the DB `NUMERIC(2,1)` agree; the domain `Rating` validates the range so bad data cannot round-trip. Mitigation: domain invariant + DB `CHECK`.
- **Genre enum is a fixed vocabulary** → adding a genre later requires an enum + OpenAPI update. Mitigation: additive, non-breaking (new enum members extend the contract without removing any); flagged as an open question.
- **Demo seed drift vs schema** → seed uses the same JPA path/migration. Mitigation: idempotent, guarded by the `test` profile so it never affects tests.

## Migration Plan

1. Author OpenAPI (`paths/movies.yaml`, `components/schemas/movie.yaml`, `MovieLinks` in `common.yaml`, `$ref`s in `openapi.yaml`), then bundle + generate.
2. Ship `V2__movies.sql`. Flyway applies it on both H2 default and Postgres profile at startup.
3. Deploy is additive; no coordination needed. Rollback = revert code and drop the two new tables (no other feature depends on them).

## Open Questions

- **Genre controlled vocabulary**: UC-001 does not specify the genre set. This design assumes a conventional enum (Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy, History, Horror, Music, Mystery, Romance, SciFi, Thriller, War, Western). This is deferrable — extending the set is additive and does not break the API contract, the approach, or the task breakdown. A curator can refine the vocabulary later.
