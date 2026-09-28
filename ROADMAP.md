# Roadmap

The sequence of capabilities and use cases for the movie catalog. This is the **planning** view —
what's built, what's next, and why in this order. It is the source of truth for "what's the next use
case?"; keep it in step with `use-cases/`, `tickets/`, `domain/bounded-contexts.md`, and
`openspec/changes/archive/`.

**Legend:** ✅ Done (archived) · 🔜 Next · 📝 Planned (use case not yet written) · 💡 Idea (not yet shaped)

> **Naming:** `UC-<n>` = a business use case in `use-cases/`. `CAT-<n>` / `PLAT-<n>` = the delivery
> ticket/change for a `catalog` / `platform` capability. A use case is authored first, then taken
> through the SDLC pipeline as one or more changes, each on its own `feat/uc-<n>-<slug>` branch cut
> from `develop` and merged back into `develop`.

## Done ✅
| Ref | Capability | Use case | Delivered as |
|-----|------------|----------|--------------|
| UC-000 | **Platform foundation** — availability check, interface description (browsable + tool-readable), uniform responses, runtime modes | [`UC-000`](use-cases/UC-000-get-started-with-the-catalog-service.md) | `add-platform-foundation` (#35) |
| UC-001 / CAT-001 | **Retrieve a movie's details** — `GET /api/v1/movies/{id}` | [`UC-001`](use-cases/UC-001-retrieve-a-movies-details.md) | `add-movie-details` (#36) |

## Next 🔜
| Ref | Capability | Use case | Depends on | Notes |
|-----|------------|----------|------------|-------|
| UC-002 / CAT-002 | **Search & browse movies** — `GET /api/v1/movies`: title match, genre / release-year range / minimum-rating filters, ordering, pagination | 📝 to write | UC-001 (Movie) | The main way a consumer *finds* a movie id; UC-001 assumes they already hold one. Introduces the paging convention (and nav links that preserve filter & sort params) that every later list reuses. |

## Planned 📝 (use case not yet written)
Ordered by dependency. The **people track** (UC-003 → UC-004 → UC-005 / UC-006) is sequential. The
**genres/keywords** and **reviews** tracks depend only on Movie (and UC-002 for the keyword filter),
so they can be pulled forward or run in parallel if priorities change.

| # | Ref | Capability | Depends on | Why it comes here |
|---|-----|------------|------------|-------------------|
| 3 | UC-003 / CAT-003 | **View a movie's cast & crew** — `GET /api/v1/movies/{id}/credits`, returned whole (not paged) | UC-001 | Introduces Person/Credit modelling, which unlocks the whole people track. Movie detail gains a link to its credits; credited people are named only until UC-004. |
| 4 | UC-004 / CAT-004 | **Retrieve a person's details** — `GET /api/v1/people/{id}` (id, name, links; no biography) | UC-003 | Makes a Person independently addressable; completes UC-003's deferred onward link from each credited person. |
| 5 | UC-005 / CAT-005 | **View a person's filmography** — `GET /api/v1/people/{id}/credits`, paged, one list with a capacity per entry | UC-004 | The inverse of a movie's credits, from the person side. Paged (unlike movie credits) because a career can be long. |
| 6 | UC-006 / CAT-006 | **Search & list people** — `GET /api/v1/people`, name filter, paged, ordered by name (A–Z default) | UC-004; paging from UC-002 | The person-side analogue of movie search. Independent of UC-005, so 5 and 6 can swap. |
| 7 | UC-007 / CAT-007 | **Browse genres & keywords, and filter movie search by keyword** | UC-002 | Keyword discovery is a new **filter on movie search** (like genre), not a separate search. Also settles UC-001's open question on whether a genre carries its own identity. May split so keyword *listing* ships later. |
| 8 | UC-008 / CAT-008 | **View a movie's reviews** — curated, read-only reviews sub-resource | UC-001 | Reviews only; the aggregate rating already shows on movie detail and search results and isn't duplicated. |

## Ideas 💡 (not yet shaped)
- Keyword-based discovery / "more like this" across movies (builds on UC-007).
- Rate limiting for public access — limits still TODO in `domain/business-rules.md`; likely a `PLAT-<n>` ticket.
- Admin / curation surface (would introduce auth — currently out of scope; see `domain/bounded-contexts.md`).

## Conventions that carry across capabilities
- **Read-only & public** — every `catalog` capability is unauthenticated and never mutates data (`domain/business-rules.md`).
- **Contract-first** — OpenAPI spec before controller (`standards/openapi.md`).
- **Pagination** — `size` default 20, max 100; nav links (first/prev/next/last) preserve active filter/sort params. Small sub-resources (a movie's credits) are returned **whole**; large ones (search, filmography, people list) are paged.
- **Unknown id → 404 problem+json; malformed id → 400; empty result → 200** — a valid request that matches nothing is a success, not an error.

---
_Last reviewed: 2026-09-28 (rebuilt after the `develop` reset; UC-000 and UC-001 re-delivered)._
