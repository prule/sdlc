## ADDED Requirements

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
