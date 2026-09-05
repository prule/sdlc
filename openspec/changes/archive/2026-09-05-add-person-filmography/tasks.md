## 1. OpenAPI contract (contract-first, before any code)

- [x] 1.1 Add `paths/person-filmography.yaml` with operation `getPersonFilmography`
      (`GET /api/v1/people/{id}/credits`, `security: []`, `tags: [people]`), reusing the shared
      `CorrelationId`/`Page`/`Size` parameters and the shared `400`/`404`/`500` responses; wire the path
      into `openapi.yaml`.
- [x] 1.2 Add the item + capacity schemas to `components/schemas/person.yaml`: `FilmographyItem`
      (`allOf` the existing `MovieSummary` + a required `capacity`), `FilmographyCapacity` (`oneOf`
      `ActingCapacity`/`NonActingCapacity` with a `type` discriminator + mapping), `ActingCapacity`
      (`type` + `character` + `billingOrder`), `NonActingCapacity` (`type` + `department` + `job`); each
      `required`s its own field set.
- [x] 1.3 Add `PersonFilmographyData` (`_embedded.filmography` array + `_links`) and
      `PersonFilmographyEnvelope` (`{data, meta}`) schemas; add `PersonFilmographyLinks`
      (`self`/`first`/`last`/`prev`/`next`) to `components/schemas/common.yaml` mirroring
      `MovieCollectionLinks`. Use named `$ref` schemas only — no per-operation
      `<Operation><Status>Response*` inline duplicates.
- [x] 1.4 (D12) Add a `credits` relation to `PersonLinks` in `components/schemas/common.yaml`, mirroring
      `MovieLinks` (`self` + `credits`).
- [x] 1.5 Lint/bundle the split spec and confirm it resolves (`$ref`s valid, no dangling refs).

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate`; confirm the `getPersonFilmography` interface, the
      `FilmographyItem`, the two capacity types (discriminator resolves), and the envelope DTOs generate
      and compile.
- [x] 2.2 Run `GeneratedApiCodegenTest`; confirm it stays green (no per-operation response-type
      duplicates introduced).

## 3. Domain

- [x] 3.1 Add a `Filmography`/`FilmographyEntry` value model under `com.acme.catalog.people.domain`: a
      movie-summary value plus a sealed capacity type (`ActingCapacity` | `NonActingCapacity`), and a
      paged-result value (items + page/size/totalElements). No Spring/JPA/HATEOAS imports.
- [x] 3.2 Unit tests for the domain model: capacity typing invariants (acting carries
      character+billingOrder, non-acting carries department+job), and total-order comparison
      (`releaseYear` desc, `title` asc, terminal key) — happy + edge (shared year/title ties).

## 4. Application (use case + port)

- [x] 4.1 Add outbound port `PersonFilmographyPort` (load a Person's filmography page + total by
      person id + page/size; signal person existence) under `...people.application`.
- [x] 4.2 Add `GetPersonFilmography` use case orchestrating the port; on unknown person throw the
      existing `ResourceNotFoundException("PERSON_NOT_FOUND", ...)` (the CAT-004 pattern — no bespoke
      exception); return an empty page for an existing person with no credits.
- [x] 4.3 Application unit tests with the port mocked: happy (populated page), edge (empty filmography →
      empty page, not error), failure (unknown person → `ResourceNotFoundException` with
      `PERSON_NOT_FOUND`).

## 5. Outbound persistence adapter (+ migration only if needed)

- [x] 5.1 Implement `PersonFilmographyJpaAdapter` over the existing `credits`/`movies`/genres tables,
      reusing the `catalog/people` side's `PersonJpaEntity`/`PersonJpaRepository` for the existence check
      (do NOT create a third entity/repo — CAT-004 lesson).
- [x] 5.2 Implement the bounded id-page-then-fetch query (D-B): count + ordered id-page of credits
      (`releaseYear` desc, `title` asc, credit-id terminal key) with `LIMIT/OFFSET`, then a bounded
      movie-summary fetch for the page's movie ids with genres via fetch-join/`@EntityGraph`.
- [x] 5.3 Only if the query cannot stay bounded/ordered without one, add a NEW Flyway migration
      `V<n>__<desc>.sql` for the needed index (never edit an applied migration) and note it in the PR;
      otherwise confirm no migration is needed.
- [x] 5.4 Persistence tests against real Postgres via the manual Testcontainers singleton (no H2, no
      `@Testcontainers`/`@Container` mixing) with own fixtures: ordering correctness (total + stable +
      cross-page no skip/dup), one-item-per-(movie,capacity), pagination boundaries, and the empty
      filmography case.
- [x] 5.5 N+1 query-count guard test (Hibernate statistics): seed a Person with many credits across many
      movies and genres; assert the statement count to load one page is bounded and does not grow as the
      filmography grows.

## 6. Inbound controller (web adapter)

- [x] 6.1 Implement the generated `getPersonFilmography` interface in a new controller under
      `...people.adapters.in.web`; map domain → generated DTOs; assemble `_embedded.filmography`, item
      `MovieSummary._links.self` (→ `GET /api/v1/movies/{id}`), and the collection
      `self`/`first`/`last`/`prev`/`next` pagination links via `WebMvcLinkBuilder` (web-adapter-only).
- [x] 6.2 Enforce pagination validation (`page` >= 0, 1 <= `size` <= 100) so invalid input → `400`
      problem+json via the existing advice; confirm malformed UUID → `400` via the existing
      `MethodArgumentTypeMismatchException` handler and unknown person → `404` `PERSON_NOT_FOUND` via the
      existing `ResourceNotFoundException` handler (no new advice handler).
- [x] 6.3 (D12) Add `_links.credits` (→ `GET /api/v1/people/{id}/credits`) to `PersonController`
      (person detail), assembled via `WebMvcLinkBuilder`; add no SQL to the person-detail path.
- [x] 6.4 Register `/api/v1/people/*/credits` in the single source of truth for public endpoints so the
      public-endpoint consistency test holds.

## 7. Web-layer + cross-cutting tests

- [x] 7.1 `@WebMvcTest`/MockMvc for the filmography endpoint: enveloped HAL collection shape;
      typed-capacity item (acting vs non-acting, movie summary + `_links.self`); empty-filmography `200`;
      pagination boundaries (first/last/beyond-last, links present/absent) and invalid `page`/`size` →
      `400`; `404` `PERSON_NOT_FOUND`; malformed UUID → `400`; public (no `401`/`403`); HAL discipline
      (no `_templates`; errors are problem+json with no `_links`/`_embedded`).
- [x] 7.2 Person-detail web-layer test (D12): `GET /api/v1/people/{id}` emits `data._links.credits.href`
      addressing `GET /api/v1/people/{id}/credits`; the rest of the CAT-004 shape (exactly `id`, `name`,
      `_links`) is unchanged.
- [x] 7.3 Confirm the demo (`@Profile("demo")`) seed has a Person with a multi-movie (ideally
      multi-capacity) filmography for the running-app happy path; tests remain seed-independent.
- [x] 7.4 Run `./gradlew build -x spotlessCheck` (agents do not format; the pre-commit hook does);
      confirm compile + all layers' tests pass and CAT-001/002/003/004 paths do not regress.

## 8. Docs / domain reconciliation

- [x] 8.1 Update `domain/bounded-contexts.md`: person **filmography** now exists under `catalog/people`
      (`GET /api/v1/people/{id}/credits`); the person **collection** remains planned.
- [x] 8.2 Update `domain/glossary.md`: add **filmography** (Person-side inverse of a Movie's credits, the
      `_embedded.filmography` relation) and record `credits` as the person-detail HAL relation name; and
      `domain/business-rules.md`: record the ordering (`releaseYear` desc, `title` asc) and pagination
      (`size` default 20/max 100) policy, noting explicitly the "movie credits returned whole" rule does
      NOT extend to a person's filmography, and that person detail now carries `_links.credits`.
