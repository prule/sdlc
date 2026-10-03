## Why

UC-002: an API consumer who does not know any movie identifiers needs to find the movies that match what they are looking for — or browse the whole catalog — one page at a time, in an order they choose, and then go on to any movie's details (UC-001). Today the catalog only answers lookups by identifier, so there is no way in. This change also establishes the paging convention that every later list capability (filmography, people search) reuses.

## What Changes

- Add a public, read-only operation, `GET /api/v1/movies`, that searches or browses the catalog. Every criterion is optional: a title term, one or more genres, a release-year range, a minimum rating, an order, a page and a page size.
- Each result is a **movie summary**: identifier, title, release year, genres (alphabetical), plus runtime and rating when recorded. No synopsis, keywords, credits or reviews. Each summary links to the movie's details (`GET /movies/{id}`). Unrecorded details are absent, never invented.
- Matching: title matches any part of the title ignoring case (a blank term means no title criterion); several genres mean "must carry all", recognised ignoring case; the release-year range is inclusive at both ends; the minimum rating is inclusive and leaves out unrated movies. All criteria combine.
- Ordering by title, release year or rating, ascending or descending. Default is release year newest first, then title A–Z. A final tiebreak makes every order complete and stable across pages. Unrated movies always come last when ordering by rating.
- Paging: 20 per page by default, at most 100, pages counted from `0`. Every page reports the total number of matches and links to itself and to the first, previous, next and last pages, each keeping the request's criteria, order and page size. An empty result or a page after the last is a normal `200` with no entries, never "not found".
- A search asked in a way that isn't allowed (unsupported order, page before the first, page size outside 1–100, genre not in the curated vocabulary, reversed year range, minimum rating outside 0–5, or an ill-typed value) is refused with `400 BAD_REQUEST` before any searching. The problem body now names the offending request parameter(s) in a new optional `errors` member, without echoing the value.
- Write methods on `/movies` are refused with `405` and change nothing.
- Platform: the shared `Meta` gains an optional `pagination` member, and the shared `Problem` gains an optional `errors` member. Both are additive.

No breaking API change: one new operation and two new optional response members. **No DB schema change**: the existing `movie`, `genre` and `movie_genre` tables already hold everything search needs, so no Flyway migration is added.

## Non-goals

- Retrieving one movie's full details (UC-001); search only links to them.
- Filtering by keyword (UC-007) or by cast, crew or person (UC-003…UC-006).
- Searching by anything other than title (synopsis, free text), fuzzy, misspelling-tolerant or relevance-ranked matching.
- "Any of these genres" matching.
- Listing the genre vocabulary itself (UC-007).
- Localised or translated titles, and locale-aware collation of titles beyond a stable, case-insensitive order.
- Rate limiting (still a TODO in `domain/business-rules.md`).
- Cursor/keyset paging, or caching of totals.
- Creating, correcting or removing movies or genres.

## Capabilities

### New Capabilities
<!-- none: search is part of the existing catalog/movies capability -->

### Modified Capabilities
- `catalog/movies`: ADDED requirements for searching and browsing movies (criteria, summary content, ordering, paging and navigation, refusal of disallowed searches, empty results, read-only collection, identical behaviour in both runtime modes).
- `platform/uniform-responses`: ADDED the reusable paged-collection form (counts in `meta.pagination`, items in `data._embedded`, criteria-preserving navigation links) and the rule that a client fault attributable to named request parameters lists them in `errors`. MODIFIED "Service is read-only and public" so the offered catalog collection path `/movies` refuses writes with `405`.

## Impact

- **API:** `paths/movies.yaml` gains the `movies` collection path (`searchMovies`); `components/schemas/movie.yaml` gains `MovieSummary`, `MovieSearchEnvelope` and related link/embedded schemas; `components/schemas/common.yaml` gains `Pagination` and `InvalidParam` and the optional `Meta.pagination` / `Problem.errors` members; new `components/parameters/common.yaml` with the shared `page` and `size` parameters. Generated `MoviesApi` gains `searchMovies`.
- **Code:** new search slice in `com.acme.catalog.movies` (domain criteria/order/page value objects, `SearchMoviesUseCase`, `SearchMoviesPort`, `GenreVocabularyPort`, a Criteria-API search query in the persistence adapter, a controller method). Shared kernel gains `InvalidCriteriaException`. `GlobalExceptionHandler`/`ProblemFactory` populate `errors` for parameter faults and map the AOP constraint-violation path to `400`. `ResponseMetaFactory` gains a pagination overload.
- **DB:** none (no migration). Standalone sample movies are reused unchanged for demo browsing.
- **Tests:** new domain, service, persistence (Testcontainers) and web tests; `ReadOnlyRefusalTest`, `InterfaceDescriptionContractTest`, `GeneratedApiCodegenTest`, `FailureKindsTest`, the H2 smoke test and the shared runtime-mode assertions are extended.
