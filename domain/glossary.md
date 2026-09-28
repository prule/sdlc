# Glossary — ubiquitous language

One agreed definition per business term. Use these exact terms in tickets, specs, and code. Add a row
whenever a new term appears.

| Term | Definition | Notes / synonyms to avoid |
|------|------------|---------------------------|
| Movie | A single film title in the catalog: the core aggregate. Has a stable id, title, release year, runtime, synopsis, genres, credits, and an aggregate rating. | Not "film"/"title" in code — say **Movie**. |
| Movie detail | Everything the catalog presents about one Movie when asked for it by its identifier: identifier, title, release year and genres (alphabetical), plus runtime in minutes, synopsis and rating when recorded. | See UC-001. Excludes keywords, credits and reviews. |
| Movie summary | The shorter form of a Movie shown in **search/list** results: identifier, title, release year and genres, plus runtime and rating when recorded — **no synopsis**. Each summary points to where that Movie's detail can be found. | *(planned — UC-002)* See CAT-002. |
| Person | An individual who worked on movies (actor, director, writer, …). Has an identifier, a name, and credits. A Person can be looked up on their own by identifier (Person detail: identifier and name, with pointers to where they can be requested again and to their filmography — no biographical details). Wherever a Credit is shown, the Person appears alongside it by identifier and name, with a pointer to their Person detail. | *(planned — UC-003…UC-005)* See CAT-003, CAT-004, CAT-005. |
| Person summary | The shorter form of a Person shown in **search/list** results: exactly identifier and name — **no biographical details** — pointing to where that Person's detail can be found. | *(planned — UC-006)* See CAT-006. |
| Credit | The link between a Person and a Movie in a specific capacity (e.g. "Actor as <character>", "Director"). Always one of two kinds — an acting credit (**Cast**) or a non-acting credit (**Crew**) — never a single kind carrying both sets of details. A Movie's credits are always presented in a **complete, stable order** (see Cast/Crew). | *(planned — UC-003)* Also called a "role"; prefer **Credit**. See CAT-003. |
| Filmography | A Person's credited Movies, seen from the **Person side** — the inverse of a Movie's **credits** (the Movie side). Presented as **one list** (not split into cast and crew): each entry is a movie summary plus the **capacity** the Person was credited in, which is recognisably either an acting or a non-acting capacity. A Person credited in several capacities on one Movie appears once per capacity. Presented a page at a time, newest release year first, then title A–Z, with a final tiebreak so the order is complete and stable — unlike a Movie's credits, which are presented whole (see business-rules.md). | See CAT-005. Not to be confused with **Credit** (the Movie-side link). |
| Cast | The set of acting Credits on a Movie: Person + **character** (free text) + **billing order** (a positive whole number; **1 = top billing**, a lower number is more prominent). Ordered by billing order, top billing first. | *(planned — UC-003)* See CAT-003. |
| Crew | The set of non-acting Credits on a Movie (director, writer, composer, …): Person + **department** + **job** (both free text, no controlled vocabulary yet). Ordered by department, then job, alphabetically and ignoring letter case. | *(planned — UC-003)* See CAT-003. |
| Genre | A controlled-vocabulary category a Movie belongs to (e.g. Drama, Sci-Fi). A Movie has many. | Curated taxonomy, not free text. |
| Keyword | A free-form tag describing a Movie's themes/topics (e.g. "heist", "dystopia"), used for discovery. | Distinct from **Genre** (controlled vs free-form). |
| Rating | The **aggregate** score for a Movie on a **0–5 star** scale (curated). | 0–5 stars, score only, no vote count (decided, CAT-001). Curated, not user-submitted. |
| Review | A curated written critique associated with a Movie (author, text, optional score). Read-only. | Not submitted by API consumers. |
| Search | Finding movies by title, optionally narrowed by **filters** (genre, release year, rating), presented a page at a time in a chosen order. | *(planned — UC-002)* |
| Filter | A constraint that narrows a search (e.g. genre Drama, released 1990–1999, rated at least 4 of 5 stars). Several genres combine as "must carry all"; release year is a range; the minimum rating is inclusive and leaves out unrated movies. | *(planned — UC-002)* |
| Catalog | The whole curated body of movie data the API serves. | The `catalog` bounded context (see bounded-contexts.md). |
| Availability check | A public, harmless question to the catalog service, "are you available?". Anyone can ask it without credentials, it never changes anything, and it is answered even when the catalog is empty. | See UC-000. Say **availability check**, not "ping", "health" or "heartbeat" in business text. |
| Interface description | The service's own published description of every capability it offers. It comes in a browsable form (a person can read it and try capabilities against the running service) and a form tools can read. It must always agree with actual behaviour, and shared concepts (result form, failure form, paging) are described once and reused. | See UC-000. Not "docs" or "spec" in business text. |
| Standalone mode | Running the catalog service with no external infrastructure provisioned, for evaluation. Catalog data is **not** retained across restarts. Behaviour is otherwise identical to **persistent mode**, the mode for real operation. | See UC-000. |
| Correlation id | A unique reference attached to every request and its answer, so one request can be traced end-to-end and quoted when reporting a problem. | Appears in NFRs. |

> Keep definitions business-facing: no endpoints, field names, status codes or response formats.
> Those belong to the architect (OpenSpec specs), not the domain language.
>
> **How related information is reached (CAT-003…CAT-005 — *planned*, UC-003…UC-005):**
> - **Movie detail** will point to where the Movie's credits can be found once the credits
>   capability exists; cast and crew are never included inside movie detail itself.
> - A Movie's credits are presented as **two separate lists**, cast and crew, because each has
>   different details and a different order. They are not one mixed list.
> - Every Person shown on a Credit points to that Person's detail.
> - **Person detail** points to where the Person's filmography can be found, mirroring how movie
>   detail will point to credits once that capability exists.
> - A Person's **filmography** deliberately stays **one list**, with each entry marked with its
>   capacity. Every entry is the same kind of thing (a movie), annotated with *how* the Person was
>   credited on it.
