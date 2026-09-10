## Context

See proposal.md — Why. The catalog already serves movie detail (UC-001) and search (UC-002) over a clean/hexagonal `com.acme.catalog.movies` slice, contract-first OpenAPI, the standard Envelope + HAL convention, and Flyway migrations (`V2__movies.sql`). This change adds the credits sub-resource and an additive `credits` link on movie detail, reusing those established patterns. Ubiquitous language is fixed in `domain/glossary.md` (Person, Credit, Cast, Crew) and the outcomes/orderings in `domain/business-rules.md`.

Note on domain docs: `glossary.md`/`business-rules.md` already describe the *eventual* CAT-004/005 state (an inline `person._links.self`, a `/people/{id}` resource). This change is CAT-003 only — the glossary's CAT-004 note itself records that the person self-link was "previously withheld (CAT-003) because no addressable Person endpoint existed." So here the inline person is named-only (`id` + `name`, no `_links`), per UC-003 resolved decision 1 and BR-9.

## Goals / Non-Goals

**Goals:**
- Fit the credits capability into the existing movies slice with no new architectural pattern.
- Make the movie-detail `credits` link an additive, non-breaking contract change.
- Keep cast/crew ordering in the domain; keep HAL assembly web-only.
- Keep the migration H2- and Postgres-compatible (single shared set).

**Non-Goals (design-level):**
- Person resource / person self-link, filmography, people search, credits pagination — see proposal Non-goals.
- Optimizing credits fetching for scale — cast/crew are small per movie (BR-7); a straightforward two-query load per movie is sufficient. No N+1 guard is required here (unlike search).

## Decisions

### 1. Two OpenAPI operations touched — one added, one modified

**Added:** `GET /movies/{id}/credits` — `operationId: getMovieCredits`, `security: []`, uuid path param + shared `CorrelationId` param; `200 → MovieCreditsEnvelope`, `400/404/500 → shared responses`. New `paths/movies-credits.yaml`, registered at `/movies/{id}/credits` in `openapi.yaml`.

**Modified (additive):** `MovieLinks` gains an optional `credits` relation, so movie detail's `data._links.credits` points at the credits sub-resource. Only `self` stays required; adding an optional property is backward-compatible for existing clients.

Contract snippets (authored split spec under `src/main/resources/openapi/`):

```yaml
# components/schemas/common.yaml — MODIFIED (additive)
MovieLinks:
  type: object
  required: [self]
  properties:
    self:    { $ref: '#/Link' }
    credits: { $ref: '#/Link' }   # NEW — resolves to GET /movies/{id}/credits

# a fixed-relation links object for the credits resource
CreditsLinks:
  type: object
  required: [self]
  properties:
    self: { $ref: '#/Link' }
```

```yaml
# components/schemas/movie.yaml — ADDED
CreditPerson:                     # inline person, named-only (no _links this change)
  type: object
  required: [id, name]
  properties:
    id:   { type: string, format: uuid }
    name: { type: string }
CastCredit:
  type: object
  required: [person, billingOrder]
  properties:
    person:      { $ref: '#/CreditPerson' }
    character:   { type: string }          # OPTIONAL — omitted when absent
    billingOrder:{ type: integer, minimum: 1 }
CrewCredit:
  type: object
  required: [person, department, job]
  properties:
    person:     { $ref: '#/CreditPerson' }
    department: { type: string }
    job:        { type: string }
MovieCreditsData:
  type: object
  required: [_embedded, _links]
  properties:
    _embedded:
      type: object
      required: [cast, crew]
      properties:
        cast: { type: array, items: { $ref: '#/CastCredit' } }
        crew: { type: array, items: { $ref: '#/CrewCredit' } }
    _links: { $ref: '../../openapi.yaml#/components/schemas/CreditsLinks' }
MovieCreditsEnvelope:
  type: object
  required: [data, meta]
  properties:
    data: { $ref: '#/MovieCreditsData' }
    meta: { $ref: '../../openapi.yaml#/components/schemas/Meta' }
```

Two separate embedded relations (`cast`, `crew`) rather than one `credits` array with a discriminator: cast and crew have different required fields and different orderings, so splitting keeps each schema fully populated and each ordering unambiguous (glossary CAT-003 note). `character` is modeled optional per resolved decision 2.

### 2. Domain model — sealed Credit, named-only Person, MovieCredits aggregate

New in `com.acme.catalog.movies.domain.model` (no Spring/JPA/HAL imports, inward-only):
- `Person` — record `(UUID id, String name)`, validating id/name present and name non-blank.
- `Credit` — a `sealed interface` permitting `Cast` and `Crew` records, each exposing `Person person()`:
  - `Cast(Person person, Optional<String> character, int billingOrder)` — validates `billingOrder >= 1`; a factory accepting a nullable character.
  - `Crew(Person person, String department, String job)` — validates department/job present and non-blank.
- `MovieCredits(List<Cast> cast, List<Crew> crew)` — the aggregate. **Ordering is the domain's responsibility:** the factory sorts cast by `billingOrder` ascending and crew by `department` then `job` ascending case-insensitively (e.g. `Comparator.comparing(c -> c.department().toLowerCase(Locale.ROOT))` then job), and stores immutable copies. An empty cast and/or crew is valid (BR-8).

Sealed `Credit` uses the Java 25 feature set already favored in this repo (records + sealed types + pattern matching in the web mapper). The two shapes are distinct types, mirroring the two embedded relations, so no runtime "which kind is this" flag leaks into the domain.

### 3. Application — a use case that distinguishes not-found from empty

- Inbound port `GetMovieCreditsUseCase` — `MovieCredits getMovieCredits(UUID movieId)`.
- Outbound port `LoadMovieCreditsPort` — `Optional<MovieCredits> loadCreditsForMovie(UUID movieId)`, returning `Optional.empty()` **only when the movie does not exist**, and a present `MovieCredits` (possibly with empty cast/crew) when the movie exists (BR-3 vs BR-8). This distinction is the crux: the adapter first checks movie existence, then loads credits.
- `GetMovieCreditsService` implements the use case, throwing the existing `ResourceNotFoundException("MOVIE_NOT_FOUND", …)` when the port is empty — reusing the UC-001 outcome and the existing `GlobalExceptionHandler` (no new exception, no new handler). Malformed id is handled upstream by the framework's UUID path binding → `400`, identical to UC-001.

### 4. Adapters

**Inbound (`adapters/in/web`):** `MovieController` gains `getMovieCredits(UUID id, UUID xCorrelationId)` (implementing the generated `MoviesApi` method), calling the use case and mapping `MovieCredits → MovieCreditsEnvelope`. Pattern-matches each `Cast`/`Crew` into its DTO; omits `character` when the `Optional` is empty. Builds `data._links.self` via `WebMvcLinkBuilder` (`linkTo(methodOn(MoviesApi.class).getMovieCredits(id, null))`). Also extends `toMovieDetail(...)` to set `MovieLinks.credits` via the same builder. HAL assembly stays web-only; the domain never imports Spring HATEOAS.

**Outbound (`adapters/out/persistence`):** new `PersonJpaEntity`, `CastCreditJpaEntity`, `CrewCreditJpaEntity` (or a single `CreditJpaEntity` with a kind discriminator — see decision 5), Spring Data repositories, and `MovieCreditsPersistenceAdapter implements LoadMovieCreditsPort`. It confirms movie existence (reuse `MovieJpaRepository.existsById`) then loads that movie's credits, mapping JPA rows → domain `Cast`/`Crew`/`Person`. Ordering is re-asserted in the domain factory, so query order is not relied upon. If any native SQL projects a uuid id column, apply the CLAUDE.md portability rule (`CAST(col AS varchar)` + `UUID.fromString`); the straightforward mapping here can use Spring Data derived queries and avoid native SQL entirely.

Dependency direction is inward-only: adapters → application → domain; domain imports nothing outward.

### 5. Persistence shape — separate cast/crew columns in one credits table

`V3__credits.sql` (new; `V2` is never edited):

```sql
CREATE TABLE people (
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE credits (
    id            UUID PRIMARY KEY,
    movie_id      UUID NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    person_id     UUID NOT NULL REFERENCES people (id),
    kind          VARCHAR(4) NOT NULL,          -- 'CAST' | 'CREW'
    -- cast-only (NULL for crew):
    character     TEXT NULL,                     -- optional even for cast
    billing_order INT NULL,
    -- crew-only (NULL for cast):
    department    TEXT NULL,
    job           TEXT NULL,
    CONSTRAINT credits_shape CHECK (
        (kind = 'CAST' AND billing_order IS NOT NULL AND department IS NULL AND job IS NULL)
        OR (kind = 'CREW' AND department IS NOT NULL AND job IS NOT NULL AND billing_order IS NULL AND character IS NULL)
    )
);
CREATE INDEX idx_credits_movie ON credits (movie_id);
```

**Why one table with a `kind` column over two tables:** cast and crew are both "a credit linking a person to a movie" and are always fetched together for one movie; a single table with a shape `CHECK` keeps the FK/index story simple and lets the adapter load a movie's credits in one query, splitting into the two domain lists in memory. The `CHECK` enforces at the DB that each row is exactly one valid shape (BR-4: never both sets of fields). Alternative (two tables `cast_credits`/`crew_credits`) was considered — cleaner columns but duplicates the movie/person FK plumbing and needs two queries; rejected for a small, always-co-fetched dataset. `character` stays nullable for cast (optional, resolved decision 2). Types (`UUID`, `TEXT`, `INT`, `VARCHAR`, `CHECK`) are all H2- and Postgres-compatible — no vendor split.

### 6. Demo seed + public endpoint registration

Extend the demo seed (active when the `test` profile is NOT active) so the H2 default runtime has: a movie with both cast and crew (including a cast credit with **no** character and out-of-order billing to prove ordering), and a movie with **empty** credits (to prove the `200`-empty path). Register `/movies/{id}/credits` in `PublicEndpoints.PATTERNS` so `SecurityConfig` permits it and `PublicEndpointsConsistencyTest` stays green (it cross-checks `security: []` operations against the pattern list).

## Risks / Trade-offs

- **[Movie-detail contract change]** Adding `credits` to `MovieLinks` changes an existing response. → Additive optional property; existing clients ignore unknown links. Called out in the proposal as non-breaking. The spec's MODIFIED movie-detail requirement asserts the new link.
- **[Codegen drift]** New cross-file schemas could reintroduce per-operation `<Operation><Status>Response*` duplicates. → Author schemas as shared named components `$ref`ed into the envelope (as done for movie detail/search); `GeneratedApiCodegenTest` guards against reversion.
- **[H2/Postgres portability]** Native SQL casting a uuid to `UUID` breaks on H2. → Prefer Spring Data derived queries (no native SQL); if native SQL is unavoidable, `CAST(col AS varchar)` + `UUID.fromString` per CLAUDE.md.
- **[not-found vs empty conflation]** Returning empty groups for an unknown movie would violate BR-3. → The outbound port returns `Optional.empty()` only when the movie is absent (existence check precedes credit load); the service maps empty → `404`, present-but-empty → `200`. A persistence test covers both.
- **[CHECK constraint rigidity]** The shape `CHECK` rejects malformed seed/curation rows. → Intended — it enforces BR-4 at the DB; the seed and mappers produce valid shapes only.

## Migration Plan

- Forward: add `V3__credits.sql` (new file; `V2` untouched). Flyway applies it on boot in every environment; the automated suite runs it via Testcontainers/Postgres and the H2 default runtime applies the same shared migration.
- Rollback: the change is additive (new tables, new endpoint, new optional link). To back out before release, drop `V3` (and its tables) and revert the code; no existing table or applied migration is altered, so no data migration or down-conversion is needed. Never edit `V3` once applied — add a further migration instead.
