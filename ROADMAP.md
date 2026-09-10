# Roadmap

The sequence of capabilities and use cases for the movie catalog. This is the **planning** view —
what's built, what's next, and why in this order. It is the source of truth for "what's the next use
case?"; keep it in step with `use-cases/`, `tickets/`, `domain/bounded-contexts.md`, and
`openspec/changes/archive/`.

**Legend:** ✅ Done (archived) · 🔜 Next · 📝 Planned (use case not yet written) · 💡 Idea (not yet shaped)

> **Naming:** `UC-<n>` = a business use case in `use-cases/`. `CAT-<n>` / `PLAT-<n>` = the delivery
> ticket/change for a `catalog` / `platform` capability. A use case is authored first, then taken
> through the SDLC pipeline as one or more changes.

## Done ✅
| Ref | Capability | Delivered as |
|-----|------------|--------------|
| — | Walking skeleton, shared OpenAPI components, HAL hypermedia convention, H2 default runtime, Swagger UI | `platform` foundation (archived changes) |
| UC-001 / CAT-001 | **Retrieve movie detail** — `GET /movies/{id}` | `add-movie-detail` |
| UC-002 / CAT-002 | **Search & browse movies** — `GET /movies` (filters, sort, pagination) | `add-movie-search` |
| PLAT-001 | Pagination nav links preserve filter & sort query params | `pagination-links-preserve-query-params` |

## Next 🔜
| Ref | Capability | Use case | Depends on | Notes |
|-----|------------|----------|------------|-------|
| UC-003 / CAT-003 | **View a movie's cast & crew** — a movie's credits from the *movie* side | [`use-cases/UC-003-view-movie-credits.md`](use-cases/UC-003-view-movie-credits.md) (Ready) | Movie (done) | Introduces Person/Credit modelling. Smallest next increment; unlocks the people capabilities below. **Resolved:** credited people are named-only now; the onward person link is added when UC-004 lands. |

## Defined 📝 (use case Ready, not yet built)
Ordered by dependency; all open questions resolved (each use case is **Ready** for the SDLC pipeline). The **people track** (UC-004 → 005 → 006) is sequential; the **genres/keywords** and **reviews** tracks depend only on Movie and can be scheduled independently.

| Ref | Capability | Use case | Depends on | Why it comes here |
|-----|------------|----------|------------|-------------------|
| UC-004 / CAT-004 | **Person detail** — Person as an independently addressable resource (id, name, links) | [`UC-004`](use-cases/UC-004-retrieve-person-details.md) (Ready) | UC-003 (Person/Credit modelling exists) | Makes credited people navigable; completes UC-003's deferred onward link. |
| UC-005 / CAT-005 | **Person filmography** — the movies a person is credited in, from the *person* side (paginated, filterable by capacity + year) | [`UC-005`](use-cases/UC-005-view-person-filmography.md) (Ready) | UC-004 | The inverse of a movie's credits; reuses the credits/movies data from the person side. |
| UC-006 / CAT-006 | **Search & list people** — name-filtered, paginated, sorted by name | [`UC-006`](use-cases/UC-006-search-people.md) (Ready) | UC-004 | The person-side analogue of movie search (UC-002). |
| UC-007 | **Browse genres & keywords + keyword filter on movie search** | [`UC-007`](use-cases/UC-007-browse-genres-and-keywords.md) (Ready) | Movie (done); keyword filter extends UC-002/CAT-002 | **Resolved:** keyword discovery is a **new filter on movie search** (like genre), not a separate endpoint. May be split so keyword *listing* ships later. |
| UC-008 | **Movie reviews** — curated read-only reviews sub-resource (aggregate rating stays on detail) | [`UC-008`](use-cases/UC-008-view-movie-reviews.md) (Ready) | Movie (done) | **Resolved:** reviews-only; the aggregate rating already surfaces on detail/summary (UC-001/002) and is not duplicated here. |

## Ideas 💡 (not yet shaped)
- Keyword-based discovery / "more like this" across movies.
- Admin / curation surface (would introduce auth — currently out of scope; see `domain/bounded-contexts.md`).

## Conventions that carry across capabilities
- **Read-only & public** — every `catalog` capability is unauthenticated and never mutates data (`domain/business-rules.md`).
- **Contract-first** — OpenAPI spec before controller (`standards/openapi.md`).
- **Pagination** — zero-based `page`; `size` default 20, max 100; nav links preserve active filter/sort params (PLAT-001). Small sub-resources (a movie's credits) are returned **whole**, not paged; large ones (search, filmography) are paged.
- **Unknown id → 404 problem+json; empty result → 200** — a valid request that matches nothing is a success, not an error.

---
_Last reviewed: 2026-09-10._
