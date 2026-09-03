# Glossary — ubiquitous language

One agreed definition per business term. Use these exact terms in tickets, specs, and code. Add a row
whenever a new term appears.

| Term | Definition | Notes / synonyms to avoid |
|------|------------|---------------------------|
| Movie | A single film title in the catalog: the core aggregate. Has a stable id, title, release year, runtime, synopsis, genres, credits, and an aggregate rating. | Not "film"/"title" in code — say **Movie**. |
| Movie detail | The full single-movie representation (`GET /movies/{id}`): id, title, releaseYear, genres, and when present runtimeMinutes/synopsis/rating. | See CAT-001. |
| Movie summary | The lighter representation used in **search/list** results (`GET /movies`): id, title, releaseYear, genres, and when present runtimeMinutes/rating — **no synopsis**; carries a `self` link to its detail. | See CAT-002. |
| Person | An individual who worked on movies (actor, director, writer, …). Has an id, name, and credits. | — |
| Credit | The link between a Person and a Movie in a specific capacity (e.g. "Actor as <character>", "Director"). | Also called a "role"; prefer **Credit**. |
| Cast | The set of acting Credits on a Movie (with character names, billing order). | — |
| Crew | The set of non-acting Credits on a Movie (director, writer, composer, …), grouped by department/job. | — |
| Genre | A controlled-vocabulary category a Movie belongs to (e.g. Drama, Sci-Fi). A Movie has many. | Curated taxonomy, not free text. |
| Keyword | A free-form tag describing a Movie's themes/topics (e.g. "heist", "dystopia"), used for discovery. | Distinct from **Genre** (controlled vs free-form). |
| Rating | The **aggregate** score for a Movie on a **0–5 star** scale (curated). | 0–5 stars (decided, CAT-001). Whether a vote count is exposed is a per-endpoint detail. Curated, not user-submitted. |
| Review | A curated written critique associated with a Movie (author, text, optional score). Served read-only. | Not user-submitted via this API. |
| Search | Finding movies by a query (title match) optionally narrowed by **filters** (genre, year, rating) with pagination and sorting. | — |
| Filter | A constraint that narrows a search (e.g. genre=Drama, year=1999, minRating=7). | — |
| Catalog | The whole curated body of movie data the API serves. | The `catalog` bounded context (see bounded-contexts.md). |
| Correlation id | A UUID on every request/response and log line to trace one request end-to-end. | Technical, appears in NFRs. |

> Keep definitions business-facing. The **Rating** scale (0–5 stars, score only, no vote count) is
> settled as of CAT-001.
