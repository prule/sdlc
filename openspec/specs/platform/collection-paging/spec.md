# collection-paging Specification

## Purpose

Defines the one paging convention that every collection (list or search) operation reuses: how a page is asked for, what page metadata and navigation links each page carries, how empty and beyond-the-last pages are answered, and how invalid query parameters are refused (UC-002 BR-6, BR-7, BR-8, BR-10). `GET /api/v1/movies` is the first operation to use it.

## Requirements

### Requirement: Page and size query parameters
Every collection operation SHALL accept two optional query parameters. `page` is the zero-based index of the requested page: the first page is `0`, and the default is `0`. `size` is the number of items per page: the default is `20`, the minimum `1` and the maximum `100`. An empty value (`page=` or `size=`) SHALL be treated as absent, so the default applies. The page SHALL hold the items at positions `page × size` to `page × size + size − 1` of the complete, ordered result, counted from zero. Acceptance check: a Testcontainers-backed test on `GET /api/v1/movies` with 25 matching movies asserts:
- 20 items when neither parameter is given;
- 5 items for `page=1`;
- 7 items for `size=7`;
- that walking every page with `size=7` returns all 25 movies exactly once, in the same order as one `size=100` page.

#### Scenario: Defaults apply
- **WHEN** a client sends `GET /api/v1/movies` while 25 movies are in the catalog
- **THEN** the response status is `200`, `data._embedded.movies` holds 20 items, `meta.pagination.page` is `0` and `meta.pagination.size` is `20`

#### Scenario: Second page holds the remainder
- **WHEN** a client sends `GET /api/v1/movies?page=1` while 25 movies are in the catalog
- **THEN** `data._embedded.movies` holds 5 items, and they are the 21st to 25th items of the same ordered result

#### Scenario: Empty page and size mean the defaults
- **WHEN** a client sends `GET /api/v1/movies?page=&size=`
- **THEN** the response status is `200`, `meta.pagination.page` is `0` and `meta.pagination.size` is `20`

#### Scenario: Maximum size is accepted
- **WHEN** a client sends `GET /api/v1/movies?size=100`
- **THEN** the response status is `200` and `meta.pagination.size` is `100`

### Requirement: Page metadata in meta.pagination
Every successful collection response SHALL carry `meta.pagination` with exactly four integer members:
- `page`: the requested page index, even when it is after the last page;
- `size`: the page size in effect;
- `totalElements`: the number of matching items across all pages;
- `totalPages`: `totalElements ÷ size`, rounded up, which is `0` when nothing matches.

Non-collection responses (for example `GET /api/v1/ping` and `GET /api/v1/movies/{id}`) SHALL NOT carry `meta.pagination`. Acceptance check: web tests assert the four members and their values for 25 items at `size=7` (`totalPages` `4`) and for 0 items (`totalPages` `0`). They also assert that `meta` has no `pagination` key on `/ping` and on a movie's details.

#### Scenario: Totals across all pages
- **WHEN** a client sends `GET /api/v1/movies?size=7&page=2` while 25 movies match
- **THEN** `meta.pagination` is `{"page": 2, "size": 7, "totalElements": 25, "totalPages": 4}`

#### Scenario: Single-resource responses carry no pagination
- **WHEN** a client sends `GET /api/v1/ping`
- **THEN** `meta` has no `pagination` member

### Requirement: Items are embedded in a HAL collection object
A successful collection response's `data` SHALL be an object with exactly two members. `_embedded` SHALL hold a single named array of the page's items, for example `_embedded.movies`, and `_links` SHALL hold the navigation links. The array SHALL always be present, and SHALL be empty (`[]`) when the page has no items. Each item SHALL carry its own `_links.self`. Acceptance check: a JSON assertion that `data` has exactly the keys `_embedded` and `_links`, and that the array is present and empty for a search that matches nothing.

#### Scenario: Empty page still has the array
- **WHEN** a client sends a valid search that matches nothing
- **THEN** `data._embedded.movies` is `[]`

### Requirement: Criteria-preserving navigation links
Every successful collection response SHALL carry these links in `data._links`:
- `self`, `first` and `last`, always;
- `prev`, only when `0 < page ≤ lastPage`;
- `next`, only when `page < lastPage`.

`lastPage` is `totalPages − 1`, or `0` when `totalPages` is `0`. The target pages are: `self` the requested page, `first` page `0`, `last` page `lastPage`, `prev` `page − 1`, and `next` `page + 1`.

Each `href` SHALL be an absolute URI, built as follows:
- The URI SHALL be for the same operation path, and SHALL honour forwarded scheme and host.
- The query SHALL carry every query parameter the operation recognises that was present on the request, with the values exactly as supplied (a repeated parameter keeps every value, in its original order). Any `page` value is removed, then `page=<target>` is appended last.
- A recognised parameter that the client did not send SHALL NOT be added. That includes `size` and the ordering parameter when left at their defaults.
- Unrecognised query parameters SHALL NOT be carried.

Links SHALL be navigational only. Acceptance check: web tests for:
- the first, a middle, the last and a beyond-last page, and a result with no matches, asserting exactly which relations are present and each target `page`;
- a request with repeated and mixed-case parameters, asserting that the decoded query of every link equals the request's recognised parameters with only `page` replaced;
- a request that includes an unrecognised parameter `foo=bar`, asserting that no link carries `foo`;
- `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test`, asserting that every href starts with `https://api.example.test/api/v1/movies`.

#### Scenario: Middle page links
- **WHEN** a client sends `GET /api/v1/movies?genre=drama&sort=-rating&size=7&page=1` while 25 movies match
- **THEN** `data._links` has `self`, `first`, `last`, `prev` and `next`, and their hrefs end with `/api/v1/movies?genre=drama&sort=-rating&size=7&page=1`, `…&page=0`, `…&page=3`, `…&page=0` and `…&page=2` respectively

#### Scenario: First page has no prev
- **WHEN** a client sends `GET /api/v1/movies?size=7` while 25 movies match
- **THEN** `data._links` has `self`, `first`, `next` and `last` but no `prev`, and `self.href` ends with `/api/v1/movies?size=7&page=0`

#### Scenario: Last page has no next
- **WHEN** a client sends `GET /api/v1/movies?size=7&page=3` while 25 movies match
- **THEN** `data._links` has `prev` (page `2`) but no `next`

#### Scenario: Defaults are not leaked into links
- **WHEN** a client sends `GET /api/v1/movies` with no query parameters
- **THEN** `data._links.self.href` ends with `/api/v1/movies?page=0` and no link carries `size` or `sort`

#### Scenario: Unrecognised parameters are not carried
- **WHEN** a client sends `GET /api/v1/movies?foo=bar&title=heist`
- **THEN** the response status is `200` and no link contains `foo`, but every link contains `title=heist`

#### Scenario: Links honour forwarded headers
- **WHEN** a client sends `GET /api/v1/movies` with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test`
- **THEN** every href in `data._links` starts with `https://api.example.test/api/v1/movies?`

### Requirement: Empty and beyond-last pages are successes
A valid request that matches nothing, or whose `page` is after the last page, SHALL be answered `200` with an empty item array. It SHALL NOT be answered `404` or any other failure. A beyond-last page SHALL still report the true `totalElements` and `totalPages`, and SHALL carry `self`, `first` and `last` only. A result with no matches SHALL report `totalElements` `0` and `totalPages` `0`, and SHALL carry `self`, `first` and `last`, with `first` and `last` both targeting page `0`. A very large but well-formed `page` (up to `2147483647`) SHALL be answered as a beyond-last page, never as a `5xx`. Acceptance check: web and Testcontainers tests for an empty catalog, a no-match search, `page=50` with 25 matches, and `page=2147483647&size=100`.

#### Scenario: Page after the last
- **WHEN** a client sends `GET /api/v1/movies?page=50` while 25 movies match
- **THEN** the response status is `200`, `data._embedded.movies` is `[]`, `meta.pagination.totalElements` is `25`, `meta.pagination.totalPages` is `2`, and `data._links` has `self`, `first` and `last` (page `1`) but no `prev` or `next`

#### Scenario: Nothing matches
- **WHEN** a client sends a valid search that matches no movie
- **THEN** the response status is `200`, `data._embedded.movies` is `[]`, `totalElements` and `totalPages` are `0`, and `first.href` and `last.href` both end with `page=0`

#### Scenario: Huge page index
- **WHEN** a client sends `GET /api/v1/movies?page=2147483647&size=100`
- **THEN** the response status is `200` and `data._embedded.movies` is `[]`

### Requirement: Invalid query parameters are refused and named
A collection request with an invalid query parameter SHALL be refused with `400` `application/problem+json`, with `code` `BAD_REQUEST` and the same `type` as every other `400`. It SHALL NOT perform the search. An invalid query parameter is any of:
- a `page` below `0`;
- a `size` below `1` or above `100`;
- a `page` or `size` that is not an integer within the 32-bit range;
- any operation-specific violation the operation defines.

The problem `detail` SHALL name at least one offending parameter, in the form `Query parameter '<name>' …`, and SHALL NOT name any parameter that is valid. It SHALL NOT echo the supplied value, or name an internal type or class. Acceptance check: a web-slice test with the search mocked sends `page=-1`, `size=0`, `size=101`, `page=abc`, `size=1.5` and `page=99999999999`. For each it asserts `400`, `BAD_REQUEST`, the required problem members, that `detail` contains the parameter name in quotes, that `detail` does not contain the supplied value, and that the search was never invoked.

#### Scenario: Size above the maximum
- **WHEN** a client sends `GET /api/v1/movies?size=101`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, `detail` contains `'size'` and does not contain `101`, and no search is performed

#### Scenario: Page before the first
- **WHEN** a client sends `GET /api/v1/movies?page=-1`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` contains `'page'`

#### Scenario: Non-numeric page
- **WHEN** a client sends `GET /api/v1/movies?page=abc`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, `detail` contains `'page'`, and `detail` contains neither `abc` nor `Integer`
