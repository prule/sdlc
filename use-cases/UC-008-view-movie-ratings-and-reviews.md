# UC-008: View a movie's rating and reviews

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Obtain the curated critical reception of a known movie — its aggregate rating together with the individual written reviews recorded for it — seeing a manageable, ordered portion of the reviews at a time, so the consumer can present a movie's reviews in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog contains movie data — including curated reviews and aggregate ratings — maintained out-of-band (this use case only reads it).
- The actor already **knows the identifier of the movie** whose reviews they want, typically from the movie's detail (UC-001) or a search result (UC-002), each of which surfaces the aggregate rating and offers a way to navigate onward to the reviews.

## Postconditions
- **Success:** The actor has been presented with the movie's **aggregate rating** (when the movie has one) and an ordered portion (a page) of its individual **reviews** [BR-3], [BR-4], together with enough information to page through all the reviews — which portion this is, how large it is, how many reviews there are in total, and whether and how to obtain the next and previous portions [BR-5]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed either that **no movie with the supplied identifier exists**, or that the identifier is **not a well-formed catalog identifier** — two distinct failures [BR-6] — or that the paging portion they requested is invalid [BR-7]. Nothing is presented and nothing in the catalog has changed [BR-2]. (An existing movie with no reviews, or no rating yet, is a **success** — see 4a/4b and [BR-8].)

## Main flow (basic course of events)
1. The API consumer asks the system for the reviews of a specific movie, identifying the movie by its identifier, and may state which portion of the reviews they want [BR-5].
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied identifier is well-formed, that a movie with that identifier exists, and that any requested portion is within the allowed bounds [BR-6], [BR-7].
4. The system gathers the movie's aggregate rating (if it has one) and all the reviews recorded for it [BR-3], [BR-4].
5. The system orders the reviews by a stable, total ordering [BR-4] and selects the single requested portion of that ordered result [BR-5].
6. The system presents the movie's aggregate rating together with that portion of reviews [BR-3], [BR-4], and information to page through all the reviews [BR-5].

## Alternative & exception flows
- **1a. The consumer does not state which portion they want:** the system presents the first portion at the default portion size [BR-5].
- **3a. The supplied identifier is not a well-formed catalog identifier:** the system rejects the request as a *bad request* before any lookup, presents nothing, and informs the consumer the identifier is malformed. Distinct from an unknown movie (3b) [BR-6].
- **3b. No movie with the supplied (well-formed) identifier exists:** the system rejects the request as *not found*, presents nothing, and informs the consumer the movie does not exist. Distinct from an existing movie with no reviews (4a) [BR-6].
- **3c. The consumer requests an out-of-bounds portion — a negative position, or a size below one or above the maximum:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer why [BR-7].
- **4a. The movie exists but has no reviews recorded:** this is a normal success. The system presents the aggregate rating (if any) and an empty portion of reviews, reporting a total of zero [BR-8].
- **4b. The movie exists but has no aggregate rating yet:** this is normal. The system omits the rating rather than showing an empty or zero value, and presents any reviews as usual [BR-3], [BR-8].
- **6a. A review has no score of its own:** this is normal. A review's score is optional; a review without one presents its author and text, with the score simply omitted [BR-4].

## Business rules
- **BR-1 (Public access):** The catalog is public. Viewing a movie's rating and reviews requires no authentication, account, or credential.
- **BR-2 (Read-only):** This use case only observes. Reviews and ratings are **curated**, not submitted by API consumers; there is no way to post, edit, or score via this API.
- **BR-3 (Aggregate rating):** A movie's **rating** is an **aggregate, score-only** value on the settled **0–5 star** scale, curated — with **no vote count** exposed (as decided for movie detail, UC-001). It is the same rating that already surfaces on movie detail and movie summaries; this use case re-presents it as the heading context for the reviews. A movie without a recorded rating simply omits it.
- **BR-4 (Review):** A **review** is a curated written critique associated with a movie, comprising an **author**, the **text** of the critique, and an **optional individual score**. Reviews are served **read-only**. An individual review's score, when present, is on the same **0–5** scale [see Open questions]; when absent it is omitted. Reviews are presented in a **stable, total order** so paging never skips or duplicates one.
- **BR-5 (Paging — default and bounds):** Reviews are returned **one portion (page) at a time**, never as one unbounded list — a popular movie may have many. A portion has a size; the **default portion size is 20** and the **maximum allowed is 100** (the same convention as movie search, UC-002). When no portion is specified, the first portion at the default size is presented. Alongside any portion the system reports which portion it is, its size, the total number of reviews, and whether and how to reach the next and previous portions.
- **BR-6 (Identifier handling — malformed vs unknown):** A malformed identifier is a *bad request* rejected before lookup; a well-formed identifier matching no movie is *not found*. Two distinct failures (as UC-001).
- **BR-7 (Invalid paging is a bad request):** A negative portion position, or a size below one or above the maximum, is rejected as a *bad request* before any gathering — distinct from a valid request that yields no reviews [BR-8].
- **BR-8 (Empty is success):** A valid request for an existing movie that has no reviews, no rating, or both — or a valid portion beyond the last — is a normal successful outcome, **not** an error. The reported total distinguishes "no reviews" (total zero) from "a valid but empty portion beyond the last" (total greater than zero).

## Non-goals
- Submitting, editing, scoring, voting on, or moderating reviews or ratings — the catalog is read-only and curated [BR-2].
- Exposing a vote count or the individual votes behind the aggregate rating [BR-3].
- Aggregating or comparing ratings across movies (e.g. "top rated") — that is a discovery/search concern, not this per-movie view. (Movie search already sorts by rating — UC-002.)
- Retrieving the movie's own detail (UC-001) or its credits (UC-003).
- Any create, update, or delete of catalog data.

## Open questions (need a human decision before/at Gate 1)
- **Review ordering:** what is the default order of reviews — newest first, by score, by author, or curator-defined? A stable total order is required [BR-4]; the specific default is undecided. Is a consumer-chosen sort in scope, or a single fixed order?
- **Review score scale:** is an individual review's optional score on the same 0–5 scale as the aggregate rating [BR-4], or on the source critic's own scale (e.g. a normalised value plus the original)? Confirm.
- **Rating placement:** should the aggregate rating be re-presented on the reviews response at all [BR-3], given it already appears on movie detail (UC-001) — or should this use case return **only** the reviews and leave the rating to detail? A scope/contract decision.
- **Review identity & fields:** do reviews need stable identifiers of their own (e.g. to deep-link a single review), a publication date, or a source/publication attribution field? None is assumed yet.
