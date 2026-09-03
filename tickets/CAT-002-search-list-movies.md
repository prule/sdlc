# CAT-002: Search / list movies

**Type:** Feature
**Bounded context:** catalog (capability: search) — see domain/bounded-contexts.md
**Status:** Ready (open questions resolved by the author; see Decisions)

## User story
As an **API consumer (developer)** (domain/actors-and-personas.md), I want to **search and list Movies**
by title with optional filters (genre, release-year range, minimum rating), sorted and paginated, so I can
find and present the movies my users want without downloading or indexing the whole catalog myself.

## Background & domain context
The second `catalog` capability and the first **collection** endpoint over real catalog data. CAT-001
shipped single-movie detail (`GET /api/v1/movies/{id}`); this adds `catalog/search` as `GET /api/v1/movies`.

**Search** (domain/glossary.md) finds Movies by a title **query** optionally narrowed by **Filters**
(genre, release-year range, minimum rating), with **sorting** and **pagination**. An **empty result set is
a normal 200** (domain/business-rules.md). **Rating** is an aggregate **0–5** score-only value; **Genre**
is a controlled vocabulary of inline labels. Credits and Reviews are **not** in search results (deferred,
as in CAT-001).

Each result item is a **movie summary** — lighter than CAT-001 detail — with its own `self` link to
`GET /api/v1/movies/{id}` for full detail. This is a **collection** endpoint, so it follows the HAL
collection convention (standards/openapi.md §2/§2a; hypermedia-links spec), reusing the shared
Envelope/Meta/Problem/Link components, the reusable `page`/`size` parameters, and the reusable `400`
response — nothing new at the platform layer.

## Decisions (resolved with the author — requirements, not open questions)
- **Summary field set:** `id`, `title`, `releaseYear`, `genres` (inline labels), `runtimeMinutes`
  (omitted when absent), `rating` (aggregate 0–5, omitted when absent) — plus `_links.self`. **No** synopsis
  in summaries.
- **Sorting (in scope):** a `sort` capability over `title`, `releaseYear`, `rating`, each asc/desc.
  **Default: `releaseYear` descending** (newest first), with `title` ascending as a deterministic tiebreak.
- **Pagination:** `size` default **20**, maximum **100**; `page` is zero-based. Invalid `page`/`size`
  (`page<0`, `size<1`, `size>100`) → `400`.
- **Title match:** **case-insensitive substring** (`?title=matrix` matches "The Matrix").
- **Genre filter:** **multiple values, AND** — a movie must carry **all** supplied genres.
- **Release-year filter:** a **range** via optional `yearFrom` / `yearTo` (either bound alone is valid;
  both together form an inclusive range).
- **Minimum-rating filter:** `minRating` is **inclusive** (`rating >= minRating`); Movies with **no**
  rating are **excluded** when `minRating` is supplied.
- **Filters combine with AND** across the different filter types (title AND genres AND year-range AND minRating).

## Acceptance criteria
- [ ] **Default search returns a paginated first page.** `GET /api/v1/movies` (no params, no auth) → `200`,
      `application/json`, Envelope `{data, meta}`; `data._embedded.<rel>` is an array of movie **summary**
      items (each with `_links.self` to its `GET /api/v1/movies/{id}`); `data._links` carries `self`,
      `first`, `last`, and `next` (first page → **no `prev`**); `meta.pagination` has `page`, `size`
      (=20), `totalElements`, `totalPages`; results are ordered by the default sort (`releaseYear` desc,
      then `title` asc). (standards/openapi.md §2/§2a)
- [ ] **Summary shape.** Each item exposes `id`, `title`, `releaseYear`, `genres`, and (when present)
      `runtimeMinutes` and `rating` (omitted, not null, when absent) — and no synopsis/credits/reviews.
- [ ] **Title query narrows results (case-insensitive substring).** `?title=<term>` returns only Movies
      whose title contains `<term>` case-insensitively; `meta.pagination` counts reflect the matched subset.
- [ ] **Genre filter (multiple, AND).** `?genre=Drama&genre=Crime` returns only Movies carrying **both**
      genres.
- [ ] **Release-year range filter.** `?yearFrom=1990&yearTo=1999` returns only Movies with
      `1990 <= releaseYear <= 1999`; each bound works alone (`yearFrom` only, `yearTo` only).
- [ ] **Minimum-rating filter (inclusive; unrated excluded).** `?minRating=4` returns only Movies with
      `rating >= 4`; Movies with no rating are excluded.
- [ ] **Combined filters compose (AND).** title + genres + year-range + minRating together return only
      Movies satisfying **all** constraints, with correct `meta.pagination`.
- [ ] **Sorting.** `?sort=title,asc` (and `releaseYear`/`rating`, asc/desc) orders results accordingly;
      an unsupported sort field is rejected `400` problem+json (not ignored, not 500).
- [ ] **Empty result set is a normal 200.** A query matching no Movie (or an empty catalog) → `200` with an
      empty `data._embedded.<rel>`, `data._links.self` present and no `next`/`prev`, `meta.pagination`
      `totalElements: 0` (empty-page link/`totalPages` convention aligned with the platform HAL proof —
      see Open questions) — **not** a 404/error.
- [ ] **Pagination boundaries.** First page → `self`/`first`/`last`/`next`, no `prev`; last page →
      `self`/`first`/`last`/`prev`, no `next`; a valid page index beyond the last → empty `200` (no `next`),
      not a 400/404.
- [ ] **Invalid pagination/sort params → 400, not 500.** `page<0`, `size<1`, `size>100`, or an unknown sort
      field → `400`, `application/problem+json` conforming to `Problem` (stable `code`, `correlationId`),
      no `_links`/`_embedded`. The controller is `@Validated` so `page`/`size` bounds are enforced.
      (standards/openapi.md §2a; error-handling.md §3)
- [ ] **Public access.** `security: []`; never `401`/`403` for a missing token.
- [ ] **No N+1 genre loading.** A page of N Movies (each with genres) issues a **bounded** number of
      queries independent of N — not one genre query per row. A test/assertion guards the query count.

## Non-goals
- **Movie detail** (stays with CAT-001; search returns summaries only); **credits/reviews** in results.
- **Standalone `/genres`, `/keywords`, `/people`, `/ratings-reviews`** endpoints (separate tickets).
  Genre filtering uses the inline genre labels already on Movies.
- **Keyword / free-text-over-synopsis search** — title match + the three named filters only.
- **Rate-limiting** design; any **write**; the out-of-band curation pipeline.

## NFRs / constraints (cite standards)
- **Contract-first:** the operation, the movie **summary** `data` schema, the HAL collection
  `_links`/`_embedded` shape, and all query params (`title`, `genre` (repeatable), `yearFrom`, `yearTo`,
  `minRating`, `sort`, plus reusable `page`/`size`) are authored in the split OpenAPI 3.1 spec before
  controller code; controllers implement the generated interface; no hand-written DTOs
  (standards/openapi.md §1/§4/§5; api-codegen spec).
- **Reuse (no platform drift):** `$ref` the shared `Envelope`/`Meta`/`Problem`/`Link` schemas, the reusable
  `page`/`size` parameters, and the `400` response; codegen guarantees stay intact — **no** per-operation
  `<Operation><Status>Response*` duplicates (`GeneratedApiCodegenTest` stays green).
- **HAL representation:** Envelope `{data, meta}` as `application/json`, HAL `_links`/`_embedded` inside
  `data`; pagination **counts** in `meta.pagination`, pagination **link URLs** in `data._links` (no
  duplication); each embedded item carries `_links.self`; boundary rules. Errors are
  `application/problem+json`, never HAL. Link/collection assembly is **web-adapter-only**; domain/application
  return domain results + page metadata only, never import Spring HATEOAS. (standards/openapi.md §2/§2a)
- **Security:** public `security: []` (documented divergence from security.md for the public read surface).
- **Errors:** invalid params → `400` via the single global `@RestControllerAdvice`; `correlationId` on every
  response; no SQL/stack-trace leak (error-handling.md).
- **Clean architecture:** extend `catalog/movies` domain + application with a **search use case** and an
  **outbound search/query port** (taking the filter criteria + page/sort, returning matches + page
  metadata); a persistence adapter querying **Postgres** with pagination/filters/sort. Domain has no
  Spring/JPA imports. (clean-architecture.md)
- **Performance — bounded queries per page (known issue):** `MovieJpaEntity` maps `genres`
  `@ManyToMany(fetch = EAGER)`; a page of Movies would otherwise issue one genre query per row (N+1).
  Loading a page **must** stay within a bounded query count regardless of `size` (architect chooses the
  mechanism — fetch-join / `@EntityGraph` / projection). A test/assertion guards against per-row genre
  queries. (testing.md) [tracked follow-up from CAT-001 review]
- **Testing:** happy/edge/failure per requirement; domain/application unit tests (search port mocked);
  web layer `@WebMvcTest`/MockMvc (HAL collection shape, boundary links, 400 on invalid params/sort);
  search adapter against **real Postgres via Testcontainers (no H2)** with its own fixtures independent of
  the demo seed, including the **N+1 query-count guard** and filter/sort/pagination correctness.

## Dependencies
- **CAT-001 / `catalog/movies`** — the Movie aggregate, `movies`/`movie_genre` schema, and the
  `GET /api/v1/movies/{id}` target for each item's `self` link.
- **hypermedia-links / PLAT-003** — the HAL collection convention. **api-codegen** — the contract pipeline.

## Open questions (residual — minor, for the architect / Gate 1)
- **Empty-result pagination convention.** For zero matches, confirm `totalPages: 0` (vs `1`) and whether
  `first`/`last` are emitted on an empty collection — **align with whatever the platform's existing HAL
  collection proof already does** (don't invent a new convention).
- **Query-param names / sort syntax.** Proposed: `title`, `genre` (repeatable), `yearFrom`, `yearTo`,
  `minRating`, `sort=<field>,<dir>`, `page`, `size` — design-level naming; architect confirms against any
  platform precedent.

## Domain gaps (reconciled as part of this work)
- **Movie summary field set** — now defined (see Decisions); record it in domain/glossary.md so search and
  detail stay consistent.
- **Page-size policy** — resolved: default 20, max 100; update domain/business-rules.md (remove the TODO).
- **Sort policy** — resolved: sortable `title`/`releaseYear`/`rating`, default `releaseYear` desc; update
  domain/business-rules.md (remove the TODO).
