## 1. OpenAPI contract

- [x] 1.1 Add `Capacity`, `FilmographyEntry`, `PersonFilmographyData`, and `PersonFilmographyEnvelope` schemas to `components/schemas/person.yaml` (reusing `MovieSummary`, `Genre`, `MovieLinks`, `Envelope`, `Meta`, `Pagination`).
- [x] 1.2 Add `PersonFilmographyLinks` (self/first/last/prev/next; `self` required) to `components/schemas/common.yaml` alongside `MovieCollectionLinks`.
- [x] 1.3 Add `paths/people-filmography.yaml` — `get` `getPersonFilmography`, `tags: [people]`, `security: []`, params: `X-Correlation-Id`, shared `Page`/`Size`, `capacity` (enum `acting`/`non-acting`), `releaseYearFrom`, `releaseYearTo`; `200` `PersonFilmographyEnvelope`, `400`, `404`, `500`.
- [x] 1.4 Register `/people/{id}/credits` under `paths` and the new schemas under `components/schemas` in `openapi.yaml`.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate`; confirm `PeopleApi` gains `getPersonFilmography` and the new model classes are generated; keep `GeneratedApiCodegenTest` green (no per-operation response DTOs, no duplicated `Link`/`Problem`).

## 3. Domain (people slice, no Spring/JPA)

- [x] 3.1 Add `Capacity` (sealed interface: `Acting(Optional<String> character)`, `NonActing(String department, String job)`, exposing a coarse `Type { ACTING, NON_ACTING }`) and, if not reusable, a lightweight movie-summary value object for the entry (id, title, releaseYear, genres, optional runtime, optional rating).
- [x] 3.2 Add `FilmographyEntry` (movie-summary value + `Capacity`), `FilmographyCriteria` (optional capacity type + optional year bounds), `FilmographyPageRequest(int page, int size)`, and `Filmography(List<FilmographyEntry> content, int page, int size, long totalElements)` deriving `totalPages`.

## 4. Application (ports + service)

- [x] 4.1 Add inbound port `GetPersonFilmographyUseCase` (`Filmography getFilmography(UUID personId, FilmographyCriteria, FilmographyPageRequest)`).
- [x] 4.2 Add outbound port `LoadPersonFilmographyPort` (person-existence-aware: distinguishes unknown person from empty result).
- [x] 4.3 Add `GetPersonFilmographyService implements GetPersonFilmographyUseCase` — delegates to the port; throws `ResourceNotFoundException` (`PERSON_NOT_FOUND`) when the person is unknown; depends only on domain + port.

## 5. Outbound adapter (persistence; no migration)

- [x] 5.1 Add `PersonFilmographyJpaAdapter implements LoadPersonFilmographyPort`: check `people.existsById(personId)` first (unknown → not-found signal), reusing the existing person JPA repository.
- [x] 5.2 Phase 1 — native SQL over `credits c JOIN movies m`, `WHERE c.person_id = :personId` + optional capacity (`c.kind`) and release-year bound predicates, `ORDER BY m.release_year DESC, m.title ASC, c.id ASC`, `LIMIT/OFFSET`; select `CAST(c.id AS varchar)` and parse with `UUID.fromString` (H2/Postgres portability).
- [x] 5.3 Phase 2 — `COUNT(*)` under the identical predicate/param set (no `DISTINCT`); phase 3 — hydrate the page's credit ids with movie + genres fetch-joined and capacity fields, reorder to phase-1 order, map to `FilmographyEntry`.
- [x] 5.4 Register `/people/{id}/credits` verbatim in `PublicEndpoints.PATTERNS`; confirm `PublicEndpointsConsistencyTest` stays green.

## 6. Inbound controller

- [x] 6.1 Add `getPersonFilmography` to `PersonController`: annotate `@Validated`, map query params to `FilmographyCriteria` + `FilmographyPageRequest`, call the use case, map domain → generated DTOs.
- [x] 6.2 Assemble HAL `self`/`first`/`last`/`prev`/`next` links web-side via `WebMvcLinkBuilder`, preserving `capacity`/`releaseYearFrom`/`releaseYearTo`; populate `meta.pagination`; embed each entry's `MovieSummary` with its own `self` link to movie detail.

## 7. Tests (per layer: happy + edge + failure)

- [x] 7.1 Domain/unit tests: `Capacity`/`FilmographyEntry` mapping and `Filmography.totalPages` derivation.
- [x] 7.2 Service test: unknown person → `ResourceNotFoundException`; existing person → delegates and returns the page.
- [x] 7.3 Persistence tests (Testcontainers/Postgres, never H2): ordering (release-year desc, title asc, credit-id tiebreak) stable across page boundaries; one entry per capacity (act + direct same movie → two entries); capacity filter; release-year range (incl. inverted range → empty); count matches returned rows across filters; unknown person → not-found signal; empty person → empty with total zero.
- [x] 7.4 Web-layer tests (MockMvc): `200` shape + `meta.pagination` + navigation links preserving filters; default page/size; page beyond last → `200` empty, total > 0; filter matching nothing → `200` empty, total 0; unknown person → `404 PERSON_NOT_FOUND`; malformed id → `400` (no lookup); invalid `page`/`size` → `400`; unknown `capacity` value → `400`.
- [x] 7.5 Optionally extend `H2DefaultRuntimeSmokeTest` to hit `/people/{seededPersonId}/credits` and assert the person-detail `credits` link now resolves `200`.

## 8. Validate

- [x] 8.1 Run `./gradlew build` (compile + Spotless + all tests) and `openspec validate add-person-filmography --strict`; fix any findings.
