# Actors & personas

Who and what the system serves. Tickets' user stories ("As a &lt;persona&gt; …") must use a persona
named here. Add external systems too — they shape integration tickets.

## Human personas
| Persona | Description | Primary goals / what they can do |
|---------|-------------|----------------------------------|
| End user | TODO: a person with a user account | TODO: register, sign in (via auth server), manage own data |
| Admin | TODO: internal operator | TODO: … |
| TODO | TODO | TODO |

## External systems
| System | Role | Interaction |
|--------|------|-------------|
| Auth server (external) | Issues/validates JWTs | This service is a resource server: it verifies bearer tokens, never issues them. |
| TODO (e.g. email provider) | TODO | TODO: e.g. sends transactional email (reset links) |
| TODO (e.g. payment gateway) | TODO | TODO |

> A persona/system referenced in a ticket but missing here is a signal to add it.
