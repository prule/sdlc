## 1. OpenAPI contract (author first)

- [x] 1.1 Add `MovieLinks` (required `self` → `Link`) to `components/schemas/common.yaml` and wire it into `openapi.yaml#/components/schemas`.
- [x] 1.2 Add `components/schemas/movie.yaml` with `MovieDetail` (required `id`,`title`,`year`,`genres`(minItems 1),`_links`; optional `runtimeMinutes`,`synopsis`,`rating` 0–5) and `MovieDetailEnvelope` (`data`→`MovieDetail`, `meta`→`Meta`); wire both into `openapi.yaml#/components/schemas`.
- [x] 1.3 Add `paths/movies.yaml` with `getMovieById` (`security: []`, path `id` uuid, `CorrelationId` param) → `200` MovieDetailEnvelope, `400`/`404`/`500` reusing shared responses; register `/movies/{id}` and the `movies` tag in `openapi.yaml`.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew build` (bundle→generate) and confirm the generated `MoviesApi` interface returns the shared `MovieDetailEnvelope` type.
- [x] 2.2 Confirm the codegen reuse guarantee holds (extend/verify `GeneratedApiCodegenTest`: no `getMovieById`/`<Op><Status>Response*` duplicates, one shared `Problem`, one shared `Link`).

## 3. Domain

- [x] 3.1 Create `com.acme.catalog.movies.domain.model`: `MovieId` (UUID value type), `Genre` (label), `Rating` (0–5 value type) — no Spring/JPA/HATEOAS imports.
- [x] 3.2 Create the `Movie` aggregate (record) with required id/title/year and ≥1 genre invariant enforced in the compact constructor, and optional runtime/synopsis/rating.
- [x] 3.3 Unit-test the aggregate: valid construction, rejects zero genres, rejects rating outside 0–5, optionals absent.

## 4. Application (use case + ports)

- [x] 4.1 Add inbound port `GetMovieByIdUseCase` and outbound port `LoadMovieByIdPort` (`Optional<Movie> load(MovieId)`).
- [x] 4.2 Implement `GetMovieByIdService`: returns the `Movie` or throws `ResourceNotFoundException` (code `MOVIE_NOT_FOUND`).
- [x] 4.3 Unit-test the service with the port mocked: found returns movie; missing throws not-found.

## 5. Persistence adapter + migration

- [x] 5.1 Add Flyway `V2__movies.sql`: `movies`, `genres`, `movie_genre` (uuid PKs, FKs, unique genre name, nullable optional columns, rating numeric 0–5). Do not edit `V1`.
- [x] 5.2 Add `MovieJpaEntity`/`GenreJpaEntity` and Spring Data repository under `adapters/out/persistence` (JPA annotations only here).
- [x] 5.3 Implement `MoviePersistenceAdapter implements LoadMovieByIdPort` mapping entity ↔ domain (domain never `@Entity`).
- [x] 5.4 Testcontainers (real Postgres, no H2) test: insert own fixtures, assert load-by-id maps all fields incl. optionals-absent, and unknown id → empty.

## 6. Web adapter (inbound)

- [x] 6.1 Implement `MovieController implements MoviesApi`: constructor-inject use case, map domain → generated `MovieDetail` DTO, assemble `self` link in the adapter (no HATEOAS in domain/application), wrap in envelope + meta.
- [x] 6.2 `@WebMvcTest`/MockMvc tests (port mocked): happy path 200 envelope+self link; optional-omitted 200; unknown id 404 problem+json (no `_links`); malformed UUID 400 (not 500); no auth header never 401/403; only `self` link, no `_embedded`.

## 7. Demo seed (profile-scoped, not prod, tests independent)

- [x] 7.1 Add a `@Profile("demo")` seed loader (`ApplicationRunner`) reading a small committed demo dataset, idempotent (insert-if-absent) — NOT a Flyway data migration.
- [x] 7.2 Verify the seed does not load under `prod` and that the test suite (no `demo` profile) passes with its own fixtures, independent of the seed.

## 8. Domain doc reconciliation

- [x] 8.1 Update `domain/glossary.md` (Rating row) and `domain/business-rules.md` to record the settled 0–5 star aggregate rating (resolve the TODO).

## 9. Validate

- [x] 9.1 Run `./gradlew build` (compile + Spotless + all tests green) and `openspec validate add-movie-detail --strict`.
