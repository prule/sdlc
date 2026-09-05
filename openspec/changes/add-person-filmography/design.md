## Context

See proposal.md - Why. This is the Person-side inverse of the CAT-003 movie-credits endpoint, built on the
existing `credits` + `movies` (+ genres) tables and the CAT-004 `catalog/people` slice. The neighbours set
the pattern: CAT-002 for the paginated HAL collection + `page`/`size` params + N+1 avoidance, CAT-003 for
the typed cast/crew capacity model and the credits table, CAT-004 for the addressable Person and the
`PERSON_NOT_FOUND` failure taxonomy. All decisions D1-D12 are settled in the ticket; this design records
only the residual technical choices (capacity typing, the bounded-query mechanism) and how the pieces wire
together under clean architecture.

Constraints: contract-first (OpenAPI 3.1 authored before code, controllers implement generated
interfaces); `GeneratedApiCodegenTest` must stay green (no per-operation `<Operation><Status>Response*`
duplicates); domain/application never import Spring/JPA/HATEOAS nor reference `_links`/`_embedded`; all DB
tests use the manual Testcontainers singleton (no H2, no `@Testcontainers`/`@Container` mixing).

## Goals / Non-Goals

**Goals:**
- A paginated, ordered, enveloped HAL filmography for one Person, reusing shared components with zero
  platform drift.
- A generated model that is clean: two concrete capacity types, each fully populated, rather than one flat
  object with four nullable fields.
- A bounded, filmography-size-independent SQL statement count, guarded by a query-count test.
- Person detail gains the reciprocal `_links.credits`, assembled web-adapter-only, adding no SQL.

**Non-Goals (design-level):**
- No person-collection endpoint, no filtering within a filmography, no new biographical fields (ticket
  Non-goals).
- No new capacity glossary terms; `character`/`billingOrder`/`department`/`job` are reused as-is.

## Decisions

### D-A: Capacity typing — `oneOf` with a discriminator, not a flat object
Each filmography item is a `MovieSummary` plus a `capacity`. The `capacity` is a `oneOf` of two concrete
schemas discriminated by a `type` field: `ActingCapacity` (`type` + `character` + `billingOrder`) and
`NonActingCapacity` (`type` + `department` + `job`). Each schema `required`s its own field set, so the
generated models are two clean types each fully populated — mirroring how CAT-003 modelled `CastCredit`
and `CrewCredit` as distinct concrete schemas rather than one object with everything nullable.
Alternative considered: a single `Capacity` object with all four fields optional + a `type` — rejected
because the generated model would carry four nullable fields and lose the per-kind invariant that the
spec (and the persistence CHECK constraint) guarantees. Because it is one embedded list (D8), a
discriminated union is the right analog of CAT-003's two-relation split.

Contract (added operation `getPersonFilmography` in `paths/person-filmography.yaml`):
```yaml
get:
  operationId: getPersonFilmography
  tags: [people]
  summary: Retrieve a single Person's filmography (the movies they are credited in)
  security: []
  parameters:
    - $ref: '../openapi.yaml#/components/parameters/CorrelationId'
    - $ref: '../openapi.yaml#/components/parameters/Page'
    - $ref: '../openapi.yaml#/components/parameters/Size'
    - name: id
      in: path
      required: true
      schema: { type: string, format: uuid }
  responses:
    '200':
      content:
        application/json:
          schema: { $ref: '../openapi.yaml#/components/schemas/PersonFilmographyEnvelope' }
    '400': { $ref: '../openapi.yaml#/components/responses/BadRequest' }
    '404': { $ref: '../openapi.yaml#/components/responses/NotFound' }
    '500': { $ref: '../openapi.yaml#/components/responses/InternalError' }
```
New schemas (in `components/schemas/person.yaml`, `$ref`-ing the existing `MovieSummary`):
```yaml
FilmographyItem:      # movie summary fields + one typed capacity
  allOf:
    - $ref: '../../openapi.yaml#/components/schemas/MovieSummary'
    - type: object
      required: [capacity]
      properties:
        capacity: { $ref: '../../openapi.yaml#/components/schemas/FilmographyCapacity' }
FilmographyCapacity:
  oneOf:
    - $ref: '#/ActingCapacity'
    - $ref: '#/NonActingCapacity'
  discriminator:
    propertyName: type
    mapping: { acting: '#/ActingCapacity', nonActing: '#/NonActingCapacity' }
ActingCapacity:     { required: [type, character, billingOrder], properties: { type: {type: string}, character: {type: string}, billingOrder: {type: integer, minimum: 1} } }
NonActingCapacity:  { required: [type, department, job],         properties: { type: {type: string}, department: {type: string}, job: {type: string} } }
PersonFilmographyData:      # _embedded.filmography + _links (self + pagination)
PersonFilmographyEnvelope:  # { data: PersonFilmographyData, meta: Meta }
```
`PersonFilmographyLinks` (in `components/schemas/common.yaml`) mirrors `MovieCollectionLinks`
(`self`/`first`/`last`/`prev`/`next`). `PersonLinks` gains a `credits` relation (`$ref` `Link`), mirroring
`MovieLinks` which already carries `self` + `credits`. The item reuses `MovieSummary._links.self` only —
no item-level `self`.

### D-B: Bounded queries — id-page-then-fetch (the CAT-002 pattern)
Two bounded phases, independent of filmography size:
1. Page the credit rows for the Person: one query selecting the credit ids (with their movie id +
   capacity) in the total order (`releaseYear` desc, `title` asc, terminal key) with `LIMIT size OFFSET
   page*size`, plus one `COUNT` for `totalElements`. The credit row already carries the capacity fields,
   so no per-item capacity fetch.
2. Fetch the movie summaries for that bounded page's movie ids, with genres loaded via a fetch-join /
   `@EntityGraph` so genres do not trigger a per-movie query.

Statement count is a small constant (count + id-page + movie-fetch(+genres)) regardless of how many
credits the Person has. Alternative considered: a single fetch-join across credits→movies→genres with
pagination — rejected because paginating a to-many fetch-join makes Hibernate paginate in memory
(unbounded fetch) and can duplicate rows; id-page-then-fetch is the established CAT-002 remedy and keeps
ordering stable across pages. A query-count guard test (Hibernate statistics) asserts the count does not
grow with filmography size.

### D-C: Ordering terminal key
Order by `releaseYear` DESC, `title` ASC, then the credit id (unique) as terminal tiebreak so the order is
total and identical across requests and stable across page boundaries (the CAT-002 cross-page lesson).
Using the credit id (not movie id) keeps distinct (movie, capacity) items on the same movie totally
ordered.

### D-D: Components touched (dependency direction inward-only)
- **domain** (`com.acme.catalog.people.domain`): a `Filmography` view / `FilmographyEntry` value object
  (movie summary value + a sealed capacity type: `ActingCapacity` | `NonActingCapacity`), plus paging as a
  value (page/size/total). No Spring/JPA/HATEOAS imports.
- **application** (`...people.application`): a `GetPersonFilmography` use case + an outbound port
  `PersonFilmographyPort` (load a Person's filmography page + total; signal person-not-found). The use case
  throws the existing `ResourceNotFoundException("PERSON_NOT_FOUND", ...)` for an unknown person (the exact
  CAT-004 pattern — no bespoke exception; a bespoke `DomainException` would wrongly hit the 400 fallback).
- **adapters/out/persistence** (`...people.adapters.out.persistence`): a `PersonFilmographyJpaAdapter`
  implementing the port over the existing `credits`/`movies`/genres tables, reusing the `catalog/people`
  side's `PersonJpaEntity`/`PersonJpaRepository` (do NOT create a third — the CAT-004 lesson) for the
  existence check. Implements D-B.
- **adapters/in/web** (`...people.adapters.in.web`): a controller implementing the generated
  `getPersonFilmography` interface; assembles `_embedded.filmography`, item `MovieSummary._links.self`, and
  the collection `self`/pagination links via `WebMvcLinkBuilder`. `PersonController` (CAT-004) gains
  `_links.credits` assembly. Register `/api/v1/people/*/credits` in the public-endpoint source of truth.

Dependencies point inward only: web/persistence depend on application ports; application depends on domain;
domain depends on nothing framework.

## Risks / Trade-offs

- [`oneOf` + discriminator codegen mismatch] → openapi-generator can emit an awkward hierarchy; confirm the
  generated capacity types compile and serialize with the discriminator, and keep `GeneratedApiCodegenTest`
  green. Fallback if generation is problematic: a single sealed capacity object is a last resort, but the
  discriminated union is preferred — validate early in the OpenAPI/codegen task.
- [Paginated to-many fetch-join → in-memory pagination / row duplication] → avoided by D-B id-page-then-
  fetch; the query-count + cross-page ordering tests guard against regressing into it.
- [Cross-page skip/dup on shared releaseYear/title] → mitigated by the unique credit-id terminal key
  (D-C); a cross-page test asserts every item appears exactly once.
- [Person-detail regression from the new link] → `_links.credits` is web-adapter-only; a person-detail
  test asserts the link resolves and the SQL statement count is unchanged from CAT-004.
- [Adding a third `PersonJpaEntity`] → reuse the `catalog/people` side; qualifiers/entity-names as
  established (CAT-004 lesson).

## Migration Plan

No DB schema change is anticipated: the endpoint reads the existing `credits`, `movies`, and genres tables
(CAT-001 movies migration, CAT-003 people/credits migration). If the query-count/ordering work shows a new
index is genuinely needed to stay bounded (e.g. on `credits(person_id)` or a movie release-year/title
sort), add it as a NEW Flyway migration `V<n>__<desc>.sql` — never edit an applied migration — and call it
out explicitly in the PR. Rollback: the change is additive and read-only; reverting the code removes the
endpoint and the person-detail `credits` link with no data migration. Any added index is dropped by a
follow-up migration if reverted.

## Open Questions

None that block specs, approach, or tasks. The capacity-typing (D-A) and bounded-query (D-B) residuals
from the ticket are resolved above; the only thing to verify during implementation is that the `oneOf`
discriminator generates cleanly (Risks), which does not change the contract or task breakdown.
