## 1. OpenAPI contract

- [x] 1.1 Add `PersonCollectionLinks` (`self` required; `first`/`last`/`prev`/`next` optional) and
      `PersonSummaryLinks` (`self` required) to `components/schemas/common.yaml`, mirroring the CAT-002
      `MovieCollectionLinks`/`MovieSummaryLinks`.
- [x] 1.2 Add `PersonSummary` (`id` uuid, `name`, required `_links` → `PersonSummaryLinks`),
      `PersonCollectionData` (`_embedded.people[]` + `_links` → `PersonCollectionLinks`), and
      `PersonCollectionEnvelope` (`data`, `meta`) to `components/schemas/person.yaml`; align the
      summary shape with the existing `Person` schema (`id`+`name`), no biographical field.
- [x] 1.3 Author `paths/people-collection.yaml`: `operationId: listPeople`, `tags: [people]`,
      `security: []`; reuse `CorrelationId`/`Page`/`Size` params; add `name` (string, optional) and
      `sort` (string, default `name,asc`); `200` `PersonCollectionEnvelope` + `X-Correlation-Id`, `400`
      `$ref BadRequest`, `500` InternalError.
- [x] 1.4 Wire `/people` → `paths/people-collection.yaml` in `openapi.yaml`, register the new schemas
      under `components.schemas`, and confirm no per-operation `<Op><Status>Response*` duplicates are
      introduced.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate` and confirm the generated people API `listPeople` returns
      `ResponseEntity<PersonCollectionEnvelope>` bound to the shared components (no duplicate DTOs).

## 3. Domain

- [x] 3.1 Add `PersonSortField {NAME}` and a `SortDirection {ASC, DESC}` (reuse the shared one if it
      already exists) plus a `PersonSort` value object with a default constant (`NAME`, `ASC`) — no
      Spring/JPA.
- [x] 3.2 Add `PersonSearchCriteria(Optional<String> name)` with null-safety in its compact
      constructor.
- [x] 3.3 Add `PersonPage(List<Person> items, int page, int size, long totalElements, int totalPages)`
      mirroring `FilmographyPage`/`SamplePage`.

## 4. Application (ports + use case)

- [x] 4.1 Add outbound port `SearchPeoplePort` in `application/port/out`:
      `PersonPage search(PersonSearchCriteria criteria, int page, int size, PersonSort sort)`.
- [x] 4.2 Add inbound port `SearchPeopleUseCase` in `application/port/in` and `SearchPeopleService` in
      `application/service` (constructor-injected `SearchPeoplePort`).

## 5. Outbound persistence adapter

- [x] 5.1 Implement the `SearchPeoplePort` adapter reusing the existing `PersonJpaRepository` (do NOT
      create a second repository/entity): a bounded two-statement query — a page query selecting at
      most `size` `PersonJpaEntity` rows with the optional case-insensitive name predicate
      `LOWER(p.name) LIKE LOWER(:pattern) ESCAPE '\'` (term wildcard-escaped: `\`→`\\`, `%`→`\%`,
      `_`→`\_`, then wrapped `%...%` — the derived `Containing` keyword does NOT escape, so use an
      explicit `@Query` with `ESCAPE`), ordered by `name` (asc/desc) then `id` ascending as the unique
      terminal tiebreak, with `LIMIT`/`OFFSET` from `page`/`size`; plus a `COUNT(*)` over the same
      predicate for `totalElements`/`totalPages`. Map via existing `toDomain`. No `JOIN FETCH`, no
      to-many load, so no HHH000104.
- [x] 5.2 **No new Flyway migration** unless a bounded-query need is demonstrated; if so, add a NEW
      additive `V<n>__people_name_index.sql` (functional `LOWER(name)` or `pg_trgm` GIN) — never edit
      an applied migration. Flag it in the change if added.

## 6. Inbound web adapter + security

- [x] 6.1 Implement `PersonController.listPeople` (class `@Validated`): parse `sort=<field>,<dir>`
      (unknown field/dir → `IllegalArgumentException`, which `onBadRequest` maps to `400`; do NOT use
      `ValidationException`, which maps to `422`), build `PersonSearchCriteria`/`PersonSort`, call the
      use case, map `Person` → `PersonSummary` with `_links.self` → `getPersonById`.
- [x] 6.2 Assemble `data._links` with the CAT-002 boundary logic (self/first/last always; prev if
      `page>0`; next if `page<totalPages-1`; empty → first/last at page 0, no next/prev) and carry the
      active `name`/`sort` query params into every navigation link; set `meta.pagination` counts.
- [x] 6.3 Add `/people` to `PublicEndpoints.PATTERNS` (context-relative syntax, alongside
      `/people/{id}` and `/people/{id}/credits`); confirm the public-endpoint consistency test passes.
- [x] 6.4 Confirm the global `@RestControllerAdvice` maps `ConstraintViolationException`,
      `MethodArgumentTypeMismatchException`, and the unknown-sort `IllegalArgumentException` all to
      `400 problem+json`; never `500`, no `_links`/`_embedded`. Do not re-map `ValidationException`.

## 7. Tests + docs

- [x] 7.1 Domain unit tests for `PersonSearchCriteria`, `PersonSort`, and `PersonPage` (validation,
      null-safety, default `name,asc`).
- [x] 7.2 Application unit test for `SearchPeopleService` with `SearchPeoplePort` mocked (criteria/sort
      pass-through, page returned).
- [x] 7.3 `@WebMvcTest`/MockMvc tests for `listPeople`: default HAL collection shape; person-summary
      exact fields (`id`+`name`+`_links.self` only, no bio/`_embedded`/`_templates`); name filter;
      sort asc/desc and unknown-sort → `400`; pagination boundaries (first/last/beyond-last empty-200
      and links-preserve-`name`/`sort`); empty-result-200; `400` on invalid `page`/`size`; public
      access (no 401/403 without an `Authorization` header).
- [x] 7.4 Testcontainers persistence test (shared manual Postgres singleton — do NOT mix
      `@Testcontainers`/`@Container`; own fixtures, cleaned up after): name filter including a term
      containing `%`/`_` to prove wildcard escaping; case-insensitive matching; sort asc/desc; and
      pagination determinism (page through the whole set, assert every Person exactly once with no
      cross-page skip/duplicate on duplicate-name fixtures). Seed-independent of the demo seed.
- [x] 7.5 Testcontainers bounded query-count guard: assert the SQL statement count for a multi-row page
      is bounded and does not grow with page size (Hibernate statistics/query counter); assert the
      CAT-001/002/003/004/005 paths do not regress.
- [x] 7.6 Extend `GeneratedApiCodegenTest`: `listPeople` returns
      `ResponseEntity<PersonCollectionEnvelope>`; keep the no-duplicate / single-shared-`Link` /
      single-shared-`Problem` assertions green.
- [x] 7.7 Docs: add the **Person summary** row to `domain/glossary.md` (mirroring **Movie summary**);
      record the people search/list policy in `domain/business-rules.md` (paginated; name-filtered
      case-insensitive substring; sortable by `name`, default `name` ascending — noting the deliberate
      difference from movie search's `releaseYear`-desc default); update
      `domain/bounded-contexts.md` so the person collection is no longer *planned* but exists.
- [x] 7.8 Run `./gradlew build -x spotlessCheck` and confirm all tests and the codegen guard pass (the
      pre-commit hook handles formatting; do not run spotlessApply).
