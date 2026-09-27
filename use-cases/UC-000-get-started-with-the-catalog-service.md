# UC-000: Get started with the catalog service

**Primary actor:** API consumer (developer)
**Secondary actors:** None
**Goal:** The API consumer confirms that the catalog service is available and learns, from the service itself, how to use it. They can do this before any catalog data is published.
**Scope:** The catalog service's cross-cutting foundation (the `platform` context): the behaviour every later catalog capability inherits
**Level:** User-goal
**Status:** Draft

## Preconditions
- The catalog service has been started.
- No catalog content (movies, people, …) needs to exist. This use case must be achievable against an
  empty catalog.

## Postconditions
- **Success:** The API consumer knows the service is available. They hold an interface description
  that matches what the service actually does, and they have seen one result that follows the
  service's uniform result conventions. They can begin integrating without contacting the catalog team.
- **Failure:** The API consumer is told, in the service's uniform failure form, why the request could
  not be answered, and is given a correlation id to quote. Nothing in the catalog changes.

## Main flow (basic course of events)
1. The API consumer asks the system whether it is available.
2. The system confirms that it is available [BR-1]. The answer identifies itself so it can be requested
   again [BR-3] and is presented in the uniform result form [BR-2].
3. The API consumer asks the system to describe its interface.
4. The system presents a description of every capability it currently offers, both as a document a
   person can browse and as a description a tool can read [BR-4, BR-5].
5. The API consumer picks the availability check from the browsable description and tries it from there.
6. The system performs the check and shows the result, which matches what the description says it will
   be [BR-4].

## Alternative & exception flows
- **1a. The API consumer asks for something the system does not offer:** The system reports, in the
  uniform failure form, that nothing exists there [BR-2]. The flow ends in the failure postcondition.
- **1b. The API consumer attempts to change something:** The system refuses, in the uniform failure
  form, and nothing changes [BR-2, BR-7]. The flow ends in the failure postcondition.
- **2a. The system cannot answer because of an unexpected internal fault:** The system reports a
  general failure in the uniform failure form. The report includes a correlation id and reveals no
  internal detail [BR-2]. The API consumer may retry later. The flow ends in the failure postcondition.
- **4a. A tool (not a person) asks for the interface description:** The system provides the
  tool-readable description, which has the same content as the browsable one [BR-4]. The flow continues
  at step 5 (or ends if the tool only needed the description).
- **6a. The result does not match the description:** This is a defect, not an allowed outcome. The
  description and the actual behaviour must always agree [BR-4].

## Business rules
- **BR-1: Availability is public and harmless.** Anyone can check availability without identifying
  themselves or presenting credentials. Checking never changes anything. Availability is confirmed
  even when the catalog is empty.
- **BR-2: Uniform results.** Every answer the system gives, on every capability, follows one uniform
  form:
  - A **successful** answer carries the requested information together with contextual details:
    when it was produced, and the request's correlation id.
  - A **failed** answer describes the failure in one consistent, readable form: what kind of problem
    it was, a short explanation, and the correlation id. It never reveals internal detail.
  - "Nothing exists here", "you asked in a way that isn't allowed" and "something went wrong on our
    side" are reported as distinct kinds of failure.
- **BR-3: Results are self-identifying and navigable.** Every result tells the consumer where it can be
  requested again. Where a result relates to other information, it tells the consumer where to find
  that information. Where a result is one page of a larger list, it points to the first, previous,
  next and last pages as applicable. These pointers are for navigation only. They never offer an
  action that changes anything.
- **BR-4: The interface describes itself, accurately and consistently.** The system publishes a
  description of every capability it offers. The description and the actual behaviour must always
  agree. Concepts common to many capabilities, such as the uniform result form, the failure form and
  how a list is paged, are defined once and described identically wherever they appear. Each
  capability does not reinvent them.
- **BR-5: The description is open and hands-on.** Anyone can read the interface description without
  credentials. The browsable form lets a person try a capability against the running service and see
  the real result.
- **BR-6: The service can be evaluated standalone.** The service can be started and used without
  provisioning any external infrastructure. In that standalone mode, catalog data is **not** retained
  across restarts. A persistent mode, suitable for real operation, also exists, and the service behaves
  identically in both modes.
- **BR-7: Read-only.** Nothing the API consumer does through the service ever changes catalog data
  (see `domain/business-rules.md`).

## Non-goals
- Any catalog content (movies, people, credits, genres, reviews). Those are later use cases, which
  inherit BR-2 to BR-4.
- Reporting the service's version, build or deployment details on the availability answer.
- Authentication, accounts or API keys (the service is public).
- Rate limiting of the availability check or the description. Limits and where they are enforced are
  still a TODO in `domain/business-rules.md`.
- Operational monitoring, alerting or dashboards for the catalog team.

## Open questions (need a human decision before/at Gate 1)
- **Readiness vs liveness:** Should "available" (step 2) mean only "the service is running", or also
  "the service can reach its catalog data"? If the latter, 2a gains a distinct "temporarily
  unavailable" outcome. *Suggested default:* running only. A readiness notion can come with the first
  catalog capability.
- **Description in real operation:** Should the browsable, try-it-out description also be offered in
  real (persistent) operation, or only when evaluating standalone? *Suggested default:* always offered,
  consistent with the public posture.
