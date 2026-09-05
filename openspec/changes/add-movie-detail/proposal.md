## Why

The catalog has been reset to the platform foundation — there is no movie code, schema, or spec.
UC-001 (Retrieve a movie's details) is the first catalog capability: a public API consumer that
already knows a movie's stable identifier must be able to look it up and see its detail. This change
rebuilds `catalog/movies` from the use case, establishing the movie aggregate, schema, and read
endpoint that later catalog capabilities (search, credits) build on.

## What Changes

- Add a public, read-only endpoint `GET /api/v1/movies/{id}` returning one movie's detail inside the
  standard success Envelope, with `data._links.self` (HAL).
- Movie detail carries required fields (identifier, title, release year, one-or-more genres) and
  optional fields (runtime, synopsis, aggregate rating 0–5) that are omitted when absent.
- The identifier is a **UUID** (stable, opaque, never exposes internal storage). A value that is not
  a well-formed UUID is rejected as **400 Bad Request before any lookup** (malformed); a well-formed
  UUID matching no movie is **404 Not Found** (`MOVIE_NOT_FOUND`) — two distinct outcomes per BR-3.
- Author the OpenAPI operation + `Movie` schemas contract-first (`paths/movies.yaml`,
  `components/schemas/movie.yaml`, `MovieLinks` in `common.yaml`); the controller implements the
  generated interface. No per-operation `<Operation><Status>Response*` duplicates.
- New clean/hexagonal feature `com.acme.catalog.movies` (domain aggregate + `Genre` + `Rating`
  value object, application use case + outbound port, JPA persistence adapter, web adapter).
- **DB schema change**: new Flyway migration `V2` creating `movies` and `movie_genres` tables (next
  version after the `V1` baseline; not breaking — additive on a foundation with no movie tables).
- Register `/movies/{id}` (verbatim, matching the OpenAPI path key) in `PublicEndpoints.PATTERNS`
  (single source of truth) so the endpoint is reachable without authentication and the consistency
  test stays green.
- Add a small demo-profile seed so the H2 default runtime shows a movie; tests remain
  seed-independent (own fixtures).

## Capabilities

### New Capabilities
- `catalog/movies`: Retrieving a single movie's detail by its stable opaque identifier — the public,
  read-only representation (required + optional fields), and the malformed-vs-not-found outcomes.

### Modified Capabilities
<!-- None: this is the first catalog/movies capability. -->

## Impact

- **API**: adds `GET /api/v1/movies/{id}` (public, `security: []`). New OpenAPI path + schemas.
- **Code**: new package `com.acme.catalog.movies` (all layers); one new public-endpoint pattern
  (`/movies/{id}`) in `com.acme.common.security.PublicEndpoints`. Reuses the existing Envelope/Meta/Problem/Link
  components, codegen pipeline, HAL convention, and global error handler — no platform changes.
- **DB**: new Flyway migration `V2__create_movies.sql` (Postgres). No existing migration edited.
- **Demo/runtime**: optional demo-profile seed for the H2 default runtime; the default-runtime smoke
  test may be extended to assert the movie endpoint. DB/persistence tests use Testcontainers-Postgres.
