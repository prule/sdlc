# UC-002: Search and browse the movie catalog

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Find movies without already knowing an identifier — browse the catalog and narrow it by criteria, seeing a manageable, ordered portion at a time — so the consumer can present a browsable/searchable movie list in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Ready (open questions resolved by the author; see Business rules & Resolved decisions)

## Preconditions
- The catalog contains movie data curated out-of-band (this use case only reads it).
- The actor does **not** need to know any movie identifier in advance; discovering movies (and their identifiers) is the point of this use case.

## Postconditions
- **Success:** The actor has been presented with an ordered portion (a page) of movie summaries for the movies that match their criteria, together with enough information to page through the full result set — which portion this is, how large it is, how many movies match in total, and whether and how to obtain the next and previous portions. Each summary lets the actor navigate onward to that movie's full detail (UC-001). Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed that their request could not be fulfilled because the search criteria they supplied are invalid, and nothing is presented and nothing in the catalog has changed [BR-2], [BR-8]. (A valid request that simply matches no movies is a **success** with an empty portion, not a failure — see 5a/5b and [BR-7].)

## Main flow (basic course of events)
1. The API consumer asks the system for movies, optionally supplying search criteria: a title term to match, and/or one or more filters — genre(s), a release year, and a minimum rating [BR-5]. The consumer may also state how the results should be ordered [BR-6] and which portion of the results they want [BR-4].
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied criteria are valid — that any requested ordering is by a supported field, and that the requested portion is expressed within the allowed bounds [BR-4], [BR-6], [BR-8].
4. The system finds the movies in the catalog that match all of the supplied criteria; the criteria combine to narrow the result — a movie is included only if it satisfies every criterion supplied [BR-5]. When no criteria are supplied, every movie in the catalog matches [BR-9].
5. The system orders all the matching movies — by the ordering the consumer requested, or by the default ordering when none was requested [BR-6] — and selects the single requested portion of that ordered result [BR-4].
6. The system presents that portion to the consumer as a list of **movie summaries** [BR-3], together with information to page through the full result set: which portion this is, how large it is, how many movies match in total, and whether and how to obtain the next and previous portions [BR-4].

## Alternative & exception flows
- **1a. The consumer supplies no criteria (browse the whole catalog):** the flow proceeds normally; every movie matches [BR-9] and is ordered by the default ordering [BR-6]. The consumer is presented with a portion of the whole catalog.
- **1b. The consumer does not state an ordering:** the system applies the default ordering — newest first (release year, most recent first), with title in ascending order as a tiebreak [BR-6].
- **1c. The consumer does not state which portion they want:** the system presents the first portion, sized to the default portion size [BR-4].
- **5a. The criteria match no movies at all:** this is a normal success, not a failure. The system presents an empty portion and reports that the total number of matching movies is zero; there is no next or previous portion to obtain [BR-7].
- **5b. The requested portion lies beyond the last portion of the result set:** this is a normal success, not a failure. The system presents an empty portion while still reporting the true total number of matching movies (which may be greater than zero) and the way back to the portions that do contain results [BR-7].
- **3a. The consumer requests ordering by a field the system does not support:** the system rejects the request as invalid (a bad request), presents nothing, and informs the consumer that the requested ordering is not supported. This is distinct from a valid request that matches nothing (5a). The flow ends [BR-8].
- **3b. The consumer requests an out-of-bounds portion — for example a negative portion position, or a portion size below one or above the maximum allowed:** the system rejects the request as invalid (a bad request), presents nothing, and informs the consumer why. This is distinct from a valid request that matches nothing (5a) and from asking for a valid-but-empty portion beyond the last (5b). The flow ends [BR-8].
- **6a. A matching movie has one or more optional summary details absent (runtime or rating):** this is normal. Each summary presents the movie's required details and only those optional details it has recorded; absent optional details are simply omitted [BR-3].

## Business rules
- **BR-1 (Public access):** The catalog is public. Searching or browsing requires no authentication, account, or credential; any consumer may make the request.
- **BR-2 (Read-only):** Searching or browsing never changes catalog data. This use case only observes.
- **BR-3 (Movie summary):** A search result item is a **movie summary** — a lighter representation than the full movie detail (UC-001). It comprises the movie's identifier, title, release year, and genres, and — when the movie has them recorded — its runtime and aggregate rating. A summary **omits the synopsis**. Every summary lets the consumer navigate onward to that movie's full detail (the UC-001 behaviour). Absent optional details (runtime, rating) are omitted rather than shown as empty.
- **BR-4 (Paging — default and bounds):** Results are returned one portion (page) at a time, never as one unbounded list. A portion has a size; the **default portion size is 20** and the **maximum allowed is 100**. When the consumer does not specify a portion, the first portion at the default size is presented. Alongside any portion the system always reports which portion it is, its size, the total number of matching movies, and whether and how to reach the next and previous portions.
- **BR-5 (Filters combine to narrow):** A search may be narrowed by any combination of: a title term, genre(s), a release-year range, and a minimum rating. These criteria **combine conjunctively** — a movie is included only if it satisfies every criterion supplied. Supplying more criteria can only narrow, never widen, the result. The semantics of each (resolved with the author):
  - **Title term:** a **case-insensitive substring** match — a movie matches if its title contains the term anywhere (e.g. "matrix" matches "The Matrix").
  - **Genre(s):** when more than one genre is supplied, a movie matches only if it carries **all** of them (conjunctive within the genre filter too).
  - **Release year:** a **range** with an optional lower and/or upper bound (either bound may be given alone; a single year is the range from that year to itself). A movie matches if its release year falls within the given bounds.
  - **Minimum rating:** a movie matches only if it has an aggregate rating **at least** the given minimum, expressed on the settled **0–5** scale; a movie with **no** recorded rating is **excluded** when a minimum rating is supplied.
- **BR-6 (Sorting — supported fields and default):** Results are sortable by exactly one of: title, release year, or aggregate rating, in ascending or descending order. When the consumer requests no ordering, the **default ordering is release year descending (newest first), with title ascending as a tiebreak**. Requesting ordering by any other field is invalid [BR-8].
- **BR-7 (Empty result is success):** A valid request that matches no movies — whether because the criteria exclude everything, or because the requested portion lies beyond the last — is a normal successful outcome presenting an empty portion, **not** an error. The reported total number of matching movies distinguishes "nothing matches" (total zero) from "a valid but empty portion beyond the last" (total greater than zero).
- **BR-8 (Invalid criteria are a bad request):** A request whose criteria are themselves invalid — an unsupported sort field, a negative portion position, or a portion size below one or above the maximum — is rejected as a bad request before any matching is attempted. Nothing is presented. This is a **distinct outcome** from a valid request that matches nothing [BR-7].
- **BR-9 (Browse with no criteria):** Supplying no criteria at all is valid; it matches every movie in the catalog, which are then ordered and paged like any other result.

## Non-goals
- Retrieving one known movie's full detail — that is UC-001 (navigable *from* each summary here, but not performed here).
- Presenting a movie's cast and crew / credits, or searching people.
- Listing or browsing the genre or keyword vocabularies themselves (as opposed to filtering movies by genre).
- Any create, update, or delete of catalog data.
- Rate limiting, caching, or other cross-cutting protections of the public API.

## Resolved decisions (settled with the author; also recorded in domain/business-rules.md)
- **Title match:** case-insensitive substring (BR-5).
- **Multiple genres:** a movie must carry **all** supplied genres (BR-5).
- **Release-year filter:** a **range** with optional lower/upper bounds (BR-5).
- **Minimum rating:** inclusive `>=` on the **0–5** scale; **unrated movies are excluded** when a minimum is supplied (BR-5).
- **Rating scale:** the settled **0–5** scale; the stale `minRating=7` example in domain/business-rules.md has been corrected.

## Open questions
- None outstanding.
