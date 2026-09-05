## Context

See proposal.md — Why. Today `application.yml` hardcodes a PostgreSQL default datasource, so
`./gradlew bootRun` requires an external Postgres. Tests already override the datasource programmatically:
`PostgresIntegrationTest` starts a shared `PostgreSQLContainer` and injects its JDBC URL via
`@DynamicPropertySource`, so tests are independent of `application.yml`'s default. The Flyway set is V1–V5
(baseline `COMMENT ON SCHEMA`, `uuid`/`numeric` columns, `chk_movies_rating`, `chk_credits_type`, the
compound `chk_credits_cast_fields`, and btree indexes). The demo seed (`DemoMovieSeedLoader`) is an
`ApplicationRunner` gated `@Profile("demo")`. Constraint: no API/contract change; clean architecture confines
this to config/build/test wiring; the Flyway rule forbids changing an applied migration's Postgres semantics.

## Goals / Non-Goals

**Goals:**
- Default (no-profile) runtime on H2 in-memory, Flyway-migrated + demo-seeded, zero external dependencies.
- Postgres retained behind a profile with identical behaviour and env-var overrides.
- Tests remain on Testcontainers-Postgres, provably (a guard), with no H2 leak.

**Non-Goals (design-level):**
- No vendor-split migration directories (decision D-MIG below makes them unnecessary).
- No change to persistence-logic tests, fixtures, or the shared-container pattern.
- No `ddl-auto` change — Flyway remains the sole schema owner on both engines.

## Decisions

### OpenAPI operations
None. This change adds/changes **no** OpenAPI operations. The HTTP contract, generated interfaces/DTOs, and
`GeneratedApiCodegenTest` are untouched. No contract snippet applies.

### Components touched (dependency direction preserved)
Only adapter-out/config and test wiring change; domain and application are untouched and carry no
DB-vendor knowledge (dependencies still point inward only):
- `build.gradle.kts` — add `runtimeOnly("com.h2database:h2")`.
- `src/main/resources/application.yml` — default datasource → H2.
- `src/main/resources/application-postgres.yml` (new) — Postgres datasource under the `postgres` profile.
- `adapters/out/persistence/DemoMovieSeedLoader` — profile gating only (no logic change).
- `common/test/PostgresIntegrationTest` — add a `test` profile marker; new guard + smoke tests.

### D-MIG (crux): single shared migration set, V1–V5 unchanged
**Chosen: option (a) — one shared migration set that runs on both engines. NOT vendor-split.**
Empirically verified: running V1→V5 concatenated against `jdbc:h2:mem:…;MODE=PostgreSQL` applied all 14
statements with exit 0, including `COMMENT ON SCHEMA public`, `uuid`/`numeric(2,1)` columns, FKs, and every
btree index. CHECK constraints enforce identically on H2: inserting a `CAST` credit carrying `department`
was rejected by `chk_credits_cast_fields`, and `rating = 9.9` was rejected by `chk_movies_rating`. H2 in
`MODE=PostgreSQL` also lower-cases unquoted identifiers as Postgres does, so the lowercase unquoted DDL
needs no case handling. Therefore **no migration file changes and no `{vendor}` split** — the smallest change
that satisfies the ticket, and it keeps Postgres semantics byte-for-byte untouched (Flyway rule honoured).
*Alternative (b) vendor-specific locations* was rejected: it adds duplication/maintenance for no benefit
given (a) is proven to work. If a **future** migration ever needs Postgres-only SQL, that later change can
introduce the `{vendor}` split then; this change does not pre-build it.

### D-CONN: H2 connection settings & dialect
Default datasource URL `jdbc:h2:mem:sdlc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`.
- `mem:` satisfies D3 (in-memory, reset on restart).
- `MODE=PostgreSQL` gives PG-compatible DDL/DML so the shared migrations and runtime queries behave.
- `DB_CLOSE_DELAY=-1` keeps the in-mem DB alive for the JVM lifetime (otherwise it would be dropped when the
  pool's last connection closes mid-startup). H2 driver is `runtimeOnly`.
- **Dialect:** not set explicitly. Spring Boot auto-detects the Hibernate dialect from connection metadata
  (H2 → `H2Dialect`; Postgres → `PostgreSQLDialect`) on both profiles. Confirmed as the intended behaviour;
  no `spring.jpa.database-platform` override is added. Flyway needs no extra module for H2 (flyway-core has
  built-in H2 support); `flyway-database-postgresql` stays for the Postgres path.

### D-PROFILE: profile layout & test-datasource guard
- Default (no profile) = H2, in `application.yml`. `postgres` profile = Postgres, in `application-postgres.yml`
  carrying today's `${DB_URL:…}`/`${DB_USERNAME:…}`/`${DB_PASSWORD:…}` values.
- **Tests keep selecting Postgres via the existing mechanism**: `PostgresIntegrationTest`'s
  `@DynamicPropertySource` overrides `spring.datasource.*` and `spring.flyway.*` to the Testcontainers URL.
  This override is programmatic and wins over `application.yml`, so making the file default H2 does not change
  what tests connect to.
- **Guard (D2 safeguard):** add a guard test extending `PostgresIntegrationTest` that opens a connection and
  asserts `getMetaData().getDatabaseProductName()` equals `PostgreSQL` (equivalently, the resolved Hibernate
  dialect is the Postgres dialect). If H2 ever leaked into the test datasource, this fails loudly. This is the
  concrete artifact behind spec requirement "Tests always run against PostgreSQL, never the in-memory engine".
- H2 is `runtimeOnly`, so it is present on the test runtime classpath; the guard — not classpath exclusion —
  is the chosen safeguard (robust and simple; matches what the ticket asks for).

### D-SEED: demo seed default-on for H2, off for postgres/test
The runtime default and the test suite both run under the *no-profile default*, so profile name alone cannot
tell them apart. Resolution:
- Add `@ActiveProfiles("test")` to `PostgresIntegrationTest` as an explicit test marker (inert — there is no
  `application-test.yml`; datasource still comes from `@DynamicPropertySource`).
- Re-gate the seed to `@Profile("(!test & !postgres) | demo")`:
  - default H2 runtime (no profile) → **on** (demo populated at first boot);
  - `postgres` profile → **off** (never seeds a real/prod DB);
  - test suite (`test` profile) → **off** (tests own their fixtures — D2/testing.md);
  - `demo` profile → **on** even alongside postgres/test, preserving the existing explicit-demo behaviour that
    `DemoMovieSeedLoaderEnabledTest` (`@ActiveProfiles("demo")`) relies on.
- The seed body is unchanged and stays idempotent (deterministic ids, existence checks).
- Existing seed tests adapt: `DemoMovieSeedLoaderDisabledTest` (inherits `test`) still asserts the bean is not
  registered; `DemoMovieSeedLoaderEnabledTest` (`@ActiveProfiles("demo")`) still runs the seed and cleans up.

### D-SMOKE: proving the H2 default boots (narrow, intentional H2 test)
Spec requirement "Default runtime uses a zero-dependency in-memory database" needs a test that actually
exercises H2. Add ONE narrow default-profile smoke test that boots the app context on H2 **without
Testcontainers** and asserts Flyway applied, the seed populated, and `GET /api/v1/movies` + `GET /api/v1/people`
return populated `200`s (MockMvc). This is testing the H2 *runtime configuration itself*, not substituting H2
for Postgres in a persistence-logic test — so it does not violate D2's intent. standards/testing.md is updated
to carve out exactly this: no-H2 applies to DB/persistence tests; a single dedicated smoke test may verify the
H2 default runtime because the assertion is meaningless on Postgres.

### D-UUID (approved scope addition): native-query UUID portability across H2/Postgres

Implementation surfaced a blocker not anticipated in D-MIG's empirical check (which covered DDL/CHECK
constraints, not driver-level result mapping): two native-SQL id-page queries select a `uuid` column and
cast the JDBC result directly to `java.util.UUID`. On Postgres the driver returns `java.util.UUID` for a
`uuid` column; on H2 — even in `MODE=PostgreSQL` — the same column comes back as `byte[]`, so the cast
throws `ClassCastException: [B cannot be cast to java.util.UUID`, surfacing as a `500` on `GET
/api/v1/movies` (search) and `GET /api/v1/people/{id}/credits` (filmography) under the H2 default runtime.

**Chosen fix (approved):** in both native queries, project the id as text —
`SELECT CAST(m.id AS varchar) ...` / `SELECT CAST(c.id AS varchar), CAST(c.movie_id AS varchar), ...` —
read each row as `String`, and `UUID.fromString(...)` it before use. `CAST(... AS varchar)` (not the
Postgres-only `::text`) behaves identically on both engines. Only the *projection* changes: `WHERE`,
`ESCAPE`, `ORDER BY` (still ordering on the real `uuid`/other columns), `LIMIT`/`OFFSET`, and the bounded
statement count are all unchanged, so Postgres ordering/pagination/behaviour and the existing
query-count guard tests are unaffected.

Affected components (adapters/out/persistence only — no domain/application change):
- `MovieSearchPersistenceAdapter.selectPageIds` (movie search id-page).
- `PersonFilmographyJpaAdapter.selectCreditRowsPage` (filmography credit-row id-page; `c.id` and
  `c.movie_id` both fixed). `countCredits`/`countMatchingIds` were already engine-agnostic (`(Number)
  result).longValue()`), so no change was needed there.

**Coverage:** the H2 default-profile smoke test (D-SMOKE) is expanded beyond the original two
list-endpoint assertions to also hit `GET /api/v1/movies/{id}`, `GET /api/v1/movies/{id}/credits`, and
`GET /api/v1/people/{id}/credits` against known demo-seed ids — the endpoints that exercise these two
native-query paths — so a future native query reintroducing this bug fails the H2 smoke test, not just a
user's `bootRun`.

**Risk carried forward:** any *future* native query that selects a `uuid` column and casts straight to
`UUID` will reintroduce this exact failure mode on H2. Documented in CLAUDE.md so new native queries use
the `CAST(... AS varchar)` + `UUID.fromString(...)` pattern (or avoid native id projection) from the
start.

## Risks / Trade-offs

- **[H2 runtime diverges from Postgres in untested ways]** (pagination, ordering, `LIKE … ESCAPE`, UUID byte
  ordering) → Accepted and bounded: H2 is a *demo convenience*, Postgres remains the only correctness surface
  (all persistence tests stay on it). Documented in CLAUDE.md/testing.md so no one treats H2 as tested.
- **[H2 on the test runtime classpath could leak into a test datasource]** → Mitigated by the D-PROFILE guard
  test that fails the build if the active test datasource is not Postgres.
- **[Seed becoming default-on changes startup behaviour]** → Seed is idempotent (deterministic ids +
  existence checks); gated off under postgres/test so it cannot touch a real or test DB.
- **[Future migration uses Postgres-only SQL]** → Not a problem for this change (V1–V5 verified shared);
  a later change introduces a `{vendor}` split if/when needed.

## Migration Plan

- **Flyway:** no migration added or edited. V1–V5 run unchanged on both engines (verified on H2
  `MODE=PostgreSQL`). Rollback: revert the config/build/test commits; migrations are untouched, so no schema
  rollback is involved. The in-mem H2 DB is disposable (reset on restart); Postgres data is unaffected.
- **Deploy:** production/real use activates the `postgres` profile — same datasource wiring and env-vars as
  today, so no operational change. Default (no-profile) runs are demo-only.
- **Verify:** `./gradlew build -x spotlessCheck` (full Testcontainers-Postgres suite + guard test) plus the
  H2 default-profile smoke test; optionally `./gradlew bootRun` with no Docker to confirm zero-dependency boot.
