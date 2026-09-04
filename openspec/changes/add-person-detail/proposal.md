## Why

CAT-003 exposed a **Person** only inline on a Movie's credits (`id` + `name`) and deliberately
withheld a `self` link because no `/people/{id}` endpoint existed yet. API consumers cannot resolve
the Person a Credit refers to without running their own catalog. CAT-004 delivers that endpoint and
closes the CAT-003 deferral — mirroring how CAT-003 closed the CAT-001 credits deferral.

## What Changes

- Add a new **`catalog/people`** capability with its first endpoint `GET /api/v1/people/{id}`:
  public (`security: []`), read-only, single-resource HAL detail of a Person.
- Person `data` exposes **exactly `id` + `name`** plus `_links.self` only (D5, D6). No biographical
  fields, no `_embedded`, no `_templates`.
- Unknown id → `404 application/problem+json` with stable `code` `PERSON_NOT_FOUND` (D8); malformed
  (non-UUID) id → `400`; both via the existing global `@RestControllerAdvice`.
- Register the new `/api/v1/people/{id}` pattern in the single `PublicEndpoints` source of truth (the
  `/movies*` patterns are already registered) so the public-endpoint consistency test holds.
- **Modify `catalog/credits`** (D7): now that `/people/{id}` exists, each inline Person in
  `GET /api/v1/movies/{id}/credits` (cast **and** crew) gains a resolvable `person._links.self`
  addressing `GET /api/v1/people/{id}`. The rest of the credits shape is unchanged (same fields,
  same cast/crew orderings).
- No **BREAKING** API change: the credits change is purely additive (a new link on an existing
  inline object). No **DB schema change**: the existing `people` table (`id`, `name`) from
  `V4__people_and_credits.sql` is reused; **no new Flyway migration**.

## Capabilities

### New Capabilities
- `catalog/people`: A Person as its own addressable, public, read-only resource. First endpoint is
  `GET /api/v1/people/{id}` (person detail: `id` + `name` + `_links.self`). Person collection and
  cross-filmography are out of scope (see Non-goals).

### Modified Capabilities
- `catalog/credits`: The inline Person on each cast and crew item now carries a resolvable
  `person._links.self` (previously prohibited because no `/people/{id}` endpoint existed). Affects the
  "Cast item exposes person, character, and billing order", "Crew item exposes person, department,
  and job", and "HAL discipline on credits responses" requirements.

## Non-goals

- **Person collection / search-list** (`GET /api/v1/people`) — a later ticket, as CAT-002 was to CAT-001.
- **Cross-filmography** (`GET /api/v1/people/{id}/credits`) — deferred (D6); hence no `filmography`
  link on person detail (no dangling links).
- **New biographical data** (birth date, biography, …) and any new `people` columns — deferred (D5).
- Any **write** to catalog data; the out-of-band curation/ingestion pipeline.
- **Rate-limiting** design.

## Impact

- **OpenAPI** (`src/main/resources/openapi/`): new `paths/people.yaml`, new `Person`/`PersonDetailEnvelope`/
  `PersonLinks` schemas, new `/people/{id}` path and schema registrations in `openapi.yaml`; a `self`
  link added to `CreditPerson` (new `CreditPersonLinks`). Regenerate stubs (`./gradlew openApiGenerate`).
- **New code** under `com.acme.catalog.people`: `Person`/`PersonId` domain, `GetPersonByIdUseCase` +
  `LoadPersonByIdPort`, persistence adapter (JPA entity ↔ domain over the existing `people` table),
  `PersonController` implementing the generated interface with web-adapter-only link assembly.
- **Modified code**: the credits web adapter (`MovieController` credits area) assembles the inline
  Person `self` link; `PublicEndpoints.PATTERNS` gains `/people/{id}`.
- **Errors**: not-found reuses the existing `ResourceNotFoundException` with code `PERSON_NOT_FOUND`,
  mapped to `404` by the existing `@RestControllerAdvice` (no new exception type, no new handler).
- **Domain docs** (`domain/`): Person addressability rule flips; bounded-contexts `people` detail now
  exists; HAL relation-naming note records inline Person now carries `self`.
- **No** DB migration and **no** breaking API change.
