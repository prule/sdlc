## MODIFIED Requirements

### Requirement: Shared concepts defined once
Every shared concept that the served description contains SHALL appear exactly once, as a named component. Every use SHALL reference that component rather than redefine it.

The shared concepts are:
- the success-envelope metadata (`Meta`);
- the paging counts (`Pagination`);
- the paging navigation links (`PageLinks`);
- the paging query parameters `page` and `size`;
- `Link`;
- `Problem`;
- each reusable error response;
- the correlation-id response header.

No response SHALL use an inline object schema. A shared concept that no operation references yet is not required to appear in the served description; it is added when the first operation uses it.

Acceptance check, a test on the served document asserts that:
- `Meta`, `Pagination`, `PageLinks`, `Link`, `Problem` and the `X-Correlation-Id` header each appear exactly once under `components`;
- `page` and `size` each appear exactly once under `components.parameters`, and every operation that takes them references them;
- every response `content` schema in the document is a `$ref`;
- every `application/problem+json` response resolves to the one `Problem` schema.

#### Scenario: Availability error responses reuse the shared problem
- **WHEN** the served description is inspected
- **THEN** every non-2xx response of `/ping` uses the `application/problem+json` media type and a `$ref` to the single `Problem` schema

#### Scenario: No duplicated shared schema
- **WHEN** the served description is inspected
- **THEN** `components.schemas` contains exactly one `Problem`, one `Meta`, one `Pagination`, one `PageLinks` and one `Link`, and no other schema has the same set of properties as `Problem`

#### Scenario: Paging parameters are shared
- **WHEN** the served description is inspected
- **THEN** the `/movies` operation's `page` and `size` parameters are `$ref`s to the single `page` and `size` entries under `components.parameters`, and its page's `_links` schema is a `$ref` to the single `PageLinks` schema
