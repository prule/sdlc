## Context

See proposal.md — Why. This adds the first **collection** endpoint over `catalog/people`, the
person-side analogue of CAT-002 movie search. Existing building blocks in play:

- `PersonJpaEntity` maps a Person as `id` + `name` over the `people` table (CAT-003 migration), with a
  JPA→domain `toDomain` mapping already used by CAT-004/CAT-005. A person has **no to-many
  associations** on it — no genres, no credits — so the summary carries nothing lazy to load.
- The single-person path (`LoadPersonByIdPort` → `PersonPersistenceAdapter` → `PersonJpaRepository`)
  and the filmography path already exist and are reused; `PersonJpaRepository` is reused, **not**
  duplicated.
- The HAL collection convention is proven by CAT-002 (`SampleController`/movie search): items under
  `data._embedded.<rel>`, boundary links in `data._links`, counts in `meta.pagination`, and the
  empty-page link behaviour. This change follows that proof exactly.
- The OpenAPI pipeline is bundle-then-generate; `GeneratedApiCodegenTest` guards that operations bind
  to the shared component schemas and that no per-operation `<Op><Status>Response*` duplicates appear.
- Integration tests share the manual Testcontainers Postgres singleton (no `@Testcontainers`/
  `@Container` on the base — the CAT-001 lesson).

## Goals / Non-Goals

**Goals:**
- Deliver `GET /api/v1/people` contract-first, reusing the shared HAL/envelope components.
- Keep domain/application free of Spring/JPA/HATEOAS; confine link assembly to the web adapter.
- Guarantee a **bounded, page-size-independent** query count per page, with a test that guards it.
- Case-insensitive substring name match that stays bounded and escapes `LIKE` wildcards; a total,
  stable ordering (`name` + id terminal key) so pagination never skips or duplicates.

**Non-Goals (design-level):**
- Any new biographical Person field, any filter other than `name`, any sort field other than `name`.
- Changing the CAT-004 detail or CAT-005 filmography paths or `PersonJpaEntity`'s mapping.
- A `pg_trgm` trigram index for substring name search (deferred — see Open Questions).

## Decisions

### D1. Contract-first OpenAPI additions
- New collection path authored as `paths/people-collection.yaml`, wired into `openapi.yaml` at path key
  `/people` (the existing `/people/{id}` and `/people/{id}/credits` stay in their files).
  `operationId: listPeople`, `tags: [people]`, `security: []`.
- Parameters: reuse `CorrelationId`, `Page`, `Size`; add inline query params `name` (string, optional)
  and `sort` (string, default `name,asc`).
- New schemas in `components/schemas/person.yaml`: `PersonSummary` (`id` uuid, `name`, required
  `_links`) — aligning with the existing `Person` schema's `id`+`name` shape; `PersonCollectionData`
  (`_embedded.people[]` + `_links`); `PersonCollectionEnvelope` (`data`, `meta`). New link schemas in
  `common.yaml` mirroring the CAT-002 pattern: `PersonCollectionLinks` (`self` required;
  `first`/`last`/`prev`/`next` optional) and `PersonSummaryLinks` (`self` required). All registered in
  `openapi.yaml#/components/schemas`.
- Responses: `200` → `PersonCollectionEnvelope` (`application/json`) + `X-Correlation-Id` header; `400`
  → `$ref BadRequest`; `500` → `$ref InternalError`.
- Reuse shared `Envelope`/`Meta`/`Pagination`/`Link`/`Problem`, the `page`/`size` params, and the
  `400` response — **no** per-operation `<Op><Status>Response*` duplicates. Follows the established
  per-resource link-schema pattern (matching `MovieSummaryLinks`/`PersonLinks`) to avoid platform
  drift.

### D2. Name filter — case-insensitive, bounded, wildcard-escaped (a key decision)
The `name` filter is a case-insensitive substring match implemented in SQL as
`LOWER(p.name) LIKE LOWER(:pattern) ESCAPE '\'` where `:pattern` is `%<escaped-term>%`. The term MUST
be escaped before wrapping so that `%`, `_`, and the escape character in a user-supplied term match
**literally**, not as wildcards (the CAT-002 LIKE-escaping lesson): escape `\` → `\\`, `%` → `\%`,
`_` → `\_`, then wrap with leading/trailing `%`. Without this, a term like `50%` or `a_b` would
over-match.

### D3. Query shape — single bounded page query + count (the bounded-query mechanism)
Because a person summary has **no to-many associations**, there is no N+1 risk from lazy loading — the
CAT-002 two-step id-page-then-fetch dance is **not** needed. The adapter issues a bounded, fixed number
of statements per page independent of `size`:

1. **Page query.** Select `PersonJpaEntity` rows with the optional `LOWER(name) LIKE ... ESCAPE`
   predicate, ordered in SQL by `name` (asc/desc) then `id` ascending (the unique terminal tiebreak),
   with SQL `LIMIT`/`OFFSET` from `page`/`size`. This is one statement and returns at most `size` rows;
   Spring Data JPA's derived paging query fits (no `JOIN FETCH`, no to-many collection, so no
   HHH000104 in-memory pagination).
2. **Total count.** A `SELECT COUNT(*)` over the same `name` predicate → `totalElements`; `totalPages`
   derives from it and `size`.

Result: exactly two statements (page + count) regardless of page size. Mapping reuses the existing
`toDomain`. This may be realised with `PersonJpaRepository`'s Spring Data `Page<PersonJpaEntity>`
support (a `findByNameContainingIgnoreCase`-style derived query, or `@Query` with the explicit
`ESCAPE` clause for wildcard-literal correctness — the derived `Containing` keyword does **not** escape
wildcards, so an explicit `@Query` with `ESCAPE` is preferred to satisfy D2). The unique `id`
tiebreak is appended to the `Sort` (or the `@Query` `ORDER BY`) so the order is total and stable and
pagination cannot skip or duplicate across pages.

### D4. Search port shape (clean architecture)
- New outbound port `SearchPeoplePort` in `application/port/out`:
  `PersonPage search(PersonSearchCriteria criteria, int page, int size, PersonSort sort)`.
- New domain value objects (no Spring/JPA):
  - `PersonSearchCriteria(Optional<String> name)` with null-safety in its compact constructor.
  - `PersonSort(PersonSortField field, SortDirection direction)` with enums
    `PersonSortField {NAME}` and `SortDirection {ASC, DESC}` (reuse the existing `SortDirection` if one
    is already shared; otherwise add one local to `people`); a default constant (`NAME`, `ASC`). The
    `id` terminal tiebreak is applied by the adapter, not modelled as a sort field.
  - `PersonPage(List<Person> items, int page, int size, long totalElements, int totalPages)` mirroring
    the existing `FilmographyPage`/`SamplePage`.
- Inbound port `SearchPeopleUseCase` (or `ListPeopleUseCase`) in `application/port/in` +
  `SearchPeopleService` in `application/service` (constructor-injected `SearchPeoplePort`).
- The persistence adapter implementing `SearchPeoplePort` lives in `adapters/out/persistence`, reusing
  `PersonJpaRepository` and `toDomain`. Dependency direction stays inward-only.

### D5. Summary is a web-adapter representation, not a new domain type
Search reuses the existing `Person` aggregate for results — it already carries `id` + `name`, the whole
summary. `PersonController.listPeople` maps `Person` → the generated `PersonSummary` DTO and builds each
item's `_links.self` (→ `getPersonById`) and the collection `_links` via `WebMvcLinkBuilder`. This
keeps the domain lean and confines all HAL/link assembly to `adapters/in/web`. Domain/application never
import Spring HATEOAS nor reference `_links`/`_embedded`.

### D6. Web adapter — validation, sort parsing, link boundary logic
- `PersonController` implements the generated `listPeople` and is class-annotated `@Validated` so the
  generated `@Min`/`@Max` on `page`/`size` are enforced on the bean (the interface annotation alone is
  not honoured — same rationale as `SampleController`/`MovieController`).
- `sort=<field>,<dir>` is parsed in the adapter: map `<field>` to `PersonSortField` and `<dir>` to
  `SortDirection`; an unknown field or direction throws `IllegalArgumentException`, which the global
  `@RestControllerAdvice`'s `onBadRequest` already maps to `400 problem+json`. Do **not** use
  `ValidationException` (mapped to `422`). The advice's `ConstraintViolationException` and
  `MethodArgumentTypeMismatchException` handlers cover out-of-range/non-numeric `page`/`size` → `400`,
  never `500`.
- Collection `_links` reuse the exact boundary logic proven in CAT-002: `self`/`first`/`last` always;
  `prev` when `page > 0`; `next` when `page < totalPages - 1`; on empty (`totalPages == 0`),
  `first`/`last` address page `0` and no `next`/`prev`. Navigation links carry through the active
  `name`/`sort` query params.

### D7. Security registration
Add `/people` (context-relative Spring MVC pattern syntax, no `/api/v1` prefix, no `*`) to
`PublicEndpoints.PATTERNS`, alongside the existing `/people/{id}` and `/people/{id}/credits`. The
public-endpoint consistency test then reconciles it with the `security: []` operation.

### D8. Contract-guard test
Extend `GeneratedApiCodegenTest` with a test asserting `PeopleApi.listPeople` (or the generated people
API interface) returns `ResponseEntity<PersonCollectionEnvelope>`; the existing no-duplicate /
single-shared-`Link` / single-shared-`Problem` assertions remain and must stay green.

## Migration Plan

- **No new Flyway migration is anticipated.** The `people` table already exists (CAT-003) and the page
  + count queries are bounded without a new index at current dataset size.
- **If** a case-insensitive-name index proves genuinely necessary to keep the query bounded/performant,
  it is a **new additive** Flyway `V<n>__people_name_index.sql` (e.g. a functional
  `LOWER(name)` btree, or a `pg_trgm` GIN for substring) — **never** an edit to an applied migration.
  This is flagged, not scheduled; the change ships without it unless a measured need appears.
- **Rollback:** the API operation is additive; no existing consumer is affected. Any added index would
  be additive and non-breaking (rollback = `DROP INDEX`).

## Risks / Trade-offs

- [Case-insensitive substring `LIKE '%term%'` cannot use a plain btree index → full table scan on
  large catalogs] → acceptable at current dataset size; a `LOWER(name)` functional index or `pg_trgm`
  GIN is the fix when it matters (deferred, see Open Questions).
- [The derived Spring Data `Containing` keyword does not escape `LIKE` wildcards] → mitigated by using
  an explicit `@Query` with `ESCAPE '\'` and manual term escaping (D2), guarded by a persistence test
  using a term containing `%`/`_`.
- [Pagination skip/duplicate if the sort is not total] → mitigated by appending the unique `id`
  terminal tiebreak to every sort (D3), guarded by a cross-page determinism test.
- [Testcontainers rows leaking into the shared singleton container] → each integration test class
  inserts its own fixtures and cleans them up (delete-after), asserting only against its own ids, per
  the CAT-001 precedent and testing.md.

## Open Questions

- **Name index.** Recommend shipping without a new index; add a `LOWER(name)` functional or `pg_trgm`
  index only when catalog size/latency warrants it (new additive migration then). Confirm acceptable
  for now — does not change these specs or the task breakdown.
- **Empty-result convention.** Resolved by aligning with the existing CAT-002 HAL collection proof
  (`totalPages` and `first`/`last` on an empty collection). Confirm no divergence is desired.
- **Reuse of `SortDirection`.** If a shared `SortDirection` enum already exists from CAT-002 in a
  common/domain package, reuse it; otherwise add one scoped to `people`. Confirm the preferred home.
