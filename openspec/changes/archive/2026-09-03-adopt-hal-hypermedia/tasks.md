## 1. OpenAPI contract (source of truth first)

- [x] 1.1 Add shared `Link` schema (`href` required URI; optional `templated`, `title`) to `components/schemas/common.yaml`.
- [x] 1.2 Add per-resource `_links` object schemas (`PingLinks` with `self`; `SampleCollectionLinks` with `self`/`first`/`last`/`prev`/`next`; `SampleItemLinks` with `self`) referencing `Link` via `$ref`.
- [x] 1.3 Modify `PingData` (`components/schemas/health.yaml`) to add `_links: { $ref: PingLinks }`; keep `200` media type `application/json`.
- [x] 1.4 Add sample collection schemas (`SampleItem` with `_links`; `SampleCollectionData` with `_embedded.samples[]` + `_links`; `SampleCollectionEnvelope`) referencing shared `Meta`/`Pagination`.
- [x] 1.5 Define shared `Page` and `Size` query parameters in `components/parameters/common.yaml` (per `standards/openapi.md` §4): `page` integer `minimum: 0` default `0`; `size` integer `minimum: 1`, a documented `maximum` (e.g. 100) and a default (e.g. 20). (Currently that file defines only `CorrelationId`; must exist before 1.6 references it.)
- [x] 1.6 Add `paths/samples.yaml` with `GET /api/v1/samples` (public `security: []`, `page`/`size` params `$ref`ed from `parameters/common.yaml`, correlation-id header, `200` envelope, shared `400 BadRequest` and `500 InternalError` from `responses/common.yaml`), and `$ref` it from root `openapi.yaml`.
- [x] 1.7 Bundle + lint the spec (`redocly bundle` / spec-lint) and confirm it resolves with no errors.

## 2. Generate stubs & guard codegen

- [x] 2.1 Add `spring-boot-starter-hateoas` dependency (used by the web adapter only).
- [x] 2.2 Run `./gradlew openApiGenerate`; confirm generated `HealthApi.ping` still returns `PingEnvelope` and a new interface method for `GET /api/v1/samples` returns the shared sample envelope type.
- [x] 2.3 Extend `GeneratedApiCodegenTest`: assert exactly one shared `Link` model, one `Problem`, no `<Operation><Status>Response*` duplicates (incl. the sample op), and the sample op returns its shared envelope type.

## 3. Domain (no HAL, no framework)

- [x] 3.1 Add a minimal in-memory sample domain model/value object for the demonstrative collection (id + label). No Spring/HATEOAS imports.

## 4. Application / ports

- [x] 4.1 Add a use-case/port returning a page of sample domain items plus page metadata (page/size/totalElements/totalPages) — no link/HAL concept in the port contract.

## 5. Outbound adapter / persistence

- [x] 5.1 Provide a static/in-memory source for the sample items (no persistence). Confirm NO Flyway migration is added (read-only; no schema change).

## 6. Inbound web adapter (link assembly here only)

- [x] 6.1 Update `PingController` to populate `data._links.self` via `WebMvcLinkBuilder.linkTo(methodOn(HealthApi.class).ping(...))` into the generated `_links` DTO field.
- [x] 6.2 Add the sample collection controller: map domain page → generated envelope; build `_embedded.samples` (each item with `self`) and `data._links` (`self`, `first`, `last`, plus `prev`/`next` only when applicable) via `WebMvcLinkBuilder`; keep `meta.pagination` counts. A valid page beyond the last page returns an empty `200` (not 400).
- [x] 6.3 Close the query-param validation gap so invalid `page`/`size` return `400`, not `500`: map `jakarta.validation.ConstraintViolationException` → `400` `Problem` in `GlobalExceptionHandler` (today only `MethodArgumentNotValidException`/body validation is handled), and add `@Validated` on the sample controller (class level) so the generated `@Min`/`@Max` param constraints are enforced. Verify no bad input reaches pagination arithmetic.

## 7. Tests (per layer: happy / edge / failure)

- [x] 7.1 Web test: `GET /api/v1/ping` returns `application/json`, `data.status == ok`, `data._links.self.href` ends in `/api/v1/ping`, `meta.correlationId` a UUID, and the `X-Correlation-Id` response header is present.
- [x] 7.2 Web test (collection happy): middle page has `_embedded.samples[]` items each with `self`, `_links` = `self`/`first`/`last`/`prev`/`next` with correct `page` params, and the `X-Correlation-Id` response header is present.
- [x] 7.3 Web test (edge boundaries): first page omits `prev`; last page omits `next`; empty result is `200` with no `next`/`prev`; a valid page beyond the last page is `200` with an empty `_embedded.samples` (not 400).
- [x] 7.4 Web test (invalid-param failure): `GET /api/v1/samples?size=0` and `?page=-1` return HTTP `400` (assert the status is exactly 400, NOT merely problem+json — a 500 would also be problem+json), `Content-Type: application/problem+json`, a stable `code`, the `correlationId`, and NO `_links`/`_embedded`. Include a case for `size` above the documented maximum.
- [x] 7.5 Conformance test: parse the bundled OpenAPI spec (`build/openapi/openapi.bundled.yaml`) to extract each operation's documented `_links` property set, hit the runtime endpoint (ping and the sample collection), and assert every key in the runtime `data._links` is declared in that operation's documented `_links` schema (fails on any undocumented relation).
- [x] 7.6 Architecture test: no `org.springframework.hateoas` import under any `domain`/`application` package.

## 8. Docs & domain

- [x] 8.1 Update `standards/openapi.md`: (a) AMEND the §2 sentence "collection endpoints put an array in `data`" to reflect the new convention — collection `data` is a HAL object with `_embedded.<rel>` array + `_links` (one rule, not a contradicting second one); and (b) add a section on how HAL `_links`/`_embedded` sit inside `data` alongside the Envelope and Problem shapes, the `self`/pagination relations, boundary rules (page-beyond-last = empty 200; invalid page/size = 400), and the `application/json` (success) vs `application/problem+json` (error) media types.
- [x] 8.2 Update `domain/business-rules.md` "API behaviour": note resource/collection responses carry navigational links (read-only, no action affordances).
- [x] 8.3 (Pending Gate-1 human call) Add glossary entries for Hypermedia / HAL / Link relation to `domain/glossary.md` only if the human decides these rise to ubiquitous language; otherwise leave them as a technical standard.

## 9. Verify green

- [x] 9.1 Run clean `./gradlew build`; confirm all previously passing tests (34+) plus new tests pass and `GeneratedApiCodegenTest` is green.
