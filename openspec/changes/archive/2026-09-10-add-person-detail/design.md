## Context

See proposal.md — Why. The catalog already serves movie detail (UC-001), search (UC-002), and a movie's credits (UC-003) over clean/hexagonal `com.acme.catalog.movies`, contract-first OpenAPI, the standard Envelope + HAL convention, a global `@RestControllerAdvice`, the redocly-bundle → openapi-generator pipeline, the H2-default / `postgres`-profile datasource, and a single `PublicEndpoints.PATTERNS`. UC-003 (`V3__credits.sql`) already created a `people` table (`id UUID PK`, `name TEXT NOT NULL`) and populates it (a person exists by virtue of being credited). This change adds the standalone person resource on top of that, reusing all those patterns and reading the existing table — so it is deliberately parallel to UC-001, minus optional fields, plus one onward link.

Ubiquitous language is fixed in `domain/glossary.md` (Person) and the outcomes/link naming in `domain/business-rules.md` and the glossary's CAT-004/CAT-005 notes. `bounded-contexts.md` lists `people` as a distinct capability that depends on `credits` for Person/Credit modelling.

## Goals / Non-Goals

**Goals:**
- Fit person detail into a new `com.acme.catalog.people` slice with no new architectural pattern — mirror the UC-001 movie-detail shape (UUID path param, two failure outcomes, Envelope + HAL, web-only link assembly).
- Read the existing `people` table with a read-only outbound adapter; add **no** migration.
- Carry the onward filmography (`credits`) link now, assembled from the person id alone, even though the target endpoint (UC-005) is not built yet.

**Non-Goals (design-level):**
- Filmography, people search, biographical fields — see proposal Non-goals.
- Adding `person._links.self` to the inline person on the movie-side credits response — deferred (see Open Questions).
- Any write path, caching, or a bespoke identifier scheme.

## Decisions

### 1. OpenAPI operation added (contract-first)

**Added:** `GET /people/{id}` — `operationId: getPersonById`, `security: []`, uuid path param + the shared `CorrelationId` param; `200 → PersonDetailEnvelope`, `400/404/500 → shared responses`. New `paths/people.yaml`, new `components/schemas/person.yaml`, and a new `PersonLinks` in `components/schemas/common.yaml`, all `$ref`ed from `openapi.yaml`. No existing operation or schema changes (movie detail, search, and credits are untouched), so this is additive and non-breaking.

Contract snippets (authored split spec under `src/main/resources/openapi/`):

```yaml
# paths/people.yaml — ADDED
get:
  operationId: getPersonById
  tags: [people]
  summary: Retrieve a person's core details by id
  security: []
  parameters:
    - $ref: '../openapi.yaml#/components/parameters/CorrelationId'
    - name: id
      in: path
      required: true
      schema: { type: string, format: uuid }
  responses:
    '200': { description: The person's core details, content: { application/json: { schema: { $ref: '../openapi.yaml#/components/schemas/PersonDetailEnvelope' } } } }
    '400': { $ref: '../openapi.yaml#/components/responses/BadRequest' }
    '404': { $ref: '../openapi.yaml#/components/responses/NotFound' }
    '500': { $ref: '../openapi.yaml#/components/responses/InternalError' }

# components/schemas/person.yaml — ADDED
PersonDetail:
  type: object
  required: [id, name, _links]
  properties:
    id:     { type: string, format: uuid }
    name:   { type: string }
    _links: { $ref: '../../openapi.yaml#/components/schemas/PersonLinks' }
PersonDetailEnvelope:
  type: object
  required: [data, meta]
  properties:
    data: { $ref: '#/PersonDetail' }
    meta: { $ref: '../../openapi.yaml#/components/schemas/Meta' }

# components/schemas/common.yaml — ADDED (fixed relations, never additionalProperties)
PersonLinks:
  type: object
  required: [self, credits]
  properties:
    self:    { $ref: '#/Link' }
    credits: { $ref: '#/Link' }   # resolves to GET /people/{id}/credits (the filmography, UC-005)
```

Schemas are authored as shared named components `$ref`ed into the envelope (as for movie detail/credits), so the generator binds to the shared `Meta`/`Link` components and does not emit per-operation `<Operation><Status>Response*` duplicates; `GeneratedApiCodegenTest` guards against reversion.

`PersonDetail` carries **no** optional/biographical properties (BR-3). `name` is required. Both `self` and `credits` are required on `PersonLinks` because every person has both.

### 2. Link-relation name for the filmography link: `credits`

The onward link points at `GET /people/{id}/credits` (the person's filmography). Per `domain/glossary.md` (CAT-005 note), person detail's filmography link is named `credits` (`_links.credits`), mirroring how movie detail's `credits` link points at `GET /movies/{id}/credits`. Using `credits` keeps the HAL vocabulary consistent across the two `{resource}/{id}` → `{resource}/{id}/credits` navigations. (The person-side *embedded* relation on the UC-005 response is `filmography`; that is a separate concern and not introduced here.)

**Alternative considered:** name the link `filmography`. Rejected — the glossary already settled `credits` as the link relation for symmetry with movie detail; the embedded relation name `filmography` is reserved for the UC-005 collection body.

### 3. Emitting the filmography link before UC-005 exists

BR-5 requires the `credits` link on person detail now, even though the filmography endpoint (UC-005) is not built. The href is assembled from the person id alone at zero DB cost — exactly the movies/credits pattern (`bounded-contexts.md`: "person detail never queries the filmography at read time; its `credits` link is assembled from the id alone"). Because no generated controller method exists for `/people/{id}/credits` yet, the href is built by appending `/credits` to the self URI via `WebMvcLinkBuilder`: `linkTo(methodOn(PeopleApi.class).getPersonById(id, null)).slash("credits")` — not a method reference to a not-yet-existing method. This keeps the context path (`/api/v1`) correct.

This diverges from UC-001, which deferred its `credits` link until the target existed (UC-003). The divergence is deliberate and ticket-driven (BR-5); the trade-off (a link that 404s until UC-005 ships) is recorded under Risks.

### 4. New `com.acme.catalog.people` slice (own model), not an extension of movies

`bounded-contexts.md` treats `people` as a distinct capability. So this change lives in a new package tree `com.acme.catalog.people`, dependency direction inward-only:
- **domain/model:** `Person` — record `(UUID id, String name)`, factory validating id present and name non-blank. (This duplicates the tiny value record already in `com.acme.catalog.movies.domain.model.Person`; duplication is accepted — each bounded context owns its language, and a two-field value record is far cheaper to duplicate than to couple two peer contexts. See Open Questions if consolidation is later preferred.)
- **application/port/in:** `GetPersonDetailUseCase` — `Person getPersonDetail(UUID personId)`.
- **application/port/out:** `LoadPersonPort` — `Optional<Person> loadPerson(UUID personId)` (`Optional.empty()` when no person matches — the "not found" signal; malformed id never reaches here).
- **application/service:** `GetPersonDetailService` implements the use case, throwing the existing `ResourceNotFoundException("PERSON_NOT_FOUND", …)` when the port is empty. No new exception type, no new handler.
- **adapters/out/persistence:** `PersonDetailJpaEntity` (read-only mapping of the `people` table), `PersonDetailJpaRepository` (Spring Data), `PersonDetailPersistenceAdapter implements LoadPersonPort` mapping JPA entity → domain `Person` via `findById`. Its own entity/repository (not the movies slice's `PersonJpaEntity`/`PersonJpaRepository`) so the people slice does not import another slice's adapter classes; both read the same table read-only, which is fine. Prefer Spring Data derived queries (no native SQL), so the CLAUDE.md native-query UUID-portability rule (`CAST(col AS varchar)` + `UUID.fromString`) does not apply here; if native SQL is ever introduced, apply it.
- **adapters/in/web:** `PersonController implements PeopleApi` (generated), maps domain `Person → PersonDetailEnvelope`, builds `data._links.self` and `data._links.credits` via `WebMvcLinkBuilder`. HAL assembly stays web-only; domain/application never import Spring HATEOAS.

### 5. Identifier scheme & two failure outcomes (reuse UC-001 exactly)

A person is addressed by a UUID path variable, matching the `people.id` column, the glossary ("stable opaque id"), and `standards/openapi.md` §4. This yields UC-004's two distinct outcomes at zero extra cost, identical to UC-001:
- A value that is not a well-formed UUID fails Spring's path binding → `MethodArgumentTypeMismatchException` → the existing global handler → `400 BAD_REQUEST` **before** the controller body runs (no lookup — flow 3a).
- A well-formed UUID matching no row → the service throws the existing `ResourceNotFoundException("PERSON_NOT_FOUND", …)` → `404` (flow 4a).

**Alternative considered:** a bespoke slug/short-code with a `@Pattern` validator. Rejected for the same reason as UC-001 — UUID already meets "stable, opaque, hides storage" and is the platform convention.

### 6. Public-endpoint registration & demo seed

Add `/people/{id}` **verbatim** (the OpenAPI path key) to `PublicEndpoints.PATTERNS`; `PublicEndpointsConsistencyTest` string-matches path keys against `security: []` operations, so a glob would fail it. The existing demo seed (`MovieDemoSeed`, active when the `test` profile is NOT active) already inserts people with fixed UUIDs via credits, so the H2 default runtime already serves a retrievable person — no seed change is required. `H2DefaultRuntimeSmokeTest` MAY be extended to assert `GET /people/{seededPersonId}` returns `200`; DB/persistence-logic tests stay on Testcontainers-Postgres.

## Risks / Trade-offs

- **[Dangling filmography link until UC-005]** `data._links.credits` points at `GET /people/{id}/credits`, which does not exist until UC-005 ships, so following it returns `404`/no-route in the interim. → Accepted and ticket-driven (BR-5 requires the link now, assembled from the id alone). The href is well-formed and will resolve once UC-005 lands; the link is navigational only, and clients that probe it get a normal error. If the reviewer prefers UC-001's "link only to existing targets" approach, the link can be withheld until UC-005 — flagged as an Open Question.
- **[Person model duplication across slices]** `Person` exists in both `catalog/movies` and `catalog/people`. → Accepted: a two-field value record is cheap to duplicate and avoids coupling two peer bounded contexts; consolidation into a shared kernel can be revisited if a third consumer appears.
- **[Codegen drift]** New cross-file schemas could reintroduce per-operation `<Operation><Status>Response*` duplicates. → Author as shared named components `$ref`ed into the envelope; `GeneratedApiCodegenTest` guards against reversion.
- **[People seeded only via credits]** The `people` table is populated as a side effect of credits curation, so a person with zero credits may not exist in the current data. → The endpoint reads by id regardless of credit count; the spec's "filmography link present even with no credits" scenario is covered by a persistence/web test with a seeded credit-less person fixture (Testcontainers), independent of the demo seed.

## Migration Plan

- **No Flyway migration.** The `people` table already exists (`V3__credits.sql`, UC-003) with exactly the columns this change reads (`id`, `name`); `V3` is never edited. Flyway state is unchanged in every environment (H2 default and Postgres profile).
- Forward: author OpenAPI (`paths/people.yaml`, `components/schemas/person.yaml`, `PersonLinks` in `common.yaml`, `$ref`s in `openapi.yaml`), bundle + generate, then add the `com.acme.catalog.people` code and register `/people/{id}` in `PublicEndpoints.PATTERNS`.
- Rollback: fully additive (new endpoint, new schemas, new code, new public pattern; no table or applied migration touched). Back out = revert the code and the OpenAPI/`PublicEndpoints` additions. No data migration or down-conversion needed.

## Open Questions

- **Deferred `person._links.self` on the movie credits response.** The glossary (CAT-004) anticipates that once a person resource exists, the inline person on each cast/crew credit gains a resolvable `person._links.self`. The UC-004 ticket lists movie-side credits as an explicit non-goal, so this change excludes it. Confirm whether that follow-up should be a separate small change now or bundled here. Either way it is additive and does not change this change's specs, approach, or task breakdown.
- **Whether to withhold the `credits` filmography link until UC-005 exists** (see Risks). The ticket (BR-5) says emit it now; noted in case the reviewer prefers the UC-001 "existing-targets-only" convention. This is answerable without changing the slice's structure — only whether the controller sets one link.
