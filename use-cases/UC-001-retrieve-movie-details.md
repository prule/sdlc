# UC-001: Retrieve a movie's details

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Look up one specific movie and see its details, so the consumer can present accurate movie information in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Ready (open questions resolved by the author; see Business rules & Resolved decisions)

## Preconditions
- The catalog contains movie data curated out-of-band (this use case only reads it).
- The actor already knows the identifier of the movie they want (obtained, for example, from a prior search or another catalog response).

## Postconditions
- **Success:** The actor has been presented with the identified movie's detail — its identifier, title, release year, and genres, plus any of the optional details (runtime, synopsis, aggregate rating) that the movie has — and nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed that their request could not be fulfilled — either because no movie in the catalog carries the given identifier, or because what they supplied is not a well-formed movie identifier at all — and nothing in the catalog has changed [BR-2].

## Main flow (basic course of events)
1. The API consumer asks the system for the details of a movie, identifying it by its stable movie identifier [BR-3].
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied identifier is a well-formed movie identifier [BR-3].
4. The system locates the movie carrying that identifier in the catalog.
5. The system presents the movie's detail to the consumer: its identifier, title, release year, and genres (its required details), together with each optional detail — runtime, synopsis, and aggregate rating — that the movie has recorded [BR-4], [BR-5].

## Alternative & exception flows
- **3a. The supplied identifier is not a well-formed movie identifier:** the system does not attempt to locate any movie. It informs the consumer that the request was malformed and cannot be understood, distinguishing this from the case of a well-formed identifier that simply matches no movie (see 4a). The flow ends; nothing in the catalog changes.
- **4a. No movie in the catalog carries the supplied identifier:** the system informs the consumer that the requested movie could not be found. This is reported as a distinct outcome from a malformed request (3a). The flow ends; nothing in the catalog changes.
- **5a. The located movie has one or more optional details absent:** this is a normal success, not a failure. The system presents the movie's required details and only those optional details it has recorded; absent optional details are simply omitted rather than shown as empty or as an error [BR-5].

## Business rules
- **BR-1 (Public access):** The catalog is public. Retrieving a movie's detail requires no authentication, account, or credential; any consumer may make the request.
- **BR-2 (Read-only):** Retrieving a movie's detail never changes catalog data. This use case only observes.
- **BR-3 (Stable, opaque identifier):** A movie is identified by a stable identifier that is unique within the catalog and never exposes internal storage details. A value that does not conform to the shape of a movie identifier is treated as malformed (3a), which is distinct from a well-formed identifier that matches no movie (4a).
- **BR-4 (Required movie details):** Every movie in the catalog has, and is always presented with, these required details: its identifier, title, release year, and its genres — **one or more** controlled-vocabulary categories (a movie always carries at least one genre).
- **BR-5 (Optional movie details):** A movie's runtime, synopsis, and aggregate rating are optional. A movie that lacks any of them is still valid and is presented successfully; an absent optional detail is omitted from what is presented. The aggregate rating, when present, is a curated score on a 0–5 star scale.

## Non-goals
- Searching or listing movies (finding a movie without already knowing its identifier).
- Presenting a movie's cast and crew / credits, or any navigation onward to related resources.
- Any create, update, or delete of catalog data.
- Rate limiting, caching, or other cross-cutting protections of the public API.

## Resolved decisions (settled with the author)
- **Genres are required — one or more.** A movie always carries at least one genre (BR-4); there is no "movie with no genres" case.
- **Malformed identifier and not-found are distinct outcomes.** A value that is not a well-formed movie identifier is rejected as a bad request before any lookup (3a); a well-formed identifier that matches no movie is a not-found outcome (4a) — two different failures (BR-3). This distinction is recorded as a durable rule in domain/business-rules.md.

## Open questions
- None outstanding.
