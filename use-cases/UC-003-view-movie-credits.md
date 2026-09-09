# UC-003: View a movie's cast and crew

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** Obtain the full list of people who worked on a known movie — its **cast** (performers and the characters they played) and its **crew** (non-acting contributors and their roles) — each presented in a meaningful order, so the consumer can display a movie's credits in their own app.
**Scope:** The movie catalog (the public read API).
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog contains movie data curated out-of-band (this use case only reads it).
- The actor already **knows the identifier of the movie** whose credits they want. That identifier is typically obtained beforehand — from a movie's detail (UC-001) or from a search result (UC-002), each of which offers a way to navigate onward to that movie's credits. Discovering movies is **not** part of this use case.

## Postconditions
- **Success:** The actor has been presented with the movie's credits, separated into two groups: its **cast** and its **crew** [BR-4]. The cast is ordered with the most prominently billed performers first [BR-5]; each cast entry names the performer and the character they portrayed. The crew is grouped and ordered by the area of work and the specific role [BR-6]; each crew entry names the contributor and their role. Every credited person can be navigated onward to that person's own detail [BR-9]. Nothing in the catalog has changed [BR-2].
- **Failure:** The actor is informed that **no movie with the supplied identifier exists** in the catalog; nothing is presented and nothing in the catalog has changed [BR-2], [BR-3]. (An existing movie that simply has no credits recorded is a **success** with empty groups, not a failure — see 4a and [BR-8].)

## Main flow (basic course of events)
1. The API consumer asks the system for the credits of a specific movie, identifying the movie by its identifier.
2. The system accepts the request without requiring the consumer to authenticate or identify themselves [BR-1].
3. The system confirms that a movie with the supplied identifier exists in the catalog [BR-3].
4. The system gathers all of the credits recorded for that movie, separating each into its **cast** (acting) or **crew** (non-acting) shape [BR-4].
5. The system orders the cast by billing, most prominent first [BR-5], and orders the crew by area of work and then specific role [BR-6]. The full set of credits is presented as a whole, not a portion at a time [BR-7].
6. The system presents the movie's cast and crew to the consumer as two distinct groups [BR-4]; each credited person can be navigated onward to that person's own detail [BR-9].

## Alternative & exception flows
- **3a. No movie with the supplied identifier exists:** the system rejects the request as *not found*, presents nothing, and informs the consumer that the movie does not exist. This is distinct from an existing movie that has no credits (4a). The flow ends [BR-3].
- **4a. The movie exists but has no credits recorded at all (neither cast nor crew):** this is a normal success, not a failure. The system presents an empty cast group and an empty crew group [BR-8].
- **4b. The movie has cast but no crew, or crew but no cast:** this is normal. The system presents whichever group has entries and presents the other group as empty [BR-8].
- **6a. A cast entry has no recorded character, or a crew entry's role detail is otherwise incomplete:** this is normal. Each entry presents the details it has recorded; an absent optional detail is simply omitted rather than shown as empty [BR-4]. (See Open questions on whether a missing character name is expected.)
- **6b. A credited person's own detail is not independently addressable yet:** while the catalog does not yet expose people as their own resources, each credited person is still named (identifier and name) but the onward navigation to a standalone person detail is simply absent until that capability exists [BR-9]. (See Open questions.)

## Business rules
- **BR-1 (Public access):** The catalog is public. Viewing a movie's credits requires no authentication, account, or credential; any consumer may make the request.
- **BR-2 (Read-only):** Viewing credits never changes catalog data. This use case only observes.
- **BR-3 (Known movie required; unknown id is *not found*):** This use case operates on a movie the consumer already identifies. A request for an identifier that matches no movie in the catalog is a *not found* outcome — a distinct failure from an existing movie that has no credits [BR-8].
- **BR-4 (Credit shapes — cast vs crew):** Every credit on a movie is exactly one of two shapes, never a single shape carrying both sets of fields:
  - **Cast** (an acting credit): the performer, the **character** they portrayed, and a **billing position** (a whole number where 1 is top billing, ascending thereafter — a lower number is more prominent).
  - **Crew** (a non-acting credit): the contributor, the **area of work** (e.g. directing, writing, music) and the **specific role** within it (e.g. director, screenplay, composer). Both are free text with no controlled vocabulary.
  Cast and crew are presented as two distinct groups, because they carry different details and follow different orderings.
- **BR-5 (Cast ordering):** The cast is ordered by billing position ascending — the most prominently billed performer (billing position 1) first.
- **BR-6 (Crew ordering):** The crew is ordered by area of work and then by specific role, both ascending and compared case-insensitively, so contributors in the same area appear together.
- **BR-7 (Credits presented whole):** A movie's credits are presented **in full, in one response — not one portion (page) at a time**. A movie's cast and crew are small enough to return whole, unlike the movie catalog itself (UC-002) or a person's filmography, which are paged.
- **BR-8 (Empty credits is success):** An existing movie with no cast and/or no crew recorded is a normal successful outcome presenting the corresponding group(s) as empty — **not** a *not found* [BR-3] and not an error.
- **BR-9 (Credited people are navigable):** Each credited person is named by their identifier and name wherever they appear in the credits, and — when the catalog exposes people as their own addressable resources — can be navigated onward to that person's own detail. Until such a capability exists, the person is still named but no onward navigation target is presented [see 6b, Open questions].

## Non-goals
- Presenting a person's **filmography** — the movies a person is credited in, from the *person* side. That is the inverse view and a separate use case.
- Searching or listing **people** as their own resource.
- Retrieving the movie's own detail (UC-001) or searching the catalog (UC-002) — those are the routes by which the consumer arrives here, not performed here.
- Introducing controlled vocabularies for crew area/role, or any create/update/delete of catalog data.

## Open questions (need a human decision before/at Gate 1)
- **Onward navigation to a person's detail** assumes people are independently addressable. In the catalog's **current** state there is no standalone person resource, so onward navigation cannot yet be offered. Decision needed: does UC-003 ship with credited people named-only (identifier + name, no onward link) and gain the link when the people capability lands, or is a minimal person-detail capability a prerequisite delivered alongside it? (See ROADMAP.md — this is the credits → people ordering.)
- **Missing character on a cast credit:** is a cast credit ever recorded without a character (e.g. an uncredited or "as self" appearance), and if so should the character simply be omitted (6a) or is a character always required? Confirm with the domain owner.
