# Bounded contexts (subdomains / capabilities)

The system is split into bounded contexts, each owning its own language and rules. A ticket should
name the context it belongs to; new capabilities become `openspec/specs/<context>/<capability>`.
Keep this map current as contexts are added.

## Current

### `platform`
Cross-cutting technical foundation every other context builds on (not a business domain per se).
- **health-check** — liveness/ping endpoint. (`openspec/specs/platform/health-check`)
- **api-codegen** — contract-first OpenAPI bundle→generate pipeline. (`openspec/specs/platform/api-codegen`)

### `identity`
Accounts, credentials, and (later) authentication-adjacent concerns. This service is a JWT
**resource server** — it verifies tokens; an external auth server issues them.
- **user-account** *(planned)* — account aggregate, password policy, password hashing; registration.
- Future: password reset, and integration points for the external auth server.

## Planned / candidate contexts
- TODO: list the real business subdomains (e.g. `ordering`, `billing`, `catalog`, `notifications`).
  For each: its responsibility, the key entities it owns, and how it relates to others
  (upstream/downstream, shared kernel, etc.).

## Context relationships
- TODO: describe how contexts interact (who calls whom, what data crosses boundaries). A simple list
  or a mermaid diagram is fine.
