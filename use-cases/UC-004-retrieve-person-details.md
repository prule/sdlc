# UC-004: Retrieve a person's details

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Obtain the core details of a single known person — an individual who worked on movies — and the means to navigate onward to the movies they are credited in, so the consumer can present a person's page in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog contains person data curated out-of-band (this use case only reads it). People exist in the catalog by virtue of being credited on movies.
- The actor already **knows the identifier of the person** they want. That identifier is typically obtained beforehand — from a movie's credits (UC-003), where each credited person is named and navigable, or from a people search (UC-006). Discovering people is **not** part of this use case.

## Postconditions
- **Success:** The actor has been presented with the person's core details — their identifier and name — together with the means to navigate onward to the movies that person is credited in (their filmography, UC-005) [BR-3], [BR-5]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed either that **no person with the supplied identifier exists**, or that the identifier they supplied is **not a well-formed catalog identifier** — two distinct failures [BR-4]. Nothing is presented and nothing in the catalog has changed [BR-2].

## Main flow (basic course of events)
1. The API consumer asks the system for a specific person, identifying the person by their identifier.
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms the supplied identifier is a well-formed catalog identifier [BR-4].
4. The system confirms that a person with that identifier exists in the catalog [BR-4].
5. The system presents the person's core details — identifier and name — together with a way to navigate onward to that person's filmography [BR-3], [BR-5].

## Alternative & exception flows
- **3a. The supplied identifier is not a well-formed catalog identifier:** the system rejects the request as a *bad request* before attempting any lookup, presents nothing, and informs the consumer the identifier is malformed. This is distinct from a well-formed identifier that matches no person (4a). The flow ends [BR-4].
- **4a. No person with the supplied (well-formed) identifier exists:** the system rejects the request as *not found*, presents nothing, and informs the consumer that the person does not exist. The flow ends [BR-4].

## Business rules
- **BR-1 (Public access):** The catalog is public. Retrieving a person's details requires no authentication, account, or credential; any consumer may make the request.
- **BR-2 (Read-only):** Retrieving a person's details never changes catalog data. This use case only observes.
- **BR-3 (Person core details):** A person's details are deliberately minimal: their stable identifier and their name. **No biographical fields** (birth date, biography, images, and the like) are carried — the catalog models a person only as far as is needed to attribute credits and offer navigation. Absent optional details, if any are later added, are omitted rather than shown as empty.
- **BR-4 (Identifier handling — malformed vs unknown):** A value that is not a well-formed catalog identifier is a *bad request*, rejected before any lookup. A well-formed identifier that matches no person is *not found*. These are two distinct, separately reported failures — the same rule the catalog applies to movie identifiers (UC-001).
- **BR-5 (Navigable to filmography):** A person's details always offer a way to navigate onward to the movies that person is credited in — their filmography (UC-005) — assembled from the person's identifier alone, without the details response itself having to enumerate those movies.

## Non-goals
- Enumerating the movies a person is credited in — that is their **filmography** (UC-005), reachable *from* here but not performed here.
- Searching or listing **people** (UC-006).
- Presenting a movie's credits from the *movie* side (UC-003).
- Any create, update, or delete of catalog data.

## Open questions (need a human decision before/at Gate 1)
- **Person identity beyond a name:** the catalog currently models a person as identifier + name only [BR-3]. If two different real people share a name, only the identifier distinguishes them; there is no disambiguating field. Confirm this is acceptable for consumers, or whether a minimal disambiguator is ever needed.
