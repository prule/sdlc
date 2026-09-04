## MODIFIED Requirements

### Requirement: Cast item exposes person, character, and billing order

Each item in `data._embedded.cast` SHALL be a HAL resource exposing its Person inline as `person`
(with `id` — the Person's opaque UUID — and `name`), the `character` name (a free-text string), and
`billingOrder` (a positive integer). The inline `person` SHALL carry a `person._links.self` whose
`href` addresses `GET /api/v1/people/{id}` for that Person (now that the endpoint exists). The cast
item SHALL NOT carry its own `self` link and SHALL NOT carry `_embedded` or `_templates`. Internal DB
ids SHALL NOT appear.

Acceptance check: retrieve a Movie with cast; assert each cast item exposes `person.id` (UUID),
`person.name`, `character`, and a positive integer `billingOrder`, and that `person._links.self.href`
addresses `GET /api/v1/people/{person.id}`, and the item carries no item-level `_links`, no `_embedded`,
and no `_templates`. Every cast item SHALL always carry both `character` and `billingOrder`
(the per-type invariant is observable — no cast item is missing either), reflecting the persistence
CHECK constraint.

#### Scenario: Cast item shape
- **WHEN** a client retrieves a Movie's cast
- **THEN** each cast item exposes `person` (`id` + `name`), `character`, and `billingOrder`
- **AND** the inline `person` carries `person._links.self` addressing `GET /api/v1/people/{id}`
- **AND** the item carries no item `self` link, no `_embedded`, no `_templates`

### Requirement: Crew item exposes person, department, and job

Each item in `data._embedded.crew` SHALL be a HAL resource exposing its Person inline as `person`
(with `id` and `name`), the `department` (a free-text string), and the `job` (a free-text string).
The inline `person` SHALL carry a `person._links.self` whose `href` addresses `GET /api/v1/people/{id}`
for that Person (now that the endpoint exists). The crew item SHALL NOT carry its own `self` link and
SHALL NOT carry `_embedded` or `_templates`. Internal DB ids SHALL NOT appear.

Acceptance check: retrieve a Movie with crew; assert each crew item exposes `person.id` (UUID),
`person.name`, `department`, and `job`, and that `person._links.self.href` addresses
`GET /api/v1/people/{person.id}`, and the item carries no item-level `_links`, no `_embedded`, and no
`_templates`. Every crew item SHALL always carry both `department` and `job` (the per-type invariant is
observable — no crew item is missing either), reflecting the persistence CHECK constraint.

#### Scenario: Crew item shape
- **WHEN** a client retrieves a Movie's crew
- **THEN** each crew item exposes `person` (`id` + `name`), `department`, and `job`
- **AND** the inline `person` carries `person._links.self` addressing `GET /api/v1/people/{id}`
- **AND** the item carries no item `self` link, no `_embedded`, no `_templates`

### Requirement: HAL discipline on credits responses

The credits response SHALL emit navigational `_links` only — no HAL-FORMS `_templates` and no
action/write affordances. Success responses SHALL be `application/json` with HAL fields inside `data`;
error responses SHALL be `application/problem+json` and SHALL NOT carry `_links` or `_embedded`. Because
`GET /api/v1/people/{id}` now exists, each inline Person on a credit item SHALL carry a resolvable
`person._links.self` addressing that operation; no other person link SHALL be emitted.

Acceptance check: assert a `200` credits response is `application/json` with no `_templates`; assert
each inline Person carries a `person._links.self` addressing `GET /api/v1/people/{id}` and no other
person link; assert an error response is `application/problem+json` with no `_links`/`_embedded`.

#### Scenario: Success is HAL-in-envelope, errors are problem+json
- **WHEN** a client retrieves credits successfully
- **THEN** the `Content-Type` is `application/json` with HAL `_links` inside `data` and no `_templates`
- **AND** each inline Person carries a `person._links.self` addressing `GET /api/v1/people/{id}`
- **AND** on an error the `Content-Type` is `application/problem+json` with no `_links`/`_embedded`
