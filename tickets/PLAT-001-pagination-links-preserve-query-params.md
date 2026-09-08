# PLAT-001: Pagination navigation links must preserve filter & sort query parameters

**Type:** Technical
**Area:** platform (cross-cutting HAL hypermedia convention — see domain/bounded-contexts.md `platform`, standards/openapi.md §2a)
**Status:** Ready

## Problem / rationale
On every paginated collection endpoint, the HAL navigation links (`self`, `first`, `last`, `prev`,
`next`) in `data._links` are rebuilt from **only** `page` and `size`. Any other query parameter on
the original request — filters and sort — is dropped. As a result, paging through a *filtered* or
*non-default-sorted* collection navigates the client back to the **unfiltered, default-sorted**
result set: the links do not describe "the next/previous page of *this* query", they describe the
next/previous page of a different, broader query. This makes the pagination links incorrect for any
request that carries filters or a non-default sort, breaking a core promise of the HAL navigation
contract (standards/openapi.md §2a: pagination link URLs live in `data._links` and must let a client
walk the *current* result set).

This was surfaced by the **senior-dev review of the add-movie-search (UC-002 / CAT-002) change**.
In that change the behaviour matched its own spec (which scoped the links to `page`/`size` only), so
this is a **deliberate follow-up to tighten the shared convention**, not a regression introduced by
that change.

Confirmed occurrences today (the only two collection endpoints with pagination links; PingController
has none):
- `src/main/java/com/acme/catalog/movies/adapters/in/web/MovieController.java` — `pageLink(page, size)`
  (~line 169) calls `getMovies(null, page, size, null, null, null, null, null, null)`, passing `null`
  for `title`, `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating`, and `sort`. This is the live
  defect: every nav link on a filtered/sorted movie search loses the filters and the sort.
- `src/main/java/com/acme/platform/sample/adapters/in/web/SampleController.java` — `pageLink(page, size)`
  (~line 121) has the identical pattern. It has no filter/sort params to lose *today*, but shares the
  flaw and would regress the moment any are added. Treat it as part of the same pattern.

## Scope
- Fix the pagination-link behaviour so that **all** generated navigation links preserve the request's
  active filter and sort query parameters, on every current paginated collection endpoint
  (MovieController now; SampleController guarded against future regression).
- Establish the fix as the **shared, consistent approach** for all current *and future* paginated
  collection endpoints, so a new filtered/sorted collection inherits correct links rather than
  re-introducing this bug.
- Add test coverage proving filters + sort survive round-trips across pages.

## Out of scope
- The design/mechanism of *how* links are built or how parameters are carried through — that is the
  architect's call.
- Any change to the pagination **counts** in `meta.pagination`, to boundary semantics, or to the set
  of relations emitted (`self`/`first`/`last`/`prev`/`next`).
- Any change to which filters or sorts an endpoint supports, or their validation rules.
- Endpoints without pagination links (e.g. PingController), and single-resource `self` links.

## Constraints (standards that apply)
- **standards/openapi.md §2 / §2a** — pagination link URLs live in `data._links`; counts stay in
  `meta.pagination` with no duplication; links are absolute; `_links` shapes remain the fixed,
  contract-authored named relations (no new relations, no `additionalProperties`); success stays
  `application/json`.
- **standards/openapi.md §2a boundary rules** — `prev` absent on first page, `next` absent on last
  page; a valid page beyond the last is a normal empty `200`, not an error; invalid `page`/`size`
  stays `400 application/problem+json`. These must be unchanged.
- **standards/clean-architecture.md + openapi.md §2a layering** — link assembly stays a
  web-adapter-only concern; domain and application layers must not learn about `_links`/hypermedia or
  Spring HATEOAS.
- **standards/testing.md** — one requirement → tests covering happy path, an edge case, and a failure
  path; DB-backed tests use Testcontainers/Postgres, not H2.

## Acceptance / done criteria
- [ ] For a movie-search request carrying filters (e.g. `genre`, `releaseYearFrom`/`releaseYearTo`,
      `minRating`, `title`) **and** a non-default `sort`, **every** emitted link (`self`, `first`,
      `last`, `prev`, `next`) preserves **all** of those filter and sort parameters unchanged, and
      differs from `self` only in `page` (honouring the requested `size`).
- [ ] Following any emitted `first`/`last`/`prev`/`next` link returns a page of the **same** filtered,
      same-sorted result set (same `meta.pagination.totalElements`/`totalPages` and same ordering) as
      the originating request — i.e. filters + sort survive a round-trip across pages, both forward and
      backward.
- [ ] Edge case: a request with **no** filters and default sort still produces correct links
      (behaviour for the unfiltered/default case is unchanged — no spurious/empty params introduced).
- [ ] Boundary rules unchanged: no `prev` on the first page, no `next` on the last page; a valid page
      beyond the last page is still a `200` with empty `data._embedded.<rel>` (with its links still
      carrying the filters + sort); invalid `page`/`size` is still rejected `400`
      `application/problem+json`.
- [ ] All emitted links remain **absolute** and syntactically valid URIs.
- [ ] The same guarantee holds for the SampleController pattern, so it will not regress if/when it
      gains filter/sort params.
- [ ] Automated tests prove filters + sort are preserved across `first`/`prev`/`next`/`last` round
      trips (happy path), the unfiltered/default case still works (edge case), and invalid `page`/`size`
      still returns `400` (failure path).
- [ ] The "nav links preserve all active filter/sort query params" rule is recorded as a durable
      convention (standards/openapi.md §2a and/or the `adopt-hal-hypermedia` convention doc).

## Risks
- **Blast radius:** touches the shared HAL link-assembly used by every paginated endpoint; a careless
  fix could alter link shape for the currently-correct unfiltered case, or leak params where none were
  supplied. Mitigated by the "unchanged unfiltered case" and boundary acceptance criteria.
- Parameter round-tripping can introduce subtle encoding differences (multi-valued `genre`, sort
  direction, ranges); tests must assert semantic equality of the target query, not just presence.

## Resolved decisions (confirmed)
- **`self` carries the filter/sort params too** — it round-trips to the exact same request, consistent
  with `first/last/prev/next` (not page/size-only).
- **Omitted/default params are left absent** in the links — only parameters actually present on the
  request are preserved (defaults such as an unspecified `sort` are not echoed explicitly), matching
  today's unfiltered behaviour.
- **Record the durable convention.** As part of this work, "pagination navigation links preserve all of
  the request's active filter and sort query parameters" must be captured as a durable rule in the HAL
  hypermedia convention (standards/openapi.md §2a and/or the `adopt-hal-hypermedia` convention doc) so
  future paginated endpoints inherit it rather than re-introducing this bug. The exact venue/wording is
  the architect's call at plan time.

## Domain gaps
- None. No new glossary terms, personas, or business rules are required — this is a correctness fix to
  an existing technical convention. The relevant terms (**Search**, **Filter**, movie **sort**) already
  exist in `domain/glossary.md`.
