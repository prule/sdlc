# Clean Code Standard

How individual classes and methods are written. Complements
[clean-architecture.md](clean-architecture.md) (which governs layering) — this governs the code inside
each layer.

## 1. Single Responsibility

- One class, one reason to change. If you describe a class with "and", split it.
- Keep classes **small and focused**. Rough guardrails (not hard limits — flag, don't fail, on breach):
  class ≲ 200 lines, method ≲ 30 lines, ≤ ~5 public methods per class.
- A method does one thing at one level of abstraction. If a method mixes high-level orchestration with
  low-level detail, extract the detail into a well-named private method or a collaborator.
- Prefer many small collaborating classes over one large "manager/util/helper" grab-bag. `*Util`,
  `*Helper`, `*Manager` names are a smell — name the responsibility instead.

## 2. Naming

- Names reveal intent: `expiredTokens`, not `list2`. Booleans read as predicates (`isActive`, `hasAccess`).
- Method names are verbs (`registerUser`), classes are nouns (`UserRegistration`), no abbreviations.
- Consistent vocabulary across the codebase — one term per concept (don't mix `fetch`/`get`/`load` for
  the same idea).

## 3. Functions & parameters

- Few parameters (≤ 3). Group related params into a record/command object.
- No boolean flag parameters that switch behavior — split into two methods.
- No output parameters or side effects hidden behind a query-looking name (command/query separation).
- Return early to avoid deep nesting; keep the happy path un-indented.

## 4. Immutability & nulls

- Prefer immutability: `record`s, `final` fields, unmodifiable collections. Construct valid or not at all.
- Avoid returning `null` — return `Optional<T>` or an empty collection. Don't accept `null` params; validate.
- Use sealed interfaces + pattern matching for closed result/state hierarchies instead of null/instanceof chains.

## 5. Modern Java (21+/25)

- Use `record`s for DTOs/value objects, `switch` pattern matching + `sealed` types for exhaustive handling,
  text blocks for multi-line strings, and virtual threads for blocking I/O where it simplifies concurrency.
- Streams for transformation, plain loops when clearer — don't force a one-liner that hides intent.

## 6. Comments & formatting

- Code explains *how*; comments explain *why* (non-obvious decisions, trade-offs, links to tickets).
- No commented-out code, no redundant Javadoc restating the signature. Delete dead code — git remembers.
- Auto-format on commit (Spotless). Formatting is not a review topic; the tool decides.

## 7. Dependencies & structure

- Depend on abstractions (ports/interfaces), not concretes — constructor injection, no field `@Autowired`,
  no `new`-ing collaborators inside business logic.
- No static mutable state; no service locators. A class's collaborators are visible in its constructor.
- DRY within reason: extract genuine duplication, but don't over-abstract two lines that merely look alike.

## 8. Tests are code too

- Same standards apply to tests. One behavior per test; descriptive names (`rejects_expired_token`).
- Arrange–Act–Assert structure; assert on behavior/outcomes, not implementation details.
- No logic in tests (loops/conditionals that could themselves be buggy). Prefer parameterized tests.

## 9. Review smells to reject

- God class / god method; a class that reaches into another's internals (feature envy).
- Long parameter lists, boolean flag args, primitive obsession (passing raw `String`/`long` where a value
  object belongs, e.g. an `Email` or `UserId`).
- Returning `null`, swallowed exceptions, magic numbers/strings (extract named constants).
- Business logic in getters, `equals`/`hashCode` with side effects, static mutable singletons.
