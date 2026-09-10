# UC-005: View a person's filmography

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Obtain the movies a known person is credited in — from the *person* side — each annotated with the capacity in which they contributed, optionally narrowed by capacity or release year, seeing a manageable, ordered portion at a time, so the consumer can present a person's filmography in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Ready (open questions resolved by the author; see Resolved decisions)

## Preconditions
- The catalog contains person and credit data curated out-of-band (this use case only reads it).
- The actor already **knows the identifier of the person** whose filmography they want, typically obtained from that person's details (UC-004) or a people search (UC-006), each of which offers a way to navigate onward to the filmography.

## Postconditions
- **Success:** The actor has been presented with an ordered portion (a page) of the person's filmography entries [BR-3], together with enough information to page through the whole filmography — which portion this is, how large it is, how many entries there are in total, and whether and how to obtain the next and previous portions [BR-6]. Each entry identifies a movie (as a movie summary, navigable to that movie's full detail, UC-001) and the single capacity in which the person contributed to it [BR-3], [BR-4]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed either that **no person with the supplied identifier exists**, or that the identifier is **not a well-formed catalog identifier** — two distinct failures [BR-5] — or that the paging portion they requested is invalid [BR-7]. Nothing is presented and nothing in the catalog has changed [BR-2]. (An existing person with no credits is a **success** with an empty portion, not a failure — see 4a and [BR-8].)

## Main flow (basic course of events)
1. The API consumer asks the system for the filmography of a specific person, identifying the person by their identifier, and may narrow the result by **capacity** (acting or non-acting) and/or by a **release-year range** [BR-9], state which portion of the results they want [BR-6], and how the results should be ordered [BR-3].
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied identifier is well-formed, that a person with that identifier exists, and that any requested filter, ordering, and portion are valid and within the allowed bounds [BR-5], [BR-7], [BR-9].
4. The system gathers every credit recorded for that person across all movies — restricted to those matching the supplied capacity and release-year filters [BR-9] — producing one filmography entry per credited **capacity**: a person credited in more than one capacity on the same movie yields one entry per capacity [BR-4].
5. The system orders the whole filmography — newest movie first, then by title, with a stable tiebreak so the order is total and unchanging across portion boundaries [BR-3] — and selects the single requested portion of that ordered result [BR-6].
6. The system presents that portion as a list of filmography entries — each a movie summary annotated with its one capacity [BR-3], [BR-4] — together with information to page through the whole filmography [BR-6].

## Alternative & exception flows
- **1a. The consumer does not state which portion they want:** the system presents the first portion at the default portion size [BR-6].
- **1b. The consumer supplies no filter:** the flow proceeds normally over the person's whole filmography [BR-9].
- **3d. The consumer supplies an invalid filter — an unrecognised capacity value, or a release-year range whose bounds are malformed:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer why. Distinct from a valid filter that matches nothing (4b) [BR-9].
- **4b. The person exists and has credits, but none match the supplied filter:** this is a normal success — the system presents an empty portion and reports a total of zero for the filtered result [BR-8], [BR-9].
- **3a. The supplied identifier is not a well-formed catalog identifier:** the system rejects the request as a *bad request* before any lookup, presents nothing, and informs the consumer the identifier is malformed. Distinct from an unknown person (3b) [BR-5].
- **3b. No person with the supplied (well-formed) identifier exists:** the system rejects the request as *not found*, presents nothing, and informs the consumer the person does not exist. Distinct from an existing person with no credits (4a) [BR-5].
- **3c. The consumer requests an out-of-bounds portion — a negative portion position, or a portion size below one or above the maximum allowed:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer why. Distinct from a valid-but-empty portion beyond the last (5b) [BR-7].
- **4a. The person exists but has no credits recorded at all:** this is a normal success, not a failure. The system presents an empty portion and reports that the total number of entries is zero [BR-8].
- **5a. Several entries share the same movie and ordering values (e.g. one person, two capacities on one movie):** the stable terminal tiebreak keeps their order total and unchanging across portion boundaries, so no entry is ever skipped or duplicated when paging [BR-3].
- **5b. The requested portion lies beyond the last portion:** this is a normal success, not a failure. The system presents an empty portion while still reporting the true total number of entries and the way back to the portions that contain results [BR-8].

## Business rules
- **BR-1 (Public access):** The catalog is public. Viewing a filmography requires no authentication, account, or credential.
- **BR-2 (Read-only):** Viewing a filmography never changes catalog data. This use case only observes.
- **BR-3 (Filmography entry & ordering):** A filmography is presented as a **single list** (not split into acting and non-acting groups). Each entry is a **movie summary** (the UC-002 movie summary — identifier, title, release year, genres, and when present runtime and rating; navigable onward to that movie's full detail) annotated with **one capacity** [BR-4]. The default order is **newest movie first** (by release year, most recent first), then by **title ascending**, then by a **stable terminal tiebreak** so the ordering is total and unchanging across portion boundaries. The same tiebreak stabilises any other supported ordering.
- **BR-4 (Capacity; one entry per capacity):** Each entry carries exactly one **capacity** describing how the person contributed to that movie. The capacity has two parts: a **coarse type** — **acting** or **non-acting** — which is the dimension the capacity filter uses [BR-9]; and a **display role label** — for an acting capacity the **character** portrayed (omitted when not recorded, per UC-003), for a non-acting capacity the **area of work and specific role** (e.g. "Directing / Director"). A person credited in several capacities on one movie (e.g. directed and acted in it) appears as **several entries**, one per capacity — never a single entry listing multiple capacities. (The full credit detail always remains reachable from the movie side, UC-003.)
- **BR-5 (Identifier handling — malformed vs unknown):** A malformed identifier is a *bad request* rejected before lookup; a well-formed identifier matching no person is *not found*. Two distinct failures (as UC-004).
- **BR-6 (Paging — default and bounds):** A filmography is returned **one portion (page) at a time**, never as one unbounded list — a person may have a long career. A portion has a size; the **default portion size is 20** and the **maximum allowed is 100** (the same convention as movie search, UC-002). When no portion is specified, the first portion at the default size is presented. Alongside any portion the system reports which portion it is, its size, the total number of entries, and whether and how to reach the next and previous portions.
- **BR-7 (Invalid paging is a bad request):** A request whose portion is invalid — a negative position, or a size below one or above the maximum — is rejected as a *bad request* before any gathering. This is distinct from a valid request that yields nothing [BR-8].
- **BR-8 (Empty filmography is success):** A valid request for an existing person who has no credits — or a valid portion beyond the last, or a valid filter that matches nothing — is a normal successful outcome presenting an empty portion, **not** an error. The reported total (of the filtered result) distinguishes "nothing matches" (total zero) from "a valid but empty portion beyond the last" (total greater than zero).
- **BR-9 (Filters — capacity and release-year range):** A filmography may be narrowed by two optional criteria, which combine conjunctively (narrow only):
  - **Capacity:** restrict to **acting** or to **non-acting** entries (the coarse capacity type of [BR-4]). A person's multiple entries on one movie are filtered independently — an acting-only filter keeps only the acting entry.
  - **Release-year range:** the movie's release year within an optional lower and/or upper bound (either bound may be given alone; a single year is that year to itself) — the same range semantics as movie search (UC-002, BR-5).
  Supplying no filter returns the whole filmography. An unrecognised capacity value or a malformed range is a *bad request* [flow 3d]; a valid filter that matches nothing is an empty success [BR-8].

## Non-goals
- Presenting a movie's credits from the *movie* side (its cast & crew) — that is UC-003, the inverse view.
- Retrieving the person's own core details (UC-004) or searching people (UC-006).
- Retrieving a listed movie's full detail — navigable *from* each entry, but performed by UC-001.
- Filtering a filmography by anything **other than** capacity and release-year range [BR-9] — e.g. by genre or title term is out of scope for now.
- Any create, update, or delete of catalog data.

## Resolved decisions (settled with the author)
- **Filmography is filterable by capacity and release-year range** [BR-9] — a consumer can narrow to acting/non-acting and/or a release-year range, combining conjunctively, mirroring movie search's range semantics. Other facets (genre, title) are out of scope for now.
- **Capacity granularity** [BR-4] — each entry carries the **coarse acting/non-acting type** (the filter dimension) **and** a **display role label** (character for acting; area/role for non-acting). The full credit detail always remains reachable from the movie side (UC-003), so the entry stays a lightweight annotated summary.

## Open questions
- None outstanding.
