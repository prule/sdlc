## ADDED Requirements

### Requirement: Constraint violations are classified by ownership
When a declared bean-validation constraint fails while a request is handled, the service SHALL classify the failure by who owns it:
- When **every** violation is on one of the handling operation's own request parameters, the request SHALL be refused with `400` `BAD_REQUEST`. A violation on an element of a parameter's value (for example one value of a repeated query parameter) counts as a violation on that parameter. When at least one violated parameter is a named query parameter, the `detail` SHALL be `Query parameter '<name>' is invalid.` for the lowest-positioned such parameter. The `detail` for a violation that is only on path segments is not specified by this requirement.
- **Any other** constraint violation SHALL be treated as a server-side fault: one on the operation's result, one raised by a component the operation calls, or a failure that mixes parameter and non-parameter violations. It SHALL be answered `500` with `code` `INTERNAL_ERROR` and the generic detail `An unexpected error occurred.`. The body SHALL NOT contain the violation message, the constraint name or the offending value. The failure SHALL be logged at ERROR level with the correlation id.

A server-side violation SHALL never be reported as a client fault. Acceptance check: web-slice tests through a test-only validated operation, using the production validation and error-handling wiring:
- an operation whose result violates its declared result constraint asserts `500`, `INTERNAL_ERROR`, the generic detail, that the body does not contain the violation message, and that an ERROR log entry carries the correlation id;
- the existing tests for a violating called component (`500`) and for out-of-range query parameters (named `400`) still pass;
- a test-only operation with a repeated query parameter `ids` whose values must each be at least `0` asserts that `ids=1&ids=-1` gives `400` `BAD_REQUEST` with `detail` `Query parameter 'ids' is invalid.`;
- a handler-level test that passes a real violation set containing one parameter violation and one result violation of the same operation asserts `500`.

#### Scenario: Result constraint violation is a server fault
- **WHEN** a client calls a test-only operation whose result violates the constraint declared on it, with the message `secret-detail must not be blank`
- **THEN** the response status is `500`, `code` is `INTERNAL_ERROR`, `detail` is `An unexpected error occurred.`, the body does not contain `secret-detail`, and the server log has an ERROR entry with the response's correlation id

#### Scenario: Violation from a called component is a server fault
- **WHEN** a test-only operation with valid parameters calls a validated component that rejects its argument
- **THEN** the response status is `500` and `code` is `INTERNAL_ERROR`

#### Scenario: Mixed parameter and non-parameter violations are a server fault
- **WHEN** one constraint failure carries both a violation of the operation's `page` parameter and a violation of the operation's result
- **THEN** it is answered `500` with `code` `INTERNAL_ERROR`, and `detail` does not name `'page'`

#### Scenario: Parameter violations remain a named client fault
- **WHEN** a client sends `GET /api/v1/movies?size=101`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` is `Query parameter 'size' is invalid.`

#### Scenario: Violation on one value of a repeated parameter is a named client fault
- **WHEN** a client calls a test-only operation whose repeated query parameter `ids` requires every value to be at least `0`, with `ids=1&ids=-1`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` is `Query parameter 'ids' is invalid.`
