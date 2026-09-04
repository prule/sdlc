## Purpose

The `catalog/people` capability serves a public, read-only **Person** — an individual who worked on
movies — as its own addressable single resource, so API consumers can resolve and display the Person a
Credit refers to without running their own catalog.

## ADDED Requirements

### Requirement: A person's detail returns an enveloped single resource

The system SHALL expose `GET /api/v1/people/{id}` where `{id}` is a Person's stable opaque UUID. For
an existing Person the system SHALL respond `200` with `Content-Type: application/json` as the standard
Envelope (`{data, meta}`). `data` SHALL expose the Person's `id` (the opaque UUID, never an internal DB
id) and `name`, plus a `data._links` object whose `self.href` is the absolute URI of this operation.
`meta` SHALL carry `timestamp` and a `correlationId` (UUID). Internal DB ids SHALL NOT appear in the
response.

Acceptance check: `GET /api/v1/people/{existing-id}` with no `Authorization` header returns `200`,
`application/json`, a body with top-level `data` and `meta`; `data.id` is the requested opaque UUID,
`data.name` is present, `data._links.self.href` is the absolute URI of this operation; `meta.correlationId`
is a UUID and `meta.timestamp` is present.

#### Scenario: Existing person returns an enveloped detail

- **WHEN** a client sends `GET /api/v1/people/{id}` with a matching valid UUID and no auth header
- **THEN** the response is `200` with `Content-Type: application/json`
- **AND** the body root is the Envelope with `data` and `meta`
- **AND** `data` exposes the person's opaque `id` and `name`
- **AND** `data._links.self.href` is the absolute URI of this operation
- **AND** `meta` contains a UUID `correlationId` and a `timestamp`

### Requirement: Person detail exposes exactly id and name

The `data` object of a person detail response SHALL contain exactly `id`, `name`, and `_links`. It
SHALL NOT carry any biographical field (for example birth date or biography), SHALL NOT carry
`_embedded`, and SHALL NOT carry `_templates`.

Acceptance check: retrieve an existing Person; assert `data` has exactly the keys `id`, `name`, and
`_links`; assert there is no biographical field, no `_embedded`, and no `_templates`.

#### Scenario: Person data carries only id, name, and links

- **WHEN** a client retrieves an existing Person's detail
- **THEN** `data` contains exactly `id`, `name`, and `_links`
- **AND** `data` carries no biographical field, no `_embedded`, and no `_templates`

### Requirement: HAL discipline on person detail

`data._links` SHALL carry `self` only — no `credits`/`filmography` link SHALL be emitted, since no
cross-filmography endpoint exists to address (no dangling links). The response SHALL emit navigational
`_links` only — no HAL-FORMS `_templates` and no action/write affordances. Success responses SHALL be
`application/json` with HAL fields inside `data`; error responses SHALL be `application/problem+json`
and SHALL NOT carry `_links` or `_embedded`.

Acceptance check: assert a `200` person response is `application/json`, `data._links` contains exactly
`self`, and carries no `_templates` and no `credits`/`filmography` link; assert an error response is
`application/problem+json` with no `_links`/`_embedded`.

#### Scenario: Success is HAL-in-envelope with self only, errors are problem+json

- **WHEN** a client retrieves a person successfully
- **THEN** the `Content-Type` is `application/json` with HAL `_links` inside `data`
- **AND** `data._links` contains exactly `self` and no `credits`/`filmography` link and no `_templates`
- **AND** on an error the `Content-Type` is `application/problem+json` with no `_links`/`_embedded`

### Requirement: Unknown person id returns 404 problem+json

For a syntactically valid UUID with no matching Person, the system SHALL respond `404` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying a stable
`code` `PERSON_NOT_FOUND`, the `status`, and the request `correlationId`. The response SHALL NOT be an
empty `200` and SHALL NOT carry `_links` or `_embedded`.

Acceptance check: `GET /api/v1/people/{random-unused-uuid}` returns `404`, `application/problem+json`,
a `Problem` body with `code` `PERSON_NOT_FOUND` and the `correlationId`, and no `_links`/`_embedded`.

#### Scenario: No person for a valid UUID is a 404

- **WHEN** a client requests a syntactically valid UUID with no matching Person
- **THEN** the response is `404` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with the stable `code` `PERSON_NOT_FOUND` and the `correlationId`
- **AND** the response is not an empty `200` and contains no `_links` or `_embedded`

### Requirement: Malformed person id is a 400, not a 500

For a path `{id}` that is not a valid UUID, the system SHALL respond `400` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying the
`correlationId`. Such input SHALL NOT fall through to a generic `500` and SHALL NOT reach the
persistence layer.

Acceptance check: `GET /api/v1/people/not-a-uuid` returns `400` (not `500`), `application/problem+json`,
a `Problem` body with the `correlationId`.

#### Scenario: Non-UUID path id is rejected 400

- **WHEN** a client requests `GET /api/v1/people/{id}` where `{id}` is not a valid UUID
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with the request `correlationId`
- **AND** the response is not `500` and does not reach the persistence layer

### Requirement: The person endpoint is public

The system SHALL serve `GET /api/v1/people/{id}` with no authentication (`security: []` in the
contract). It SHALL NOT return `401` or `403` for a missing or absent `Authorization` header. The path
pattern SHALL be registered in the single source of truth for public endpoints so the public-endpoint
consistency test holds.

Acceptance check: request the endpoint with no `Authorization` header and assert the response is `200`
(existing person), `404` (unknown), or `400` (malformed) — never `401`/`403`; the public-endpoint
consistency test passes with the new pattern registered.

#### Scenario: No authentication is required

- **WHEN** a client requests the person endpoint without an `Authorization` header
- **THEN** the response is never `401` or `403`
- **AND** an existing person returns `200` and an unknown id returns `404`

### Requirement: Demo data is demonstrable while tests stay independent

The person detail happy path SHALL be demonstrable in a running app by reusing a Person from the
existing `@Profile("demo")` seed (people were seeded in CAT-003), with that seed NOT loaded in
production. The automated tests SHALL use their own fixtures against real Postgres (Testcontainers, no
H2) and SHALL pass regardless of the demo seed's contents.

Acceptance check: the demo seed (people) loads only under the demo/dev profile (absent under prod); the
persistence and web tests insert their own fixture people and assert against those ids, passing with the
demo seed empty or changed.

#### Scenario: Tests are independent of the demo seed

- **WHEN** the automated test suite runs against a Postgres with no demo seed
- **THEN** the person tests provision their own person fixtures and pass

#### Scenario: Demo seed is not loaded in production

- **WHEN** the application runs under the production profile
- **THEN** the people demo data are not loaded
