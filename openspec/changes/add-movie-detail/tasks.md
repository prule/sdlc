## 1. OpenAPI contract (contract-first)

- [ ] 1.1 Add `MovieLinks { self }` to `components/schemas/common.yaml` (fixed-relation `_links`, `$ref`ing `Link`).
- [ ] 1.2 Create `components/schemas/movie.yaml`: `Genre` (enum), `MovieData` (required `id`,`title`,`releaseYear`,`genres` [minItems:1],`_links`; optional `runtimeMinutes`,`synopsis`,`rating` [min 0, max 5]) with `_links: $ref MovieLinks` (required, mirroring `SampleItem`/`SampleItemLinks`), and `MovieEnvelope { data: MovieData, meta: Meta }`.
- [ ] 1.3 Create `paths/movies.yaml`: `getMovie` — `GET`, `security: []`, `id` path param (`format: uuid`), `CorrelationId` param, `200` → `MovieEnvelope`, `400`/`404`/`500` → shared responses; `X-Correlation-Id` response header.
- [ ] 1.4 Register in root `openapi.yaml`: add `movie` tag, `paths./movies/{id}` → `$ref paths/movies.yaml`, and `components/schemas` refs for `Genre`, `MovieData`, `MovieEnvelope`, `MovieLinks`.

## 2. Generate stubs

- [ ] 2.1 Run the bundle → generate pipeline (`./gradlew openApiGenerate`); confirm generated `MovieApi`, `MovieEnvelope`, `MovieData`, `MovieLinks`, `Genre`, and no per-operation `<Operation><Status>Response*` duplicates (getMovie binds the shared `MovieEnvelope`).

## 3. Domain

- [ ] 3.1 `domain/model/MovieId` (opaque wrapper over `UUID`).
- [ ] 3.2 `domain/model/Genre` enum (controlled vocabulary per design D-Open-Questions).
- [ ] 3.3 `domain/model/Rating` value object enforcing the 0–5 range (reject out-of-range).
- [ ] 3.4 `domain/model/Movie` record: required id/title/releaseYear/`Set<Genre>` (≥1, validated) + optional runtime/synopsis/rating; no Spring/JPA imports.

## 4. Application (use case + port)

- [ ] 4.1 `application/port/in/GetMovieDetailUseCase` (`Movie getMovie(MovieId)`).
- [ ] 4.2 `application/port/out/LoadMoviePort` (`Optional<Movie> findById(MovieId)`).
- [ ] 4.3 `application/service/GetMovieDetailService` implementing the use case; throw `ResourceNotFoundException("MOVIE_NOT_FOUND", …)` when the port returns empty. Depends only on domain.

## 5. Persistence adapter + migration

- [ ] 5.1 Flyway `V2__create_movies.sql`: `movies` + `movie_genres` tables with the rating `CHECK` (per design D4); never edit `V1`.
- [ ] 5.2 `adapters/out/persistence` JPA entities (`MovieJpaEntity` + genres) and Spring Data repository.
- [ ] 5.3 `MoviePersistenceAdapter implements LoadMoviePort`, mapping JPA → domain `Movie`.

## 6. Web adapter (inbound)

- [ ] 6.1 `adapters/in/web/MovieController implements MovieApi`, `@Validated`; call the use case, map domain `Movie` → `MovieEnvelope`, omit absent optionals, build `data._links.self` via `WebMvcLinkBuilder`, populate `Meta` from `CorrelationId.current()`.
- [ ] 6.2 Add `/movies/{id}` (verbatim, matching the OpenAPI path key — not `/movies/*`) to `PublicEndpoints.PATTERNS`; confirm the public-endpoint consistency test passes.

## 7. Tests (per layer)

- [ ] 7.1 Domain unit tests: `Rating` rejects out-of-range / accepts 0–5; `Movie` rejects empty genres.
- [ ] 7.2 Application unit test: `GetMovieDetailService` returns the movie on hit; throws `MOVIE_NOT_FOUND` on miss.
- [ ] 7.3 Web `@WebMvcTest` (mock use case): success (all fields + `_links.self`), missing-optionals success (optional keys absent, not null), not-found → `404` `MOVIE_NOT_FOUND` problem+json, malformed id → `400` problem+json; assert `400` ≠ `404` distinctness.
- [ ] 7.4 Persistence Testcontainers-Postgres test: saved movie (with and without optionals, ≥1 genre) round-trips via the adapter to domain; own fixtures (seed-independent).

## 8. Demo seed + verification

- [ ] 8.1 Add a small demo-profile seed so the H2 default runtime serves a movie; optionally extend the default-runtime smoke test to assert `GET /movies/{seededId}`. Keep all other tests seed-independent.
- [ ] 8.2 Run `./gradlew build -x spotlessCheck` and confirm green (includes `GeneratedApiCodegenTest`).
