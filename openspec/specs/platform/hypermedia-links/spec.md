# platform/hypermedia-links Specification

## Purpose
Defines the HAL navigational-link representation convention for the public, read-only API: where
`_links`/`_embedded` sit relative to the success Envelope, which link relations are emitted, how
pagination-boundary behaviour works, how the link shape is described in the OpenAPI contract, the
success/error media types, and the clean-architecture placement of link assembly.

## Requirements

### Requirement: HAL links are carried inside the envelope `data` resource

The system SHALL represent navigational hypermedia links using the HAL convention placed **inside**
the existing success Envelope: the `data` object of a `2xx` response is a HAL resource that MAY carry
a `_links` object, and (for collections) a `_embedded` object. The Envelope root and the `meta` object
(`timestamp`, `correlationId`, `pagination`) SHALL be unchanged. Links SHALL NOT be placed at the
Envelope root nor under `meta`. The `_links` object SHALL follow the HAL shape: an object whose
properties are the supported link relations — a fixed, documented set, not open-ended
`additionalProperties` — each property an object with at least an `href` (URI), and OPTIONAL
`templated` (boolean) and `title` (string). Links are navigational only — no action, write, or state-transition affordances
(HAL-FORMS templates SHALL NOT be emitted), because the API is read-only.

Acceptance check: on a single-resource `2xx` response, assert the body has top-level `data` and `meta`
keys (root shape unchanged), `data._links.self.href` is an absolute URI resolving to the resource, and
`meta.correlationId`/`meta.timestamp` are still present.

#### Scenario: Single-resource response carries a self link inside data
- **WHEN** a client requests a single-resource read endpoint
- **THEN** the `2xx` body root is the standard Envelope with `data` and `meta`
- **AND** `data._links.self.href` is an absolute URI addressing the requested resource
- **AND** `meta` still contains `correlationId` and `timestamp`

#### Scenario: No action or write affordances are emitted
- **WHEN** any `2xx` response is produced
- **THEN** `_links` contains only navigational relations (e.g. `self`, `next`, `prev`, `first`, `last`, related)
- **AND** no HAL-FORMS `_templates` object or write/action affordance is present

### Requirement: Collection responses embed items and carry pagination links

For a collection endpoint, the system SHALL represent the `data` object as a HAL collection resource:
the collection items SHALL be carried under `data._embedded` keyed by a relation name, each embedded
item itself a HAL resource carrying its own `self` link. The collection's `data._links` SHALL carry a
`self` link and page-navigation links using page/size semantics aligned with `meta.pagination`
(`page`, `size`, `totalElements`, `totalPages`). The relations emitted SHALL be `self`, `next`, `prev`,
`first`, and `last`, subject to boundary rules: `prev` SHALL be absent on the first page and `next`
SHALL be absent on the last page; `first`/`last` SHALL be derivable from `totalPages`. Pagination
**counts** remain in `meta.pagination`; pagination **link URLs** live in `data._links` (no duplication).

A request for a page **beyond the last page** (a syntactically valid `page`/`size` whose page index
exceeds `totalPages`) SHALL be a normal `200` with an empty `data._embedded.<rel>` — it is NOT a `400`.
Only **invalid** `page`/`size` values (outside the documented bounds — e.g. `page < 0`, `size < 1`, or
`size` above the documented maximum) SHALL be rejected `400` (see the "Invalid pagination parameters"
requirement below).

Acceptance check: request a collection with more than one page at a middle page; assert
`data._embedded.<rel>` is an array of items each with `_links.self.href`, and `data._links` contains
`self`, `next`, `prev`, `first`, `last` whose `page` query params are correct.

#### Scenario: Middle page emits all navigation links
- **WHEN** a client requests a collection page that is neither the first nor the last page
- **THEN** `data._embedded.<rel>` is an array of HAL items each with a `self` link
- **AND** `data._links` contains `self`, `first`, `last`, `prev`, and `next`
- **AND** the `page` query parameter of `next`/`prev` is the current page +/- 1

#### Scenario: First page omits prev
- **WHEN** a client requests the first page of a multi-page collection
- **THEN** `data._links` contains `self`, `first`, `last`, and `next`
- **AND** `data._links` does NOT contain `prev`

#### Scenario: Last page omits next
- **WHEN** a client requests the last page of a multi-page collection
- **THEN** `data._links` contains `self`, `first`, `last`, and `prev`
- **AND** `data._links` does NOT contain `next`

#### Scenario: Empty collection is a normal success
- **WHEN** a client requests a collection that yields zero items
- **THEN** the response is `200` with an empty (or absent) `data._embedded.<rel>` array
- **AND** `data._links.self` is present and `data._links` contains no `next`/`prev`

#### Scenario: Page beyond the last page is an empty success, not an error
- **WHEN** a client requests a valid `page`/`size` whose page index is beyond `totalPages`
- **THEN** the response is `200` with an empty `data._embedded.<rel>` array
- **AND** `data._links.self` is present and `data._links` contains no `next`

### Requirement: Pagination links preserve the request's active filter and sort query parameters

For a collection endpoint, EVERY emitted pagination navigation link (`self`, `first`, `last`, `prev`,
`next`) SHALL preserve, unchanged, every filter and sort query parameter that was present on the
originating request. Each generated link SHALL differ from the request's `self` link only in the `page`
value (honouring the requested `size`), so that following any link walks *the same query's* result set
rather than a broader, unfiltered, or default-sorted one.

Only parameters actually present on the request SHALL be echoed: a filter or sort parameter that the
client omitted (including a sort left at its server default) SHALL remain absent from the generated
links — no spurious, empty, or default-valued parameters are introduced. Multi-valued parameters (e.g.
repeated `genre`) and structured values (e.g. a `sort` of the form `field,direction`, range bounds)
SHALL round-trip such that following a generated link targets a semantically equal query. All emitted
links SHALL remain absolute, syntactically valid URIs.

This requirement adds to — and does not alter — the boundary, relation-set, counts, media-type, and
layering rules of the "Collection responses embed items and carry pagination links" requirement:
`prev` is still absent on the first page and `next` on the last page, a valid page beyond the last is
still a `200` with an empty `data._embedded.<rel>` (its links still carrying the filter/sort params),
invalid `page`/`size` is still rejected `400 application/problem+json`, counts stay in
`meta.pagination` with no duplication, no new relations or `additionalProperties` are introduced, and
link assembly stays a web-adapter-only concern (domain and application layers remain unaware of
`_links`/hypermedia).

Acceptance check: issue a collection request carrying multiple filters and a non-default sort against a
multi-page result; assert that `self`, `first`, `last`, `prev`, and `next` each carry the identical set
of filter and sort query parameters as the request, differ from `self` only in `page`, and that
following `first`/`last`/`prev`/`next` returns the same `meta.pagination.totalElements`/`totalPages`
and the same item ordering as the originating request. Then issue an unfiltered, default-sort request
and assert the generated links carry no filter/sort query parameters at all.

#### Scenario: Every link on a filtered, sorted page preserves all filter and sort params
- **WHEN** a client requests a middle page of a collection with one or more filters AND a non-default sort
- **THEN** `self`, `first`, `last`, `prev`, and `next` in `data._links` each carry every filter and sort query parameter from the request, unchanged
- **AND** each link differs from `self` only in its `page` query parameter, keeping the requested `size`

#### Scenario: Following a navigation link stays within the same filtered, sorted result set
- **WHEN** a client follows the `first`, `last`, `prev`, or `next` link emitted for a filtered, non-default-sorted request
- **THEN** the response describes the same result set: `meta.pagination.totalElements` and `totalPages` match the originating request
- **AND** the item ordering reflects the same sort as the originating request (filters and sort survive the round-trip both forward and backward)

#### Scenario: Unfiltered, default-sort request introduces no spurious params
- **WHEN** a client requests a collection with no filter parameters and no explicit sort
- **THEN** the emitted `self`, `first`, `last`, `prev`, and `next` links carry only `page`/`size` query parameters
- **AND** no empty, default-valued, or otherwise spurious filter or sort parameter appears in any link

#### Scenario: A sort equal to the server default is left absent whether omitted or sent explicitly
- **WHEN** a client requests a collection with a sort that resolves to the server default (either by omitting the sort parameter entirely OR by sending its value explicitly)
- **THEN** the emitted `self`, `first`, `last`, `prev`, and `next` links carry no sort query parameter
- **AND** the links are identical to those of the equivalent request that omitted the sort parameter (the default is normalised to absent, never echoed)

#### Scenario: Multi-valued and structured params round-trip to a semantically equal query
- **WHEN** a client requests a collection using a repeated multi-valued filter (e.g. multiple `genre` values), a range filter, and a `sort` of the form `field,direction`
- **THEN** each emitted link carries those parameters such that following it targets a semantically equal query (same values, same sort field and direction)
- **AND** every emitted link is an absolute, syntactically valid URI

#### Scenario: Boundary links still carry filter and sort params
- **WHEN** a client requests, for a filtered, non-default-sorted collection, the first page, the last page, or a valid page beyond the last page
- **THEN** the boundary rules are unchanged (`prev` absent on the first page, `next` absent on the last page, a page beyond the last is a `200` with empty `data._embedded.<rel>`)
- **AND** every link that IS emitted still carries all of the request's filter and sort query parameters

#### Scenario: Invalid pagination params are still rejected before link assembly
- **WHEN** a client requests a filtered collection with an invalid `page` or `size` (e.g. `page=-1` or `size=0`)
- **THEN** the response is `400 application/problem+json` conforming to the `Problem` schema with the request `correlationId`
- **AND** no `_links`/`_embedded` is produced (filter/sort preservation never reaches invalid requests)

### Requirement: Invalid pagination parameters are rejected with 400, not 500

The system SHALL validate `page`/`size` query parameters at the boundary and reject values outside the
documented bounds (`page < 0`, `size < 1`, or `size` above the documented maximum) with `400`
`application/problem+json` conforming to the shared `Problem` schema, carrying the correlation id. Such
invalid input SHALL NOT fall through to a generic handler and SHALL NOT return `500`, and SHALL NOT
reach pagination arithmetic.

Acceptance check: issue `GET /api/v1/samples?size=0` and `?page=-1`; assert HTTP `400` (not `500`),
`Content-Type: application/problem+json`, a body with a stable `code` and the `correlationId`, and no
`_links`/`_embedded`.

#### Scenario: Out-of-range size is rejected 400
- **WHEN** a client requests a collection with `size=0` (or `size` above the documented maximum)
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the body conforms to the `Problem` schema with the request `correlationId`
- **AND** the response is not `500` and contains no `_links`/`_embedded`

#### Scenario: Negative page is rejected 400
- **WHEN** a client requests a collection with `page=-1`
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the body conforms to the `Problem` schema with the request `correlationId`

### Requirement: The link shape is described in the OpenAPI contract

The HAL link shape SHALL be described in the authored OpenAPI spec as shared, reusable named
components so the contract remains the single source of truth even though concrete URLs are built at
runtime. There SHALL be a shared `Link` schema (`href` required URI; optional `templated`, `title`) and
a `_links` object schema whose properties are the supported link relations, referenced (via `$ref`) by
each resource's `data` schema. The documented relation set SHALL match what the runtime emits.

Acceptance check: the bundled OpenAPI spec defines a single shared `Link` component and a `_links`
schema referenced by resource `data` schemas; a conformance test asserts the runtime `_links` relations
for a response are all present in the documented `_links` schema for that operation.

#### Scenario: Contract defines shared link components
- **WHEN** the authored spec is bundled
- **THEN** it contains a single shared `Link` schema and a `_links` object schema
- **AND** resource `data` schemas reference the `_links` schema via `$ref` rather than inlining it

#### Scenario: Runtime output conforms to the documented relations
- **WHEN** a proof endpoint returns a HAL response at runtime
- **THEN** every relation present in `data._links` is declared in that operation's documented `_links` schema

### Requirement: Success responses stay application/json; errors stay problem+json

Success (`2xx`) HAL-bearing responses SHALL use media type `application/json` (HAL fields embedded in
`data`), NOT `application/hal+json`, because the document root is the Envelope rather than a pure HAL
resource. Error responses SHALL remain `application/problem+json` conforming to the shared `Problem`
schema and SHALL NOT carry HAL `_links`/`_embedded`. Every response SHALL still carry the correlation
id.

Acceptance check: assert a `2xx` proof response has `Content-Type: application/json`; trigger an error
and assert `Content-Type: application/problem+json`, a body with no `_links`/`_embedded`, and the
correlation id present.

#### Scenario: Success response uses application/json
- **WHEN** a client requests a HAL-bearing proof endpoint successfully
- **THEN** the `Content-Type` is `application/json`
- **AND** the body carries HAL `_links` inside `data`

#### Scenario: Error response is problem+json and not HAL
- **WHEN** a request triggers an error (e.g. unknown route or bad input)
- **THEN** the `Content-Type` is `application/problem+json`
- **AND** the body conforms to the `Problem` schema and contains no `_links` or `_embedded`
- **AND** the body contains the request `correlationId`

### Requirement: Link assembly is a web-adapter concern only

Hypermedia/link assembly SHALL live exclusively in the inbound web adapter layer. The domain and
application layers SHALL NOT import Spring HATEOAS nor reference HAL/link concepts; they operate on
domain results and page metadata only. Link construction SHALL use the framework link builder in the
adapter, and responses SHALL serialize via the generated contract DTO `_links` fields.

Acceptance check: a static check / architecture test asserts no `import org.springframework.hateoas`
appears under `domain/` or `application/` packages; link assembly appears only under `adapters/in/web`.

#### Scenario: Domain and application are free of hypermedia imports
- **WHEN** the codebase is inspected
- **THEN** no class under a `domain` or `application` package imports Spring HATEOAS or references HAL
- **AND** link construction appears only in inbound web adapter classes
