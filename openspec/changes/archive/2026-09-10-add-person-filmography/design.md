## Context

See proposal.md — Why. This branch already has: the `catalog/people` slice (UC-004: `PersonController implements PeopleApi`, `GetPersonDetailUseCase`, `LoadPersonPort`, a read-only person JPA adapter over the `people` table) whose person-detail response emits a `data._links.credits` link at `GET /people/{id}/credits` that currently 404s; the `catalog/movies` slice with `MovieSummary`/`Genre`/`MovieLinks` schemas, the `Movie` aggregate, and the UC-002 two-phase paged native-query pattern (`MovieSearchPersistenceAdapter`); the UC-003 credits model (`credits` table + `CreditJpaEntity`, one row per capacity per person per movie, discriminated by `kind` `CAST`/`CREW`). Shared platform pieces exist and MUST be reused: `Envelope`/`Meta`/`Pagination`/`Problem`/`Link` schemas, the `Page`/`Size` query params (`page>=0 default 0`; `size 1..100 default 20`, which the generator turns into `@Min`/`@Max` → `ConstraintViolationException` → 400), the HAL collection-links convention (`self`/`first`/`last`/`prev`/`next`), `GlobalExceptionHandler` (maps `ConstraintViolationException`, `MethodArgumentTypeMismatchException`, `IllegalArgumentException`, and `ResourceNotFoundException`), `PublicEndpoints.PATTERNS` (string-matched against `security: []` operations by a consistency test), and `GeneratedApiCodegenTest`.

Key data fact: each `credits` row is already exactly one capacity for one person on one movie. So **one credit row = one filmography entry** — no fan-out or de-duplication is needed; a person with N capacities on one movie simply has N rows.

## Goals / Non-Goals

**Goals:**
- Resolve the person-detail `credits` link with a paged, filterable, ordered person-side filmography, reusing the UC-002 paging/query pattern and the UC-002 `MovieSummary` shape verbatim.
- Keep the entry a lightweight movie-summary + one-capacity annotation; the full credit detail stays on the movie side (UC-003).
- Preserve the malformed-vs-unknown-vs-empty distinctions exactly as UC-004/UC-002.

**Non-Goals (design-level):**
- No new table, column, index, or migration — the read is served from existing `credits`/`movies`/`movie_genres`/`people`.
- No configurable sort — one fixed order only (see Open Questions).
- No cross-slice dependency on the `movies` domain — the `people` slice defines its own lightweight filmography value objects (see Decisions).

## Decisions

### 1. Endpoint shape: `GET /api/v1/people/{id}/credits`, `operationId: getPersonFilmography`
Matches the `credits` link UC-004 already emits (`linkTo(getPersonById(id)).slash("credits")`), so no UC-004 change is needed. `security: []` (public). New `paths/people-filmography.yaml`, registered under `/people/{id}/credits` in `openapi.yaml#/paths` and added verbatim to `PublicEndpoints.PATTERNS`. Path `id` is `type: string, format: uuid` (framework binding → malformed id becomes `400` via `MethodArgumentTypeMismatchException`, exactly as UC-004, before any lookup). Alternative rejected: `GET /people/{id}/filmography` — the established link and the domain glossary already say `credits`; changing it would break UC-004's link and its tests.

**Query params:** reuse shared `Page`/`Size`; add `capacity` (enum `acting`/`non-acting`, optional) and `releaseYearFrom`/`releaseYearTo` (integer, optional). No `sort` param. An unknown `capacity` enum value fails binding → `MethodArgumentTypeMismatchException` → 400 (flow 3d), for free.

### 2. Contract additions (OpenAPI)
New schemas in `components/schemas/person.yaml`:
```yaml
Capacity:
  type: object
  required: [type]
  properties:
    type: { type: string, enum: [acting, non-acting] }
    character: { type: string }   # acting only, omitted when not recorded
    department: { type: string }  # non-acting only
    job: { type: string }         # non-acting only
FilmographyEntry:
  type: object
  required: [movie, capacity]
  properties:
    movie: { $ref: '.../MovieSummary' }   # reuse the UC-002 summary verbatim
    capacity: { $ref: '#/Capacity' }
PersonFilmographyData:
  type: object
  required: [_embedded, _links]
  properties:
    _embedded:
      type: object
      required: [filmography]
      properties:
        filmography: { type: array, items: { $ref: '#/FilmographyEntry' } }
    _links: { $ref: '.../PersonFilmographyLinks' }
PersonFilmographyEnvelope:  # data + meta (meta.pagination populated)
```
`PersonFilmographyLinks` (self/first/last/prev/next, `self` required) added to `components/schemas/common.yaml` alongside `MovieCollectionLinks`. The embedded `movie` reuses the existing `MovieSummary` (which already carries its own `MovieLinks.self` → movie detail). Reuse shared `Envelope`/`Meta`/`Pagination`. Regenerate stubs; keep `GeneratedApiCodegenTest` green (no per-operation response DTOs, no duplicated `Link`/`Problem`). Alternative rejected: a bespoke summary shape — reusing `MovieSummary` keeps the entry identical to UC-002 output and avoids drift.

### 3. Slice components (dependencies inward-only)
Extend `com.acme.catalog.people`:
- **domain/model** (no Spring/JPA): `FilmographyEntry` (record: a movie-summary value + a `Capacity`), a `MovieSummary`-equivalent value object local to the people slice (id, title, releaseYear, `List<Genre>`, `Optional<Integer> runtimeMinutes`, `Optional<Rating>` — or reuse the people slice's needs minimally), `Capacity` (sealed interface with `Acting(Optional<String> character)` and `NonActing(String department, String job)`, exposing a coarse `type`), `FilmographyCriteria` (record: `Optional<Capacity.Type> capacity`, `Optional<Integer> yearFrom`, `Optional<Integer> yearTo`), and a page-request/page pair (`FilmographyPageRequest(int page, int size)`, `Filmography(List<FilmographyEntry> content, int page, int size, long totalElements)` deriving `totalPages`). Mirrors the UC-002 `MoviePageRequest`/`MoviePage` records. The people slice defines its own value objects rather than importing `com.acme.catalog.movies.domain` — slices stay decoupled (the same reason both slices already have their own `Person`).
- **application**: inbound `GetPersonFilmographyUseCase` (`Filmography getFilmography(UUID personId, FilmographyCriteria, FilmographyPageRequest)`, throws `ResourceNotFoundException` when the person is unknown) + `GetPersonFilmographyService` (delegates to the port; depends only on domain + port). Outbound `LoadPersonFilmographyPort`.
- **adapters/in/web**: add the operation to `PersonController` (already implements `PeopleApi`), mapping domain → generated DTOs and assembling HAL links web-side only (`self`/`first`/`last`/`prev`/`next` via `WebMvcLinkBuilder`, preserving `capacity`/`releaseYearFrom`/`releaseYearTo`, exactly as `MovieController.collectionLinks`). Annotate the controller `@Validated` so the `@Min`/`@Max` param constraints fire.
- **adapters/out/persistence**: `PersonFilmographyJpaAdapter implements LoadPersonFilmographyPort`.

### 4. Persistence: person-existence check then two-phase paged native query
The adapter distinguishes unknown-person (`404`) from person-with-no-credits (empty `200`) by **checking `people.existsById(personId)` first** (reusing the existing person JPA repository); absent → signal not-found (empty `Optional`/dedicated result the service turns into `ResourceNotFoundException`), exactly as `MovieCreditsPersistenceAdapter` does for movies. Then, mirroring `MovieSearchPersistenceAdapter`:
- **Phase 1 (page ids):** native SQL over `credits c JOIN movies m ON m.id = c.movie_id`, `WHERE c.person_id = :personId` plus the optional predicates — capacity (`c.kind = :kind`, mapping `acting`→`CAST`, `non-acting`→`CREW`), and `m.release_year >= :from` / `<= :to` for the bounds supplied. `ORDER BY m.release_year DESC, m.title ASC, c.id ASC` (release year newest-first, title, then the **credit id** as the strict, unique terminal tiebreak — one credit = one entry, so `c.id` is guaranteed unique per entry and keeps paging stable across ties). `LIMIT :size OFFSET :page*:size`. **Select `CAST(c.id AS varchar)` and parse with `UUID.fromString`** — casting a `uuid` column straight to `java.util.UUID` throws `ClassCastException` on the H2 default (CLAUDE.md; the `MovieSearchPersistenceAdapter` pattern).
- **Phase 2 (count):** `SELECT COUNT(*) FROM credits c JOIN movies m ... WHERE <same predicate>`. One credit row = one entry, so a plain `COUNT(*)` under the **identical** predicate is correct — do not add a `DISTINCT` (that would collapse a person's two capacities on one movie into one and undercount). Count and page selection MUST share the exact predicate set so `totalElements`, `totalPages`, and `next`/`prev` never disagree.
- **Phase 3 (hydrate in order):** load exactly the phase-1 credit ids with their movie + the movie's genres fetch-joined (avoiding the genres N+1), plus the credit's capacity fields (`kind`, `character`, `department`, `job`), and reorder to the phase-1 id order. Map each to a `FilmographyEntry`.

An inverted year range (`from > to`) is a **valid** request that simply matches nothing (empty `200`) — no bounds check rejects it (same as UC-002).

### 5. Error mapping (all reuse existing handlers)
- Malformed `id` → framework UUID bind → `MethodArgumentTypeMismatchException` → 400 (before lookup).
- Unknown person → service throws `ResourceNotFoundException` → 404 `PERSON_NOT_FOUND` (reuse UC-004's code).
- `page`/`size` out of bounds → generated `@Min`/`@Max` → `ConstraintViolationException` → 400.
- Unknown `capacity` value → enum bind → `MethodArgumentTypeMismatchException` → 400.
- Empty match / page-beyond-last → normal `200` empty page (never 400/404).

## Risks / Trade-offs
- **Count/page predicate drift** → the count and phase-1 queries build from one shared `WHERE`/param map (the UC-002 discipline); a guard/integration test asserts `totalElements` matches the rows actually returned across filters.
- **UUID portability regression** → forgetting `CAST(... AS varchar)` passes on Postgres CI but breaks the single H2 smoke test; the adapter follows the documented pattern and an H2-path assertion covers it.
- **Statement count / N+1 on genres** → phase-3 fetch-joins genres over the bounded id set, keeping the read page-size-independent (same rationale as UC-002).
- **Reusing `MovieSummary` couples the entry to the movie contract** → acceptable and desired: BR-3 explicitly says the entry *is* the UC-002 movie summary; a future summary change should apply to both by design.

## Migration Plan
No DB migration. Deploy is additive: add the OpenAPI operation + schemas, regenerate stubs, add the slice code, register the public endpoint. Rollback is removing the operation/code — no data or schema state to revert. The existing demo seed already yields a person with a multi-capacity filmography on the H2 default, so `bootRun` demonstrates it immediately.

## Open Questions
- **Configurable sort orders.** UC-005 main-flow step 1 mentions the consumer stating "how the results should be ordered," but BR-3 defines only one order and lists no alternatives. This design ships the single default order and omits a `sort` param (smallest change satisfying the ticket). If a future ticket needs alternate orders, add a `sort` param mirroring UC-002 — additive, no spec change here. Flagged for confirmation; does not block implementation.
