## 1. OpenAPI contract (contract-first)

- [x] 1.1 In `components/schemas/common.yaml`, add a `PersonLinks` object with required `self` and `credits` relations (both `$ref` `Link`; fixed relations, never `additionalProperties`); register `PersonLinks` in `openapi.yaml#/components/schemas`.
- [x] 1.2 Create `components/schemas/person.yaml` with `PersonDetail` (required `id` [uuid], `name`, `_links` → `PersonLinks`; no biographical/optional fields) and `PersonDetailEnvelope` (required `data` → `PersonDetail`, `meta` → shared `Meta`).
- [x] 1.3 Create `paths/people.yaml` with `GET /people/{id}` (`operationId: getPersonById`, `tags: [people]`, `security: []`, uuid path param + shared `CorrelationId` param; `200 → PersonDetailEnvelope`, `400/404/500 → shared responses`).
- [x] 1.4 Register the new person schema `$ref`s (`PersonDetail`, `PersonDetailEnvelope`) and the `/people/{id}` path in `openapi.yaml`.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate` (bundle → generate); confirm `PeopleApi.getPersonById` and the person DTOs are generated and bind to the shared `Meta`/`Link` components (no per-operation `<Operation><Status>Response*` duplicates); confirm `GeneratedApiCodegenTest` stays green.

## 3. Domain

- [x] 3.1 Create `com.acme.catalog.people.domain.model.Person` — record `(UUID id, String name)` with a factory validating id present and name non-blank; no Spring/JPA/HAL imports.

## 4. Application / ports

- [x] 4.1 Create inbound port `GetPersonDetailUseCase` (`Person getPersonDetail(UUID personId)`).
- [x] 4.2 Create outbound port `LoadPersonPort` (`Optional<Person> loadPerson(UUID personId)` — empty only when no person matches).
- [x] 4.3 Create `GetPersonDetailService` implementing the use case, throwing the existing `ResourceNotFoundException("PERSON_NOT_FOUND", …)` when the port returns empty.

## 5. Outbound adapter (no migration — `people` table already exists from V3)

- [x] 5.1 Create `PersonDetailJpaEntity` (read-only mapping of the existing `people` table: `id`, `name`) and `PersonDetailJpaRepository` (Spring Data) in `com.acme.catalog.people.adapters.out.persistence`.
- [x] 5.2 Create `PersonDetailPersistenceAdapter implements LoadPersonPort`: `findById` on the repository, map JPA entity → domain `Person`; use a derived query (no native SQL). If native SQL is ever introduced, `CAST(id AS varchar)` + `UUID.fromString` per CLAUDE.md.
- [x] 5.3 Confirm no Flyway migration is added and `V3__credits.sql` is unedited (this change only reads the `people` table).

## 6. Inbound controller + wiring

- [x] 6.1 Implement `getPersonById` in `PersonController implements PeopleApi`: call `GetPersonDetailUseCase`, map `Person → PersonDetailEnvelope` (id + name only, no extra fields), build `data._links.self` via `WebMvcLinkBuilder` and `data._links.credits` as `linkTo(methodOn(PeopleApi.class).getPersonById(id, null)).slash("credits")` (filmography URL assembled from the id alone).
- [x] 6.2 Register `/people/{id}` in `PublicEndpoints.PATTERNS`; confirm `PublicEndpointsConsistencyTest` passes.

## 7. Tests (happy / edge / failure per requirement)

- [x] 7.1 Domain unit test: `Person` factory validates id present and name non-blank (rejects null id / blank name); a valid `Person` exposes id and name.
- [x] 7.2 Application unit test (port mocked): `GetPersonDetailService` returns the person when the port is present; throws `ResourceNotFoundException("PERSON_NOT_FOUND")` when the port is empty.
- [x] 7.3 Web `@WebMvcTest` (use case mocked): `200` with `data.id`/`data.name` and no biographical fields; `data._links.self` resolves to the person and `data._links.credits` resolves to `/people/{id}/credits`; unknown id → `404 application/problem+json` with `code`/`correlationId`; malformed id → `400 application/problem+json` (no lookup, distinct from `404`).
- [x] 7.4 Persistence Testcontainers/Postgres test: adapter loads a seeded person by id mapped to domain `Person`; returns `Optional.empty()` for an unknown id; include a credit-less person fixture to prove person detail (and thus the filmography link) is served independent of any credits.
- [x] 7.5 (Optional) Extend `H2DefaultRuntimeSmokeTest` to assert `GET /people/{seededPersonId}` returns `200` on the default runtime.
- [x] 7.6 Run `./gradlew build -x spotlessCheck` and confirm green (agents do not format; the pre-commit hook does).
