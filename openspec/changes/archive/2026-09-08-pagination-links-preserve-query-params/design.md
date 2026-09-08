## Context

See proposal.md — Why. Both paginated collection endpoints build their pagination links through a
private `pageLink(int page, int size)` helper that calls
`WebMvcLinkBuilder.linkTo(methodOn(<Api>.class).<handler>(...))` and passes `null` for every argument
other than `page`/`size`:

- `MovieController.pageLink` → `getMovies(null, page, size, null, null, null, null, null, null)`
  (drops `title`, `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`) — the live defect.
- `SampleController.pageLink` → `listSamples(null, page, size)` (no filter/sort params today).

`WebMvcLinkBuilder` derives the query string from the non-null arguments passed to the handler method:
a `null` argument yields no query parameter, a non-null one is rendered as `name=value` (and a
`List<T>` as repeated `name=v1&name=v2`). This is the mechanism the fix leans on for the *filters*. Link
assembly already lives entirely in `adapters/in/web`; the domain/application layers never see `_links`.

**Important asymmetry — `sort` is never null.** `sort`'s OpenAPI schema declares
`default: releaseYear,desc`, so openapi-generator emits
`@RequestParam(value = "sort", required = false, defaultValue = "releaseYear,desc") String sort`. Spring
resolves `sort` to the literal `"releaseYear,desc"` *before* `getMovies` runs, whether or not the client
sent it — the argument is NEVER `null`. The other five parameters (`title`, `genre`, `releaseYearFrom`,
`releaseYearTo`, `minRating`) have no schema default and DO stay `null` when omitted. Therefore raw
pass-through is correct for the filters but WRONG for `sort`: forwarding the raw `sort` argument stamps
`sort=releaseYear,desc` onto every link — including on a fully unfiltered/default-sort request —
violating this change's own "Unfiltered, default-sort request introduces no spurious params" scenario.
The fix must normalise a default `sort` back to absent (see Decision 1).

The controllers implement generated OpenAPI interfaces (`MoviesApi`, `SampleApi`); the `_links` DTO
shapes (`MovieCollectionLinks`, `SampleCollectionLinks`, `Link`) are contract-authored and unchanged by
this work.

## Goals / Non-Goals

**Goals:**
- Thread the request's active filter/sort arguments into pagination-link construction so every emitted
  link carries them, on every current paginated collection endpoint.
- Keep the fix a single, consistent, copyable pattern for current and future paginated endpoints.
- Preserve only parameters present on the request; introduce no defaults or empty params.

**Non-Goals (design-level):**
- No change to the `pageLink` relation set, boundary arithmetic, `meta.pagination`, media types, or the
  OpenAPI contract / generated DTOs.
- No new abstraction/framework for link building beyond passing the existing handler arguments through.
- No change to `MovieSort`/`MovieSearchCriteria` parsing or validation.

## Decisions

**Decision 1 — Forward the raw request arguments for the filters, but normalise `sort` to absent when it
equals the server default.** `pageLink` gains the filter/sort parameters as extra arguments; the
`collectionLinks(...)` helper is given these values (carried alongside the `MoviePage`, e.g. via a small
carrier or extra parameters) so each of `self`/`first`/`last`/`prev`/`next` is built with them.

- **Filters** (`title`, `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating`) — forward the raw
  nullable DTO-level arguments the handler already received, unchanged. They have no OpenAPI schema
  default, so they are `null` exactly when the client omitted them and `WebMvcLinkBuilder` emits no
  param for them. Raw pass-through is correct.
- **`sort`** — must NOT be forwarded raw. Because its schema declares `default: releaseYear,desc`, the
  bound `sort` argument is the literal `"releaseYear,desc"` even when the client omitted it (see
  Context). The caller MUST detect "sort at its default" and pass `null` to `pageLink` in that case, so
  a default sort stays absent from the links; a non-default sort is forwarded. Concretely: the handler
  already computes `MovieSort movieSort = MovieSort.parse(sort)`; derive
  `String sortForLink = movieSort.equals(MovieSort.defaultSort()) ? null : sort;` and pass `sortForLink`
  (not `sort`) into `collectionLinks`/`pageLink`. Comparing the *parsed* value to
  `MovieSort.defaultSort()` (rather than a string literal) normalises case/whitespace and treats an
  explicitly-sent default identically to an omitted one — which is correct, since the two produce the
  same result set and cannot be distinguished at this layer. A non-default `sort` forwards the original
  raw string, preserving its exact wire form.

*Why not re-serialize the parsed `MovieSort`/`MovieSearchCriteria` for non-default values:* forwarding
the raw string avoids re-encoding drift and keeps a non-default sort byte-identical to what the client
sent. The only transformation applied is the default→absent normalisation, which is mandatory because
the generated binding erases the "was it omitted?" signal for `sort`. *Alternative rejected:* rebuild
all links from the parsed domain objects — cleaner-looking but re-introduces defaults on every param and
risks encoding drift. *Alternative rejected (Option 2):* remove `default: releaseYear,desc` from the
OpenAPI schema so the `sort` binding stays `null` when omitted — this is an OpenAPI contract change
(breaking the "No OpenAPI contract change" impact statement) and risks altering behaviour elsewhere that
relies on the bound default; not worth it when the web-adapter normalisation is contained and sufficient.

**Decision 2 — Reuse `WebMvcLinkBuilder`'s native query-param rendering; do not hand-assemble query
strings.** The builder already renders non-null scalars and repeated list params and omits nulls, and it
already produces absolute URIs (as the current `self`/`pageLink` do). Passing the arguments through is
sufficient for multi-valued `genre` and the `sort` string. *Why:* least code, no bespoke URI encoding to
get wrong, consistent with the existing `selfLink`/`pageLink` construction, and keeps links absolute and
valid by the same mechanism already trusted. *Alternative rejected:* build query strings via
`UriComponentsBuilder` manually — more surface area for encoding bugs (the ticket's stated risk).

**Decision 3 — Apply the identical pass-through shape to `SampleController` now, as a guard.**
`listSamples` has no filter/sort params today, so behaviour is unchanged, but structuring its `pageLink`
the same way (params forwarded through, even if currently none) documents the convention at the second
call site and means a future filter/sort addition inherits correct links by default. *Why:* the ticket
explicitly asks the fix to be the shared pattern, not a one-endpoint patch.

**Decision 4 — Record the durable convention in standards/openapi.md §2a and the
`platform/hypermedia-links` spec.** §2a already states pagination link URLs must let a client walk the
*current* result set; add an explicit sentence that nav links preserve all active filter/sort query
params (present-only, no defaults echoed). The spec delta carries the normative requirement. *Why:* the
ticket names both venues; §2a is the human-facing convention doc, the spec is the testable contract.

**Decision 5 — Test at the web-slice layer for link shape, with a DB-backed round-trip for the
"same result set" criterion.** `@WebMvcTest` with the use-case port mocked proves each link carries the
filter/sort params, differs from `self` only by `page`, and that the unfiltered case stays clean, plus
the `400` failure path. A Testcontainers-Postgres round-trip test (following an emitted `first`/`prev`/
`next`/`last` link back through the real handler) proves `totalElements`/`totalPages` and ordering are
identical forward and backward — the acceptance criterion the web slice alone cannot honestly assert.
*Why:* honours testing.md's happy/edge/failure per requirement and the "no H2 for DB-backed" rule; keeps
the fast assertions fast and reserves the container for the seam that needs real data.

## Risks / Trade-offs

- **Blast radius: shared link assembly touched, could alter the currently-correct unfiltered case** →
  Decision 1 leaves the unfiltered/default path byte-for-byte unchanged: the filter args are `null` and
  `sort` is normalised to `null` when it equals the default, so no filter/sort params are emitted; a
  dedicated test asserts the unfiltered/default-sort links carry only `page`/`size`.
- **Encoding drift on multi-valued `genre`, `sort` direction, ranges** → Decision 2 delegates rendering
  to the same builder already used, and tests assert *semantic* target-query equality (via round-trip),
  not raw string equality, per the ticket's risk note.
- **Leaking a default sort** → the raw `sort` argument is NEVER null (its schema has
  `default: releaseYear,desc`), so it cannot be forwarded blindly. Guarded by Decision 1's
  default-detection: the caller compares the parsed sort to `MovieSort.defaultSort()` and passes `null`
  to the link builder when they match, so a default (or explicitly-default) sort stays absent while a
  non-default sort is forwarded. Covered by the unfiltered/default-sort test (4.2).
- **`pageLink` signature grows** (six extra args on `MovieController`) → acceptable for a thin adapter;
  a small carrier record (request filter/sort values) may be used to keep the helper readable — an
  implementation nicety, not a contract change.

## Migration Plan

No API, DB, or Flyway change — the OpenAPI contract and generated DTOs are untouched, so no
`openApiGenerate` regeneration is required and no migration is added. Deploy is a plain code change to
two web adapters plus a standards-doc edit. Rollback is a straight revert; no data or schema state is
affected. No client-visible breaking change (links gain query params clients were already sending).

## Open Questions

None. The resolved decisions in the ticket (self carries params, omitted params stay absent, convention
recorded in §2a/spec) settle the behaviour; the small carrier-vs-extra-args choice for `pageLink` is an
implementation detail left to apply.
