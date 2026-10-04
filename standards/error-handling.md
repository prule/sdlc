# Error Handling Standard

Errors are modeled as domain concepts, surfaced as RFC 7807 problem details, and never leak
internals. The wire shape is defined in [openapi.md](openapi.md) §3; this doc governs the Java side.

## 1. Exception taxonomy

Define a small, sealed hierarchy in the domain/application layers — not ad-hoc `RuntimeException`s.

```java
// domain — base for all business rule violations
public abstract class DomainException extends RuntimeException {
    private final String code;                 // stable machine code, e.g. "USER_EMAIL_TAKEN"
    protected DomainException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}

// specific, meaningful subtypes
public final class EmailAlreadyInUseException extends DomainException {
    public EmailAlreadyInUseException(String email) {
        super("USER_EMAIL_TAKEN", "Email already in use: " + email);
    }
}
public final class ResourceNotFoundException extends DomainException { /* code NOT_FOUND */ }
public final class ValidationException extends DomainException { /* code VALIDATION_FAILED + field errors */ }
```

- One exception type per distinct failure the caller might handle differently. No generic `BusinessException`.
- Carry a **stable `code`** (machine-readable, never changes) separate from the human `message`.
- Domain/application throw domain exceptions only — never `ResponseStatusException` or servlet types.

## 2. Translation to HTTP — one place

A single global `@RestControllerAdvice` maps exceptions → `Problem` responses. It is the **only**
place that knows HTTP status codes. Controllers never build error responses.

```java
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail onNotFound(ResourceNotFoundException e, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, e.code(), e.getMessage(), req);
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    ProblemDetail onConflict(EmailAlreadyInUseException e, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)   // Bean Validation
    ProblemDetail onValidation(MethodArgumentNotValidException e, HttpServletRequest req) { /* 422 + errors[] */ }

    @ExceptionHandler(Exception.class)                          // catch-all
    ProblemDetail onUnexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled error [correlationId={}]", correlationId(), e);   // log full trace server-side
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                       "An unexpected error occurred.", req);                    // generic message to client
    }
}
```

## 3. Code ↔ status mapping (canonical)

| Situation | Exception | HTTP | code |
|-----------|-----------|------|------|
| Malformed request / bad param | `IllegalArgumentException`, parse errors | 400 | `BAD_REQUEST` |
| Constrained handler-method parameter violates its bound (e.g. `@Min`/`@Max` on `page`/`size`) | `ConstraintViolationException` (from a handler method, via a `@Validated` proxy) | 400 | `BAD_REQUEST`, `detail` names the query parameter |
| Any other constraint violation — a `@Validated` service bean, a handler return value, a JPA/Hibernate Validator check on flush | `ConstraintViolationException` whose violations are **not all** on a web handler's method parameters | 500 | `INTERNAL_ERROR` (a server-side bug, logged at ERROR — never blamed on the client) |
| Query parameter cannot convert to its declared type (non-numeric, overflow) | `MethodArgumentTypeMismatchException` | 400 | `BAD_REQUEST`, `detail` names the query parameter |
| Resource does not exist | `ResourceNotFoundException` | 404 | `NOT_FOUND` |
| Method not allowed on an offered path | `HttpRequestMethodNotSupportedException` (framework-detected) | 405 | `METHOD_NOT_ALLOWED` |
| Unsatisfiable `Accept` | `HttpMediaTypeNotAcceptableException` (framework-detected) | 406 | `NOT_ACCEPTABLE` |
| State/uniqueness conflict | `*AlreadyInUseException`, optimistic lock | 409 | domain code |
| Field validation failed (request body) | `MethodArgumentNotValidException`, `ValidationException` | 422 | `VALIDATION_FAILED` |
| Unsupported request body media type | `HttpMediaTypeNotSupportedException` (framework-detected) | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| Anything unexpected | catch-all | 500 | `INTERNAL_ERROR` |

`ConstraintViolationException` is a `ValidationException`, but it is never mapped by the 422 row: it
is classified by the two `ConstraintViolationException` rows above. Decide ownership first — 400 only
when the set is non-empty and every violation's root bean is a web controller (proxy unwrapped) and
the node after the handler's METHOD node is a PARAMETER node of the handler that ran (deeper element
or property nodes are allowed: one bad value of a repeated parameter is a violation on that
parameter); anything else, including an empty or null set, is 500. When several parameters fail,
name the lowest parameter index. The named `detail` applies to **query parameters** only, taking the
name from `@RequestParam`, never the Java argument name; a violation only on path variables gets the
generic 400 detail.

Any other framework-detected 4xx not listed above (a malformed request, a missing or ill-typed
parameter, or any other client fault Spring resolves to a 4xx status) collapses to `400`
`BAD_REQUEST`, keeping the code set closed. No framework-detected client fault is ever reported as
`500` — see `platform`'s `GlobalExceptionHandler`/`ProblemFactory` (`openspec/specs/platform/uniform-responses`) for the full classification.

## 4. Rules

- **Never** return a stack trace, SQL, class name, or internal message to the client. 5xx bodies get a
  generic message + the `correlationId`; the real detail is logged server-side under that id.
- Every request carries a `correlationId` (generated in a filter if absent, echoed in `meta`/`Problem`
  and in every log line). This is how a client error report maps to server logs.
- Fail fast at the boundary: validate inputs with Bean Validation on request DTOs; validate invariants
  in domain constructors/factories.
- Do not use exceptions for normal control flow. For expected "not found on lookup" in the application
  layer, prefer `Optional`; throw only when the caller cannot reasonably continue.
- Log at the point you handle, not at every rethrow. Log 5xx at ERROR with the trace; 4xx at WARN/DEBUG
  (they're client mistakes, not incidents).
- Wrap-and-rethrow to preserve cause; never swallow (`catch (Exception ignored) {}`).
