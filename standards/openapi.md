# OpenAPI Standard (contract-first)

Every HTTP API is defined in OpenAPI 3.1 **before** any controller code, and server interfaces +
DTOs are generated from it (`./gradlew openApiGenerate`). The spec is the single source of truth.

## 1. File layout — split by domain

Do **not** put everything in one giant `openapi.yaml`. One root file references per-domain files:

```
src/main/resources/openapi/
├── openapi.yaml                 # root: info, servers, security, $ref to each domain's paths
├── paths/
│   ├── users.yaml               # all /users* operations
│   └── orders.yaml              # all /orders* operations
└── components/
    ├── schemas/
    │   ├── common.yaml          # Envelope, ErrorResponse, Problem, Page, etc.
    │   ├── user.yaml
    │   └── order.yaml
    ├── responses/common.yaml    # reusable 400/401/403/404/409/422/500 responses
    └── parameters/common.yaml   # pagination, correlation-id, etc.
```

- Root `openapi.yaml` holds only `info`, `servers`, global `security`, `tags`, and `$ref`s into
  `paths/`. Add a new domain = add a file + one `$ref`.
- Reuse via `$ref` — never copy a schema or response between files.

## 2. Standard success envelope

All 2xx JSON responses wrap the payload in a consistent envelope so clients parse uniformly.

```yaml
Envelope:
  type: object
  required: [data, meta]
  properties:
    data:                         # the resource or array; schema per endpoint
      description: The response payload.
    meta:
      $ref: '#/components/schemas/Meta'
Meta:
  type: object
  required: [timestamp, correlationId]
  properties:
    timestamp: { type: string, format: date-time }
    correlationId: { type: string, format: uuid }
    pagination:                   # present only on collection endpoints
      $ref: '#/components/schemas/Pagination'
Pagination:
  type: object
  required: [page, size, totalElements, totalPages]
  properties:
    page: { type: integer, minimum: 0 }
    size: { type: integer, minimum: 1 }
    totalElements: { type: integer, format: int64 }
    totalPages: { type: integer }
```

Endpoint payload schemas are `$ref`ed into the envelope's `data`. Single-resource endpoints put the
object in `data`; collection endpoints put an array in `data` and fill `meta.pagination`.

## 3. Standard error responses (RFC 7807)

All non-2xx responses use `application/problem+json` and this shape (extends RFC 7807):

```yaml
Problem:
  type: object
  required: [type, title, status, code, correlationId]
  properties:
    type:    { type: string, format: uri }      # stable URI identifying the problem class
    title:   { type: string }                     # short human-readable summary
    status:  { type: integer }                    # HTTP status
    detail:  { type: string }                     # human-readable, specific to this occurrence
    instance:{ type: string, format: uri }        # URI of the request
    code:    { type: string }                     # stable machine code, e.g. USER_EMAIL_TAKEN
    correlationId: { type: string, format: uuid }
    errors:                                        # field-level validation errors (422)
      type: array
      items:
        type: object
        required: [field, message]
        properties:
          field:   { type: string }
          message: { type: string }
          code:    { type: string }
```

Define reusable responses in `components/responses/common.yaml` and `$ref` them everywhere:
`BadRequest` (400), `Unauthorized` (401), `Forbidden` (403), `NotFound` (404), `Conflict` (409),
`UnprocessableEntity` (422, includes `errors[]`), `InternalError` (500). Never return a bare string
or a stack trace. See [error-handling.md](error-handling.md) for the code↔status mapping.

## 4. Conventions

- Resource paths are plural nouns, kebab-case (`/user-profiles`); actions are HTTP verbs, not path segments.
- Every operation has a unique `operationId` (drives generated method names), a `summary`, and `tags`.
- Every request body and every response references a named schema in `components/schemas/*` — no inline object schemas.
- Use `format` (`uuid`, `date-time`, `email`, `int64`) and validation keywords (`minLength`, `pattern`, `enum`).
- IDs in URLs are opaque strings (UUID); do not expose DB sequence integers.
- Declare `security` globally (bearer JWT — see [security.md](security.md)); mark public endpoints with `security: []`.
- Version the API via a base path (`/api/v1`). Breaking changes → new major version, called out in the proposal.
- Every collection endpoint supports `page` and `size` query params from `components/parameters/common.yaml`.

## 5. Generation & validation

- Controllers implement the generated API interface; never hand-write DTOs that duplicate the contract.
- Lint/validate the spec in CI (`openapi` validation or Spectral); a broken or drifted spec fails the build.

### Pipeline: authored split spec → bundle → generate

`openapi-generator`'s spring generator inlines and renames cross-file `$ref`-composed response
schemas per operation (e.g. it would emit `Ping200Response`/`Ping200ResponseData`/
`Ping200ResponseMeta` instead of binding to the shared `PingEnvelope`/`PingData`/`Meta`
components) if it is fed the multi-file authored spec directly. To keep generated code bound to
the shared component schemas — one `Problem` model, one envelope type per payload, no
per-operation `<Operation><Status>Response*` duplicates — the build resolves the authored
multi-file spec into a single bundled file **before** generation:

1. **Author** the spec split by domain under `src/main/resources/openapi/` (§1) as normal.
2. **Bundle**: the `bundleOpenApiSpec` Gradle `Exec` task runs `redocly bundle` on the root spec
   and writes a single resolved file to `build/openapi/openapi.bundled.yaml`. `redocly bundle`
   lifts every cross-file `$ref` target into that one document's `components` and keeps
   operations referencing them by name, so the generator sees named component schemas instead of
   inlined compositions.
3. **Generate**: `openApiGenerate` consumes the bundled file (`inputSpec` points at it) and
   `dependsOn("bundleOpenApiSpec")`. `compileJava` already `dependsOn("openApiGenerate")`, so a
   plain `./gradlew build` runs bundle → generate → compile with no manual pre-step.

The bundled spec is a derived build artifact under `build/` — it is git-ignored and never
committed. The authored split spec remains the single source of truth; never hand-edit or commit
the bundled file.

A codegen assertion test (`GeneratedApiCodegenTest`) inspects the generated sources on every
build and fails if a per-operation `<Operation><Status>Response*` duplicate reappears or if
`HealthApi.ping()` stops returning the shared `PingEnvelope` type — this catches accidental
reversion to feeding the generator the multi-file spec directly.

### Bundler provisioning (pinned, deterministic)

`redocly bundle` (and the CI spec-lint step) run via `@redocly/cli`, pinned as a `devDependency`
in the repo-root `package.json` and installed reproducibly with `npm ci` (never
`npx @redocly/cli@latest`) — see the committed `package-lock.json`. This keeps bundling
deterministic (no silent CLI-version drift changing generated output) and lets CI cache
`node_modules`. Node/npx is therefore a hard dependency of the core `./gradlew build`, not just
CI; the dev container provisions it. Run `npm ci` once locally (or let the dev container do it)
before running `./gradlew build`.
