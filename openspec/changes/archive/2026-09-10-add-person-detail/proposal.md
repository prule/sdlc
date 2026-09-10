## Why

A consumer can already reach a person by name from a movie's credits (UC-003) or, later, from people search (UC-006), but there is no way to retrieve that person as an addressable resource. UC-004 requires that, given a known person identifier, a consumer can obtain the person's core details — their identifier and name — and navigate onward to that person's filmography, so consumers can present a person's page. This is the smallest next increment on the catalog and introduces the `catalog/people` capability the later filmography (UC-005) and people-search (UC-006) features build on.

## What Changes

- Add a public, read-only `GET /api/v1/people/{id}` operation (`operationId: getPersonById`) returning one person's core details wrapped in the standard success Envelope.
- Person core details are **deliberately minimal** — identifier and name only, **no biographical fields** (BR-3). This mirrors UC-001's movie detail but for a person.
- Return the person as a HAL resource: `data._links.self` resolves to the person, and `data._links.credits` resolves to that person's filmography sub-resource (`GET /people/{id}/credits`, UC-005) — assembled from the person's identifier alone, at zero extra DB cost, without enumerating any movies (BR-5).
- Distinguish two failure outcomes exactly as UC-001 does (BR-4): a **malformed** identifier is rejected `400 application/problem+json` before any lookup (framework UUID path binding, reusing the existing handler); a **well-formed** identifier matching no person is `404 application/problem+json` (`code: PERSON_NOT_FOUND`).
- Add OpenAPI schemas (`PersonDetail`, `PersonDetailEnvelope`) and a `PersonLinks` fixed-relation `_links` object (`self`, `credits`); register `paths/people.yaml` and the new schemas in `openapi.yaml`, reusing the shared `Envelope`/`Meta`/`Link` components; regenerate stubs.
- Introduce a `com.acme.catalog.people` slice (domain, application ports/service, inbound web adapter, outbound persistence adapter) reading the **existing** `people` table.
- Register `/people/{id}` in the single `PublicEndpoints.PATTERNS` source of truth (verbatim path key), keeping `PublicEndpointsConsistencyTest` green.

**No new database migration.** The `people` table (`id UUID PK`, `name TEXT NOT NULL`) already exists from `V3__credits.sql` (UC-003); this change only reads it. No applied migration is edited.

**No BREAKING changes.** Purely additive: a new capability, a new endpoint, new schemas. No existing contract, schema, or behavior changes.

## Capabilities

### New Capabilities
- `catalog/people`: Retrieve one person's core details (identifier + name only) by their stable opaque identifier over the public read API — required detail fields, the onward filmography navigation link, malformed-vs-not-found outcomes, and public/read-only access.

### Modified Capabilities
<!-- None. catalog/movies (including the movie-side credits response) is unchanged; adding person._links.self to the inline person on movie credits is a UC-004 non-goal — see Non-goals. -->

## Impact

- **API:** new operation `getPersonById` (`GET /api/v1/people/{id}`), `security: []` (public). New `paths/people.yaml`, new `components/schemas/person.yaml` (`PersonDetail`, `PersonDetailEnvelope`), new `PersonLinks` in `components/schemas/common.yaml`, all `$ref`ed from `openapi.yaml`. Regenerate stubs (`openApiGenerate`); keep `GeneratedApiCodegenTest` green.
- **Code:** new `com.acme.catalog.people` slice — domain `Person` (record: id + name), inbound `GetPersonDetailUseCase` + `GetPersonDetailService`, outbound `LoadPersonPort` + `PersonDetailPersistenceAdapter` (+ a read-only JPA entity/repository over the `people` table), and `PersonController implements PeopleApi` with web-only HAL link assembly. Reuse the existing `ResourceNotFoundException` + global `@RestControllerAdvice` (no new exception type, no new handler). Register `/people/{id}` in `PublicEndpoints.PATTERNS`.
- **Data:** none — reads the existing `people` table (from `V3__credits.sql`). No Flyway migration. The existing demo seed already populates people (via credits) with fixed UUIDs, so the H2 default runtime already has a retrievable person.
- **Tests:** per-layer happy/edge/failure — person returned with id+name, `data._links.self` and `data._links.credits` present, unknown id `404`, malformed id `400` (no lookup). Persistence tests use Testcontainers/Postgres (never H2). Optionally extend `H2DefaultRuntimeSmokeTest` to hit `/people/{seededPersonId}`.

## Non-goals

- A person's **filmography** — enumerating their credited movies (`GET /people/{id}/credits`, UC-005). It is reachable *from* here via the `credits` link but not performed here.
- Searching or listing **people** (`GET /people`, UC-006).
- Presenting a movie's credits from the **movie side** (UC-003), and **adding `person._links.self` to the inline person on the movie credits response**. The domain glossary anticipates that link once a person resource exists (CAT-004), but the UC-004 ticket lists movie-side credits as an explicit non-goal, so it is deferred to a small additive follow-up (see design Open Questions).
- Any biographical fields on a person (birth date, biography, images) — BR-3.
- Any create/update/delete of catalog data — the API stays read-only.
