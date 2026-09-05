# Testing Standard

Every change ships with tests that prove it works and would catch it breaking. Tests are
first-class code — the [clean-code.md](clean-code.md) rules apply to them too.

## 1. Write useful tests for all new code

- **Every requirement in a spec delta gets at least one test** covering the happy path, a meaningful
  edge case, and a failure path. A requirement with no test is incomplete, not done.
- A test is **useful** only if it would **fail when the behavior regresses**. If it passes no matter
  what the code does, delete it.
- Test **behavior and outcomes**, not implementation details. Assert on what the caller observes
  (returned value, thrown exception, persisted row, HTTP response), never on private fields or on how
  many times an internal method was called (unless the interaction *is* the contract, e.g. "an email
  was sent").
- Prefer real collaborators where cheap; mock only at architectural seams (ports), external systems,
  and non-determinism (clock, random, network). Don't mock what you own and can exercise directly.
- Cover the cases that actually break software: boundaries (empty, null, zero, max, off-by-one),
  error/exception paths, concurrency where relevant, and security rules (authz, ownership).
- Coverage is a **diagnostic, not a target** — 100% of trivial getters proves nothing. Aim for every
  branch of real logic exercised; don't write tests solely to move a number.

## 2. The test pyramid (per Clean Architecture layer)

| Layer | Test type | Tooling | Speed |
|-------|-----------|---------|-------|
| domain | pure unit — no Spring, no I/O | JUnit 5 + AssertJ | instant |
| application (use cases) | unit with **ports mocked** | JUnit 5 + Mockito + AssertJ | instant |
| adapters/in/web | web slice — `@WebMvcTest`, use-case port mocked | MockMvc / WebTestClient | fast |
| adapters/out/persistence | integration against **real Postgres** | `@DataJpaTest`/`@SpringBootTest` + **Testcontainers** | slower |
| full flow (optional, sparingly) | end-to-end through all layers | `@SpringBootTest` + Testcontainers | slowest |

Most tests are unit tests at the domain/application layers (fast, run constantly). Integration tests
are fewer and reserved for the seams that unit tests can't honestly cover (real SQL, mapping, wiring).

## 3. Database tests use Testcontainers — never H2

- All tests that touch the database — every domain/persistence-logic test, every test asserting on
  query results, mapping, constraints, or migration behavior — run against a **real PostgreSQL** via
  Testcontainers. H2 (or any in-memory substitute) is banned for these tests: it hides dialect
  differences, real constraints, and migration bugs.
- **The one exception, and only this one:** the application ships an H2 in-memory database as its
  **default runtime datasource** (no profile active — zero-dependency local/demo boot; PostgreSQL is
  selected via the `postgres` profile). Exactly **one** test in the whole codebase,
  `H2DefaultRuntimeSmokeTest`, is permitted to boot the application on H2. It exists solely to prove the
  H2 **runtime configuration** works (Flyway applies, the demo seed populates, the documented endpoints
  respond) — an assertion that is meaningless against Postgres, because it is asserting on the H2 default
  wiring itself, not on persistence logic. It MUST NOT be used to assert on query correctness, mapping, or
  any behavior that a Testcontainers-Postgres test could instead cover — that work stays on Postgres. This
  is not general permission to add more H2 tests: a second H2-booting test is a standards violation unless
  this document is explicitly amended to add it.
- Provide **one reusable base class** that starts the container and points Spring at it; integration
  tests extend it so the container is shared, not restarted per class:

```java
@Testcontainers
@SpringBootTest
public abstract class PostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");   // pin the version

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

- **Flyway runs against the container** in integration tests, so every migration is exercised on the
  same engine as production. A broken migration fails the build.
- Reuse containers across the suite (a single shared static container, or Testcontainers reuse) to keep
  the suite fast; each test isolates its own data (transactional rollback or explicit cleanup), never
  relies on another test's leftovers.

## 4. Structure & style

- **Arrange–Act–Assert**, one behavior per test. No logic (loops/conditionals) in tests — use
  `@ParameterizedTest` for variations.
- Descriptive names stating the behavior: `redeem_rejects_expired_token`, not `test3`.
- Deterministic: inject `Clock`, seed randomness, no `Thread.sleep`, no dependence on wall-clock or test
  ordering. Fix flaky tests immediately — a flaky test is a failing test.
- Use AssertJ fluent assertions (`assertThat(x).isEqualTo(...)`); assert exceptions with
  `assertThatThrownBy(...)`.
- Keep fixtures close and readable (builders/object mothers over sprawling setup).

## 5. Definition of done (tests)

- Every new/changed requirement has happy-path + edge + failure tests.
- Every DB-touching path has a Testcontainers integration test; no H2 anywhere.
- `./gradlew build` (tests + lint) is green locally and in CI before review.
- QA treats a missing test for a requirement as a defect (see [../.claude/agents/qa.md](../.claude/agents/qa.md)).
