# UC-007: Browse genres and keywords, and discover movies by them

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Discover the vocabularies the catalog organises movies by — its **genres** (a controlled category taxonomy) and its **keywords** (free-form topical tags) — and use a keyword to find the movies tagged with it, so the consumer can offer genre/keyword browsing and discovery in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog contains movies, a controlled **genre** vocabulary, and free-form **keyword** tags, all curated out-of-band (this use case only reads them).
- The actor does not need to know any identifier in advance; discovering the available genres and keywords is part of this use case.

## Postconditions
- **Success:** The actor has been presented with the catalog's available **genres** and/or **keywords** [BR-3], [BR-4], and — where they chose a keyword — an ordered, paged portion of the movies tagged with it [BR-5], each navigable to that movie's full detail (UC-001). Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed that their request could not be fulfilled because the criteria they supplied are invalid [BR-7], or because a specific keyword they named does not exist [BR-6]; nothing is presented and nothing in the catalog has changed [BR-2]. (A valid discovery request that matches no movies is a **success** with an empty portion — see 4a and [BR-8].)

## Main flow (basic course of events)
### 7A — Browse the vocabularies
1. The API consumer asks the system for the catalog's genres, or for its keywords [BR-3], [BR-4].
2. The system accepts the request without requiring the consumer to authenticate [BR-1].
3. The system presents the available genres (the whole controlled vocabulary) or a portion of the keywords [BR-3], [BR-4], with, for each, enough information to then discover the movies under it.

### 7B — Discover movies by a keyword
1. The API consumer asks the system for the movies tagged with a particular keyword, and may state how the results should be ordered and which portion they want [BR-5].
2. The system accepts the request without requiring the consumer to authenticate [BR-1].
3. The system confirms the named keyword exists and that any requested ordering/portion is valid [BR-6], [BR-7].
4. The system finds the movies tagged with that keyword, orders them, and selects the requested portion [BR-5].
5. The system presents that portion as a list of **movie summaries** (as UC-002), with information to page through the full result set [BR-5].

## Alternative & exception flows
- **7A·3a. The catalog has no keywords (or no genres) yet:** this is a normal success — the system presents an empty vocabulary, not an error [BR-8].
- **7B·3a. The named keyword does not exist in the catalog:** the system informs the consumer the keyword is unknown; whether this is a *not found* or an empty success is an open question — see Open questions and [BR-6].
- **7B·3b. The consumer requests an unsupported ordering, or an out-of-bounds portion:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer why [BR-7].
- **7B·4a. The keyword exists but tags no movies:** this is a normal success — the system presents an empty portion and reports a total of zero [BR-8].

## Business rules
- **BR-1 (Public access):** The catalog is public. Browsing vocabularies or discovering movies by them requires no authentication, account, or credential.
- **BR-2 (Read-only):** This use case only observes; it never changes catalog data.
- **BR-3 (Genre is a controlled vocabulary):** **Genres** are a curated, controlled taxonomy — a fixed, catalog-wide set of categories, not free text. Listing genres returns that whole set. (Filtering movie *search* by genre already exists — UC-002, BR-5 — and is not redefined here.)
- **BR-4 (Keyword is free-form):** **Keywords** are free-form topical tags used for discovery; a movie may carry many. Unlike genres, keywords are open-ended and potentially numerous, so listing keywords is paged, not returned whole. [See Open questions on whether keyword browsing is in scope now.]
- **BR-5 (Keyword discovery paging & summaries):** Movies discovered by a keyword are returned as **movie summaries** (as UC-002), **paginated** (default portion size 20, maximum 100) and **sortable** by the same fields as movie search, with the same default ordering (release year descending, title ascending as tiebreak). Each summary is navigable to that movie's full detail (UC-001).
- **BR-6 (Unknown keyword handling):** A request to discover movies by a keyword that does not exist in the catalog is reported as a distinct outcome from a keyword that exists but tags no movies. The exact reporting (a *not found* vs a normal empty success) is an open decision — see Open questions.
- **BR-7 (Invalid criteria are a bad request):** An unsupported sort field or an out-of-bounds portion is rejected as a *bad request* before any matching, distinct from a valid request that matches nothing [BR-8].
- **BR-8 (Empty result is success):** An empty vocabulary, or a valid discovery request that matches no movies, is a normal successful outcome presenting an empty result — **not** an error.

## Non-goals
- Redefining movie **search** or its existing **genre** filter — that is UC-002; this use case adds vocabulary *browsing* and *keyword* discovery, it does not restate genre filtering.
- Managing or editing the genre/keyword vocabularies (curation is out-of-band).
- Presenting people, credits, ratings, or reviews.
- Any create, update, or delete of catalog data.

## Open questions (need a human decision before/at Gate 1)
- **Scope split:** this use case bundles three related behaviours — list genres, list keywords, and discover movies by a keyword. Should it ship as one capability, or be split (e.g. genre/keyword listing separately from keyword-based movie discovery)? The architect may want to divide it; flagged so the boundary is a deliberate decision.
- **Keyword as a movie-search filter vs a discovery endpoint:** should discovering movies by keyword be a **new filter on movie search** (UC-002) — consistent with how genre already works — rather than a separate keyword-scoped listing? This materially changes the contract; needs a decision.
- **Unknown keyword:** is naming a non-existent keyword a *not found* or a normal empty success [BR-6]? (Genre, being a controlled vocabulary, would more naturally be *bad request* for an unknown value; keyword, being free-form, is less clear-cut.)
- **Do keywords need to be browsable at all now,** or is only *filtering/discovery by* a known keyword required for the near term? [BR-4]
