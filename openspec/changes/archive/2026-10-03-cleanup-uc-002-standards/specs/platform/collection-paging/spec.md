## MODIFIED Requirements

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

Links SHALL be navigational only. Acceptance check, web tests for:
- the first, a middle, the last and a beyond-last page, and a result with no matches, asserting exactly which relations are present and each target `page`;
- a request that includes an unrecognised parameter `foo=bar`, asserting that no link carries `foo`.

Acceptance check, on the real `GET /api/v1/movies` against PostgreSQL (Testcontainers), not a test-only operation:
- a request with repeated and mixed-case parameter values and an unrecognised parameter, asserting that the decoded query of every link equals the request's recognised parameters, in order, with only `page` replaced by that link's target page;
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

#### Scenario: Recognised parameters are echoed exactly on the real operation
- **WHEN** a client sends `GET /api/v1/movies?title=Movie&genre=drama&genre=SCI-FI&foo=bar&sort=-rating&size=7&page=1` to the service backed by PostgreSQL, while 25 movies titled `Movie …` carry both Drama and Sci-Fi
- **THEN** the decoded query of every href in `data._links` is `title=Movie&genre=drama&genre=SCI-FI&sort=-rating&size=7&page=<target>`, where `<target>` is `1` for `self`, `0` for `first` and `prev`, `3` for `last` and `2` for `next`

#### Scenario: Links honour forwarded headers
- **WHEN** a client sends `GET /api/v1/movies` with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test` to the service backed by PostgreSQL
- **THEN** every href in `data._links` starts with `https://api.example.test/api/v1/movies?`
