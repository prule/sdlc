# UC-006: Search and list people

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Find people without already knowing an identifier — browse the people in the catalog and narrow them by name, seeing a manageable, ordered portion at a time — so the consumer can present a browsable/searchable list of people in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog contains person data curated out-of-band (this use case only reads it). People exist by virtue of being credited on movies.
- The actor does **not** need to know any person identifier in advance; discovering people (and their identifiers) is the point of this use case.

## Postconditions
- **Success:** The actor has been presented with an ordered portion (a page) of **person summaries** for the people that match their criteria, together with enough information to page through the full result set — which portion this is, how large it is, how many people match in total, and whether and how to obtain the next and previous portions [BR-4]. Each summary lets the actor navigate onward to that person's full details (UC-004) [BR-3]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed that their request could not be fulfilled because the criteria they supplied are invalid [BR-8]; nothing is presented and nothing in the catalog has changed [BR-2]. (A valid request that matches no people is a **success** with an empty portion — see 4a/4b and [BR-7].)

## Main flow (basic course of events)
1. The API consumer asks the system for people, optionally supplying a **name term** to match [BR-5], stating how the results should be ordered [BR-6], and which portion of the results they want [BR-4].
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied criteria are valid — that any requested ordering is by a supported field, and that the requested portion is within the allowed bounds [BR-6], [BR-8].
4. The system finds the people in the catalog whose name matches the supplied term; when no term is supplied, every person matches [BR-5], [BR-9].
5. The system orders all the matching people — by the ordering the consumer requested, or by the default ordering when none was requested [BR-6] — and selects the single requested portion of that ordered result [BR-4].
6. The system presents that portion to the consumer as a list of **person summaries** [BR-3], together with information to page through the full result set [BR-4].

## Alternative & exception flows
- **1a. The consumer supplies no name term (browse all people):** the flow proceeds normally; every person matches [BR-9] and is ordered by the default ordering [BR-6].
- **1b. The consumer does not state an ordering:** the system applies the default ordering — **by name, ascending** [BR-6].
- **1c. The consumer does not state which portion they want:** the system presents the first portion at the default portion size [BR-4].
- **4a. The name term matches no people:** this is a normal success, not a failure. The system presents an empty portion and reports that the total number of matching people is zero [BR-7].
- **4b. The requested portion lies beyond the last portion:** this is a normal success, not a failure. The system presents an empty portion while still reporting the true total and the way back to the portions that contain results [BR-7].
- **3a. The consumer requests ordering by a field the system does not support:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer the requested ordering is not supported. Distinct from a valid request that matches nothing (4a). The flow ends [BR-8].
- **3b. The consumer requests an out-of-bounds portion — a negative position, or a size below one or above the maximum:** the system rejects the request as a *bad request*, presents nothing, and informs the consumer why. Distinct from a valid-but-empty portion beyond the last (4b). The flow ends [BR-8].

## Business rules
- **BR-1 (Public access):** The catalog is public. Searching or listing people requires no authentication, account, or credential.
- **BR-2 (Read-only):** Searching or listing people never changes catalog data. This use case only observes.
- **BR-3 (Person summary):** A result item is a **person summary** — deliberately minimal: exactly the person's identifier and name, with **no biographical field**. Every summary lets the consumer navigate onward to that person's full details (UC-004).
- **BR-4 (Paging — default and bounds):** Results are returned one portion (page) at a time, never as one unbounded list. A portion has a size; the **default portion size is 20** and the **maximum allowed is 100** (the same convention as movie search, UC-002). When no portion is specified, the first portion at the default size is presented. Alongside any portion the system reports which portion it is, its size, the total number of matching people, and whether and how to reach the next and previous portions.
- **BR-5 (Name filter — the only filter):** People may be narrowed by a single criterion: a **name term**, matched as a **case-insensitive substring** (a person matches if their name contains the term anywhere). There is **no other filter** at the person level — no filter by role, department, known-for, or whether they have credits. Supplying no term matches everyone [BR-9].
- **BR-6 (Sorting — supported field and default):** Results are sortable **only by name**, ascending or descending. When the consumer requests no ordering, the **default ordering is name ascending** — deliberately unlike movie search's release-year-descending default, because a person has no date-like field to default-sort by. Requesting ordering by any other field is invalid [BR-8].
- **BR-7 (Empty result is success):** A valid request that matches no people — whether because the name term excludes everyone, or because the requested portion lies beyond the last — is a normal successful outcome presenting an empty portion, **not** an error. The reported total distinguishes "nobody matches" (total zero) from "a valid but empty portion beyond the last" (total greater than zero).
- **BR-8 (Invalid criteria are a bad request):** A request whose criteria are invalid — an unsupported sort field, a negative portion position, or a portion size below one or above the maximum — is rejected as a *bad request* before any matching. This is a distinct outcome from a valid request that matches nothing [BR-7].
- **BR-9 (Browse with no term):** Supplying no name term is valid; it matches every person in the catalog, then ordered and paged like any other result.

## Non-goals
- Retrieving one known person's full details — that is UC-004 (navigable *from* each summary here, but not performed here).
- Presenting a person's filmography (UC-005) or a movie's credits (UC-003).
- Filtering people by anything other than name (see [BR-5]); no role/department/known-for facets.
- Any create, update, or delete of catalog data.

## Open questions (need a human decision before/at Gate 1)
- **Name-match scope:** the name term matches the person's single stored name as a substring [BR-5]. If names are ever split into given/family parts, or a person has alternate/stage names, confirm whether the match should span those. Current scope assumes one whole-name substring match.
