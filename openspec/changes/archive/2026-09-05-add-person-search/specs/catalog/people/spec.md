## ADDED Requirements

### Requirement: A people collection returns an enveloped HAL collection

The system SHALL expose `GET /api/v1/people`. The system SHALL respond `200` with
`Content-Type: application/json` as the standard Envelope (`{data, meta}`). `data` SHALL be a HAL
collection resource carrying person **summary** items under `data._embedded.people` and a `data._links`
object carrying `self` and the pagination links. `meta` SHALL carry `timestamp`, a `correlationId`
(UUID), and `pagination` (`page`, `size`, `totalElements`, `totalPages`). Internal DB ids SHALL NOT
appear in the response.

Acceptance check: `GET /api/v1/people` with no `Authorization` header returns `200`,
`application/json`, a body with top-level `data` and `meta`; `data._embedded.people` is an array;
`data._links.self.href` is the absolute URI of this operation; `meta.correlationId` is a UUID,
`meta.timestamp` is present, and `meta.pagination` carries `page`/`size`/`totalElements`/`totalPages`.

#### Scenario: Default list returns an enveloped first page

- **WHEN** a client sends `GET /api/v1/people` with no params and no auth header
- **THEN** the response is `200` with `Content-Type: application/json`
- **AND** the body root is the Envelope with `data` and `meta`
- **AND** the person summaries appear under `data._embedded.people`
- **AND** `data._links.self.href` is the absolute URI of this operation
- **AND** `meta` contains a UUID `correlationId`, a `timestamp`, and `pagination` with `page`, `size` (`20`), `totalElements`, and `totalPages`

### Requirement: A person summary exposes exactly id, name, and self link

Each item in `data._embedded.people` SHALL expose the Person as a person **summary** carrying exactly
`id` (the Person's opaque UUID, never an internal DB id) and `name`, plus its own `_links.self` whose
`href` is the absolute URI of `GET /api/v1/people/{id}` for that Person. A summary item SHALL NOT carry
any biographical field, SHALL NOT carry `_embedded`, and SHALL NOT carry `_templates`.

Acceptance check: list people and assert each item has exactly the keys `id`, `name`, and `_links`;
`_links.self.href` is the absolute URI of `GET /api/v1/people/{item.id}`; assert no biographical field,
no `_embedded`, and no `_templates` on any item; assert no internal DB id appears.

#### Scenario: Item exposes only id, name, and a self link

- **WHEN** a client lists people successfully
- **THEN** each item exposes exactly `id`, `name`, and `_links`
- **AND** `_links.self.href` is the absolute URI of `GET /api/v1/people/{id}` for that Person
- **AND** the item carries no biographical field, no `_embedded`, and no `_templates`

### Requirement: The people collection is filtered by name (case-insensitive substring)

The system SHALL accept an optional `name` query parameter. When supplied, the collection SHALL contain
only People whose `name` contains the given term as a case-insensitive substring, and `meta.pagination`
counts SHALL reflect the matched subset. The match SHALL treat the term literally: SQL `LIKE` wildcard
characters (`%`, `_`) present in the term SHALL be matched literally, not as wildcards. `name` SHALL be
the only filter; no role, department, known-for, or has-credits filter is accepted.

Acceptance check: seed people with mixed-case names, request `?name=<term>` in the opposite case, and
assert only the substring-matching People are returned and that `meta.pagination.totalElements` equals
the matched count; request a term containing `%` and `_` and assert those characters match literally
(no over-matching).

#### Scenario: Name query narrows the collection case-insensitively

- **WHEN** a client requests `GET /api/v1/people?name=<term>`
- **THEN** the response contains only People whose `name` contains `<term>` case-insensitively
- **AND** `meta.pagination` counts reflect the matched subset

#### Scenario: Wildcard characters in the term are matched literally

- **WHEN** a client supplies a `name` term containing `%` or `_`
- **THEN** those characters are matched literally and do not act as SQL wildcards

### Requirement: The people collection is sorted, defaulting to name ascending

The system SHALL accept an optional `sort=<field>,<dir>` parameter over the single sortable field
`name`, with `<dir>` `asc` or `desc`. When `sort` is absent the system SHALL order results by `name`
ascending. The ordering SHALL be total and stable: `name` in the requested direction, then a unique
terminal key (the Person's id) so that ties are broken deterministically and paging through the whole
collection yields every Person exactly once with no cross-page skip or duplicate. An unsupported sort
field or direction SHALL be rejected `400` `application/problem+json` — not silently ignored and not
`500`.

Acceptance check: seed People with duplicate names, request `?sort=name,asc` and `?sort=name,desc` and
assert results are ordered by `name` in the requested direction with ties resolved by the terminal id
key and two repeated requests are order-identical; page through the whole collection and assert every
Person appears exactly once; request `?sort=unknownField,asc` and assert `400`
`application/problem+json`.

#### Scenario: Default sort is name ascending

- **WHEN** a client lists people with no `sort` parameter
- **THEN** results are ordered by `name` ascending, ties broken by the Person's id

#### Scenario: Explicit sort direction is honoured, total and stable

- **WHEN** a client requests `?sort=name,asc` or `?sort=name,desc`
- **THEN** results are ordered by `name` in that direction, ties broken by the Person's id
- **AND** repeated requests return an identical order and paging yields every Person exactly once

#### Scenario: Unknown sort field is a 400

- **WHEN** a client requests the collection with an unsupported sort field or direction
- **THEN** the response is `400` with `Content-Type: application/problem+json` and is not `500`

### Requirement: The people collection is paginated

The collection SHALL be paginated using the platform `page` (zero-based, default `0`) and `size`
(default `20`, maximum `100`) parameters. `meta.pagination` SHALL carry `page`, `size`,
`totalElements`, and `totalPages`, and `data._links` SHALL carry pagination links following the
platform HAL collection convention: `self`, `first`, and `last` on every page; `next` when a later page
exists; `prev` when an earlier page exists. Navigation links SHALL preserve the active `name` and
`sort` query parameters. A valid page beyond the last SHALL return an empty `200` (no `next`), not
`404` or `400`. Invalid pagination input — `page` < 0, `size` < 1, or `size` > 100 — SHALL return
`400` `application/problem+json`, not `500`.

Acceptance check: request the first page and assert `self`/`first`/`last`/`next` present and no `prev`;
request the last page and assert `self`/`first`/`last`/`prev` present and no `next`; request a page
beyond the last and assert an empty `200` with no `next`; request with a `name`/`sort` set and assert
every navigation link carries those params; request `page=-1`, `size=0`, and `size=101` and assert each
returns `400` `application/problem+json`.

#### Scenario: First page carries forward pagination links

- **WHEN** a client requests the first page of a multi-page collection
- **THEN** `data._links` carries `self`, `first`, `last`, and `next` and no `prev`
- **AND** `meta.pagination` carries `page`, `size`, `totalElements`, and `totalPages`

#### Scenario: Last page carries no next link

- **WHEN** a client requests the last page of the collection
- **THEN** `data._links` carries `self`, `first`, `last`, and `prev` and no `next`

#### Scenario: A page beyond the last is an empty 200

- **WHEN** a client requests a valid page number beyond the last page
- **THEN** the response is `200` with an empty `data._embedded.people` and no `next` link

#### Scenario: Navigation links preserve active filter and sort

- **WHEN** a client requests the collection with `name` and/or `sort` set on a multi-page result
- **THEN** every pagination navigation link URL carries the active `name` and `sort` parameters

#### Scenario: Invalid pagination input is a 400

- **WHEN** a client requests the collection with `page` < 0, `size` < 1, or `size` > 100
- **THEN** the response is `400` with `Content-Type: application/problem+json` and is not `500`

### Requirement: An empty people collection returns a normal 200

For a query matching no Person, or an empty catalog, the system SHALL respond `200` with the standard
Envelope, an empty `data._embedded.people` array, `data._links.self` present and no `next`/`prev`, and
`meta.pagination.totalElements` equal to `0`. The empty-collection `totalPages` and `first`/`last` link
behaviour SHALL follow the platform's existing HAL collection convention (as proven by CAT-002). It
SHALL NOT respond `404` and SHALL NOT return an error.

Acceptance check: request a `name` term matching no Person; assert `200`, empty
`data._embedded.people`, `data._links.self` present with no `next`/`prev`,
`meta.pagination.totalElements` is `0`, and the response is not `404` or an error.

#### Scenario: No match returns an empty success

- **WHEN** a client lists people with a filter matching no Person
- **THEN** the response is `200` with an empty `data._embedded.people` array
- **AND** `data._links.self` is present with no `next`/`prev`, `meta.pagination.totalElements` is `0`, and the response is not `404` or an error

### Requirement: HAL discipline on the people collection

The people collection response SHALL emit navigational `_links` only — no HAL-FORMS `_templates` and no
action/write affordances. Success responses SHALL be `application/json` with HAL fields inside `data`;
error responses SHALL be `application/problem+json` and SHALL NOT carry `_links` or `_embedded`. Each
embedded item SHALL carry a resolvable `_links.self` addressing `GET /api/v1/people/{id}`; no other item
link SHALL be emitted. Link and collection assembly SHALL be web-adapter-only; the domain and
application layers SHALL NOT reference `_links`/`_embedded`.

Acceptance check: assert a `200` collection response is `application/json` with HAL `_links` inside
`data` and no `_templates`; assert each embedded item carries `_links.self` addressing
`GET /api/v1/people/{id}` and no other item link; assert an error response is
`application/problem+json` with no `_links`/`_embedded`.

#### Scenario: Success is HAL-in-envelope, errors are problem+json

- **WHEN** a client lists people successfully
- **THEN** the `Content-Type` is `application/json` with HAL `_links` inside `data` and no `_templates`
- **AND** each embedded item carries `_links.self` addressing `GET /api/v1/people/{id}`
- **AND** on an error the `Content-Type` is `application/problem+json` with no `_links`/`_embedded`

### Requirement: The people collection endpoint is public

The system SHALL serve `GET /api/v1/people` with no authentication (`security: []` in the contract). It
SHALL NOT return `401` or `403` for a missing or absent `Authorization` header. The path pattern SHALL
be registered in the single source of truth for public endpoints so the public-endpoint consistency
test holds.

Acceptance check: request the endpoint with no `Authorization` header and assert the response is `200`
(matches or empty) or `400` (invalid params) — never `401`/`403`; the public-endpoint consistency test
passes with the new `/people` pattern registered.

#### Scenario: No authentication is required

- **WHEN** a client lists people without an `Authorization` header
- **THEN** the response is never `401` or `403`
- **AND** a valid request returns `200` (a match or an empty collection)

### Requirement: Listing people uses a bounded, page-size-independent query count

Loading a page of N person summaries SHALL issue a number of SQL statements that is bounded and
independent of N — it SHALL NOT issue one query per row (no N+1). Increasing `size` SHALL NOT increase
the statement count. The CAT-001 detail, CAT-002 search, CAT-003 credits, CAT-004 person-detail, and
CAT-005 filmography paths SHALL NOT regress.

Acceptance check: a Testcontainers test against real Postgres seeds many People, counts the SQL
statements issued to load one page of the collection (e.g. via Hibernate statistics or a query
counter), and asserts the count is bounded and does not grow as the page size grows.

#### Scenario: People-list load stays bounded as page size grows

- **WHEN** the search adapter loads a page for a collection of many People
- **THEN** the number of SQL statements issued is bounded and independent of the page size
- **AND** it does not issue one query per person row

### Requirement: People-collection demo data is demonstrable while tests stay independent

The people-collection happy path SHALL be demonstrable in a running app by listing the People from the
existing `@Profile("demo")` seed (people were seeded in CAT-003), with that seed NOT loaded in
production. The automated tests SHALL use their own fixtures against real Postgres (Testcontainers, no
H2) and SHALL pass regardless of the demo seed's contents.

Acceptance check: the demo seed (people) loads only under the demo/dev profile (absent under prod); the
persistence and web tests insert their own fixture people and assert against those ids, passing with
the demo seed empty or changed.

#### Scenario: Tests are independent of the demo seed

- **WHEN** the automated test suite runs against a Postgres with no demo seed
- **THEN** the people-collection tests provision their own person fixtures and pass

#### Scenario: Demo seed is not loaded in production

- **WHEN** the application runs under the production profile
- **THEN** the people demo data are not loaded
