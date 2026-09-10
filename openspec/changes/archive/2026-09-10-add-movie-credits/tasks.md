## 1. OpenAPI contract (contract-first)

- [x] 1.1 In `components/schemas/common.yaml`, add the `credits` (optional) relation to `MovieLinks` and add a new `CreditsLinks { self }` object; register `CreditsLinks` `$ref` in `openapi.yaml#/components/schemas`.
- [x] 1.2 In `components/schemas/movie.yaml`, add `CreditPerson` (required id/name, no `_links`), `CastCredit` (required person/billingOrder[min 1]; optional character), `CrewCredit` (required person/department/job), `MovieCreditsData` (required `_embedded.cast`/`_embedded.crew` + `_links`), and `MovieCreditsEnvelope` (reusing shared `Meta`).
- [x] 1.3 Create `paths/movies-credits.yaml` with `GET /movies/{id}/credits` (`operationId: getMovieCredits`, `security: []`, uuid path param + shared `CorrelationId` param; `200 → MovieCreditsEnvelope`, `400/404/500 → shared responses`).
- [x] 1.4 Register the new movie-credits schema `$ref`s and the `/movies/{id}/credits` path in `openapi.yaml`.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate` (bundle → generate); confirm `MoviesApi.getMovieCredits` and the credit DTOs are generated and bind to the shared `Meta`/`Link` components (no per-operation `<Operation><Status>Response*` duplicates); `GeneratedApiCodegenTest` stays green.

## 3. Domain

- [x] 3.1 Create `Person` (record `id`+`name`, validating presence and non-blank name), no Spring/JPA imports.
- [x] 3.2 Create the sealed `Credit` interface with `Cast` (person, `Optional<String>` character via a nullable-accepting factory, `billingOrder >= 1`) and `Crew` (person, non-blank department, non-blank job) records.
- [x] 3.3 Create the `MovieCredits` aggregate whose factory orders cast by `billingOrder` ascending and crew by `department` then `job` ascending case-insensitively, storing immutable copies; empty cast and/or crew is valid.

## 4. Application / ports

- [x] 4.1 Create inbound port `GetMovieCreditsUseCase` (`MovieCredits getMovieCredits(UUID movieId)`).
- [x] 4.2 Create outbound port `LoadMovieCreditsPort` returning `Optional<MovieCredits>` — empty only when the movie does not exist; present (possibly empty groups) when it does.
- [x] 4.3 Create `GetMovieCreditsService` implementing the use case, throwing the existing `ResourceNotFoundException("MOVIE_NOT_FOUND", …)` when the port returns empty.

## 5. Outbound adapter + migration

- [x] 5.1 Add Flyway `src/main/resources/db/migration/V3__credits.sql` creating `people` (uuid pk, name) and `credits` (uuid pk, movie_id fk cascade, person_id fk, kind, nullable cast-only character/billing_order + crew-only department/job, shape `CHECK`, index on movie_id); keep H2- and Postgres-compatible; never edit `V2`.
- [x] 5.2 Create `PersonJpaEntity`, credit JPA entity/entities (single `credits` table with `kind`), and Spring Data repositories in `adapters/out/persistence`.
- [x] 5.3 Create `MovieCreditsPersistenceAdapter implements LoadMovieCreditsPort`: check movie existence (reuse `MovieJpaRepository`), load the movie's credits, map rows → domain `Cast`/`Crew`/`Person`; prefer derived queries (no native SQL). If any native SQL projects a uuid id, `CAST(col AS varchar)` + `UUID.fromString` per CLAUDE.md.

## 6. Inbound controller + wiring

- [x] 6.1 Implement `getMovieCredits` in `MovieController`: call `GetMovieCreditsUseCase`, map `MovieCredits → MovieCreditsEnvelope` (pattern-match `Cast`/`Crew`; omit `character` when absent; inline person as id+name with no `_links`), build `data._links.self` via `WebMvcLinkBuilder`.
- [x] 6.2 Extend `MovieController.toMovieDetail` to set `MovieLinks.credits` (link to `getMovieCredits`) via `WebMvcLinkBuilder`.
- [x] 6.3 Register `/movies/{id}/credits` in `PublicEndpoints.PATTERNS`; confirm `PublicEndpointsConsistencyTest` passes.
- [x] 6.4 Extend the demo seed (active when `test` profile is NOT active) with a movie having cast+crew (including a cast credit with no character and out-of-order billing) and a movie with empty credits; fixed UUIDs; confirm it does not run under `test`.

## 7. Tests (happy / edge / failure per requirement)

- [x] 7.1 Domain unit tests: `Person` validation; `Cast` (`billingOrder >= 1`, nullable character), `Crew` (non-blank department/job); `MovieCredits` orders cast by billing and crew by department-then-job case-insensitively; empty groups allowed.
- [x] 7.2 Application unit test: `GetMovieCreditsService` returns credits when the port is present (including empty groups); throws `ResourceNotFoundException("MOVIE_NOT_FOUND")` when the port is empty.
- [x] 7.3 Web `@WebMvcTest` (use case mocked): `200` with cast+crew ordered correctly; cast entry omits `character` when absent; empty-credits movie → `200` with both groups empty; unknown id → `404` problem+json; malformed id → `400` problem+json (no lookup); assert `data._links.self` present and no person `_links`; assert `getMovieById` now returns `data._links.credits`.
- [x] 7.4 Persistence Testcontainers/Postgres test: adapter loads a seeded movie's cast+crew mapped to domain (with a no-character cast credit); returns present-but-empty `MovieCredits` for an existing movie with no credits; returns `Optional.empty()` for an unknown movie id.
- [x] 7.5 (Optional) Extend `H2DefaultRuntimeSmokeTest` to assert `GET /movies/{seededId}/credits` returns `200` on the default runtime.
- [x] 7.6 Run `./gradlew build -x spotlessCheck` and confirm green (agents do not format; the pre-commit hook does).
