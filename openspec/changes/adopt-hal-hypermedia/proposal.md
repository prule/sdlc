## Why

Our public, read-only movie API (`domain/overview.md`) will grow a set of interlinked read
capabilities (movie detail, search, people/credits, genres). For a third-party API consumer to
**discover** related resources and **navigate pages** without hard-coding URL templates, responses
must carry hypermedia links. We have decided to adopt HATEOAS using Spring HATEOAS in the **HAL**
representation. Establishing and proving the convention now — while the surface is only the walking
skeleton — lets future `catalog` tickets build on one already-proven link convention instead of each
reinventing it.

This is a foundational **representation** change, not a business feature. The crux to settle at
Gate 1 is how HAL `_links`/`_embedded` coexist with our two existing representation standards: the
success Envelope (`{data, meta}`) and RFC 7807 Problem errors, under the contract-first pipeline
where OpenAPI is the source of truth.

## What Changes

- Introduce **Spring HATEOAS (HAL)** as the hypermedia mechanism, used for **navigational links only**
  (`self`, pagination `next`/`prev`/`first`/`last`, related resources) — no action/write affordances,
  no HAL-FORMS (the API is read-only).
- **Recommended topology (Gate-1 decision):** option **(a)** — `data` is a HAL resource carrying its
  own `_links` (and `_embedded` for collections) **inside** the existing `{data, meta}` envelope. The
  envelope root and `meta` (correlationId, timestamp, pagination counts) are unchanged. This is **NOT
  a breaking change**: single-resource `data` gains an optional `_links`; collection `data` becomes an
  object with `_embedded.<rel>` + `_links` instead of a bare array.
- **Recommended success media type (Gate-1 decision):** keep `application/json` (HAL fields embedded in
  `data`), not `application/hal+json`, because with topology (a) the document root is our envelope, not
  a pure HAL resource. Error responses stay `application/problem+json` and are never HAL.
- **Describe the link shape in the OpenAPI contract** as shared named components (`Link`, `_links`
  objects per relation) so the spec stays authoritative even though Spring HATEOAS builds concrete URLs
  at runtime. Spring HATEOAS is used only to **construct** hrefs in the web adapter; responses serialize
  via the generated contract DTOs.
- **Prove the mechanism**: add a `self` link to the existing `/api/v1/ping` response (single-resource
  proof) and add a minimal, clearly-labelled **trivial sample collection** resource to exercise
  pagination links (`self`/`next`/`prev`/`first`/`last` + `_embedded` items with `self`). The sample is
  demonstrative, not a real catalog capability.
- **Update `standards/openapi.md`** to document how `_links`/`_embedded` fit alongside the Envelope and
  Problem shapes, so future tickets follow one rule.
- **Update `domain/business-rules.md`** ("API behaviour") to note that resource/collection responses
  carry navigational links (read-only, no action affordances). Propose glossary entries for
  Hypermedia / HAL / Link relation — flagged for the human to decide whether these rise to ubiquitous
  language vs remaining a technical standard.
- **NOT BREAKING**: envelope root unchanged, existing `application/json` content negotiation preserved,
  no DB schema change and **no Flyway migration** (read-only API).

## Capabilities

### New Capabilities
- `platform/hypermedia-links`: the HAL navigational-link representation convention — where `_links`
  live relative to the envelope, the link relations (`self`, `next`, `prev`, `first`, `last`, related),
  pagination-boundary behaviour, the contract-described link shape, the success/error media types, and
  the clean-architecture placement of link assembly (web adapter only).

### Modified Capabilities
- `platform/health-check`: the `ping` single-resource response gains a `self` link in `data._links`.
- `platform/api-codegen`: the reuse/no-duplicate guarantee is extended to cover the shared HAL
  components (`Link`, `_links`) and a second (sample collection) envelope-returning operation, so
  introducing HAL media types/schemas does not resurrect per-operation `<Operation><Status>Response*`
  duplicates.

## Impact

- **OpenAPI spec** (`src/main/resources/openapi/`): new shared `Link`/`_links` schemas in
  `components/schemas/common.yaml`; new shared `Page`/`Size` query parameters in
  `components/parameters/common.yaml`; `PingData` gains `_links`; new sample path + schemas with `200`,
  `400`, and `500` responses; new `application/json` HAL-bearing responses. No new media type.
- **Build/codegen**: `redocly bundle` → `openapi-generator` pipeline unchanged; `GeneratedApiCodegenTest`
  stays green and is extended for the sample operation and shared HAL components.
- **Dependencies**: add `spring-boot-starter-hateoas` (used in the web adapter only for
  `WebMvcLinkBuilder`).
- **Code** (web adapter only): `PingController` and a new `@Validated` sample controller populate
  generated `_links` DTO fields via `WebMvcLinkBuilder`; `GlobalExceptionHandler` gains a
  `ConstraintViolationException` → `400` mapping so invalid `page`/`size` return `400`, not `500`.
  Domain and application layers unchanged — they must not import Spring HATEOAS.
- **Docs**: `standards/openapi.md` and `domain/business-rules.md` (+ possibly `domain/glossary.md`).
- **Tests**: new web-layer tests (self link present; pagination-boundary edge cases; failure path still
  Problem+json, not HAL). All 34+ existing skeleton tests stay green.
