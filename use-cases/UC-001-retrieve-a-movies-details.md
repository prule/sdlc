# UC-001: Retrieve a movie's details

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** The API consumer gets the full, curated details of one specific movie they already know how to identify, so they can show it to their own end users.
**Scope:** The movie catalog (the `catalog` context, **movies** capability)
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog service is available (UC-000).
- The API consumer holds the catalog identifier of the movie they want. How they came by it (from a
  search result, from their own records) is outside this use case.

## Postconditions
- **Success:** The API consumer has the movie's details as currently curated [BR-2]. They know where to
  request the same details again [BR-6]. Nothing in the catalog changes.
- **Failure:** The API consumer is told, in the service's uniform failure form, why the details could
  not be provided: the identifier was not a valid catalog identifier, no such movie exists, or the
  service failed. Nothing in the catalog changes.

## Main flow (basic course of events)
1. The API consumer asks the system for the details of a movie, giving its catalog identifier.
2. The system checks that the identifier is a well-formed catalog identifier [BR-1].
3. The system finds the movie in the catalog [BR-1].
4. The system presents the movie's details to the API consumer [BR-2, BR-3, BR-4, BR-5]. The result
   uses the uniform result form and identifies where it can be requested again [BR-6].

## Alternative & exception flows
- **1a. The API consumer attempts to change the movie (or add or remove one):** The system refuses, in
  the uniform failure form, and nothing changes [BR-7]. The flow ends in the failure postcondition.
- **2a. The identifier is not a well-formed catalog identifier:** The system rejects the request as
  asked in a way that isn't allowed. It does not look anything up [BR-1]. It reports this in the
  uniform failure form, as a distinct kind of failure from "no such movie" [BR-6]. The flow ends in the
  failure postcondition.
- **3a. No movie in the catalog has that identifier:** The system reports that no such movie exists, in
  the uniform failure form [BR-6]. It never presents an empty or placeholder movie. The flow ends in the
  failure postcondition.
- **3b. The system cannot find the movie because of an unexpected internal fault:** The system reports
  a general failure in the uniform failure form, with a correlation id and no internal detail [BR-6].
  The API consumer may retry later. The flow ends in the failure postcondition.
- **4a. The movie has no synopsis, runtime or rating recorded yet:** The system still presents the
  movie. The missing details are shown as absent. They are never shown as invented or zero values
  (for example, a runtime of 0 or a rating of 0 stars) [BR-3]. The flow ends in the success
  postcondition.
- **4b. The movie belongs to no genres:** The system still presents the movie, with an empty set of
  genres [BR-3, BR-4]. The flow ends in the success postcondition.

## Business rules
- **BR-1: Movies are identified by a stable, opaque catalog identifier.** Each movie has exactly one
  identifier. It never changes and reveals nothing about how the catalog is stored. A value that is
  not a well-formed identifier at all is a different outcome from a well-formed identifier that
  matches no movie. The first is rejected before any lookup (see `domain/business-rules.md`).
- **BR-2: What a movie's details contain.** The details always include the movie's identifier, title,
  release year and genres. They also include its runtime (in minutes), synopsis and aggregate rating
  when the curator has recorded them. Keywords, cast and crew, and reviews are **not** part of a
  movie's details.
- **BR-3: Optional details may be missing.** A movie without a synopsis, runtime or rating is still a
  valid movie and is still presented in full. A detail that has not been recorded is shown as absent,
  never as a made-up or default value.
- **BR-4: Genres are presented in alphabetical order** by genre name. The same movie therefore always
  lists its genres in the same order. Genres come from the curated controlled vocabulary.
- **BR-5: The rating is the curated aggregate score on a 0–5 star scale.** It is a single score only;
  no count of votes is shown. It is curated, never submitted by API consumers.
- **BR-6: Uniform, self-identifying results.** Success and failure answers follow the uniform result
  and failure forms established in UC-000 (BR-2, BR-3 there). "Not a valid identifier", "no such movie"
  and "something went wrong on our side" are distinct kinds of failure.
- **BR-7: Read-only and public.** Anyone can retrieve a movie's details without identifying themselves
  or presenting credentials. Nothing the API consumer does here changes catalog data.

## Non-goals
- Finding or browsing movies by title, genre, year or rating (movie search is a separate use case).
- A movie's cast and crew, its keywords, and its curated reviews. These are separate capabilities.
  Pointing from a movie's details to them is added when those capabilities exist.
- Retrieving several movies in one request.
- Localised or translated titles and synopses.
- Rate limiting (still a TODO in `domain/business-rules.md`).
- Creating, correcting or removing movies. Curation happens out-of-band.

## Open questions (need a human decision before/at Gate 1)
- **Release year always known?** BR-2 treats the release year as always present, matching the glossary.
  Can the curated catalog hold a movie whose release year is not yet known (for example, an announced
  movie)? *Suggested default:* no; every movie in the catalog has a release year.
- **Genre display name vs identity:** BR-4 orders genres by name. Is a genre shown to the consumer by
  its name alone, or does it also carry its own identity (so it can later be used to browse that
  genre)? *Suggested default:* name alone for now; revisit with the genres-keywords capability.
