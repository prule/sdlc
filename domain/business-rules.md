# Business rules & policies

Cross-cutting rules and invariants that hold across features — the kind a ticket must respect and a
reviewer should check against. Feature-specific acceptance criteria live in the ticket; durable
policies live here.

## Identity & accounts
- Email uniquely identifies an account (case-insensitive); no two accounts share an email.
- Passwords are validated against the current password policy before acceptance; plaintext is never
  stored, logged, or returned.
- This service verifies JWTs but never issues them (external auth server owns issuance).

## Data handling / privacy
- TODO: retention rules, PII handling, what may/may not be logged, deletion/GDPR obligations.

## Cross-cutting policies
- TODO: e.g. rate-limiting expectations on abuse-prone endpoints, audit-logging requirements,
  tenancy/isolation rules, money/rounding rules — whatever holds product-wide.

> Add a rule here whenever a ticket or review surfaces a policy that isn't feature-specific.
