## Why

CAT-004 made a Person independently addressable at `GET /api/v1/people/{id}` but deliberately shipped
`_links.self` only, deferring any `credits`/`filmography` link because no cross-filmography endpoint
existed to point at. This change creates that endpoint — the set of Movies a Person is credited in, from
the Person side — so API consumers can show "what this person has worked on" and navigate from a Person to
their body of work, and person detail can finally carry the reciprocal link. It is the inverse of the
CAT-003 movie-credits endpoint and closes the CAT-004 D6 deferral in the same change, exactly as CAT-003
and CAT-004 each closed the previous ticket's deferral.

## What Changes

- Add `GET /api/v1/people/{id}/credits` — a Person's paginated filmography — as a second endpoint of the
  existing `catalog/people` capability. Public (`security: []`), read-only.
- One embedded relation `data._embedded.filmography` (not split cast/crew). Each item is a movie summary
  (reusing the CAT-002 `MovieSummary` schema + its `_links.self`) plus one typed capacity object: an
  acting capacity carries `character` + `billingOrder`; a non-acting capacity carries `department` +
  `job`. One item per (movie, capacity) — a Person credited in N capacities on a Movie yields N items.
- Ordering: `releaseYear` desc, then `title` asc, then a unique terminal key; total and stable across
  repeated requests and across page boundaries (no cross-page skip/dup).
- Paginated, reusing the CAT-002 convention: `page`/`size` (zero-based, default 20, max 100),
  `meta.pagination`, and HAL `self`/`first`/`last`/`next`/`prev`; invalid `page`/`size` → 400.
- Failure modes reuse the existing taxonomy: unknown person id → 404 `PERSON_NOT_FOUND` (existing
  `ResourceNotFoundException`); malformed UUID → 400 (existing type-mismatch handler); existing person
  with no credits → empty 200, not 404.
- Bounded queries (no N+1): the filmography loads in a bounded number of SQL statements independent of
  filmography size, guarded by a query-count test.
- **MODIFIES `catalog/people`:** person detail (`GET /api/v1/people/{id}`) gains `_links.credits` →
  `/people/{id}/credits`, assembled web-adapter-only (closing the CAT-004 D6 deferral).
- No new domain concept; a new *view* over the existing Person/Credit/Movie model. No breaking API
  change. No DB schema change is anticipated (reuses the existing `credits`, `movies`, genres tables); a
  new Flyway migration is added only if a new index proves genuinely necessary to stay bounded.

## Capabilities

### New Capabilities

None. This adds a second endpoint to the existing `catalog/people` capability rather than a new one.

### Modified Capabilities

- `catalog/people`: ADD the filmography endpoint requirements (`GET /api/v1/people/{id}/credits`:
  enveloped HAL collection, typed-capacity item, ordering, empty-200, pagination, 404/400 failures, HAL
  discipline, public, bounded queries). MODIFY the existing "HAL discipline on person detail" requirement
  so person detail now emits `_links.credits` (was `self` only).

## Impact

- **OpenAPI (`src/main/resources/openapi/`):** new `paths/person-filmography.yaml` operation
  (`getPersonFilmography`); new filmography item + capacity + envelope schemas; add `credits` to
  `PersonLinks`; reuse shared `Envelope`/`Meta`/`Problem`/`Link`, the `MovieSummary` schema, and the
  `Page`/`Size` parameters. No per-operation `<Operation><Status>Response*` codegen duplicates.
- **Code (`com.acme.catalog.people`):** new application use case + outbound port; new persistence adapter
  reading the existing `credits` + `movies` (+ genres) tables (reuse the `catalog/people` side's
  `PersonJpaEntity`/`PersonJpaRepository`; no third one); new web controller implementing the generated
  interface; `PersonController` gains the `_links.credits` assembly.
- **Public-endpoint registry:** register the new `/api/v1/people/{id}/credits` path pattern.
- **Tests:** domain/application unit, `@WebMvcTest` web-layer, Testcontainers persistence (N+1 guard,
  ordering, pagination), and a person-detail web test for the new `_links.credits`.
- **domain/** docs reconciled (bounded-contexts, glossary, business-rules) per the ticket's domain gaps.
- No breaking API change; no DB schema change anticipated.
