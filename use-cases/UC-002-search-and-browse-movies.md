# UC-002: Search & browse movies

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** The API consumer finds the movies in the catalog that match what they are looking for (or browses the whole catalog), one page at a time and in an order they choose, so they can show the movies to their end users and go on to any movie's details.
**Scope:** The movie catalog (the `catalog` context, **movies** capability)
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog service is available (UC-000).
- Nothing else. The API consumer does not need to know any movie identifiers in advance, and the
  catalog may be empty.

## Postconditions
- **Success:** The API consumer has one page of movie summaries matching their criteria, in the order
  they asked for [BR-2, BR-5, BR-6]. They know how many matches there are across all pages, where to
  request this page again, and where the first, previous, next and last pages are, with the same
  criteria and order [BR-7, BR-9]. Each summary points to that movie's details (UC-001) [BR-2]. The
  page may be empty [BR-8]. Nothing in the catalog changes.
- **Failure:** The API consumer is told, in the service's uniform failure form, why the search could not
  be carried out: the search was asked in a way that isn't allowed [BR-10], or the service failed.
  Nothing in the catalog changes.

## Main flow (basic course of events)
1. The API consumer asks the system for movies. They may give any combination of: a title term, one or
   more genres, a release-year range, a minimum rating, an order, a page and a page size. All of them
   are optional.
2. The system checks that the search is asked in an allowed way [BR-10].
3. The system finds every movie in the catalog that meets all the given criteria [BR-3, BR-4]. If no
   criteria are given, every movie matches (browsing).
4. The system puts the matching movies in the requested order, or the default order if none was asked
   for [BR-5].
5. The system takes the requested page of that ordered list [BR-6].
6. The system presents the page to the API consumer: a movie summary for each movie on it [BR-2], the
   total number of matches, and pointers to this page and to the first, previous, next and last pages,
   each keeping the same criteria and order [BR-7, BR-9].

## Alternative & exception flows
- **1a. The API consumer attempts to change, add or remove movies:** The system refuses, in the uniform
  failure form, and nothing changes [BR-11]. The flow ends in the failure postcondition.
- **2a. The search is asked in a way that isn't allowed:** This covers an unsupported order, a page
  before the first, a page size below 1 or above 100, a genre that is not in the curated genre
  vocabulary, a release-year range whose lower bound is after its upper bound, and a minimum rating
  outside 0–5. The system refuses the search as asked in a way that isn't allowed and does not search
  [BR-10]. It reports this in the uniform failure form, saying which part of the request was at fault.
  This is a distinct kind of failure from a search that matches nothing [BR-9]. The flow ends in the
  failure postcondition.
- **3a. No movie meets the criteria (or the catalog is empty):** This is not a failure. The system
  presents an empty page with a total of zero, still pointing to where it can be requested again
  [BR-8]. The flow ends in the success postcondition.
- **3b. The system cannot search because of an unexpected internal fault:** The system reports a
  general failure in the uniform failure form, with a correlation id and no internal detail [BR-9].
  The API consumer may retry later. The flow ends in the failure postcondition.
- **3c. A minimum rating is given:** Movies with no recorded rating are left out, because they have not
  shown they meet the minimum [BR-4]. The flow resumes at step 4.
- **4a. The order is by rating and some matching movies have no recorded rating:** Unrated movies come
  after all rated movies, whether the order is ascending or descending [BR-5]. The flow resumes at step 5.
- **5a. The requested page is after the last page:** This is not a failure. The system presents an
  empty page that still gives the total number of matches and points to the first and last pages
  [BR-6, BR-8]. The flow ends in the success postcondition.
- **6a. A matching movie has no runtime or rating recorded, or belongs to no genres:** It is still
  presented. The missing details are shown as absent, never as invented or zero values, and its
  genres are an empty set [BR-2]. The flow resumes at step 6.

## Business rules
- **BR-1: Read-only and public.** Anyone can search the catalog without identifying themselves or
  presenting credentials.
- **BR-2: What a movie summary contains.** Each result is a movie summary: identifier, title, release
  year and genres (alphabetical by name), plus runtime in minutes and aggregate rating (0–5 stars, score
  only) when recorded. It has **no synopsis**, keywords, credits or reviews. A detail that has not been
  recorded is shown as absent, never as a made-up value. Each summary points to where that movie's
  details can be found (UC-001).
- **BR-3: Title matching.** The title term matches a movie if it appears anywhere in the movie's title,
  ignoring letter case. An empty or blank title term counts as no title criterion (browsing), not as a
  refusal.
- **BR-4: Filters narrow; all criteria combine.** A movie must meet every given criterion to match.
  - **Genres:** When several genres are given, a movie must carry **all** of them. Genres are the
    curated controlled vocabulary, and a genre is recognised ignoring letter case ("drama" means
    Drama).
  - **Release year:** a range with an optional lower bound, an optional upper bound, or both, and both
    bounds count. A single year means only that year.
  - **Minimum rating:** inclusive, on the 0–5 scale. Movies with no recorded rating are left out.
- **BR-5: Ordering.** Results can be ordered by title, release year or rating, ascending or descending.
  The **default order is release year, newest first, then title A–Z**. After any order there is always
  a final tiebreak, so the order is complete and stable. The tiebreak only has to be stable. It is not
  something the consumer can choose or should rely on. The same search always lists the same movies
  in the same order, and no movie appears on two pages or is skipped between pages. When ordering by
  rating, movies with no recorded rating always come last.
- **BR-6: Paging.** Results come a page at a time, **20 per page by default, at most 100**. Pages are
  counted from the first page. A page after the last is empty, not refused. Every page gives the total
  number of matches across all pages, so the consumer always knows where the last page is.
- **BR-7: Navigable, criteria-preserving pages.** Every page says where it can be requested again and
  points to the first, previous, next and last pages (where they exist). Each pointer keeps the same
  criteria, order and page size. The pointers only move around the results; they never offer an action
  that changes anything. (Establishes the paging convention that later list capabilities reuse.)
- **BR-8: No matches is a success.** A valid search that matches nothing, or a page after the last, is a
  normal result with no entries. It is never "not found".
- **BR-9: Uniform, distinct results.** Success and failure answers follow the uniform result and
  failure forms established in UC-000. "Asked in a way that isn't allowed", "matched nothing" (a
  success) and "something went wrong on our side" are distinct outcomes.
- **BR-10: What counts as a search asked in a way that isn't allowed.** Any of the following causes a
  refusal before any searching: an unsupported order, a page before the first, a page size below 1 or
  above 100, a genre not in the curated vocabulary, a release-year range whose lower bound is after its
  upper bound, or a minimum rating outside 0–5.
- **BR-11: Nothing changes.** Searching never changes catalog data. Any attempt to change it is refused.

## Non-goals
- Retrieving a single movie's full details (UC-001). Search only points to them.
- Filtering by keyword (UC-007), or by cast, crew or person (people track, UC-003…UC-006).
- Searching by anything other than title (for example synopsis or free text) and fuzzy, misspelling or
  relevance-ranked matching.
- "Any of these genres" matching. Several genres always mean "all of them".
- Browsing or listing the genre vocabulary itself (UC-007).
- Localised or translated titles.
- Rate limiting (still a TODO in `domain/business-rules.md`).
- Creating, correcting or removing movies. Curation happens out-of-band.

## Open questions (need a human decision before/at Gate 1)
None. The earlier questions were resolved with their suggested defaults (2026-09-28):
- Genres are recognised ignoring letter case [BR-4].
- An empty or blank title term means no title criterion [BR-3].
- Every page gives the total number of matches [BR-6], which is fine for a curated catalog of modest size.
- The final tiebreak only has to be stable. The consumer doesn't rely on it, and the architect chooses it [BR-5].
