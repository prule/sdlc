# UC-005: View a person's filmography

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Obtain the movies a known person is credited in — from the *person* side — each annotated with the capacity in which they contributed, seeing a manageable, ordered portion at a time, so the consumer can present a person's filmography in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog contains person and credit data curated out-of-band (this use case only reads it).
- The actor already **knows the identifier of the person** whose filmography they want, typically obtained from that person's details (UC-004) or a people search (UC-006), each of which offers a way to navigate onward to the filmography.

## Postconditions
- **Success:** The actor has been presented with an ordered portion (a page) of the person's filmography entries [BR-3], together with enough information to page through the whole filmography — which portion this is, how large it is, how many entries there are in total, and whether and how to obtain the next and previous portions [BR-6]. Each entry identifies a movie (as a movie summary, navigable to that movie's full detail, UC-001) and the single capacity in which the person contributed to it [BR-3], [BR-4]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed either that **no person with the supplied identifier exists**, or that the identifier is **not a well-formed catalog identifier** — two distinct failures [BR-5] — or that the paging portion they requested is invalid [BR-7]. Nothing is presented and nothing in the catalog has changed [BR-2]. (An existing person with no credits is a **success** with an empty portion, not a failure — see 4a and [BR-8].)

## Main flow (basic course of events)
1. The API consumer asks the system for the filmography of a specific person, identifying the person by their identifier, and may state which portion of the results they want [BR-6].
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied identifier is well-formed, that a person with that identifier exists, and that any requested portion is expressed within the allowed bounds [BR-5], [BR-7].
4. The system gathers every credit recorded for that person across all movies, producing one filmography entry per credited **capacity**: a person credited in more than one capacity on the same movie yields one entry per capacity [BR-4].
5. The system orders the whole filmography — newest movie first, then by title, with a stable tiebreak so the order is total and unchanging across portion boundaries [BR-3] — and selects the single requested portion of that ordered result [BR-6].
6. The system presents that portion as a list of filmography entries — each a movie summary annotated with its one capacity [BR-3], [BR-4] — together with information to page through the whole filmography [BR-6].

## Alternative & exception flows
- **1a. The consumer does not state which portion they want:** the system presents the first portion at the default portion size [BR-6].
- **3a. The supplied identifier is not a well-formed catalog identifier:** the system rejects the request as a *bad request* before any lookup, presents nothing, and informs the consumer the identifier is malformed. Distinct from an unknown person (3b) [BR-5].
- **3b. No person with the supplied (well-formed) identifier exists:** the system rejects the request as *not found*, presents nothing, and informs the consumer the person does not exist. Distinct from an existing person with no credits (4a) [BR-5].
- **3c. The consumer requests an out-of-bounds portion — a negative portion position, or a portion size below one or above the maximum allowed:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer why. Distinct from a valid-but-empty portion beyond the last (5b) [BR-7].
- **4a. The person exists but has no credits recorded at all:** this is a normal success, not a failure. The system presents an empty portion and reports that the total number of entries is zero [BR-8].
- **5a. Several entries share the same movie and ordering values (e.g. one person, two capacities on one movie):** the stable terminal tiebreak keeps their order total and unchanging across portion boundaries, so no entry is ever skipped or duplicated when paging [BR-3].
- **5b. The requested portion lies beyond the last portion:** this is a normal success, not a failure. The system presents an empty portion while still reporting the true total number of entries and the way back to the portions that contain results [BR-8].

## Business rules
- **BR-1 (Public access):** The catalog is public. Viewing a filmography requires no authentication, account, or credential.
- **BR-2 (Read-only):** Viewing a filmography never changes catalog data. This use case only observes.
- **BR-3 (Filmography entry & ordering):** A filmography is presented as a **single list** (not split into acting and non-acting groups). Each entry is a **movie summary** (the UC-002 movie summary — identifier, title, release year, genres, and when present runtime and rating; navigable onward to that movie's full detail) annotated with **one capacity** [BR-4]. The whole filmography is ordered **newest movie first** (by release year, most recent first), then by **title ascending**, then by a **stable terminal tiebreak** so the ordering is total and unchanging across portion boundaries.
- **BR-4 (Capacity; one entry per capacity):** Each entry carries exactly one **capacity** describing how the person contributed to that movie — either an **acting** capacity or a **non-acting** capacity, distinguishable by the consumer. A person credited in several capacities on one movie (e.g. directed and acted in it) appears as **several entries**, one per capacity — never a single entry listing multiple capacities.
- **BR-5 (Identifier handling — malformed vs unknown):** A malformed identifier is a *bad request* rejected before lookup; a well-formed identifier matching no person is *not found*. Two distinct failures (as UC-004).
- **BR-6 (Paging — default and bounds):** A filmography is returned **one portion (page) at a time**, never as one unbounded list — a person may have a long career. A portion has a size; the **default portion size is 20** and the **maximum allowed is 100** (the same convention as movie search, UC-002). When no portion is specified, the first portion at the default size is presented. Alongside any portion the system reports which portion it is, its size, the total number of entries, and whether and how to reach the next and previous portions.
- **BR-7 (Invalid paging is a bad request):** A request whose portion is invalid — a negative position, or a size below one or above the maximum — is rejected as a *bad request* before any gathering. This is distinct from a valid request that yields nothing [BR-8].
- **BR-8 (Empty filmography is success):** A valid request for an existing person who has no credits — or a valid portion beyond the last — is a normal successful outcome presenting an empty portion, **not** an error. The reported total distinguishes "no credits" (total zero) from "a valid but empty portion beyond the last" (total greater than zero).

## Non-goals
- Presenting a movie's credits from the *movie* side (its cast & crew) — that is UC-003, the inverse view.
- Retrieving the person's own core details (UC-004) or searching people (UC-006).
- Retrieving a listed movie's full detail — navigable *from* each entry, but performed by UC-001.
- Filtering a filmography (e.g. only acting credits, or by genre/year) — the filmography is returned as one ordered, paged list with no filter at this level. (See Open questions.)
- Any create, update, or delete of catalog data.

## Open questions (need a human decision before/at Gate 1)
- **Filtering / faceting a filmography:** should a consumer be able to narrow a filmography (e.g. acting only, or by release-year range) as they can narrow movie search, or is a single unfiltered ordered list sufficient for now? Current scope assumes no filter; confirm.
- **Capacity granularity in the summary:** each entry names one capacity as acting vs non-acting [BR-4]; confirm whether the specific role within a non-acting capacity (e.g. "director" vs "composer") is carried on the filmography entry itself, or only reached via the movie's credits (UC-003).
