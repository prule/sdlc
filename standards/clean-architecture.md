# Clean Architecture Standard (Java 25 / Spring Boot)

This is the authoritative guide for how code is layered in this repository. It applies to
every feature and is enforced by the architect (design), junior-dev (implementation), and
senior-dev/qa (review). When in doubt, the dependency rule wins.

## 1. The dependency rule

Dependencies point **inward only**:

```
        adapters (in / out)
              │  depends on
              ▼
        application (use cases + ports)
              │  depends on
              ▼
           domain
```

- **domain** depends on nothing (no Spring, no JPA, no other layer).
- **application** depends only on **domain**.
- **adapters** depend on **application** (and transitively domain).
- Nothing inner ever imports anything outer. The domain must never mention a controller,
  a JPA entity, or a Spring type.

Inversion happens through **ports**: the application defines an interface (port); an outbound
adapter implements it. So the application "calls the database" only through an interface it owns.

## 2. Package layout

One package tree per feature (bounded context):

```
com.acme.<feature>
├── domain/
│   ├── model/          # entities, value objects, aggregates (records where possible)
│   ├── event/          # domain events
│   └── service/        # domain services (logic spanning multiple entities)
├── application/
│   ├── port/
│   │   ├── in/         # inbound ports = use-case interfaces (e.g. RegisterUserUseCase)
│   │   └── out/        # outbound ports (e.g. LoadUserPort, SaveUserPort, SendEmailPort)
│   └── service/        # use-case implementations of the inbound ports
└── adapters/
    ├── in/
    │   └── web/        # REST controllers implementing GENERATED OpenAPI interfaces
    └── out/
        ├── persistence/  # JPA entities, Spring Data repos, port implementations
        └── client/       # HTTP/gRPC clients to other systems, port implementations
```

## 3. What each layer may contain / import

| Layer | May contain | Allowed imports | Forbidden |
|-------|-------------|-----------------|-----------|
| domain | entities, value objects, domain services, domain events, domain exceptions | JDK, domain itself | Spring, JPA/`jakarta.persistence`, Jackson, any adapter, generated DTOs |
| application | use-case impls, inbound/outbound ports, application services, transaction boundaries | domain, JDK, `@Transactional`, `@Service` (see note) | JPA entities, controllers, generated web DTOs, concrete adapter classes |
| adapters/in/web | controllers, request/response mapping | application ports (in), generated OpenAPI interfaces + DTOs, Spring Web | domain business logic, JPA repositories directly |
| adapters/out | JPA entities, Spring Data repositories, external clients, port impls | application ports (out), domain (for mapping), Spring Data/JPA | inbound ports, controllers |

**Spring in the application layer:** keep it minimal. `@Service`/`@Transactional` are tolerated on
use-case implementations for wiring and transaction demarcation. Ports and domain stay annotation-free.
If you want a Spring-free application layer, wire beans in a `config` package with `@Configuration` +
`@Bean` factories instead.

## 4. Domain modeling rules

- Prefer **records** for value objects and immutable entities; use **sealed interfaces** for closed
  hierarchies (e.g. domain result/error types) and pattern matching to handle them.
- Put business invariants **in the domain**, enforced in constructors/factory methods — not in
  controllers or transaction-script services.
- Domain objects are **not** JPA entities. Do not annotate them with `@Entity`, `@Table`, `@Id`, etc.
- Domain raises domain-specific exceptions (e.g. `EmailAlreadyInUseException`), never `ResponseStatusException`.

## 5. Persistence rules (adapters/out/persistence)

- JPA `@Entity` classes are **separate** from domain models and live only here.
- A repository adapter implements an outbound port, uses a Spring Data repository internally, and
  **maps** between JPA entity ↔ domain model (a hand-written mapper or MapStruct).
- Schema changes ship as **Flyway** migrations (`src/main/resources/db/migration/V<n>__desc.sql`).
  Never edit an applied migration; add a new one. Integration tests run against real Postgres via
  Testcontainers.

## 6. Web rules (adapters/in/web)

- The HTTP contract is **OpenAPI 3.1**, defined before code, in `src/main/resources/openapi/`.
- `./gradlew openApiGenerate` produces the API interface + DTOs. Controllers **implement** the
  generated interface; they never hand-roll DTOs that duplicate the contract.
- Controllers are thin: validate/deserialize (Bean Validation), map DTO → command, call the inbound
  port, map result → DTO. No business logic.
- Errors surface through a global `@RestControllerAdvice` returning RFC 7807 problem details;
  it translates domain exceptions to HTTP status codes.

## 7. A request's path through the layers

```
HTTP POST /users
  → UserController (adapters/in/web)  implements generated UsersApi
      maps RegisterUserRequest (generated DTO) → RegisterUserCommand
  → RegisterUserUseCase (application/port/in)     ← the port the controller depends on
  → RegisterUserService (application/service)     ← the implementation
      uses domain: new User(...) enforcing invariants
      calls LoadUserPort / SaveUserPort (application/port/out)
  → UserPersistenceAdapter (adapters/out/persistence) implements those ports
      maps domain User ↔ UserJpaEntity, calls Spring Data repo
  ← returns domain User → Service → Controller maps → RegisterUserResponse (generated DTO)
```

## 8. Testing per layer

- **domain**: plain, fast unit tests (JUnit 5 + AssertJ). No Spring context.
- **application**: unit tests with the outbound ports mocked; assert orchestration + domain rules.
- **adapters/in/web**: `@WebMvcTest` / MockMvc against the generated API, use-case port mocked;
  assert request validation, status codes, DTO mapping, and problem-detail responses.
- **adapters/out/persistence**: `@DataJpaTest` or full slice with **Testcontainers** Postgres; assert
  mapping and queries against a real database — no H2.
- **contract**: validate the OpenAPI spec in CI; optionally verify controllers against it.
- Each requirement in a spec delta gets a test covering happy path, an edge case, and a failure path.

## 9. Common violations to reject in review

- `@Entity` on a domain class, or a domain class importing `jakarta.persistence` / Spring.
- A controller calling a Spring Data repository or containing business rules.
- Application layer importing a JPA entity or a concrete adapter class.
- Hand-written request/response DTOs that duplicate the OpenAPI contract.
- Editing an already-applied Flyway migration instead of adding a new one.
- Business logic in a "service" that's really a transaction script bypassing the domain model.
