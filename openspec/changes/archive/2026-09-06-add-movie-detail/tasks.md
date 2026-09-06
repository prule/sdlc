## 1. OpenAPI contract (contract-first)

- [x] 1.1 Add `MovieLinks { self }` to `src/main/resources/openapi/components/schemas/common.yaml` and register its `$ref` in `openapi.yaml#/components/schemas`.
- [x] 1.2 Create `src/main/resources/openapi/components/schemas/movie.yaml` with `Genre` (enum), `MovieDetail` (required id/title/releaseYear/genres[minItems 1]/_links; optional runtimeMinutes/synopsis/rating 0–5), `MovieDetailData`, and `MovieDetailEnvelope` (reusing shared `Meta`).
- [x] 1.3 Create `src/main/resources/openapi/paths/movies.yaml` with `GET /movies/{id}` (`operationId: getMovieById`, `security: []`, uuid path param, shared `CorrelationId` param; 200 → `MovieDetailEnvelope`, 400/404/500 → shared responses).
- [x] 1.4 Register the movie schema `$ref`s and the `/movies/{id}` path in `openapi.yaml`; add a `movies` tag.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate` (bundle → generate) and confirm `MoviesApi` + movie DTOs are generated and bind to the shared `Meta`/`Link` components (no per-operation `<Operation><Status>Response*` duplicates); `GeneratedApiCodegenTest` stays green.

## 3. Domain

- [x] 3.1 Create `com.acme.catalog.movies.domain.model.Genre` (enum matching the OpenAPI enum) and `Rating` (value object validating 0–5).
- [x] 3.2 Create `com.acme.catalog.movies.domain.model.Movie` (record aggregate) with a factory enforcing invariants: id + title + releaseYear present, ≥1 genre, optional runtimeMinutes/synopsis/rating (rating within 0–5). No Spring/JPA imports.

## 4. Application / ports

- [x] 4.1 Create inbound port `com.acme.catalog.movies.application.port.in.GetMovieDetailUseCase`.
- [x] 4.2 Create outbound port `com.acme.catalog.movies.application.port.out.LoadMoviePort` returning `Optional<Movie>`.
- [x] 4.3 Create `com.acme.catalog.movies.application.service.GetMovieDetailService` implementing the use case, throwing the existing `ResourceNotFoundException("NOT_FOUND", …)` when the port returns empty.

## 5. Outbound adapter + migration

- [x] 5.1 Add Flyway `src/main/resources/db/migration/V2__movies.sql` creating `movies` (uuid pk, title, release_year, nullable runtime_minutes/synopsis, nullable rating NUMERIC(2,1) CHECK 0–5) and `movie_genres` (movie_id fk cascade, genre, pk(movie_id,genre)).
- [x] 5.2 Create `MovieJpaEntity` (+ genres via `@ElementCollection`) and Spring Data `MovieJpaRepository` in `adapters/out/persistence`.
- [x] 5.3 Create `MoviePersistenceAdapter` implementing `LoadMoviePort`, mapping JPA entity ↔ domain `Movie`.

## 6. Inbound controller

- [x] 6.1 Create `com.acme.catalog.movies.adapters.in.web.MovieController implements MoviesApi`, binding the id as `UUID`, calling `GetMovieDetailUseCase`, mapping domain → `MovieDetailEnvelope`, and building `data._links.self` via `WebMvcLinkBuilder`.
- [x] 6.2 Register `/movies/{id}` verbatim in `com.acme.common.security.PublicEndpoints.PATTERNS`; confirm `PublicEndpointsConsistencyTest` passes.

## 7. Demo seed (H2 default runtime)

- [x] 7.1 Add an idempotent demo-seed component active when the `test` profile is NOT active (seeds one movie with all optional fields and one with none, each ≥1 genre, fixed UUIDs); confirm it does not run under the `test` profile.

## 8. Tests (happy / edge / failure per UC-001)

- [x] 8.1 Domain unit tests: `Movie` factory invariants (≥1 genre required, rating 0–5 bounds), `Rating`, `Genre`.
- [x] 8.2 Application unit test: `GetMovieDetailService` returns detail when found; throws `ResourceNotFoundException` when the mocked port is empty.
- [x] 8.3 Web `@WebMvcTest` (use case mocked): 200 with all fields; 200 with optionals omitted; well-formed unknown id → 404 problem+json; malformed id → 400 problem+json (no lookup); assert `data._links.self`.
- [x] 8.4 Persistence Testcontainers-Postgres test: adapter maps entity ↔ domain and returns the movie (with and without optionals) for a seeded fixture; `Optional.empty()` for an unknown id.
- [x] 8.5 (Optional) Extend `H2DefaultRuntimeSmokeTest` to assert `GET /movies/{seededId}` returns 200 on the default runtime.
- [x] 8.6 Run `./gradlew build -x spotlessCheck` and confirm green (agents do not format; the pre-commit hook does).
