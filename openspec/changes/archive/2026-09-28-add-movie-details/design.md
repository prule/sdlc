## Context

See proposal.md (Why). The tree contains only the UC-000 platform: `com.acme.platform.**`, the split OpenAPI spec (`/ping` only), `GlobalExceptionHandler` + `ProblemFactory` with a closed set of failure kinds classified by status, `ResponseMetaFactory`, a permit-all stateless `SecurityConfig`, the H2 default datasource + `postgres` profile, and an empty `db/migration`. No movie code, schema or contract exists. The earlier movie changes were removed by the reset and predate the current platform; they are not reused.

Constraints carried from the platform specs: success is always `application/json` (preset content type, D3 of platform design); success schemas are closed (`additionalProperties: false`) and validated at runtime by `InterfaceDescriptionContractTest`; shared components are defined once; problem bodies never echo input or internals; only one H2-booting test (`H2DefaultRuntimeSmokeTest`) may exist.

## Goals / Non-Goals

**Goals:**
- One public read-only operation with the three distinct failure outcomes, reusing the platform's failure kinds (no new kind).
- A domain model that owns BR-3/BR-4/BR-5 invariants (optional fields, genre ordering, rating range) so they are unit-testable.
- A schema that allows the genres-keywords capability to grow later without a breaking change.

**Non-Goals:**
- No `credits`/`reviews` links, no caching, no new problem kind or code.

## Decisions

### D1. Contract (added operation `GET /movies/{id}`)
Added: `paths/movies.yaml`, `components/schemas/movie.yaml`, `BadRequest` and `NotFound` in `components/responses/common.yaml`, tag `Movies` in `openapi.yaml`. Nothing changed or removed.

```yaml
# openapi.yaml (additions)
tags:
  - name: Movies
    description: Retrieve curated movie details
paths:
  /movies/{id}:
    $ref: './paths/movies.yaml#/movie'

# paths/movies.yaml
movie:
  get:
    operationId: getMovie
    summary: Retrieve a movie's details
    description: >
      Returns the curated details of one movie. `id` must be a canonical UUID
      (8-4-4-4-12 hex digits, any case); any other value is 400. Runtime, synopsis
      and rating are omitted when not recorded.
    tags: [Movies]
    security: []
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: string, format: uuid }
    responses:
      '200':
        description: The movie's details.
        headers:
          X-Correlation-Id: { $ref: '../components/headers/common.yaml#/X-Correlation-Id' }
        content:
          application/json:
            schema: { $ref: '../components/schemas/movie.yaml#/MovieEnvelope' }
      '400': { $ref: '../components/responses/common.yaml#/BadRequest' }
      '404': { $ref: '../components/responses/common.yaml#/NotFound' }
      '406': { $ref: '../components/responses/common.yaml#/NotAcceptable' }
      '500': { $ref: '../components/responses/common.yaml#/InternalError' }

# components/schemas/movie.yaml
MovieEnvelope:
  type: object
  required: [data, meta]
  additionalProperties: false
  properties:
    data: { $ref: '#/MovieDetail' }
    meta: { $ref: './common.yaml#/Meta' }
MovieDetail:
  type: object
  required: [id, title, releaseYear, genres, _links]
  additionalProperties: false
  properties:
    id:             { type: string, format: uuid }
    title:          { type: string, minLength: 1 }
    releaseYear:    { type: integer }
    genres:
      type: array
      items: { type: string, minLength: 1 }
    runtimeMinutes: { type: integer, minimum: 1 }
    synopsis:       { type: string, minLength: 1 }
    rating:         { type: number, minimum: 0, maximum: 5 }
    _links:         { $ref: '#/MovieLinks' }
MovieLinks:
  type: object
  required: [self]
  additionalProperties: false
  properties:
    self: { $ref: './common.yaml#/Link' }
```
`BadRequest`/`NotFound` mirror the existing `NotAcceptable`/`InternalError` (problem+json → `Problem`, `X-Correlation-Id` header). No `pattern` on the path parameter: with `format: uuid` the generator types it `UUID`, and a `@Pattern` on a non-`CharSequence` would fail at runtime; strictness is enforced by D4 and documented in `description`. Genres are plain names (A-GENRE), not an enum, so adding a curated genre never changes the contract. `uniqueItems` is deliberately omitted: it would make the generator emit a `Set` and lose the A–Z order; uniqueness is guaranteed by the domain factory and the `movie_genre` primary key. The operation `description` also lists the standalone sample movie ids (D7). `rating` without `format` generates `BigDecimal`, avoiding float artefacts. Wire form: the controller writes `rating.stripTrailingZeros()`, so `NUMERIC(2,1)` values read as `5.0`/`4.5`/`0.0` serialise as `5`/`4.5`/`0` (exact literals asserted by tests; values are ≤ 5 so no exponent form arises). `GeneratedApiCodegenTest` gains an assertion that `MoviesApi.getMovie` returns `MovieEnvelope`.

### D2. Components and dependency direction (inward only)
```
com.acme.shared.domain
├── DomainException                  abstract, JDK-only; (String code, String message) + code() (error-handling.md §1)
└── ResourceNotFoundException        final, extends DomainException; code NOT_FOUND
com.acme.catalog.movies
├── domain/model/  Movie (aggregate: MovieId, title, int releaseYear, List<String> genres,
│                   Optional<RuntimeMinutes> runtime, Optional<String> synopsis, Optional<Rating> rating)
│                   MovieId (record wrapping UUID), Rating (record wrapping BigDecimal, 0–5 enforced),
│                   RuntimeMinutes (record wrapping positive int minutes)
├── application/port/in/GetMovieUseCase         Movie getMovie(MovieId id)
├── application/port/out/LoadMoviePort           Optional<Movie> loadMovie(MovieId id)
├── application/service/GetMovieService          @Service; empty → throws ResourceNotFoundException
├── adapters/out/persistence/  MovieJpaEntity, GenreJpaEntity, MovieJpaRepository,
│                              MoviePersistenceAdapter implements LoadMoviePort (entity → domain)
└── adapters/in/web/MovieController              implements generated MoviesApi
```
- `Movie`'s factory enforces: non-blank title, genres de-duplicated and **sorted case-insensitively then by exact name** (BR-4 lives in the domain, not the SQL `ORDER BY`), and optional fields held as `Optional` accessors. Invalid curated data (e.g. rating 7) fails the factory → `IllegalArgumentException` → catch-all 500. This is an internal fault, not a client one, and the DB `CHECK`s make it unreachable in practice.
- Domain imports JDK only; application imports domain + shared kernel; adapters import application ports. Platform imports the shared kernel (for the 404 handler) and never `catalog`; `catalog` web adapter imports `com.acme.platform.web.ResponseMetaFactory`, as the platform design intended.
- `MovieController` maps `Movie` → `MovieEnvelope`: `rating` via `stripTrailingZeros()` (D1); absent `Optional`s are left `null` so global `non_null` inclusion omits them (BR-3); `self` via `linkTo(methodOn(MoviesApi.class).getMovie(id))`; returns `ResponseEntity.ok().contentType(APPLICATION_JSON)` (platform D3 rule).
- Alternative considered: a `MovieNotFoundException` in the catalog domain. Rejected because `GlobalExceptionHandler` (platform) would then import `catalog`, inverting the context dependency. A tiny shared kernel is the smallest inward-pointing option, and `people` will reuse it.

### D3. Not found = the existing `NOT_FOUND` kind
`GlobalExceptionHandler` gains `@ExceptionHandler(ResourceNotFoundException.class)` → `problemFactory.create(404)`, logged at DEBUG. The body is the platform's fixed `NOT_FOUND` problem (`urn:problem-type:not-found`, detail `The requested resource does not exist.`); the id is never echoed. This keeps the closed kind set intact and still satisfies BR-6: malformed (400 `BAD_REQUEST`), no such movie (404 `NOT_FOUND`) and internal (500 `INTERNAL_ERROR`) differ in status, code and type. Consequence: "no such movie" and "no such path" share a code, which Gate 1 accepted (Q2).

### D4. Strict identifier binding
Spring converts path variables with `UUID.fromString`, which accepts non-canonical forms (`1-1-1-1-1` → `00000001-0001-…`), so a value that is not well-formed per BR-1 would be silently looked up. A `StrictUuidConverter implements Converter<String, UUID>` (regex `^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$`, then `UUID.fromString`) is applied through `StrictUuidWebConfig`, an unscoped `@ControllerAdvice` in `com.acme.platform.web` whose `@InitBinder` registers, on every `WebDataBinder`, a `UUID` `PropertyEditor` that delegates to the converter. It is not registered through `WebMvcConfigurer.addFormatters`: when a conversion-service converter throws, Spring's `TypeConverterDelegate` falls back to the default lenient `UUIDEditor`, so `1-1-1-1-1` would still bind. A custom editor for the type is consulted before the conversion service and has no fallback. An unscoped advice's `@InitBinder` runs for every `@Controller` whatever its package; all applicable `@InitBinder` methods run (unlike first-match `@ExceptionHandler` resolution), so ordering against `GlobalExceptionHandler` is irrelevant. A new editor instance is created per binder, so the stateful `PropertyEditorSupport` is never shared across requests; the converter itself is stateless. A global `ConfigurableWebBindingInitializer` bean with a `PropertyEditorRegistrar` was rejected: it would replace Boot's initializer and have to re-wire its conversion service and validator by hand. A mismatch throws → `MethodArgumentTypeMismatchException` → existing 400 `BAD_REQUEST` path, before the controller runs (no lookup). It applies to every UUID-typed parameter, including the platform's test-only controller, whose 400 test still holds. Alternative: declare the parameter as a patterned `string` without `format: uuid`, validated with `@Validated`. Rejected: it loses `format: uuid` in the published description and relies on per-controller `@Validated`.

### D5. Persistence and migration (Flyway)
`V1__create_movie_catalog.sql` (first migration; portable across PostgreSQL and H2 `MODE=PostgreSQL`):
```sql
CREATE TABLE genre (
  id   UUID PRIMARY KEY,
  name VARCHAR(64) NOT NULL UNIQUE
);
CREATE TABLE movie (
  id              UUID PRIMARY KEY,
  title           VARCHAR(500) NOT NULL CHECK (char_length(title) > 0),
  release_year    INTEGER NOT NULL,
  runtime_minutes INTEGER NULL CHECK (runtime_minutes > 0),
  synopsis        TEXT NULL,
  rating          NUMERIC(2,1) NULL CHECK (rating >= 0 AND rating <= 5)
);
CREATE TABLE movie_genre (
  movie_id UUID NOT NULL REFERENCES movie(id),
  genre_id UUID NOT NULL REFERENCES genre(id),
  PRIMARY KEY (movie_id, genre_id)
);
CREATE INDEX movie_genre_genre_idx ON movie_genre (genre_id);
```
- Genre is its own table (controlled vocabulary, BR-4) with its own id, so genre identity can later be exposed additively (Q-GENRE) and genres-keywords can filter by it.
- Column mapping: `synopsis` is a plain `String` (no `@Lob`, no `columnDefinition`; `TEXT` validates against `String` on both dialects); `rating` is `BigDecimal` (never `Double`); `runtime_minutes` is `Integer` (nullable).
- `MovieJpaEntity` has `@ManyToMany` to `GenreJpaEntity` via `movie_genre`; `MovieJpaRepository.findById` uses `@EntityGraph(attributePaths = "genres")` → one query, no N+1. Adapter method is `@Transactional(readOnly = true)`.
- `spring.jpa.hibernate.ddl-auto: validate` (entities must match the migration, checked at boot in both modes) and `spring.jpa.open-in-view: false`.
- Tests insert fixtures with `JdbcTemplate` (Testcontainers Postgres) and clean up per test; there is no write path in the app.
- **Rollback:** additive. Reverting the code leaves three unused tables. If they must go, a forward migration `V2__drop_movie_catalog.sql` drops `movie_genre`, `movie`, `genre`; V1 is never edited. In standalone mode nothing persists anyway.

### D6. Read-only, security, runtime modes
- No `SecurityConfig` change: it is already `permitAll`, stateless, CSRF-off, so writes on `/movies/{id}` reach MVC and get 405 from the generated GET-only mapping. `ReadOnlyRefusalTest`'s unknown-path case moves from `/movies/123` (now offered → 405) to `/no-such-thing/123`, and `/movies/{id}` is added to the 405 cases (the MODIFIED platform requirement).
- `MovieRuntimeModeAssertions` (below) runs the movie 404/400 checks in both mode tests, which also proves V1 applies on H2. No new H2 test is added.
- Movie mode checks live in a sibling `MovieRuntimeModeAssertions` (not in `Uc000Assertions`), called by both mode tests.
- `InterfaceDescriptionContractTest` validates real `getMovie` 200 (Postgres fixture), 400, 404 and 405 bodies against the served schema.

### Assumptions (confirmed at Gate 1)
- **A-YEAR:** every movie has a release year (UC-001 open question, suggested default). `release_year` is `NOT NULL` and `releaseYear` is required. Reversing this later would make the field optional in the contract, which is a breaking change for strict clients.
- **A-GENRE:** a genre is shown by its name alone (UC-001 open question, suggested default). The `genre` table keeps an id, so exposing genre identity later is additive.
- **A-RATING:** a rating is curated to one decimal place (e.g. `4.5`).
- **A-SORT:** "alphabetical by genre name" means ignoring letter case.
- **A-EMPTY-SYNOPSIS:** a blank synopsis is treated as not recorded (the adapter normalises blank → absent), so an empty value is never presented.

### D7. Standalone-only demo seed (Gate 1, Q1)
- **What:** `src/main/resources/db/demo/R__demo_movies.sql`, a Flyway **repeatable** migration inserting a handful of genres and ~4 sample movies with fixed UUIDs, including `11111111-1111-4111-8111-111111111111` (all optional details, ≥2 genres inserted out of alphabetical order) and `22222222-2222-4222-8222-222222222222` (no runtime/synopsis/rating, no genres). Plain `INSERT`s of literal values only (portable, and the in-memory DB is empty at every start).
- **Scoping to standalone mode:** by Flyway location, per profile. `application.yml` (no profile) sets `spring.flyway.locations: classpath:db/migration,classpath:db/demo`; `application-postgres.yml` overrides it to `classpath:db/migration` only. The seed therefore can never reach a PostgreSQL database run in persistent mode.
- **Persistent-mode tests:** most Testcontainers tests extend `PostgresIntegrationTest` *without* the `postgres` profile, so they would otherwise pick up the default locations and get sample rows. `PostgresIntegrationTest`'s `@DynamicPropertySource` therefore also registers `spring.flyway.locations=classpath:db/migration`. All Postgres tests see only the schema plus their own fixtures.
- **How the persistent-mode exclusion is verified:** the base-class property outranks the profile yml. So no Postgres-booting test can prove that `application-postgres.yml` excludes the seed; `PersistentModeIntegrationTest`'s `404` for the sample id would pass even if the override were missing. The primary check is therefore a plain unit test, `FlywayLocationsConfigTest` in `com.acme.platform.runtimemodes`, with no Spring context. It loads both files with `YamlPropertySourceLoader` and asserts:
  - in `application-postgres.yml`, `spring.flyway.locations` is exactly `classpath:db/migration`;
  - in `application.yml`, the locations include `classpath:db/demo`.

  The `404` assertion is kept as a secondary check.
- **Flyway/Hibernate validation:** the seed is data only, so Hibernate `ddl-auto: validate` is unaffected. A repeatable migration has no version, so it never collides with future `V2…`, always runs after all versioned migrations (schema exists first), and Flyway's own validate never sees a missing versioned script in either mode. Its history row exists only in the in-memory H2 database.
- **Why a repeatable SQL migration over a Java seeder (`@Profile`/`ApplicationRunner`):** no extra production bean, no profile-negation logic, and the same mechanism that builds the schema loads the data, so ordering is guaranteed. Alternative rejected: a versioned `V1_1__demo` in the main location guarded by placeholders — it would put demo rows in the migration history of real databases.
- **Tests:** the single `H2DefaultRuntimeSmokeTest` asserts both sample movies (its permitted purpose, "the demo seed populates", `standards/testing.md` §3). Query and mapping correctness stays on Testcontainers.

## Risks / Trade-offs

- [H2 and PostgreSQL SQL dialect drift in V1] → Only portable DDL (no `gen_random_uuid()`, no partial/functional indexes); the H2 smoke test boots V1, and `ddl-auto: validate` checks both.
- [`NUMERIC(2,1)` limits ratings to one decimal place (e.g. 4.5, not 4.25)] → Matches the 0–5 star, curated-score model; recorded as assumption A-RATING. Widening is an additive migration.
- [Strict converter is global] → Only stricter than before; there are no other UUID parameters besides the test-only controller.
- [Shared `NOT_FOUND` code for unknown path and unknown movie] → Distinct from the other two outcomes as required; kept by Gate 1 decision Q2.
- [A new `PostgresIntegrationTest` subclass that bypasses the base class could pick up the demo location] → The base class pins the location for tests.
- [The `application-postgres.yml` override is removed or wrong] → `FlywayLocationsConfigTest` reads the yml directly, so it fails even though the Postgres-booting tests would still pass.
- [Seed drifts from the schema] → The H2 smoke test fails at boot if the seed's SQL no longer matches V1.

## Migration Plan

1. Ship the contract, code and `V1__create_movie_catalog.sql` together. Flyway applies V1 at start-up in both modes; the tables start empty and are populated by out-of-band curation.
2. Rollback: revert the commit. Tables may remain unused, or be dropped by a new forward migration (never by editing V1).

## Gate 1 decisions

- **Q1:** add a standalone-only demo seed (D7).
- **Q2:** keep reusing `NOT_FOUND`; no new kind (D3).
- **A-YEAR, A-GENRE, A-RATING, A-SORT, A-EMPTY-SYNOPSIS:** confirmed.
- **Credits wording:** the domain rule "Movie detail points to where the Movie's credits can be found" is reworded to say the link is added when the credits capability exists (task 8.1).
