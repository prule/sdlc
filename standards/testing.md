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
  error/exception paths, concurrency where relevant, and security rules (headers, public access).
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

**Tests run the production framework wiring.** A slice or integration test uses the same framework
beans production uses (validation, message conversion, exception handling, security filters). Do not
replace a framework bean with a hand-built test double "because the slice doesn't load it" — check
first: a claim about what a Spring/Boot slice does or doesn't auto-configure must cite the framework
source or docs for the version in use (e.g. Boot's `AutoConfigureWebMvc.imports`), in a comment next
to the override. An unexplained replacement of framework wiring in test config is a defect: the tests
would prove the double, not the application.

## 3. Database tests run on the project's target database

- The project chooses its database; no engine is banned. What the standard requires is that tests
  exercising persistence logic — query results, mapping, constraints, migration behavior — run on the
  **same engine the project targets**, started via Testcontainers when it needs a server. Substituting a
  different engine in tests (an in-memory stand-in for a server database) hides dialect differences, real
  constraints, and migration bugs, so it is a defect.
- **This project's choices:** PostgreSQL is the tested and production target (`postgres` profile), so
  persistence-logic tests run on Testcontainers Postgres. H2 in-memory is the default local/demo runtime
  (no profile), so tests may boot the application on H2 to prove that **runtime configuration** works
  (Flyway applies, the demo seed populates, the documented endpoints respond) — `H2DefaultRuntimeSmokeTest`
  does this.
- A runtime-configuration test asserts on the wiring, not on persistence logic, because Postgres is
  where that logic is verified. Per documented endpoint it may assert the status, the content type, the
  envelope shape, and that a known seeded record is present (or reachable by id). Result **order**,
  filter or match semantics, exact result sets, paging arithmetic and field mapping are asserted on
  Postgres — even when a spec delta's runtime-mode scenario asks for them on H2 (such a scenario is a
  plan defect).
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
  `@ParameterizedTest` for variations. Two narrow exceptions:
  - **Arrange**: a loop that only builds fixtures (e.g. insert 25 movies) is fine — prefer a
    builder/object-mother method that hides it.
  - **Iteration is the behavior**: when the requirement is about traversal itself (follow `next`
    until it is absent, walk every page and see each item exactly once), a loop in the Act phase is
    allowed. It MUST have a hard upper bound that fails the test when exceeded (no `while (true)`),
    put no assertions or conditionals inside the loop body, and assert on the collected result after
    the loop. Never loop over cases to assert each one — that is a `@ParameterizedTest`.
- Descriptive names stating the behavior: `redeem_rejects_expired_token`, not `test3`.
- Deterministic: inject `Clock`, seed randomness, no `Thread.sleep`, no dependence on wall-clock or test
  ordering. Fix flaky tests immediately — a flaky test is a failing test.
- Use AssertJ fluent assertions (`assertThat(x).isEqualTo(...)`); assert exceptions with
  `assertThatThrownBy(...)`.
- Keep fixtures close and readable (builders/object mothers over sprawling setup).
- **Send query strings exactly.** A MockMvc request whose query is already percent-encoded uses
  `get(URI.create(...))` or `.param(...)`, never a string URL template: the template is encoded again,
  so `%20` silently becomes `%2520` and the test exercises the wrong input.
- **Proving a value is bound** (`standards/clean-architecture.md` §5): capture the SQL (a test-scoped
  Hibernate `StatementInspector`) and assert the statement carries a bind placeholder and contains
  neither the raw value nor its SQL-escaped form (`n's e` / `n''s e`), and that SQL was captured at
  all. A behaviour check with a quote is not enough — Hibernate escapes quotes when it inlines a
  literal, so the search still works on the buggy code.

## 5. Definition of done (tests)

- Every new/changed requirement has happy-path + edge + failure tests.
- Every DB-touching path has an integration test on the project's target database (Testcontainers).
- `./gradlew build` (tests + lint) is green locally and in CI before review.
- QA treats a missing test for a requirement as a defect (see [../.claude/agents/qa.md](../.claude/agents/qa.md)).
