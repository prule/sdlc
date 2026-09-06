## Why

UC-001 requires the public catalog to serve one movie's detail by its stable identifier so consumers can present accurate movie information. The catalog is currently reset to the walking-skeleton foundation — there is no movie code, schema, or contract — so the `catalog/movies` capability must be introduced from first principles.

## What Changes

- Introduce a public, read-only `GET /movies/{id}` operation that returns one movie's detail wrapped in the standard success Envelope with a HAL `self` link.
- Model the Movie aggregate (id, title, releaseYear, one-or-more genres; optional runtimeMinutes, synopsis, rating 0–5) across a clean/hexagonal `com.acme.catalog.movies` package.
- Distinguish two failure outcomes per UC-001: a **malformed** identifier is rejected as `400` before any lookup; a **well-formed** identifier that matches no movie is `404`. Both reuse the existing global exception handler — no new exception type, no new handler.
- Add a Flyway migration (V2) creating the `movies` and `movie_genres` schema on PostgreSQL, plus a persistence adapter.
- Add a demo-profile seed so the zero-dependency H2 default runtime serves a demonstrable movie; tests stay seed-independent (own fixtures, seed disabled under the test profile).
- Register `/movies/{id}` as a public endpoint in the single `PublicEndpoints.PATTERNS` source of truth (verbatim as the OpenAPI path key), keeping the consistency test green.

No BREAKING changes: this is purely additive (new capability, new schema, new endpoint). V2 is a new migration; no applied migration is edited.

## Capabilities

### New Capabilities
- `catalog/movies`: Retrieve one movie's detail by its stable opaque identifier over the public read API — required vs optional detail fields, malformed-vs-not-found outcomes, public/read-only access.

### Modified Capabilities
<!-- None: catalog/movies is new; platform specs are unchanged. -->

## Impact

- **New code**: `com.acme.catalog.movies` (domain, application, adapters/in/web, adapters/out/persistence) and a demo seed component.
- **API**: new `GET /api/v1/movies/{id}`; new OpenAPI files `paths/movies.yaml` + `components/schemas/movie.yaml`, `$ref`ed from `openapi.yaml`. Generated `MoviesApi` interface + DTOs.
- **DB**: new Flyway migration `V2__movies.sql` (tables `movies`, `movie_genres`).
- **Security**: `PublicEndpoints.PATTERNS` gains `/movies/{id}` (public, `security: []`).
- **Reused unchanged**: shared Envelope/Meta/Problem/Link components, HAL convention, global `@RestControllerAdvice` (`ResourceNotFoundException` → 404, `MethodArgumentTypeMismatchException` → 400), H2-default/Postgres-profile datasource, redocly-bundle → openapi-generator pipeline.
