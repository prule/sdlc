# Actors & personas

Who and what the system serves. User stories ("As a &lt;persona&gt; …") must use a persona named here.

## Human / consumer personas
| Persona | Description | Primary goals / what they can do |
|---------|-------------|----------------------------------|
| API consumer (developer) | A third-party developer integrating our catalog into their own app/site. **The main customer.** | Search movies, retrieve movie/person detail, browse genres/keywords, read ratings/reviews — all read-only, over HTTP. |
| End user (indirect) | A person using the consumer's app. Never calls our API directly. | Shapes what consumers need (fast search, rich detail), but is not our direct actor. |
| Curator (internal, out-of-band) | Internal editorial staff who create/maintain the catalog **outside this API**. | Not an actor *of this API* — listed so we remember the data has an owner. Their tooling is out of scope. |

## External systems
| System | Role | Interaction |
|--------|------|-------------|
| API gateway / CDN | Public entry point | Likely fronts the API for TLS, caching, and IP rate limiting. TODO: confirm whether rate limiting lives at the gateway or in-app. |
| (none required for data) | — | Data is curated internally; no external data provider is a dependency. |

> No auth server / identity provider — the read API is public. If an admin surface is added later,
> add its actors and the auth server here at that point.
