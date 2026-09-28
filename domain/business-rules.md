# Business rules & policies

Cross-cutting rules and invariants that hold across features. Feature-specific acceptance criteria
live in the ticket; durable policies live here.

## Access & security posture
- The catalog is **public**: no authentication, no user accounts, no credentials needed to read it.
  > **Deliberate divergence from `standards/security.md`** (which assumes authenticated access): that
  > standard applies to any *future authenticated* surface (e.g. an admin/curation surface). For the
  > public catalog, the primary abuse control is **rate limiting**, not authentication.
- **Rate limiting** applies to public access (per client). TODO: confirm the limits (e.g. requests
  per minute) and where they are enforced.
- No secrets are needed to use the catalog, and none are ever revealed.

## Read-only
- The service **never changes catalog data**. Any attempt to create, change or remove anything is
  refused. All data changes happen through the out-of-band curation process, which is not part of
  this product.

## Catalog data integrity
- A **Movie** is uniquely identified by a stable, opaque identifier. It never changes and reveals
  nothing about how the catalog is stored.
- **Genre** is a controlled vocabulary; **Keyword** is free-form — a Movie may have many of each.
- A Movie's **genres** are always presented in **alphabetical order by genre name**, so the same Movie
  always lists its genres the same way (not the curator's entry order). (Decided UC-001.)
- A **Rating** is an aggregate **score-only** on a **0–5 star** scale, curated, not user-submitted
  here. No vote count is shown. (Decided in CAT-001.)
- **Reviews** are curated and read-only; they are not submitted by API consumers.
- Movies with missing optional details (no synopsis, no rating yet) are still valid and presented.
  A missing detail is shown as absent, never as an invented or default value.

## Catalog behaviour (product-level)
> Rules marked *(planned — UC-<n>)* are decisions carried over from before the `develop` reset
> (2026-09-28). They describe the **target** behaviour for capabilities not yet rebuilt — see
> [ROADMAP.md](../ROADMAP.md) — and should be confirmed or revised when that use case is written.

- **Uniform results.** Every answer follows one uniform form: a success carries the requested
  information plus when it was produced and the correlation id; a failure says what kind of problem it
  was, gives a short explanation and the correlation id, and reveals no internal detail. (UC-000.)
- **Navigable results.** Every result says where it can be requested again, points to related
  information where there is some, and, when it is one page of a longer list, points to the first,
  previous, next and last pages. These pointers are for navigation only; they never offer an action
  that changes anything. (UC-000.)
- *(planned — UC-003, UC-004, UC-005)* **Movie detail** will point to where the Movie's credits can
  be found once the credits capability exists; cast and crew are never included inside movie detail.
  A Person will be looked up on their own by identifier (Person detail: identifier and name, pointing
  to their filmography — no biographical details), and every Person shown on a Credit will point to
  that Person's detail.
- *(planned — UC-003)* A Movie's **credits** are presented **whole, not a page at a time**, because cast and crew are
  typically small for one movie. An existing Movie with no cast or crew recorded is a normal success
  with empty lists, not "no such movie" (decided CAT-003).
  This "presented whole" rule does **not** extend to a Person's **filmography**: a Person may work on
  many movies over a career, so their filmography is paged like Search (decided CAT-005).
- *(planned — UC-005)* A Person's **filmography** is **paged** (the same convention as Search: **20 per page by default,
  at most 100**) and ordered by release year **newest first**, then title **A–Z**, then a final
  tiebreak, so the order is complete and stable across pages. It is **one list**, not split into cast
  and crew: each entry is a movie summary plus one **capacity** (acting or non-acting). A Person
  credited in several capacities on one Movie appears once per capacity. An existing Person with no
  credits is a normal success with an empty list, not "no such person" (decided CAT-005).
- *(planned — UC-002)* **Search** results are **paged** (**20 per page by default, at most 100**) and can be **ordered** by
  title, release year or rating, ascending or descending. The **default order is release year newest
  first**, then title A–Z. No matches is a normal success with an empty list, not a failure. (Decided
  in CAT-002.)
- *(planned — UC-002)* **Search matching** (decided CAT-002 / UC-002): the **title** term matches any part of the title,
  ignoring letter case. Several **genres** combine as "must carry all". The **release-year** filter
  is a **range** with an optional lower and/or upper bound (a single year means just that year). The
  **minimum-rating** filter is inclusive on the **0–5** scale and **leaves out movies with no recorded
  rating**. All criteria combine to narrow the results. An invalid search (an unsupported order, a
  page before the first, a page size below 1 or above 100) is refused as asked in a way that isn't
  allowed, which is different from a valid search that matches nothing.
- *(planned — UC-006)* **People search/list** follows the same paging convention as movie Search (20 per page by default,
  at most 100). It is filtered by **name** only (matching any part of the name, ignoring letter case —
  there is no role, department, known-for or has-credits filter for a Person) and can be ordered only
  by name, A–Z or Z–A. The **default order is name A–Z**, unlike movie Search's newest-first default,
  since a Person has no date to order by. No matches is a normal success with an empty list, not a
  failure. (Decided in CAT-006.)
- Asking for a Movie (or, once UC-004 lands, a Person) that does not exist is reported as
  **"no such movie/person"**, a failure, never an empty success.
- A **malformed identifier** (a value that is not a well-formed catalog identifier at all) is a
  distinct outcome from **not found**: it is refused as asked in a way that isn't allowed, before any
  lookup, whereas a well-formed identifier that matches nothing is "not found". The two are reported as
  different failures. (Recorded UC-001.)

## Privacy / compliance
- The catalog is not personal data of API users (no accounts, minimal PII). Data about **people**
  (cast/crew) is public professional/biographical info. Data-source licensing/attribution: N/A for
  now — internally curated; revisit if an external data source with attribution obligations is
  adopted. No attribution is shown on anything the catalog presents (decided in CAT-001).
