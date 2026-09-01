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
| Missing/invalid credentials | (Spring Security) | 401 | `UNAUTHENTICATED` |
| Authenticated but not allowed | `AccessDeniedException` | 403 | `FORBIDDEN` |
| Resource does not exist | `ResourceNotFoundException` | 404 | `NOT_FOUND` |
| State/uniqueness conflict | `*AlreadyInUseException`, optimistic lock | 409 | domain code |
| Field validation failed | `MethodArgumentNotValidException`, `ValidationException` | 422 | `VALIDATION_FAILED` |
| Anything unexpected | catch-all | 500 | `INTERNAL_ERROR` |

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
