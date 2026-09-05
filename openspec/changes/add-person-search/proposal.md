## Why

CAT-004 shipped single-person detail (`GET /api/v1/people/{id}`) and CAT-005 added a person's
filmography, but API consumers still cannot **find** people without knowing an id — they can only
resolve a known id or download the whole catalog. CAT-006 adds the person **collection**
`GET /api/v1/people`: the person-side analogue of CAT-002 movie search, letting consumers find people
by name and browse them alphabetically. This delivers the person collection/search-list listed as
*planned* under `catalog/people` in `domain/bounded-contexts.md`.

## What Changes

- Add `GET /api/v1/people`: a public, read-only, paginated, sorted, name-filtered collection of person
  **summary** resources, following the HAL collection convention (items in
  `data._embedded.people`, pagination links in `data._links`, counts in `meta.pagination`).
- Summary item fields (D8): exactly `id` (the Person's opaque UUID) and `name`, plus `_links.self` →
  `/people/{id}`. No biographical field, no `_embedded`, no `_templates`.
- Filter (D9/D10): `name` only — case-insensitive substring match (`?name=<term>` matches people whose
  `name` contains `<term>`). No role/department/known-for filters. Empty result set is a normal `200`.
- Sorting (D11): `sort=<field>,<dir>` over `name` only, asc/desc; **default `name` ascending**
  (alphabetical — deliberately unlike movie search's `releaseYear` desc, as a Person has no date-like
  field); unknown sort field → `400`.
- Pagination (D4): zero-based `page`; `size` default 20, max 100 (reuses shared `page`/`size` params);
  a valid page beyond the last → empty `200`; invalid `page`/`size` → `400` via a `@Validated`
  controller. Navigation links preserve the active `name`/`sort` params.
- Extend `com.acme.catalog.people` application/domain with a search/list use case + an outbound
  search/query port, and a Postgres persistence adapter over the existing `people` table.
  **Reuse the existing `PersonJpaEntity`/`PersonJpaRepository` — do not create a second.**
  **Performance**: a page of N people must issue a bounded number of SQL statements independent of N
  (lower N+1 risk than CAT-002/CAT-005 — a person summary has no to-many associations); a
  Testcontainers test guards the query count.
- Register the new `/people` pattern in the single `PublicEndpoints.PATTERNS` source of truth so the
  public-endpoint consistency test holds.
- **BREAKING**: none. Additive API operation. **No new Flyway migration anticipated** (the `people`
  table and its lookups already exist); if a case-insensitive-name index proves genuinely necessary to
  keep the query bounded, that is a new additive migration — flagged, never an edit to an applied one.

## Non-goals

- **Person detail** (CAT-004) and **filmography** (CAT-005) — this endpoint returns person summaries,
  not detail or credited movies.
- **Filtering people by role / department / "known-for" / "has credits"** — those live on credits, not
  on the Person; no person-level domain data exists for them. Out of scope, not to be invented.
- **New biographical Person fields** in the summary (birth date, biography, …) — still deferred
  (CAT-004 D5); this endpoint adds no `people` columns and no glossary terms for bio data.
- Any **write** to catalog data, the curation/ingestion pipeline, rate-limiting, ratings/reviews, and
  genre/keyword endpoints — separate capabilities.

## Capabilities

### New Capabilities

None. This adds a third endpoint to the existing `catalog/people` capability rather than a new one.

### Modified Capabilities

- `catalog/people`: **ADD** the person collection/search-list requirements
  (`GET /api/v1/people`: enveloped HAL collection, minimal person-summary item, name filter, sorting,
  pagination boundary behaviour, empty-result-200, invalid-param-400, public access, HAL discipline,
  and the bounded-query performance guarantee). The existing person-detail and filmography requirements
  are unchanged — **ADD-only, no MODIFIED**. A person summary points to detail via `self`; detail does
  not gain a reciprocal link back to the collection.

## Impact

- **OpenAPI (`src/main/resources/openapi/`):** new `paths/people-collection.yaml` operation
  (`listPeople`); new `PersonSummary`, `PersonCollectionData`, `PersonCollectionEnvelope`, and the
  `PersonCollectionLinks`/`PersonSummaryLinks` link schemas; reuse shared
  `Envelope`/`Meta`/`Pagination`/`Link`/`Problem`, the `page`/`size` parameters, and the `400`
  response. No per-operation `<Operation><Status>Response*` codegen duplicates
  (`GeneratedApiCodegenTest` stays green).
- **Code (`com.acme.catalog.people`):** new application search/list use case + outbound search port;
  domain value objects (search criteria, sort, page); a persistence adapter querying the existing
  `people` table via the reused `PersonJpaRepository`; a new `PersonController.listPeople` inbound web
  method. Dependency direction stays inward-only; HAL/link assembly is web-adapter-only.
- **DB**: none anticipated (no schema change). If a case-insensitive-name index is genuinely needed, a
  new additive Flyway `V<n>` migration (never editing an applied one) — flagged in design.
- **Security**: add `/people` to `PublicEndpoints.PATTERNS`.
- **Tests**: `GeneratedApiCodegenTest` extended for `listPeople`; a bounded query-count Testcontainers
  guard added.
- **Docs**: `domain/glossary.md` (Person summary term), `domain/business-rules.md` (people
  search/list policy), and `domain/bounded-contexts.md` (person collection now exists) updated.
