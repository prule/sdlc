# UC-008: View a movie's reviews

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Obtain the curated written reviews recorded for a known movie, seeing a manageable, ordered portion at a time, so the consumer can present a movie's reviews in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Ready (open questions resolved by the author; see Resolved decisions)

> **Scope (resolved):** this is a **reviews-only** sub-resource. A movie's **aggregate rating** is
> already presented on the movie's detail (UC-001) and on movie summaries (UC-002); it is **not**
> re-presented here [BR-9]. "Ratings & reviews" as a roadmap heading is delivered as: rating on detail
> (already built) + this reviews sub-resource.

## Preconditions
- The catalog contains movie data — including curated reviews — maintained out-of-band (this use case only reads it).
- The actor already **knows the identifier of the movie** whose reviews they want, typically from the movie's detail (UC-001) or a search result (UC-002), each of which offers a way to navigate onward to the reviews.

## Postconditions
- **Success:** The actor has been presented with an ordered portion (a page) of the movie's **reviews** [BR-3], [BR-4], together with enough information to page through all of them — which portion this is, how large it is, how many reviews there are in total, and whether and how to obtain the next and previous portions [BR-5]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed either that **no movie with the supplied identifier exists**, or that the identifier is **not a well-formed catalog identifier** — two distinct failures [BR-6] — or that the paging portion they requested is invalid [BR-7]. Nothing is presented and nothing in the catalog has changed [BR-2]. (An existing movie with no reviews is a **success** with an empty portion — see 4a and [BR-8].)

## Main flow (basic course of events)
1. The API consumer asks the system for the reviews of a specific movie, identifying the movie by its identifier, and may state which portion of the reviews they want [BR-5].
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied identifier is well-formed, that a movie with that identifier exists, and that any requested portion is within the allowed bounds [BR-6], [BR-7].
4. The system gathers all the reviews recorded for that movie [BR-3].
5. The system orders the reviews by the default ordering — most recent first, with a stable tiebreak [BR-4] — and selects the single requested portion of that ordered result [BR-5].
6. The system presents that portion of reviews, together with information to page through all of them [BR-5].

## Alternative & exception flows
- **1a. The consumer does not state which portion they want:** the system presents the first portion at the default portion size [BR-5].
- **3a. The supplied identifier is not a well-formed catalog identifier:** the system rejects the request as a *bad request* before any lookup, presents nothing, and informs the consumer the identifier is malformed. Distinct from an unknown movie (3b) [BR-6].
- **3b. No movie with the supplied (well-formed) identifier exists:** the system rejects the request as *not found*, presents nothing, and informs the consumer the movie does not exist. Distinct from an existing movie with no reviews (4a) [BR-6].
- **3c. The consumer requests an out-of-bounds portion — a negative position, or a size below one or above the maximum:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer why [BR-7].
- **4a. The movie exists but has no reviews recorded:** this is a normal success. The system presents an empty portion and reports a total of zero [BR-8].
- **6a. A review has no score, no publication date, or no source of its own:** this is normal. Each of a review's score, publication date, and source is optional; a review presents its author and text and only those optional details it has recorded, omitting the rest [BR-3].

## Business rules
- **BR-1 (Public access):** The catalog is public. Viewing a movie's reviews requires no authentication, account, or credential.
- **BR-2 (Read-only, curated):** Reviews are **curated**, not submitted by API consumers; there is no way to post, edit, score, vote on, or moderate a review via this API. This use case only observes.
- **BR-3 (Review shape):** A **review** is a curated written critique associated with a movie, comprising:
  - a **stable identifier** — so a single review can be referred to / deep-linked;
  - an **author** (required) and the **text** of the critique (required);
  - an **optional individual score** — on the same **0–5 star** scale as the aggregate rating (UC-001), omitted when the review has none;
  - an **optional publication date** and an **optional source / publication** attribution.
  Absent optional fields are omitted rather than shown empty.
- **BR-4 (Ordering):** Reviews are presented **most recent first** by publication date, with a **stable terminal tiebreak** (e.g. the review identifier) so the order is total and unchanging across portion boundaries — paging never skips or duplicates a review. Reviews without a publication date sort **after** dated ones, kept in the same stable order. Consumer-chosen ordering is **not** offered initially [see Resolved decisions].
- **BR-5 (Paging — default and bounds):** Reviews are returned **one portion (page) at a time**, never as one unbounded list — a popular movie may have many. A portion has a size; the **default portion size is 20** and the **maximum allowed is 100** (the same convention as movie search, UC-002). When no portion is specified, the first portion at the default size is presented. Alongside any portion the system reports which portion it is, its size, the total number of reviews, and whether and how to reach the next and previous portions.
- **BR-6 (Identifier handling — malformed vs unknown):** A malformed identifier is a *bad request* rejected before lookup; a well-formed identifier matching no movie is *not found*. Two distinct failures (as UC-001).
- **BR-7 (Invalid paging is a bad request):** A negative portion position, or a size below one or above the maximum, is rejected as a *bad request* before any gathering — distinct from a valid request that yields no reviews [BR-8].
- **BR-8 (Empty is success):** A valid request for an existing movie that has no reviews — or a valid portion beyond the last — is a normal successful outcome presenting an empty portion, **not** an error. The reported total distinguishes "no reviews" (total zero) from "a valid but empty portion beyond the last" (total greater than zero).
- **BR-9 (Rating is presented elsewhere):** A movie's **aggregate rating** (aggregate, score-only, 0–5, no vote count — decided UC-001) is presented on the movie's **detail** (UC-001) and summaries (UC-002). It is deliberately **not** re-presented on this reviews response; this use case is reviews-only, avoiding duplication of the rating across responses.

## Non-goals
- Submitting, editing, scoring, voting on, or moderating reviews or ratings — the catalog is read-only and curated [BR-2].
- Presenting or recomputing the **aggregate rating** — that lives on movie detail (UC-001) [BR-9].
- Exposing a vote count or the individual votes behind the aggregate rating.
- Aggregating or comparing ratings across movies (e.g. "top rated") — a discovery concern; movie search already sorts by rating (UC-002).
- Retrieving the movie's own detail (UC-001) or its credits (UC-003).
- Any create, update, or delete of catalog data.

## Resolved decisions (settled with the author)
- **Reviews-only sub-resource** [BR-9] — the aggregate rating stays on movie detail (UC-001); the reviews response returns only reviews, no duplicated rating.
- **Default order: most recent first** by publication date, with a stable identifier tiebreak; undated reviews sort after dated ones [BR-4]. **No consumer-chosen sort** initially — may be added later additively.
- **Individual review score** is **optional**, on the **same 0–5 scale** as the aggregate rating [BR-3].
- **Review fields:** a **stable identifier** plus required **author** and **text**, and optional **score**, **publication date**, and **source/publication** [BR-3] — enough to display and deep-link a curated critic review without implying user submission.

## Open questions
- None outstanding. (Data note for the architect: the "most recent first" default assumes publication dates are usually present; if the curated data rarely carries dates, revisit whether a curator-defined order should be the primary sort — the stable-tiebreak rule already keeps paging correct either way.)
