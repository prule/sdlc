# Glossary — ubiquitous language

One agreed definition per business term. Use these exact terms in tickets, specs, and code so
everyone (and every agent) means the same thing. Add a row whenever a new term appears.

| Term | Definition | Notes / synonyms to avoid |
|------|------------|---------------------------|
| User account | A registered identity that can authenticate: unique email, credential (password hash), status. | Not "user profile" (profile data is separate). |
| Password policy | The configurable rules a new password must satisfy (e.g. min length, complexity) before it is accepted. | — |
| Reset token | A single-use, time-limited secret that lets a user set a new password. | Introduced by the password-reset feature. |
| Correlation id | A UUID attached to every request/response and log line to trace one request end-to-end. | Technical, but appears in tickets' NFRs. |
| TODO | TODO: add real business terms — this is where domain language lives. | — |

> Keep definitions business-facing. Purely technical terms (Envelope, Problem, bounded context) live
> in `standards/` and the architecture docs, not here — unless the business talks about them.
