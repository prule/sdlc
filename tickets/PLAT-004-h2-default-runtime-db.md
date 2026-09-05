# PLAT-004: Make H2 (in-memory) the default runtime database — zero-dependency run/demo

**Type:** Technical
**Area:** platform (build / runtime configuration) — supports the `catalog` bounded context (see domain/bounded-contexts.md)
**Status:** Ready (author decisions D1–D4 settled; the Open questions are architect "how" calls for Gate 1)

## Problem / rationale
The product is a public, read-only demo movie API (domain/overview.md, domain/bounded-contexts.md
`catalog`). Today the only runtime datasource is PostgreSQL: `application.yml` defaults
`spring.datasource.url` to `jdbc:postgresql://localhost:5432/sdlc`, so `./gradlew bootRun` needs a
Postgres instance (via Docker or a local install) before the app will start. That is friction for the
core "clone → run → demonstrate" journey we want for a demo API.

We want the **default runtime** (no profile active) to come up on **H2 in-memory** with **zero
external dependencies** — no Docker, no Postgres — Flyway-migrated and demo-seeded so the documented
endpoints return populated `200`s immediately. Postgres remains the **tested and production-capable**
target, retained behind a profile.

This is a **runtime/config change only**. It must not weaken test fidelity: the repository's
real-Postgres integration tests (Testcontainers) have repeatedly caught bugs that H2 would hide —
the HHH000104 in-memory-pagination trap, DB CHECK constraints (e.g. `chk_credits_cast_fields`),
`LIKE … ESCAPE` escaping, and byte-wise UUID ordering. Those tests **stay on Postgres**.

## Decisions (settled by the author — do NOT re-open)
- **D1 — H2 is the RUNTIME/DEMO default only.** `./gradlew bootRun` (default profile) boots on H2
  in-memory, Flyway-migrated and demo-seeded (the existing `@Profile("demo")` seed in
  `DemoMovieSeedLoader`, or an equivalent default-on seed) so the API is immediately demonstrable
  with no setup.
- **D2 — Tests STAY on Testcontainers-Postgres; the "no H2 in tests" rule is PRESERVED.** This ticket
  must NOT move any test to H2. standards/testing.md §3 (all DB tests on real Postgres via
  Testcontainers, never H2) remains fully in force **for tests**; only the runtime default changes.
- **D3 — H2 in-memory, reset on restart** (`jdbc:h2:mem:…`). Fresh DB each start; Flyway + demo seed
  repopulate it. Not file-based.
- **D4 — Postgres is retained as a profile-activated option** (for tests, for running against real
  Postgres, and for any production use). A profile (name TBD, e.g. `postgres`) selects the Postgres
  datasource; the default (no profile) is H2.

## Scope
- Add H2 as a **runtime dependency** and make the **default (no-profile) datasource** H2 in-memory
  (`jdbc:h2:mem:…`), with any connection settings needed for compatibility (see Open questions).
- Ensure **Flyway runs cleanly on H2** for the default profile, applying the existing migration set
  (V1–V5) — via whichever migration-compatibility strategy is chosen (see the crux Open question).
- Make the **demo seed default-on** under the H2 default so the app is populated at first boot
  (currently gated behind `@Profile("demo")`); keep it loadable/appropriate under other profiles
  (see Open questions).
- Introduce a **Postgres profile** that selects the Postgres datasource, reproducing today's behaviour
  when active.
- Add a **safeguard** so the new H2 default cannot silently leak into tests — tests must remain pinned
  to Postgres (they already override the datasource via the Testcontainers base + `@DynamicPropertySource`;
  make this guarantee explicit and provable).
- **Update docs/standards** so the split is unambiguous — CLAUDE.md Stack, standards/testing.md, and
  README.md run instructions (see "Standards / docs to update").

## Out of scope
- Moving **tests** to H2 or removing Testcontainers (explicitly excluded — D2).
- Removing Postgres support (retained — D4).
- Any production deployment/infra change beyond keeping Postgres viable via a profile.
- Any **API/contract, envelope, HAL, or behavioural change** — this is purely datasource/config.
- Any new `catalog` feature or query change.

## Constraints (standards that apply)
- **standards/testing.md §3** — DB tests remain on **real Postgres via Testcontainers**; H2 stays
  **banned in tests**. The runtime-only H2 default must not reach the test classpath's datasource.
  New/changed behaviour still ships with happy/edge/failure tests.
- **standards/clean-architecture.md** — the change is confined to configuration/dependencies (and
  possibly migration files); **domain and application layers must have no DB-vendor knowledge**, and
  no adapter logic changes beyond datasource wiring.
- **CLAUDE.md (Flyway rule)** — never edit an applied migration's Postgres semantics; prefer additive
  or genuinely-compatible edits, or vendor-split migrations, over changing V1–V5's Postgres behaviour.
- **standards/openapi.md / openspec/specs/platform/api-codegen** — the HTTP contract and codegen are
  untouched; `GeneratedApiCodegenTest` stays green.
- **standards/formatting.md** — google-java-format via Spotless still applies to any touched Java.

## Acceptance / done criteria
- [ ] **Default run is zero-dependency.** With no profile active and **no Docker/Postgres running**,
      the documented command (e.g. `./gradlew bootRun`) boots the app on H2 in-memory, Flyway applies
      all migrations cleanly, and the demo seed loads. `GET /api/v1/movies` and `GET /api/v1/people`
      each return a populated `200`. (The exact run command is documented in README.md.)
- [ ] **Migrations apply on H2.** All of V1–V5 (or the chosen migration layout) run to completion on
      H2 without error; any Postgres-specific SQL is made compatible or vendor-split per the resolved
      Open question — without changing V1–V5's Postgres semantics.
- [ ] **Tests still run on Postgres — no regression, no H2 leak.** The full suite still executes
      against Testcontainers-Postgres; the H2 default does not bleed into tests. This is proven by a
      safeguard: the DB integration tests demonstrably hit Postgres, not H2 (e.g. an assertion/guard
      that the active test datasource/dialect is PostgreSQL), and all existing DB tests stay green.
- [ ] **Postgres profile still works.** Activating the Postgres profile runs the app against a real
      Postgres, behaviourally unchanged from today (same datasource env-var overrides honoured).
- [ ] **No API/contract change.** The HTTP contract, success envelope, HAL links, and behaviour are
      unchanged; `GeneratedApiCodegenTest` and all existing tests remain green.
- [ ] **Clean architecture unaffected.** No domain/application/adapter logic changes beyond datasource
      config; the domain carries no DB-vendor knowledge.
- [ ] **Docs/standards updated** per "Standards / docs to update" below, so the H2-default /
      Postgres-tested-and-production split is stated in CLAUDE.md, standards/testing.md, and README.md.

## Standards / docs to update (part of this work)
- **CLAUDE.md (Stack):** clarify **H2 in-memory is the default local/demo runtime**, **Postgres is
  the test (Testcontainers) and production-capable target**, and Flyway migrations must remain
  **H2- and Postgres-compatible** (or vendor-split). Keep the "no H2 in tests" rule intact.
- **standards/testing.md:** state that the no-H2 rule applies to **tests** (which stay on
  Testcontainers-Postgres); the H2 default is **runtime-only**.
- **README.md:** document how to start on H2 (default, zero-setup) vs Postgres (profile), with exact
  commands, and note Docker/Node are still required for `./gradlew build` (tests).

## Risks
- **Migration incompatibility (primary).** V1–V5 use Postgres constructs — CHECK constraints
  (`chk_movies_rating`, `chk_credits_type`, the compound `chk_credits_cast_fields`), `uuid`/`numeric`
  types, `COMMENT ON SCHEMA`, btree indexes. If any statement does not run under H2 (even in
  PostgreSQL mode), the default boot fails. Some runtime queries also use `LIKE … ESCAPE` and
  UUID/ordering semantics that differ subtly between engines — but only Postgres is *tested*, so H2
  runtime divergence could surface as demo-only defects that tests never catch. (This is the crux
  Open question.)
- **H2 leaking into tests.** If the H2 runtime default is not firmly overridden, a test could silently
  fall back to H2, eroding the fidelity D2 protects. Mitigated by the required safeguard.
- **Demo seed becoming default-on.** Turning the seed on by default changes app startup behaviour;
  must remain idempotent (it already derives deterministic ids) and must not run where undesired
  (e.g. a real Postgres/production profile).
- **Divergent behaviour, one tested engine.** Because tests only exercise Postgres, H2-specific
  runtime behaviour (pagination, ordering, escaping) is effectively unverified; scope keeps H2 as a
  demo convenience, not a correctness surface.

## Open questions (need a human/architect decision at Gate 1)
- **Migration-compatibility strategy (the crux).** How do V1–V5 (and future migrations) run on **both**
  Postgres and H2 without changing V1–V5's Postgres semantics? Options:
  - **(a) Single shared migration set** in SQL that runs on both engines (H2 in `MODE=PostgreSQL`).
    Simplest; but constrains future SQL to the shared subset and requires verifying every existing
    V1–V5 statement runs on H2 (and adjusting any incompatible one compatibly).
  - **(b) Vendor-specific Flyway locations** (`db/migration/common` + `…/postgresql` + `…/h2`, or
    Flyway's `{vendor}` placeholder). Clean separation; more maintenance and duplication.
  - **Recommendation:** prefer **(a)** if V1–V5 already run (or trivially can) on H2 PostgreSQL-mode,
    falling back to **(b)** only for any migration that genuinely cannot be shared. **Architect's call.**
- **Profile naming & test datasource selection.** Default = H2; the Postgres profile name (e.g.
  `postgres`). Precisely how do tests select Postgres so they never touch H2 — confirm the existing
  Testcontainers base + `@DynamicPropertySource` override is the mechanism, and what the explicit
  guard/assertion is.
- **Demo seed default-on.** Confirm the demo seed becomes default-on under H2 (recommended, for
  demonstrability) — via removing/relaxing `@Profile("demo")` or an equivalent default-on wiring — and
  whether it should remain loadable under Postgres/other profiles or stay opt-in there.
- **H2-in-PostgreSQL-mode connection settings.** Which settings are needed —
  e.g. `MODE=PostgreSQL`, `DB_CLOSE_DELAY=-1` (keep the in-mem DB alive for the JVM lifetime),
  case handling / `DATABASE_TO_LOWER`, and JPA/Hibernate dialect selection per profile.

## Domain gaps
- None. This is a platform/runtime-config enabler; it introduces no new business language, persona, or
  durable domain rule. "H2 default runtime vs Postgres tested/production target" is a technical
  convention and belongs in CLAUDE.md/standards, not `domain/`.
