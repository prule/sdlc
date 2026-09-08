## Why

On paginated collection endpoints, the HAL navigation links (`self`, `first`, `last`, `prev`, `next`)
in `data._links` are rebuilt from ONLY `page` and `size`; every other active query parameter (filters
and sort) is dropped. Paging through a *filtered* or *non-default-sorted* collection therefore
navigates the client back to the unfiltered, default-sorted result set. This breaks the HAL navigation
contract (standards/openapi.md §2a: the links must let a client walk the *current* result set), and is
a live defect on the movie-search endpoint.

## What Changes

- Every generated pagination navigation link (`self`, `first`, `last`, `prev`, `next`) on every current
  paginated collection endpoint SHALL preserve the request's active filter and sort query parameters
  unchanged — differing from `self` only in the `page` value.
- `MovieController.getMovies` (live defect): threads the request's actual `title`, `genre`,
  `releaseYearFrom`, `releaseYearTo`, and `minRating` arguments through link assembly so they appear in
  every emitted link. `sort` is threaded too, but with one required transformation: because its OpenAPI
  schema declares `default: releaseYear,desc` the bound `sort` argument is never null (Spring resolves
  it to the default even when the client omitted it), so a `sort` equal to the server default is
  normalised back to absent before link assembly (see design Decision 1); a non-default `sort` is
  forwarded unchanged.
- `SampleController.listSamples` (guard): the same pass-through pattern is applied so the pattern won't
  regress if/when it gains filter/sort params. No behavior change today (it has no such params).
- Only parameters actually present on the request are preserved; omitted/default params are left absent
  — no spurious or empty params are introduced. Note the `sort` case specifically: because the contract
  defaults `sort`, an omitted-or-default sort is detected (parsed value equals the server default) and
  left absent rather than echoed.
- The durable convention "pagination nav links preserve all active filter/sort query params" is
  recorded in standards/openapi.md §2a and the `platform/hypermedia-links` spec.
- Tests prove filters + sort survive first/prev/next/last round-trips (forward and backward).

No breaking API change: `_links` relations, shapes, media types, `meta.pagination` counts, and boundary
semantics are all unchanged. The links merely carry the query params they always should have.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `platform/hypermedia-links`: adds a requirement that collection pagination links preserve the
  request's active filter/sort query parameters on every navigation relation, with only
  present-on-request parameters echoed. Existing boundary, media-type, relation-set, and layering
  requirements are unchanged.

## Impact

- Code (web adapters only): `MovieController` (live fix), `SampleController` (guard). Link assembly
  stays a web-adapter concern; domain and application layers are untouched and remain unaware of
  `_links`/hypermedia.
- No OpenAPI contract change: no new relations, no `additionalProperties`; the `_links` schemas are
  unchanged. No DB schema / Flyway change.
- Standards doc: standards/openapi.md §2a gains the preserve-query-params rule.
- Tests: new web-slice/integration coverage for MovieController; the SampleController guard.

## Non-goals

- Changing HOW links are built (the mechanism is the design's call).
- Changing pagination counts in `meta.pagination`, boundary semantics, or the set of emitted relations.
- Changing which filters/sorts an endpoint supports, or any validation rule.
- Endpoints without pagination links (`PingController`) and single-resource `self` links.
