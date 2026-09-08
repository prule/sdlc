## 1. MovieController — fix the live defect

- [x] 1.1 Thread the request's filter/sort arguments (`title`, `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`) into link assembly: widen `pageLink` (and the `collectionLinks` helper) to accept and forward them, replacing the `null`-only `getMovies(...)` call in `pageLink`. For the FIVE filters, pass the RAW nullable request arguments (they have no OpenAPI default, so an omitted filter is `null` and stays absent).
- [x] 1.2 Handle `sort` specially — it is NEVER null. Because `movies-collection.yaml` declares `sort`'s schema `default: releaseYear,desc`, the generated `getMovies` binds `sort` to `"releaseYear,desc"` even when the client omitted it, so forwarding it raw would stamp `sort=releaseYear,desc` onto every link. Instead, detect "sort at its default": the handler already computes `MovieSort movieSort = MovieSort.parse(sort)`; derive `String sortForLink = movieSort.equals(MovieSort.defaultSort()) ? null : sort;` and pass `sortForLink` (not the raw `sort`) into `collectionLinks`/`pageLink`. This leaves a default (or explicitly-default) sort absent while forwarding a non-default sort's raw wire string unchanged. No OpenAPI contract change.
- [x] 1.3 Confirm `self`, `first`, `last`, `prev`, `next` are all built via the updated `pageLink` so every emitted relation carries the params; leave the relation set, boundary arithmetic, and `meta.pagination` untouched.

## 2. SampleController — guard the shared pattern

- [x] 2.1 Apply the identical pass-through shape to `SampleController.pageLink`/`collectionLinks` so any future filter/sort param on `listSamples` is preserved by default. No behaviour change today (no such params exist).

## 3. Standards / convention

- [x] 3.1 Record the durable convention in `standards/openapi.md` §2a: pagination nav links preserve ALL active filter/sort query params, echoing only params present on the request (no defaults). Keep it consistent with the `platform/hypermedia-links` spec delta.

## 4. Tests — MovieController (happy path + edge + failure)

- [x] 4.1 Web-slice test (`@WebMvcTest`, use-case port mocked): for a request with multiple filters (`genre` multi-valued, `releaseYearFrom`/`releaseYearTo`, `minRating`, `title`) AND a non-default `sort` on a multi-page result, assert `self`/`first`/`last`/`prev`/`next` each carry every filter+sort param unchanged and differ from `self` only in `page` (keeping `size`).
- [x] 4.2 Web-slice edge case: an unfiltered, default-sort request produces links carrying only `page`/`size` — no spurious/empty/default filter or sort params. Cover BOTH a request that omits `sort` entirely AND one that sends the default `sort` value explicitly, asserting both yield links with no `sort` param (proves the default→absent normalisation of task 1.2, since Spring binds `sort` to its default in the omit case). Also assert a non-default `sort` IS present in the links.
- [x] 4.3 Web-slice boundary: first page omits `prev`, last page omits `next`, and a valid page beyond the last is `200` with empty `data._embedded.movies` — with every emitted link still carrying the filter+sort params.
- [x] 4.4 Web-slice failure path: invalid `page`/`size` (e.g. `page=-1`, `size=0`) still returns `400 application/problem+json` with the `correlationId` and no `_links`/`_embedded`.
- [x] 4.5 Testcontainers-Postgres round-trip test (real data, no H2): follow each emitted `first`/`prev`/`next`/`last` link back through the handler and assert the same `meta.pagination.totalElements`/`totalPages` and the same item ordering as the originating filtered, non-default-sorted request — proving filters+sort survive forward and backward. Assert semantic target-query equality, not raw string equality.

## 5. Tests — SampleController guard

- [x] 5.1 Assert `SampleController` links remain correct (unchanged) after the refactor, so the guarded pattern won't regress if/when it gains filter/sort params.

## 6. Verify

- [x] 6.1 Run `./gradlew build` (tests + Spotless) green locally; run `openspec validate pagination-links-preserve-query-params` and fix any errors.
