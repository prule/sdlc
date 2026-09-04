## MODIFIED Requirements

### Requirement: Movie detail carries only a self link and no embedded resources

The `data` resource SHALL carry a `_links` object containing the `self` relation and a `credits`
relation. The `credits.href` SHALL be the absolute URI of `GET /api/v1/movies/{id}/credits` for that
movie. No other related `_links` SHALL be emitted (no `/people`, `/genres`, or `/ratings-reviews`
endpoints exist yet), no `_embedded` object SHALL be present, and no HAL-FORMS `_templates` or
write/action affordance SHALL be emitted. Genres SHALL be exposed inline as plain labels, not as
links. Credits (cast/crew) SHALL be reachable only via the `credits` link and SHALL NOT be inlined or
embedded in the movie-detail response; individual reviews SHALL NOT appear. Building the `credits`
link SHALL NOT issue any additional SQL query for the movie-detail request.

Acceptance check: assert `data._links` has exactly the `self` and `credits` relations, `credits.href`
is the absolute URI of the movie's credits endpoint, `data` has no `_embedded` and no `_templates`,
there is no inlined cast/crew or reviews content, and the movie-detail SQL statement count is
unchanged from CAT-001.

#### Scenario: Only the self link is present
- **WHEN** a client retrieves a Movie successfully
- **THEN** `data._links` contains only the `self` and `credits` relations
- **AND** `data._links.credits.href` is the absolute URI of `GET /api/v1/movies/{id}/credits`
- **AND** `data` has no `_embedded` and no `_templates`
- **AND** the response contains no inlined cast, crew, or reviews fields

#### Scenario: The credits link adds no query
- **WHEN** `GET /api/v1/movies/{id}` is served after this change
- **THEN** the `credits` link is assembled in the web adapter without an extra SQL statement
- **AND** the movie-detail SQL statement count is unchanged from CAT-001
