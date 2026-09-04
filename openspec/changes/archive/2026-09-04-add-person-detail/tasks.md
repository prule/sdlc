## 1. OpenAPI contract (contract-first)

- [x] 1.1 Add `paths/people.yaml` with operation `getPersonById` (`GET /people/{id}`, `security: []`,
      `CorrelationId` param + `id` uuid path param, `200` `PersonDetailEnvelope`, `400`/`404`/`500` via
      shared responses) mirroring `paths/movies.yaml`.
- [x] 1.2 Add `components/schemas/person.yaml` with `Person` (`id`, `name`, `_links`) and
      `PersonDetailEnvelope` (`data` + `meta`); add `PersonLinks` (`self` only) to
      `components/schemas/common.yaml` mirroring `PingLinks`.
- [x] 1.3 In `credit.yaml`, add required `_links` to `CreditPerson` referencing a new `CreditPersonLinks`
      (`self` only) in `common.yaml` (D7 — additive, non-breaking).
- [x] 1.4 Register in `openapi.yaml`: the `/people/{id}` path, the `people` tag, and the new schemas
      (`Person`, `PersonDetailEnvelope`, `PersonLinks`, `CreditPersonLinks`).

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate`; confirm the generated `PeopleApi` interface + `Person`/
      `PersonDetailEnvelope`/`PersonLinks` DTOs and the updated `CreditPerson` `_links` appear, with no
      per-operation `<Operation><Status>Response*` duplicates. Keep `GeneratedApiCodegenTest` green.

## 3. Domain (no Spring/JPA)

- [x] 3.1 Add `com.acme.catalog.people.domain.model.PersonId` (UUID value object) and `Person`
      (record: `id`, `name`), mirroring `MovieId`/`Movie`. No framework imports.

## 4. Application (use case + ports)

- [x] 4.1 Add `application/port/in/GetPersonByIdUseCase` and `application/port/out/LoadPersonByIdPort`.
- [x] 4.2 Add `application/service/GetPersonByIdService` implementing the in-port over the out-port;
      when the port returns empty throw `new ResourceNotFoundException("PERSON_NOT_FOUND", "No person
      found for id: " + id.value())` — mirror `GetMovieByIdService` exactly (no new exception type).
      Depends only on domain.

## 5. Outbound adapter (reuse existing table — NO migration)

- [x] 5.1 Add `adapters/out/persistence/PersonJpaEntity` mapped to the existing `people` table
      (`id`, `name`) — no new Flyway migration; `PersonJpaRepository`.
- [x] 5.2 Add `PersonPersistenceAdapter` implementing `LoadPersonByIdPort`, mapping entity ↔ domain.

## 6. Inbound controllers + wiring

- [x] 6.1 Add `adapters/in/web/PersonController` implementing generated `PeopleApi`; build the Envelope
      and assemble `data._links.self` via `WebMvcLinkBuilder` (web-adapter-only), populating the
      generated `_links` DTO.
- [x] 6.2 No new handler. Confirm the existing `@ExceptionHandler(ResourceNotFoundException.class)` in
      `GlobalExceptionHandler` already yields `404` + `PERSON_NOT_FOUND` (from the exception's own `code`),
      and the existing `MethodArgumentTypeMismatchException` handler already yields `400` for a malformed
      (non-UUID) `id` path binding — both via the single global `@RestControllerAdvice`.
- [x] 6.3 Add `/people/{id}` to `PublicEndpoints.PATTERNS` (single source of truth).
- [x] 6.4 D7: in the credits web adapter (`MovieController` credits area), assemble each inline
      `person._links.self` (cast and crew) via `WebMvcLinkBuilder` pointing at `getPersonById`; leave the
      credits domain/application untouched.

## 7. Tests (happy / edge / failure per requirement)

- [x] 7.1 Domain/application unit tests: `GetPersonByIdService` returns the person (port mocked) and
      throws `ResourceNotFoundException` with code `PERSON_NOT_FOUND` on empty (mirroring the
      `GetMovieByIdService` unit test).
- [x] 7.2 `@WebMvcTest`/MockMvc for `PersonController`: `200` single-resource HAL shape (`data` = exactly
      `id` + `name` + `_links.self` absolute URI, no bio fields, no `_embedded`/`_templates`), `404`
      `PERSON_NOT_FOUND` problem+json, `400` malformed UUID (no persistence hit), and public access
      (no `401`/`403` without an auth header).
- [x] 7.3 Persistence adapter test against real Postgres via Testcontainers (no H2), reusing the existing
      singleton container pattern — do NOT mix `@Testcontainers`/`@Container` with the manual singleton
      (CAT-001 lesson); own fixtures inserted into `people`, assert load-by-id hit and miss.
- [x] 7.4 Credits web-layer test: assert each inline `person._links.self` (cast and crew) resolves to
      `GET /api/v1/people/{id}`; assert the rest of the credits shape (fields, cast/crew orderings) is
      unchanged.
- [x] 7.5 Public-endpoint consistency test passes with `/people/{id}` registered; keep all tests
      seed-independent (own fixtures), passing regardless of the demo seed.

## 8. Domain docs reconciliation

- [x] 8.1 Update `domain/glossary.md` (Person): a Person is now independently addressable at
      `/people/{id}` and carries a `self` link; the inline Person on credits now carries a resolvable
      `self` (HAL relation-naming note).
- [x] 8.2 Update `domain/business-rules.md` (Person addressability) and `domain/bounded-contexts.md`
      (`catalog/people` detail now exists; collection and cross-filmography remain planned).
