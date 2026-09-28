## 1. OpenAPI contract

- [x] 1.1 Add `BadRequest` and `NotFound` to `components/responses/common.yaml`, shaped like `NotAcceptable` (design D1). Verify: `npx redocly lint` passes.
- [x] 1.2 Add `components/schemas/movie.yaml` (`MovieEnvelope`, `MovieDetail`, `MovieLinks`, all closed, and no `uniqueItems` on `genres`), `paths/movies.yaml` (`getMovie`, `security: []`, 200/400/404/406/500, with a `description` that lists the standalone sample ids), and the `Movies` tag plus the `/movies/{id}` `$ref` in `openapi.yaml`, as in design D1. Verify: `./gradlew bundleOpenApiSpec` succeeds, and the bundled file has `/movies/{id}` with no external `$ref`.

## 2. Generate stubs

- [x] 2.1 Write the failing assertions in `GeneratedApiCodegenTest` first: `MoviesApi.getMovie(UUID)` returns `ResponseEntity<MovieEnvelope>`, `MovieDetail.getGenres()` is a `List`, `getRating()` is a `BigDecimal`, and no `GetMovie*Response*` class exists. Then run `./gradlew openApiGenerate`. Verify: `./gradlew test --tests '*GeneratedApiCodegenTest'` passes.

## 3. Domain (test-first)

- [x] 3.1 Write failing unit tests for `com.acme.shared.domain.DomainException` (abstract, carries `code()` and the message) and `ResourceNotFoundException` (extends it, code `NOT_FOUND`), then implement both until green. Both must be JDK-only.
- [x] 3.2 Write failing unit tests for `MovieId`, `RuntimeMinutes` (positive minutes) and `Rating` (a `BigDecimal` from 0 to 5 inclusive). Cover valid values, the boundaries 0 and 5, and rejection of -0.1, 5.1, runtime 0 and null. Then implement the records until green.
- [x] 3.3 Write failing unit tests for the `Movie` aggregate factory:
  - a blank title is rejected;
  - `Thriller, Drama, Sci-Fi` becomes `Drama, Sci-Fi, Thriller`;
  - mixed-case names order ignoring case, with a tie broken by exact name;
  - duplicates are removed;
  - an empty genre list is allowed;
  - all-absent optional fields give empty `Optional`s.

  Then implement until green.

## 4. Application and ports (test-first)

- [x] 4.1 Write failing unit tests for `GetMovieService` with a mocked `LoadMoviePort`: found returns the movie, empty throws `ResourceNotFoundException`, and a port exception propagates unchanged. Then add `GetMovieUseCase`, `LoadMoviePort` (`Optional<Movie>`) and `GetMovieService` until green.

## 5. Outbound adapter, migration and seed

- [x] 5.1 Add `db/migration/V1__create_movie_catalog.sql` (design D5), and set `spring.jpa.hibernate.ddl-auto: validate` and `spring.jpa.open-in-view: false` in `application.yml`. Verify immediately: `./gradlew test --tests '*H2DefaultRuntimeSmokeTest' --tests '*PersistentModeIntegrationTest'` passes.
- [x] 5.2a Add `GenreJpaEntity`, `MovieJpaEntity` and `MovieJpaRepository` (`findById` with `@EntityGraph(attributePaths = "genres")`). Map the columns as follows: `synopsis` as a plain `String` (no `@Lob`, no `columnDefinition`), `rating` as `BigDecimal` (never `Double`), `runtime_minutes` as `Integer`. `@ManyToMany` goes through `movie_genre`. Verify: the 5.1 command still passes, so `ddl-auto: validate` accepts the entities on both H2 and PostgreSQL.
- [x] 5.2b Write a failing Testcontainers test first (extending `PostgresIntegrationTest`, fixtures inserted with `JdbcTemplate`) for `MoviePersistenceAdapter`. It covers:
  - a fully populated movie;
  - a movie with no optional fields;
  - a movie with a blank synopsis, which maps to absent;
  - a movie with no genres;
  - genres linked out of order;
  - an unknown id, which returns empty.

  Then implement `MoviePersistenceAdapter implements LoadMoviePort` (`@Transactional(readOnly = true)`) until green.
- [x] 5.3 Add the standalone-only seed (design D7):
  - Add `db/demo/R__demo_movies.sql` with fixed ids `11111111-1111-4111-8111-111111111111` (all optional details, at least two genres linked out of A–Z order) and `22222222-2222-4222-8222-222222222222` (no runtime, synopsis or rating, and no genres), plus a couple more movies.
  - In `application.yml`, set `spring.flyway.locations: classpath:db/migration,classpath:db/demo`.
  - In `application-postgres.yml`, override it to `classpath:db/migration`.
  - In `PostgresIntegrationTest`'s `@DynamicPropertySource`, register `spring.flyway.locations=classpath:db/migration`.

  Verify, first: write `FlywayLocationsConfigTest` (in `com.acme.platform.runtimemodes`, plain JUnit, no Spring context) before editing the yml files. It loads each file with `YamlPropertySourceLoader` and asserts:
  - in `application-postgres.yml`, `spring.flyway.locations` is exactly `classpath:db/migration`;
  - in `application.yml`, the locations include `classpath:db/demo`.

  It should fail, then pass once the yml files are edited: `./gradlew test --tests '*FlywayLocationsConfigTest'`. Then the 5.1 command passes.

  This test is the primary check that persistent mode excludes the seed. A Postgres-booting test cannot prove it, because the base class overrides the locations.

## 6. Inbound web (test-first)

- [x] 6.1 Write failing unit tests for `StrictUuidConverter` first:
  - it accepts canonical lower-case and upper-case values;
  - it rejects `1-1-1-1-1`, the 32-hex form without hyphens, `123`, trailing characters, the empty string, and surrounding whitespace (including a value decoded from `%20`-padded input).

  Then implement it and register it through a `WebMvcConfigurer` in `com.acme.platform.web` (design D4) until green. Verify: the existing `FailureKindsTest` 400 case still passes.
- [x] 6.2 Write a failing platform web test first, with a test-only controller that throws `ResourceNotFoundException("…secret…")`. It asserts `404`, `NOT_FOUND`, and that the message is not echoed. Then add `@ExceptionHandler(ResourceNotFoundException.class)` to `GlobalExceptionHandler`, returning `problemFactory.create(404)` and logging at DEBUG, until green.
- [x] 6.3a Write failing `@PlatformWebTest` slice tests for `MovieController`, with `GetMovieUseCase` mocked, covering:
  - the 200 mapping for a full movie and a minimal one (absent optional fields are absent keys, `genres` `[]`);
  - the rating literals `5`, `4.5` and `0` coming from `5.0`, `4.5` and `0.0`;
  - `self` for a plain request and with `X-Forwarded-Proto`/`X-Forwarded-Host` headers;
  - `Accept: application/problem+json` still giving 200 `application/json`;
  - an upper-case id being canonicalised.

  Then implement `MovieController implements MoviesApi` (rating via `stripTrailingZeros()`, absent optionals left `null`, `self` via `WebMvcLinkBuilder`, content type forced to `application/json`) until green.
- [x] 6.3b Write failing slice tests for the failure cases, then make them pass:
  - 400 for each malformed value in the spec, with the use case never invoked;
  - 404 when the use case throws `ResourceNotFoundException`;
  - 500 when it throws `RuntimeException("secret-db-host:5432 refused")`: the body has no `secret-db-host`, the detail is generic, and a `com.acme.testsupport.LogCaptor` assertion shows the fault logged at ERROR with the response's correlation id.

## 7. Cross-cutting tests

- [x] 7.1 Update `ReadOnlyRefusalTest`: change the unknown-path case to `/no-such-thing/123`, and add the four write methods on `/movies/{id}`, each expecting 405 with `Allow` including `GET`. No other test needs changing. `FailureKindsTest`, `InterfaceDescriptionContractTest` and `Uc000Assertions` already use `/no-such-thing` for the "Nothing exists at the path" case, and `ReadOnlyRefusalTest` is the only test that uses `/movies`. Verify: `ReadOnlyRefusalTest` and `FailureKindsTest` pass.
- [x] 7.2 Add a Testcontainers end-to-end test for `GET /api/v1/movies/{id}` covering every `catalog/movies` scenario that needs real data:
  - the exact member set;
  - genre order stable across two requests;
  - empty genres;
  - rating literals `0` and `5`;
  - absent optional fields;
  - 404 for an unknown id;
  - `PUT` followed by `GET` showing the movie unchanged.

  Verify: the test passes.
- [x] 7.3 Add a sibling `MovieRuntimeModeAssertions` (next to `Uc000Assertions`, which stays unchanged): 404 `NOT_FOUND` for a random well-formed id, and 400 `BAD_REQUEST` for `not-a-movie-id`. Call it from both mode tests. In `H2DefaultRuntimeSmokeTest`, also assert that both sample movies return 200 with the expected members. In `PersistentModeIntegrationTest`, assert that `11111111-1111-4111-8111-111111111111` is 404. This is a secondary check only; `FlywayLocationsConfigTest` (5.3) is the primary one. Verify: `./gradlew test --tests '*H2DefaultRuntimeSmokeTest' --tests '*PersistentModeIntegrationTest'` passes.
- [x] 7.4 Extend `InterfaceDescriptionContractTest` to validate real `getMovie` 200 (Postgres fixture), 400, 404 and 405 bodies against the served schema. Verify: the test passes, and `NoUndocumentedHttpCapabilitiesTest` still passes.

## 8. Domain docs and final check

- [x] 8.1 In `domain/business-rules.md`, reword "Movie detail points to where the Movie's credits can be found … (decided CAT-003)" to say that the pointer to the Movie's credits is added when the credits capability exists, and that cast and crew are never included inside movie detail. Apply the same wording to the matching note in `domain/glossary.md` ("How related information is reached") and the movies/credits line in `domain/bounded-contexts.md`. Keep the user's other uncommitted edits in those files. Verify: `grep -n "credits" domain/*.md` shows no claim that movie detail points to credits today.
- [x] 8.2 Run the full build: `./gradlew spotlessApply build`. Verify: all tests pass and `spotlessCheck` is clean.
