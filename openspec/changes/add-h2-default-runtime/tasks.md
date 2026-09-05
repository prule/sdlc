<!-- Note: the standard OpenAPI→domain→adapter task ordering does not apply — this is a config/build/test
     change with no OpenAPI, domain, application, or migration edits. Tasks are ordered by dependency. -->

## 1. Build & datasource config

- [x] 1.1 Add `runtimeOnly("com.h2database:h2")` to `build.gradle.kts` dependencies (keep
      `flyway-database-postgresql` and `org.postgresql:postgresql`).
- [x] 1.2 In `src/main/resources/application.yml`, change the default `spring.datasource` to H2:
      `url: jdbc:h2:mem:sdlc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, `username: sa`, empty password. Do NOT set
      `spring.jpa.database-platform` (dialect auto-detected). Leave `spring.flyway.enabled/locations` as-is.
- [x] 1.3 Create `src/main/resources/application-postgres.yml` carrying today's Postgres datasource under the
      `postgres` profile: `url: ${DB_URL:jdbc:postgresql://localhost:5432/sdlc}`, `username: ${DB_USERNAME:sdlc}`,
      `password: ${DB_PASSWORD:sdlc}` (env-var overrides preserved).

## 2. Demo seed profile gating

- [x] 2.1 Change `DemoMovieSeedLoader`'s `@Profile("demo")` to `@Profile("(!test & !postgres) | demo")` and
      update its class javadoc to state it is default-on for the H2 demo runtime, off under `postgres` and
      under tests, and forced on by the `demo` profile. Do not change the seed logic (stays idempotent).

## 3. Test wiring, guard & smoke test

- [x] 3.1 Add `@ActiveProfiles("test")` to `common/test/PostgresIntegrationTest` (datasource still supplied by
      the existing `@DynamicPropertySource`; the marker only distinguishes tests from the H2 default runtime).
- [x] 3.2 Add a datasource guard test extending `PostgresIntegrationTest` that opens a connection and asserts
      `getMetaData().getDatabaseProductName()` equals `PostgreSQL` (proves no H2 leak into tests).
- [x] 3.3 Verify `DemoMovieSeedLoaderDisabledTest` (inherits the `test` profile) still asserts the seed bean is
      not registered, and `DemoMovieSeedLoaderEnabledTest` (`@ActiveProfiles("demo")`) still runs+cleans up the
      seed against Postgres; adjust profile annotations/javadoc only if needed to keep both green.
- [x] 3.4 Add ONE narrow default-profile H2 smoke test (NOT extending `PostgresIntegrationTest`, no
      Testcontainers) that boots the context on H2, and via a real HTTP client (RANDOM_PORT + TestRestTemplate,
      honouring the `/api/v1` context-path) asserts `GET /api/v1/movies`, `GET /api/v1/people`, `GET
      /api/v1/movies/{id}`, `GET /api/v1/movies/{id}/credits`, and `GET /api/v1/people/{id}/credits` each return
      `200` with populated data (proves the zero-dependency, Flyway-migrated, demo-seeded default boot, and
      exercises the two native-SQL id-page paths). Document in-file that it intentionally exercises the H2
      runtime. **Scope addition (approved):** coverage was expanded beyond the original two endpoints to also
      hit `MovieSearchPersistenceAdapter`'s and `PersonFilmographyJpaAdapter`'s native id-page queries, the
      paths affected by the H2 UUID-portability fix below.

## 3a. Approved scope addition — native-query UUID portability (blocker fix)

- [x] 3a.1 Fix `MovieSearchPersistenceAdapter`'s native id-page query (`selectPageIds`): project
      `CAST(m.id AS varchar)` instead of the raw `uuid` column, and parse each row with
      `UUID.fromString(...)`. Postgres's driver returns `java.util.UUID` for a `uuid` column, but H2 (even in
      `MODE=PostgreSQL`) returns `byte[]`, causing a `ClassCastException` on `GET /api/v1/movies` under the H2
      default runtime. `ORDER BY`/`WHERE`/`LIMIT`/`OFFSET` and the bounded statement count are unchanged; the
      count query already read via `((Number) ...).longValue()`.
- [x] 3a.2 Fix `PersonFilmographyJpaAdapter`'s native id-page query (`selectCreditRowsPage`): project
      `CAST(c.id AS varchar)` / `CAST(c.movie_id AS varchar)` instead of the raw `uuid` columns, and parse each
      with `UUID.fromString(...)`, for the same reason (`GET /api/v1/people/{id}/credits` 500s under H2
      otherwise). `countCredits` already reads via `((Number) ...).longValue()`. `ORDER BY`/`WHERE`/
      `LIMIT`/`OFFSET` and the bounded statement count are unchanged.

## 4. Docs & standards

- [x] 4.1 Update `CLAUDE.md` Stack section: H2 in-memory is the default local/demo runtime; Postgres is the
      test (Testcontainers) and production-capable target; Flyway migrations must stay H2- and Postgres-compatible
      (or be vendor-split later); keep the "no H2 in tests" rule intact.
- [x] 4.2 Update `standards/testing.md`: state the no-H2 rule applies to DB/persistence tests (which stay on
      Testcontainers-Postgres); the H2 default is runtime-only; the single H2 default-profile smoke test is the
      permitted exception (it verifies the runtime config, not persistence logic).
- [x] 4.3 Update `README.md`: document running on H2 (default, zero-setup) vs Postgres (`--args=... postgres`
      profile) with exact commands, and note Docker is still required for `./gradlew build`/tests.

## 5. Verification

- [x] 5.1 Run `./gradlew build -x spotlessCheck` — full Testcontainers-Postgres suite green, including the new
      guard test and unchanged contract/behaviour tests (`GeneratedApiCodegenTest`).
- [x] 5.2 Confirm the H2 default boots with zero dependencies: run the H2 smoke test (3.4) and/or `./gradlew
      bootRun` with no Docker/Postgres, then hit `GET /api/v1/movies` and `GET /api/v1/people` for populated `200`s.
