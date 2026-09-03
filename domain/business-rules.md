# Business rules & policies

Cross-cutting rules and invariants that hold across features. Feature-specific acceptance criteria
live in the ticket; durable policies live here.

## Access & security posture
- The read API is **public** — no authentication, no user accounts. Endpoints are `security: []`.
  > **Deliberate divergence from `standards/security.md`** (which assumes stateless JWT bearer auth):
  > that standard applies to any *future authenticated* surface (e.g. an admin/curation API). For the
  > public read API, the primary abuse control is **rate limiting**, not auth.
- **Rate limiting** applies to the public endpoints (per client IP / API gateway). TODO: confirm
  limits (e.g. requests/min) and where they're enforced (gateway vs app).
- No secrets are needed to call the API; none are ever returned.

## Read-only
- The API **never mutates catalog data** — no create/update/delete via HTTP. All data changes happen
  through the out-of-band curation process, which is not part of this product.

## Catalog data integrity
- A **Movie** is uniquely identified by a stable opaque id (UUID in URLs; never expose internal DB ids).
- **Genre** is a controlled vocabulary; **Keyword** is free-form — a Movie may have many of each.
- A **Rating** is an aggregate **score-only** on a **0–5 star** scale, curated, not user-submitted
  here. No vote count is exposed. (Decided in CAT-001.)
- **Reviews** are curated and served read-only; they are not submitted by API consumers.
- Movies with missing optional fields (no synopsis, no rating yet) are still valid and returned.

## API behaviour (product-level)
- All responses use the standard success **Envelope**; errors use RFC 7807 problem+json
  (`standards/openapi.md`, `standards/error-handling.md`).
- Resource and collection responses carry **navigational hypermedia links** (HAL `_links` /
  `_embedded`, inside the Envelope's `data`) — `self`, and for collections pagination `next`/
  `prev`/`first`/`last`. These are read-only navigation aids; they are never action or
  write/state-transition affordances (`standards/openapi.md` §2a).
- **Search** results are **paginated** (page/size) and support sorting; an empty result set is a
  normal 200, not an error. TODO: confirm default/max page size and default sort order.
- A request for a non-existent movie/person id returns **404** (problem+json), not an empty 200.

## Privacy / compliance
- The catalog is not personal data of API users (no accounts, minimal PII). Data about **people**
  (cast/crew) is public professional/biographical info. Data-source licensing/attribution: N/A for
  now — internally curated; revisit if an external data source with attribution obligations is
  adopted. No attribution field is carried on any resource (decided in CAT-001).
