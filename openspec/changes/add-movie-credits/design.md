## Context

See proposal.md — Why. This is the first Person/Credit modelling in the system. It builds on
`catalog/movies` (Movie aggregate, `movies`/`genres`/`movie_genre` tables, `GET /api/v1/movies/{id}`),
`platform/hypermedia-links` (HAL-in-envelope convention), `platform/api-codegen` (contract-first
bundle→generate, no per-operation `<Operation><Status>Response*` duplicates), and reuses the CAT-002
patterns for N+1 avoidance and empty collections. Existing anchors:
`MovieController` (assembles links via `WebMvcLinkBuilder`), `MovieSearchPersistenceAdapter`,
`PublicEndpoints.PATTERNS`, `GeneratedApiCodegenTest`, `PublicEndpointsConsistencyTest`, and the shared
`Envelope`/`Meta`/`Problem`/`Link` OpenAPI components.

## Goals / Non-Goals

**Goals:**
- One operation `getMovieCredits` for `GET /api/v1/movies/{id}/credits`, unpaginated, public.
- Model Person and Credit in the domain without addressing Person as a resource yet.
- Bounded (no N+1) credit loading with a query-count guard.
- Additive `credits` `_link` on movie detail with zero extra DB cost.

**Non-Goals (design-level):**
- No `/people/{id}` operation, no person `self` link, no cross-filmography, no credit filtering/paging.
- No controlled vocabulary for `character`/`department`/`job` (free text this slice).
- No change to `V1`–`V3` or to the Movie aggregate's schema.

## Decisions

### D-A: Two embedded relations `cast` and `crew` (not one list with a discriminator)
`data._embedded.cast` and `data._embedded.crew` are separate arrays. **Rationale:** cast and crew have
**different item shapes** (cast = `character` + `billingOrder`; crew = `department` + `job`) and
**different orderings** (billing order vs department/job). A single `credits` list with a `creditType`
discriminator would force a union item schema where half the fields are always absent per row, muddy
the ordering contract, and push type-branching onto every consumer. Two typed relations give each a
clean, fully-populated schema and an unambiguous per-relation order. This still follows the HAL
collection convention (items under `data._embedded.<rel>`); it simply uses two rels. Alternative (one
list + discriminator) rejected for the union-schema and mixed-ordering cost. The proposal's suggested
single `credits` rel name is therefore not used for the embedded items; `credits` remains the
**movie-detail link** rel name (D-F), which is consistent — the link points at the collection whose
body carries `cast`/`crew`.

### D-B: Person is a value exposed inline; Credit is the aggregate loaded per movie
`Person` (record: `PersonId` + `name`) is a small identity+label value here, not a full aggregate (it
is not addressable — D2). `Credit` is modelled as a sealed hierarchy: `sealed interface Credit` with
records `CastCredit` (person, character, billingOrder) and `CrewCredit` (person, department, job), or
equivalently `MovieCredits` holding `List<CastCredit>` + `List<CrewCredit>`. The use case returns a
`MovieCredits` aggregate for a movie. Domain records only — no Spring/JPA/HATEOAS imports.

### D-C: One outbound port + one use case
- Inbound: `GetMovieCreditsUseCase` (application port in) → `GetMovieCreditsService`.
- Outbound: `LoadMovieCreditsPort` (application port out) with `Optional<MovieCredits> load(MovieId)` —
  `Optional.empty()` distinguishes **unknown movie** (→ 404) from **movie with no credits** (→ present
  but empty lists → 200). The service throws the existing `MovieNotFoundException`
  (code `MOVIE_NOT_FOUND`) when empty-optional, reusing the CAT-001 404 handler. Dependency direction
  is inward-only: adapters→application→domain; domain imports nothing outward.

### D-D: Persistence — single fetch-join query, ordered in SQL (no N+1)
The adapter issues **one** query joining `credits` → `people` for a movie id, ordered so cast and crew
both come back fully sorted; the adapter partitions rows into cast/crew by `credit_type`. Following the
CAT-002 avoidance lesson, ordering ends in a unique terminal key. Concretely:
`SELECT ... FROM credits c JOIN people p ON p.id = c.person_id WHERE c.movie_id = ? ORDER BY
c.credit_type, (cast: c.billing_order) / (crew: c.department, c.job), p.name, c.id`. Because it is a
single JOIN (not per-credit person lookups), the statement count is bounded and independent of credit
count. Movie existence: the port checks the `movies` row (a lightweight existence check) so a movie
with zero credits still returns present-but-empty rather than 404 — either a `movies` existence probe
plus the credits query (bounded 2 statements) or a left-join variant; either satisfies "bounded and
N-independent". Alternatives (`@EntityGraph`, id-page-then-fetch) are unnecessary here since there is
no pagination. JPA entities `PersonJpaEntity`/`CreditJpaEntity` live in `adapters/out/persistence` and
map to/from domain; domain objects are never `@Entity`.

### D-E: OpenAPI — new operation, reuse shared components, no codegen duplicates
Add `getMovieCredits` to the split spec (new `paths/movie-credits.yaml`, wired into `openapi.yaml`),
`security: []`. New component schemas: `MovieCreditsEnvelope` = shared `Envelope` shape (`data` +
`Meta`), `MovieCreditsData` (`_embedded` with `cast`/`crew`, `_links` referencing the shared `Link`),
`CastCredit`, `CrewCredit`, and a shared inline `CreditPerson` (`id`, `name`). Reuse shared
`Meta`/`Problem`/`Link` and the reusable `404`/`400` responses via `$ref`. No per-operation
`<Operation><Status>Response*` schemas — `GeneratedApiCodegenTest` stays green and is extended to
assert `getMovieCredits` returns the shared envelope type and that the movie-detail `_links` schema now
declares `credits`.

Contract snippet (illustrative):
```yaml
# paths/movie-credits.yaml
get:
  operationId: getMovieCredits
  security: []
  parameters: [ { $ref: '.../parameters/MovieId' }, { $ref: '.../parameters/CorrelationId' } ]
  responses:
    '200': { content: { application/json: { schema: { $ref: '.../MovieCreditsEnvelope' } } } }
    '404': { $ref: '.../responses/NotFound' }
    '400': { $ref: '.../responses/BadRequest' }
# schemas
MovieCreditsData:
  properties:
    _embedded: { properties: { cast: { type: array, items: { $ref: '#/.../CastCredit' } },
                               crew: { type: array, items: { $ref: '#/.../CrewCredit' } } } }
    _links:    { properties: { self: { $ref: '.../Link' } } }
CastCredit: { properties: { person: { $ref: '.../CreditPerson' }, character: {type: string},
                            billingOrder: {type: integer, minimum: 1} } }
CrewCredit: { properties: { person: { $ref: '.../CreditPerson' }, department: {type: string},
                            job: {type: string} } }
```
Also add the `credits` relation to the movie-detail `_links` schema (`MovieLinks`).

### D-F: Movie-detail `credits` link built in the web adapter, no extra query
`MovieController.getMovieById` adds `credits.href` via `linkTo(methodOn(MoviesApi.class)
.getMovieCredits(id, null))`. Arity note: like the existing `getMovieById(UUID id, UUID xCorrelationId)`,
the generated `getMovieCredits` takes **exactly two** parameters — the `{id}` path UUID and the
`X-Correlation-Id` header UUID (declared on the operation). The trailing `null` here is that
`xCorrelationId` header arg (irrelevant to link building), not a phantom second argument — do not add a
third. This is pure link assembly in the web adapter — it hits no port and adds
no SQL statement (guarded by the movie-detail query-count assertion). Modelled in specs as a MODIFIED
requirement on `catalog/movies` (self-only → self + credits).

### D-G: Public endpoint pattern with a path variable
Add `/movies/{id}/credits` to `PublicEndpoints.PATTERNS`. Spring MVC patterns match per path segment,
so `/movies/{id}` (single trailing segment) does **not** match `/movies/{id}/credits`; the new pattern
is required and does not overlap the existing `/movies/{id}` and `/movies` entries.
`PublicEndpointsConsistencyTest` (which cross-checks against `security: []` operations) covers it.

## Risks / Trade-offs
- [Two-rel shape diverges from the proposal's suggested single `credits` embedded rel] → Justified in
  D-A (distinct shapes + orderings); the `credits` name is retained as the movie-detail link rel, so
  the public vocabulary stays coherent. Recorded for the `domain/` glossary reconciliation.
- [Movie existence vs empty-credits ambiguity could regress into a 404 for a no-credit movie] →
  Port returns `Optional<MovieCredits>`; empty-optional only for a missing `movies` row. Covered by an
  explicit empty-collection scenario and a 404 scenario.
- [A naive mapping could reintroduce N+1 via lazy per-credit person loads] → Single fetch-join query;
  Testcontainers query-count guard asserts statement count is credit-count-independent.
- [Free-text `department`/`job` ordering is locale/case sensitive] → SQL `ORDER BY` with the unique
  terminal key (credit id) guarantees a total, stable order regardless; no controlled vocab promised.

## Migration Plan
- New Flyway `V4__people_and_credits.sql` (never edits `V1`–`V3`):
  - `people(id uuid PK, name varchar NOT NULL)`.
  - `credits(id uuid PK, movie_id uuid NOT NULL FK→movies(id), person_id uuid NOT NULL FK→people(id),
    credit_type varchar NOT NULL CHECK in ('CAST','CREW'), character varchar NULL, billing_order int
    NULL, department varchar NULL, job varchar NULL)` — CAST rows carry character/billing_order, CREW
    rows carry department/job (enforce via CHECK per credit_type). Index on `credits(movie_id)` (and
    the sort columns) to support the ordered single-query load.
- Extend the `@Profile("demo")` seed loader with people + credits for a demoable movie.
- Rollback: forward-only Flyway; a revert change would add `V5` dropping `credits` then `people`. No
  data migration of existing tables; `V4` is purely additive so rollout is safe and reversible by a new
  migration.

## Open Questions
- Rating vote count (CAT-001 residual) and rate-limiting design remain open product decisions,
  untouched here — deferrable, they do not affect these specs, the approach, or the tasks.
