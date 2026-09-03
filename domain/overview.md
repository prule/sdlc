# Product overview

## What we're building
A **movie database** exposed as a **public, read-only REST API** for searching and retrieving movie
data — movies and their people (cast & crew), genres/keywords, and ratings/reviews. Consumers are
developers building their own apps/sites on top of our catalog.

## The problem / why it matters
Developers need reliable, well-structured movie data (search + detail) without running their own
catalog. We provide a clean, fast, well-documented API over a **curated** dataset.

## Goals
- A public REST API to **search** movies (by title, with filters) and **retrieve** full detail for a
  movie, person, genre, etc.
- High-quality, consistent, curated data (accuracy over volume).
- Fast, predictable responses; easy for third-party developers to adopt.

## Non-goals / explicitly out of scope
- **No authentication / user accounts** — the read API is open to the public (see business-rules.md).
- **No writes through the API** — it is read-only. Data is created/maintained **out-of-band** by
  internal curation; that admin/ingestion process is not part of this API product.
- **No user-generated content** — reviews/ratings are curated data we serve, not submitted by API users.
- No recommendations/personalisation, no streaming/playback, no commerce.

## Primary personas (detail in actors-and-personas.md)
- **API consumer (developer)** — the main customer; integrates our API into their product.
- **End user (indirect)** — uses the consumer's app; never talks to us directly.
- **Curator (internal, out-of-band)** — maintains the catalog outside this API's scope.

## Key constraints
- **Public access** → abuse control is via **rate limiting** (not auth). See business-rules.md.
- Data is **curated internally**; the API only ever reads it.
- Movie metadata licensing/attribution: N/A for now — internally curated; revisit if an external
  data source with attribution obligations is adopted (decided in CAT-001).
