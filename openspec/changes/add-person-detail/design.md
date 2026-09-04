## Context

See proposal.md - Why. This change adds a single-resource read (`GET /api/v1/people/{id}`) and closes
the CAT-003 person-`self`-link deferral on credits (D7). The codebase already has the exact patterns to
mirror: `catalog/movies` for a single-resource detail (`GetMovieByIdUseCase`, `MovieController`,
`MoviePersistenceAdapter`) and `catalog/credits` for the inline Person. The `people` table
(`id uuid PK`, `name varchar(500)`) already exists from `V4__people_and_credits.sql`; person detail
exposes exactly those two columns (D5), so **no schema change** is needed. All decisions D1-D8 are
resolved in the ticket and are not reopened here.

## Goals / Non-Goals

**Goals:**
- Contract-first `GET /api/v1/people/{id}` reusing the shared Envelope/Meta/Problem/Link components and
  the split-spec + codegen pipeline, with no per-operation `<Operation><Status>Response*` codegen
  duplicates (`GeneratedApiCodegenTest` stays green).
- A real `Person` domain aggregate (promoting today's inline-only Person) with an inward-only dependency
  chain: web adapter → use case (in-port) → out-port ← persistence adapter.
- Add the reciprocal inline-Person `self` link on credits (D7) purely in the credits web adapter — the
  credits domain/application stay untouched and `_links`-free.

**Non-Goals (design-level):**
- No caching, no projection/DTO caching layer — a person is a trivial 2-column read.
- No shared "person" module between `catalog/people` and `catalog/credits`; credits keeps its own inline
  `CreditPerson` representation and merely gains a link assembled at the web layer. Extracting a shared
  Person is deferred until a third consumer justifies it (avoids premature coupling).

## Decisions

### OpenAPI operations added/changed

**Added** operation `getPersonById` — new `paths/people.yaml`, registered at `/people/{id}` in
`openapi.yaml`. Mirrors `getMovieById`:

```yaml
# paths/people.yaml
get:
  operationId: getPersonById
  tags: [people]
  summary: Retrieve a single Person's detail by id
  security: []
  parameters:
    - $ref: '../openapi.yaml#/components/parameters/CorrelationId'
    - name: id
      in: path
      required: true
      schema: { type: string, format: uuid }
  responses:
    '200':
      description: The person detail
      headers:
        X-Correlation-Id: { $ref: '../openapi.yaml#/components/headers/CorrelationId' }
      content:
        application/json:
          schema: { $ref: '../openapi.yaml#/components/schemas/PersonDetailEnvelope' }
    '400': { $ref: '../openapi.yaml#/components/responses/BadRequest' }
    '404': { $ref: '../openapi.yaml#/components/responses/NotFound' }
    '500': { $ref: '../openapi.yaml#/components/responses/InternalError' }
```

New schemas (`components/schemas/person.yaml`, registered in `openapi.yaml`):

```yaml
Person:
  type: object
  required: [id, name, _links]
  properties:
    id: { type: string, format: uuid }
    name: { type: string }
    _links: { $ref: '../../openapi.yaml#/components/schemas/PersonLinks' }
PersonDetailEnvelope:
  type: object
  required: [data, meta]
  properties:
    data: { $ref: '#/Person' }
    meta: { $ref: '../../openapi.yaml#/components/schemas/Meta' }
```

`PersonLinks` (added to `components/schemas/common.yaml`, mirroring `PingLinks`): `required: [self]`,
property `self` → `#/Link`, `self` only (D6).

**Changed** operation `getMovieCredits` (D7): no path/operation change; the `CreditPerson` schema in
`components/schemas/credit.yaml` gains a required `_links` of a new `CreditPersonLinks` (`required:
[self]`, `self` → `#/Link`). This is additive (a new field on an existing inline object) — not breaking.

### Components touched (dependency direction inward-only)

New package `com.acme.catalog.people`, mirroring `catalog/movies`:
- `domain/model`: `Person` (record: `PersonId id`, `String name`), `PersonId` (UUID value object). No
  Spring/JPA imports.
- `application/port/in`: `GetPersonByIdUseCase`. `application/port/out`: `LoadPersonByIdPort`.
  `application/service`: `GetPersonByIdService` (implements the in-port, depends on the out-port,
  throws `ResourceNotFoundException("PERSON_NOT_FOUND", ...)` when the port returns empty, mirroring
  `GetMovieByIdService`). Depends only on domain.
- `adapters/out/persistence`: `PersonJpaEntity` (mapped to the existing `people` table),
  `PersonJpaRepository`, `PersonPersistenceAdapter` (implements `LoadPersonByIdPort`, maps entity ↔
  domain).
- `adapters/in/web`: `PersonController` implements the generated `PeopleApi` interface; assembles
  `data._links.self` via Spring HATEOAS `WebMvcLinkBuilder` and populates the generated `_links` DTO.

Dependency direction: web → application(in-port); application → domain; persistence(out-adapter) →
application(out-port) + domain. Domain imports nothing framework-related. Confirmed inward-only.

Not-found is signalled by throwing the existing `ResourceNotFoundException("PERSON_NOT_FOUND", ...)`
(no new exception type), which the existing `@ExceptionHandler(ResourceNotFoundException.class)` maps to
`404` using the exception's own `code` (D8) — exactly as `catalog/movies` does with `MOVIE_NOT_FOUND`.
A bespoke `PersonNotFoundException extends DomainException` would instead hit the `DomainException`
fallback handler and return `400`, so it is deliberately avoided. Malformed UUID is rejected at
path-binding by the existing `MethodArgumentTypeMismatchException` handler → `400`, before the use case
runs. No new handler is added.

`PublicEndpoints.PATTERNS` gains `/people/{id}` (single source of truth; the consistency test asserts it
agrees with the `security: []` OpenAPI operations).

### D7 link assembly stays web-adapter-only

The credits inline-Person `self` link is assembled where the credits response DTO is built (the
credits web adapter / `MovieController` credits area) using `WebMvcLinkBuilder` pointing at
`getPersonById`. The credits domain and application layers are not touched, keeping them free of
`_links`/HATEOAS — preserving the CAT-003 clean-architecture posture. No new SQL is issued: the Person
`id` is already loaded for each credit, so building the link is pure in-memory assembly (the credits
N+1/bounded-query requirement is unaffected).

### Migration plan (Flyway)

**No migration.** The `people` table already has `id` and `name` (V4). Person detail reads those two
columns; no column is added or altered. Rollback: reverting the change removes only application/contract
code and the `/people/{id}` `PublicEndpoints` entry; no data or schema is touched, so rollback is a plain
redeploy with no DB step. A future biographical-fields ticket (out of scope, D5) would add a **new**
Flyway migration — never edit V4.

## Risks / Trade-offs

- **Archive-time header mismatch on the credits MODIFIED** (the CAT-003 failure mode) → the delta copies
  the three promoted requirement headers ("Cast item exposes person, character, and billing order",
  "Crew item exposes person, department, and job", "HAL discipline on credits responses") and their
  scenario names verbatim; `openspec validate --strict` is run on this change and a `--dry-run` archive
  is simulated before hand-off so a header drift surfaces now, not at archive.
- **Codegen duplicate DTOs** (`<Operation><Status>Response*`) breaking `GeneratedApiCodegenTest` → reuse
  the shared Envelope/Problem responses via `$ref` exactly as `getMovieById` does; no inline per-status
  response schemas.
- **Duplicated Person representation** across `catalog/people` (`Person`) and `catalog/credits`
  (`CreditPerson`) → accepted deliberately (see Non-Goals); a shared extraction is premature now and would
  couple two capabilities for two fields.
