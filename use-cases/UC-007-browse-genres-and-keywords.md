# UC-007: Browse genres and keywords, and discover movies by keyword

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Discover the vocabularies the catalog organises movies by — its **genres** (a controlled category taxonomy) and its **keywords** (free-form topical tags) — and narrow a movie search by a **keyword** to find the movies tagged with it, so the consumer can offer genre/keyword browsing and keyword-based discovery in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Ready (open questions resolved by the author; see Resolved decisions)

> **Mechanism (resolved):** discovering movies by a keyword is delivered as an **added filter on movie
> search (UC-002)** — the person-side analogue of how **genre** already filters search — **not** as a
> separate keyword endpoint. This use case therefore covers (A) *listing the vocabularies* and (B) the
> *keyword filter added to movie search*.

## Preconditions
- The catalog contains movies, a controlled **genre** vocabulary, and free-form **keyword** tags, all curated out-of-band (this use case only reads them).
- The actor does not need to know an identifier in advance; discovering the available genres and keywords is part of this use case.

## Postconditions
- **Success:** The actor has been presented with the catalog's available **genres** and/or **keywords** [BR-3], [BR-4], and — where they narrowed a movie search by a keyword — an ordered, paged portion of the movies tagged with it (as UC-002), each navigable to that movie's full detail (UC-001) [BR-5]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed that their request could not be fulfilled because the search criteria they supplied are invalid [BR-7]; nothing is presented and nothing in the catalog has changed [BR-2]. (A keyword that matches no movies — including one not present in the catalog — is a normal **empty success**, not a failure — see 7B·3a and [BR-6].)

## Main flow (basic course of events)
### 7A — Browse the vocabularies
1. The API consumer asks the system for the catalog's genres, or for its keywords [BR-3], [BR-4].
2. The system accepts the request without requiring the consumer to authenticate [BR-1].
3. The system presents the whole controlled genre vocabulary, or a portion of the keywords [BR-3], [BR-4].

### 7B — Discover movies by a keyword (a filter on movie search, UC-002)
1. The API consumer performs a movie search (UC-002), additionally supplying a **keyword** to match — on its own or combined with the existing title/genre/year/rating criteria and with ordering and paging [BR-5].
2. The system validates the criteria exactly as movie search does [BR-7].
3. The system finds the movies that satisfy **all** the supplied criteria, including the keyword filter [BR-5], orders them and selects the requested portion (as UC-002).
4. The system presents that portion as a list of **movie summaries** (as UC-002), with information to page through the full result set [BR-5].

## Alternative & exception flows
- **7A·3a. The catalog has no keywords (or no genres) yet:** this is a normal success — the system presents an empty vocabulary, not an error [BR-8].
- **7B·3a. The keyword matches no movies — whether the keyword tags nothing or is not present in the catalog at all:** this is a normal success, not a failure. The system presents an empty portion and reports a total of zero. Because keywords are free-form, there is **no** "unknown keyword" error [BR-6].
- **7B·3b. The consumer supplies invalid search criteria (unsupported sort field, out-of-bounds portion, etc.):** the system rejects the request as a *bad request*, exactly as movie search does — distinct from a valid search that matches nothing [BR-7].

## Business rules
- **BR-1 (Public access):** The catalog is public. Browsing vocabularies or discovering movies by them requires no authentication, account, or credential.
- **BR-2 (Read-only):** This use case only observes; it never changes catalog data.
- **BR-3 (Genre is a controlled vocabulary):** **Genres** are a curated, controlled taxonomy — a fixed, catalog-wide set of categories, not free text. Listing genres returns that whole set. (Filtering movie *search* by genre already exists — UC-002, BR-5 — and is not redefined here.)
- **BR-4 (Keyword is free-form):** **Keywords** are free-form topical tags used for discovery; a movie may carry many. Unlike genres, keywords are open-ended and potentially numerous, so listing keywords is paged (default portion size 20, maximum 100), not returned whole.
- **BR-5 (Keyword discovery is a movie-search filter):** Discovering movies by a keyword is achieved by **adding a keyword filter to movie search (UC-002)** — not a separate endpoint. The keyword filter matches against a movie's keyword tags and **combines conjunctively** with every other search criterion (title, genre, year, rating) and, when more than one keyword is supplied, a movie must carry **all** of them — mirroring how multiple genres behave (UC-002, BR-5). Results are **movie summaries**, paged and sorted exactly as movie search, each navigable to that movie's full detail (UC-001).
- **BR-6 (Unmatched keyword is success, never *not found*):** A keyword that matches no movies — because it tags nothing, or because no such keyword exists in the free-form vocabulary — yields a normal **empty success** (total zero), consistent with an unmatched genre value in search. Keywords, being free-form, carry no controlled-vocabulary validation, so there is no *not found* or *bad request* for an unrecognised keyword.
- **BR-7 (Invalid criteria are a bad request):** An unsupported sort field or an out-of-bounds portion is rejected as a *bad request* before any matching, exactly as in movie search — distinct from a valid request that matches nothing [BR-8].
- **BR-8 (Empty result is success):** An empty vocabulary, or a valid keyword-filtered search that matches no movies, is a normal successful outcome presenting an empty result — **not** an error.

## Non-goals
- Redefining movie **search** beyond **adding the keyword filter** — the title/genre/year/rating filters, sorting, and pagination are UC-002 and unchanged.
- Managing or editing the genre/keyword vocabularies (curation is out-of-band).
- Presenting people, credits, ratings, or reviews.
- Any create, update, or delete of catalog data.

## Resolved decisions (settled with the author)
- **Keyword discovery = a filter on movie search, not a separate endpoint** [BR-5], consistent with genre. Delivered as an additive change to the existing movie search capability (UC-002 / CAT-002).
- **Multiple keywords combine conjunctively** — a movie must carry all supplied keywords — matching multi-genre semantics [BR-5].
- **An unmatched or non-existent keyword is a normal empty success**, never *not found* or *bad request* [BR-6], because keywords are free-form.
- **Genre listing is in scope** (a small, controlled, catalog-wide set — directly useful for building filter UIs). **Keyword *listing*** (7A for keywords) is the lowest-value, most optional part and may be **deferred** by the architect at plan time — the settled, higher-value behaviour is the keyword *filter* on search (7B) and genre listing.

## Open questions
- **Delivery split only (not a behaviour question):** the architect may split this use case into (a) the keyword filter on movie search and genre listing, and (b) keyword listing — and schedule (b) later or drop it for now. This is a planning decision, not an unresolved behaviour; the behaviour above stands as specified.
