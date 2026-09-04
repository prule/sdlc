## 1. OpenAPI contract (contract-first)

- [x] 1.1 Add `paths/movie-credits.yaml` with `getMovieCredits` for `GET /movies/{id}/credits`,
  `security: []`, reusing the shared `MovieId` path param and the shared `CorrelationId` header param
  (so the generated `getMovieCredits(UUID id, UUID xCorrelationId)` matches the `getMovieById` arity),
  and `$ref`ing the reusable `404`/`400` responses; wire it into `openapi.yaml`.
- [x] 1.2 Add component schemas: `MovieCreditsEnvelope` (`data` + shared `Meta`), `MovieCreditsData`
  (`_embedded.cast`/`_embedded.crew` arrays, `_links.self` → shared `Link`), `CastCredit`,
  `CrewCredit`, and shared `CreditPerson` (`id`, `name`); reuse `Meta`/`Problem`/`Link`. No
  per-operation `<Operation><Status>Response*` schemas.
- [x] 1.3 Add the `credits` relation to the movie-detail `_links` schema (`MovieLinks`).

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate`; confirm generated `MoviesApi.getMovieCredits`, the credits
  DTOs, and the updated `MovieLinks` compile.

## 3. Domain

- [x] 3.1 Add `PersonId` (UUID value) and `Person` (record: `id`, `name`) — no Spring/JPA imports.
- [x] 3.2 Add `Credit` model: `sealed interface Credit` with `CastCredit` (person, character,
  billingOrder) and `CrewCredit` (person, department, job) records, plus a `MovieCredits` aggregate
  holding ordered `cast` and `crew` lists.
- [x] 3.3 Unit-test domain invariants (billingOrder positive; cast/crew separation; equality/ordering
  helpers).

## 4. Application (ports + use case)

- [x] 4.1 Add inbound port `GetMovieCreditsUseCase` and outbound port `LoadMovieCreditsPort`
  (`Optional<MovieCredits> load(MovieId)`).
- [x] 4.2 Implement `GetMovieCreditsService`: return credits when present, throw the existing
  `MovieNotFoundException` (`MOVIE_NOT_FOUND`) on empty-optional.
- [x] 4.3 Unit-test the service with a mocked port: happy path, empty-credits (present-but-empty), and
  unknown-movie (throws) paths.

## 5. Persistence + migration

- [x] 5.1 Add Flyway `V4__people_and_credits.sql`: `people` and `credits` tables (FKs to `movies` and
  `people`, `credit_type` CHECK, per-type CHECKs, `credits(movie_id)` + sort-column index). Never edit
  `V1`–`V3`.
- [x] 5.2 Add `PersonJpaEntity`, `CreditJpaEntity`, and the JPA repository with a single fetch-join
  query ordered per D-D (cast by billingOrder; crew by department, job; both ending in person name then
  credit id).
- [x] 5.3 Implement `MovieCreditsPersistenceAdapter` mapping JPA rows ↔ domain, partitioning by
  `credit_type`, returning `Optional.empty()` only when the movie row is absent.
- [x] 5.4 Testcontainers adapter test (real Postgres, own fixtures, `@Transactional` rollback via the
  shared `PostgresIntegrationTest` singleton): ordering correctness, empty-credits present-but-empty,
  and unknown-movie empty-optional.
- [x] 5.5 Testcontainers N+1 query-count guard: assert the credits load statement count is bounded and
  independent of credit count.

## 6. Inbound web adapter

- [x] 6.1 Implement `getMovieCredits` in the controller: call the use case, map to the generated DTOs,
  assemble `data._links.self` and the `cast`/`crew` embedded arrays via `WebMvcLinkBuilder`; no
  HATEOAS in domain/application; no person/item self links.
- [x] 6.2 Add the `credits` link to `getMovieById` (movie detail) via `linkTo(methodOn(...)
  .getMovieCredits(id, null))` — no extra port call/SQL.
- [x] 6.3 Add `/movies/{id}/credits` to `PublicEndpoints.PATTERNS`.

## 7. Cross-cutting tests + demo seed

- [x] 7.1 `@WebMvcTest`/MockMvc credits tests: envelope + `self` only + no `pagination`; cast item
  shape; crew item shape; empty-collection `200`; `404` problem+json (`MOVIE_NOT_FOUND`); `400` on
  malformed UUID; HAL discipline (no `_templates`, no person links); public (`security: []`).
- [x] 7.2 Extend `GeneratedApiCodegenTest` for `getMovieCredits` (shared envelope, no per-op
  duplicates) and for the movie-detail `_links` now declaring `credits`.
- [x] 7.3 Extend the movie-detail web test to assert `data._links` has exactly `self` + `credits`
  (credits href absolute to the credits endpoint) and add/confirm the movie-detail query-count guard
  is unchanged.
- [x] 7.4 Confirm `PublicEndpointsConsistencyTest` passes with the new pattern.
- [x] 7.5 Extend the `@Profile("demo")` seed loader with people + credits; keep tests independent of
  the seed (enabled/disabled seed-loader tests as per CAT-001).

## 8. Domain docs reconciliation

- [x] 8.1 Update `domain/`: glossary (billing-order direction, department/job free-text, credits are
  ordered), the `credits` HAL relation name (link to collection; `cast`/`crew` embedded rels),
  person-addressability rule (inline, no self link), bounded-contexts credits/people split, and the
  movie-detail representation update.

## 9. Validate

- [x] 9.1 `./gradlew build` (spotlessCheck + all tests) green; `openspec validate add-movie-credits
  --strict` passes.
