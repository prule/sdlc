## Context

See proposal.md — Why. This is the first persisted `catalog` capability; everything before it was
platform foundation. The existing pipeline is established and must be reused unchanged:

- Split OpenAPI 3.1 under `src/main/resources/openapi/` (`openapi.yaml` root + `paths/` +
  `components/schemas|responses|parameters|headers/`); `./gradlew build` bundles (Redocly) then
  generates interfaces/DTOs; controllers implement the generated interface. Bundle artifact is
  git-ignored under `/build/`.
- Shared components already exist and MUST be reused: `Envelope`, `Meta`, `Problem`, `Link`. The
  `MovieLinks` schema follows the `PingLinks` pattern (a `_links` object with a required `self`).
- The single global `@RestControllerAdvice` (`GlobalExceptionHandler`) already maps
  `ResourceNotFoundException → 404` and `MethodArgumentTypeMismatchException → 400` (confirmed in
  source). A non-UUID `{id}` path variable binding to a `UUID`/`@org.openapitools`-generated param
  raises `MethodArgumentTypeMismatchException`, so the 400 path is already covered — no new handler.
- Clean-architecture package convention: `com.acme.<context>.<capability>...`. New tree:
  `com.acme.catalog.movies`.

## Goals / Non-Goals

**Goals:**
- Prove HTTP→domain→Postgres for real catalog data with correct layering and inward-only deps.
- Keep the codegen reuse guarantee intact (no `<Op><Status>Response*` duplicates; one shared
  `Problem`; one shared `Link`).
- Deliver a demonstrable running app (demo seed) without coupling tests to that seed.

**Non-Goals (design-level):**
- No `Person`/`Credit` modelling, no reviews, no search/listing (ticket Non-goals).
- No related `_links` beyond `self`; no `_embedded`.
- No caching, no rate-limiting, no ETag/conditional-GET design.

## Decisions

### API contract (added to the authored spec, then generated)
- New path file `paths/movies.yaml`, operation `operationId: getMovieById`, `security: []`,
  path param `id` (`type: string, format: uuid`), plus the shared `CorrelationId` param.
- Responses: `200` → `$ref` `MovieDetailEnvelope` (`application/json`); `400` → shared `BadRequest`;
  `404` → shared `NotFound`; `500` → shared `InternalError`. All four already exist as reusable
  `components/responses`.
- New schemas in `components/schemas/movie.yaml`, wired into `openapi.yaml#/components/schemas`:
  - `MovieDetail` — `required: [id, title, year, genres, _links]`; `id` uuid; `title` string;
    `year` integer; `genres` array of strings (`minItems: 1`); optional `runtimeMinutes` integer,
    `synopsis` string, `rating` number (`minimum: 0, maximum: 5`); `_links` `$ref` `MovieLinks`.
  - `MovieLinks` — object with required `self` (`$ref` `Link`) — in `components/schemas/common.yaml`
    next to `PingLinks` (fixed relation set, no `additionalProperties`).
  - `MovieDetailEnvelope` — `required: [data, meta]`; `data` `$ref` `MovieDetail`; `meta` `$ref`
    `Meta`.
- **Contract snippet (representative):**
  ```yaml
  # paths/movies.yaml
  get:
    operationId: getMovieById
    tags: [movies]
    security: []
    parameters:
      - $ref: '../openapi.yaml#/components/parameters/CorrelationId'
      - name: id
        in: path
        required: true
        schema: { type: string, format: uuid }
    responses:
      '200':
        description: The movie detail
        content:
          application/json:
            schema: { $ref: '../openapi.yaml#/components/schemas/MovieDetailEnvelope' }
      '400': { $ref: '../openapi.yaml#/components/responses/BadRequest' }
      '404': { $ref: '../openapi.yaml#/components/responses/NotFound' }
      '500': { $ref: '../openapi.yaml#/components/responses/InternalError' }
  ```
  Rationale: mirrors `paths/samples.yaml` so the codegen reuse test keeps passing; adding an
  envelope-`$ref`ing operation is exactly the api-codegen "generalizes to any endpoint" case.

### Rating exposes score only (not a vote count) — RECOMMENDATION for the open question
- CAT-001 exposes `rating` as a single 0–5 number, **no vote count**. Rationale: the ticket's field
  set names only "aggregate 0–5 star rating"; a vote count is a `ratings-reviews` concern and can be
  added later without breaking this additive schema. Flagged as a Gate-1 confirmation below.

### Domain model
- `Movie` domain aggregate as a Java `record` in `...movies.domain.model`: `MovieId` (value type
  wrapping `UUID`), `title`, `year`, `List<Genre>` (or `List<String>` labels), and `Optional`
  runtime/synopsis/rating. `Rating` a small value type constrained to 0–5. `Genre` a value carrying
  a label (controlled-vocabulary name). NO Spring/JPA/HATEOAS imports. Invariant: at least one
  genre, enforced in the aggregate's compact constructor.

### Application layer
- Inbound port `GetMovieByIdUseCase` (in `application/port/in`) and service `GetMovieByIdService`.
- Outbound port `LoadMovieByIdPort` (`application/port/out`) — `Optional<Movie> load(MovieId)`.
- Service returns the domain `Movie` or throws `ResourceNotFoundException` (existing
  `com.acme.common.error`) with a stable code (e.g. `MOVIE_NOT_FOUND`) → global handler maps 404.

### Persistence adapter + migration
- `adapters/out/persistence`: `MovieJpaEntity` + `GenreJpaEntity` (JPA annotations here only), a
  Spring Data repository, and `MoviePersistenceAdapter` implementing `LoadMovieByIdPort`, mapping
  entity ↔ domain. Domain objects are never `@Entity`.
- Flyway `V2__movies.sql`: `movies` (uuid PK `id`, `title`, `release_year`, nullable
  `runtime_minutes`, `synopsis`, `rating` numeric 0–5), `genres` (id, unique `name`), join
  `movie_genre` (movie_id, genre_id, PK pair, FKs). New migration only — never edit `V1`.

### Web adapter (link assembly here only)
- `MovieController implements <generated>MoviesApi`, constructor-injects the use case, maps domain →
  generated `MovieDetail` DTO, and assembles the `self` link via the framework link builder (as the
  hypermedia-links spec requires — no HATEOAS imports in domain/application). Wraps in the envelope.

### Demo-seed delivery — RECOMMENDATION for the open question
- Deliver the demo dataset as a **profile-scoped `ApplicationRunner`/`CommandLineRunner` seed loader**
  annotated `@Profile("demo")` (a dev/demo profile), reading a small committed data file (e.g.
  `demo-movies` SQL/JSON in resources), idempotent (insert-if-absent). It is **NOT** a Flyway data
  migration and **NOT** active under `prod`.
  - Why not a Flyway data migration: Flyway runs in every environment including prod and in tests,
    which would both leak demo rows into production and couple tests to the seed — violating the
    ticket constraint. A profile-scoped loader keeps prod clean and tests independent.
  - Tests never enable the `demo` profile; persistence/web tests insert their own fixtures against a
    Testcontainers Postgres and assert on their own ids.

### Domain doc reconciliation
- Update `domain/glossary.md` (Rating row) and `domain/business-rules.md` to record the settled
  **0–5 star aggregate** rating, resolving the standing TODO. (Docs only; no behavior change, so no
  spec delta for a platform capability.)

## Risks / Trade-offs

- [Non-UUID 400 relies on the generated param being a `UUID` type] → If the generator emits `String`
  for the path param, mismatch won't fire and a bad id could reach the service. Mitigation: keep
  `format: uuid` in the contract, parse to `UUID` at the boundary (constructor of `MovieId` throws
  `IllegalArgumentException` → already mapped to 400), and add a web test asserting `not-a-uuid` → 400.
- [Codegen duplicate regression] → Adding schemas could resurrect `<Op><Status>Response*` models.
  Mitigation: `$ref` shared envelope/`Problem`/`Link` exactly like `samples`; the existing
  `GeneratedApiCodegenTest` guards it.
- [New source under an ignored path] → `.gitignore` anchors `/build/` and `/out/`; all new source
  lives under `src/main/java` and `src/main/resources` (tracked). No new files under build/out.
- [Rating precision] → Storing `rating` as numeric(2,1) 0–5; contract exposes a number. Acceptable;
  vote count deferred.

## Migration Plan

- Forward: apply `V2__movies.sql` (additive, new tables only). Deploy is additive — new endpoint,
  no change to existing routes/contracts.
- Rollback: the endpoint and tables are new and isolated; reverting the release removes the route.
  The `V2` tables can be dropped by a later forward migration if needed (never edit `V2` in place).
  No data in prod depends on the demo seed.

## Open Questions

- **Rating vote count (Gate 1 confirmation):** design recommends score-only for CAT-001; confirm no
  vote count is required now. Additive to add later — does not change this task breakdown.
- **Data-source licensing/attribution (domain TODO):** if attribution must appear in a movie's
  representation, it becomes a field/rule this resource carries. Unresolved by the author; surfaced
  for a human decision — do not invent. Would require a spec/contract change if answered "yes".
