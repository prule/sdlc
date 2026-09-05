## Why

The product is a public, read-only demo movie API, but the only runtime datasource today is
PostgreSQL: `application.yml` defaults `spring.datasource.url` to `jdbc:postgresql://localhost:5432/sdlc`,
so `./gradlew bootRun` needs a Postgres instance (Docker or local install) before the app starts. That is
friction for the core "clone → run → demonstrate" journey. We want the default (no-profile) runtime to
come up on H2 in-memory with zero external dependencies — Flyway-migrated and demo-seeded so the documented
endpoints return populated `200`s immediately — while keeping Postgres as the tested and production-capable
target behind a profile.

## What Changes

- Add H2 as a **runtime** dependency and make the **default (no-profile) datasource** H2 in-memory
  (`jdbc:h2:mem:sdlc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`), reset on each restart.
- Run the **existing V1–V5 Flyway migration set unchanged** on H2 (empirically verified to apply and to
  enforce all CHECK constraints in `MODE=PostgreSQL`). Single shared migration set — no vendor split.
- Introduce a **`postgres` profile** (`application-postgres.yml`) that selects the Postgres datasource,
  reproducing today's behaviour and env-var overrides (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`).
- Make the **demo seed default-on** under the H2 default (currently `@Profile("demo")`) so first boot is
  populated; keep it OFF under the `postgres` profile and under tests, and keep it idempotent.
- Add an explicit **test-datasource guard**: tests stay pinned to Testcontainers-Postgres, and a guard test
  asserts the live test datasource/dialect is PostgreSQL so an H2 leak fails loudly. **No test moves to H2.**
- Update docs/standards: CLAUDE.md Stack, standards/testing.md (no-H2 applies to tests; H2 default is
  runtime-only), and README.md (how to run on H2 default vs Postgres profile; Docker still needed for tests).

No API/contract, envelope, HAL, or behavioural change. `GeneratedApiCodegenTest` and all existing tests stay green.

## Capabilities

### New Capabilities
- `platform/runtime-datasource`: The default runtime datasource is a zero-dependency in-memory database
  (H2), Flyway-migrated and demo-seeded; Postgres is selectable via a profile; and tests always run against
  Postgres, never H2 (enforced by a guard). Behavioural, testable, and additive.

### Modified Capabilities
<!-- None. Existing platform/catalog specs describe engine-agnostic HTTP behaviour and do not assert a
     datasource engine in any promoted requirement text, so no requirement is modified. -->

## Impact

- **Build:** `build.gradle.kts` — add `runtimeOnly("com.h2database:h2")`.
- **Config:** `src/main/resources/application.yml` (default → H2), new `application-postgres.yml` (Postgres).
- **Seed:** `DemoMovieSeedLoader` profile gating changes (default-on for H2 demo; off for postgres/test).
- **Tests:** `PostgresIntegrationTest` gains a `test` profile marker + a new datasource guard test; a new
  narrow H2 default-profile smoke test verifies the zero-dependency boot (does NOT use Testcontainers).
- **Migrations:** none changed (V1–V5 unchanged; verified compatible on H2 PostgreSQL-mode).
- **Docs:** CLAUDE.md, standards/testing.md, README.md.
- **No changes** to domain/application/adapter logic, the OpenAPI contract, or generated code.
