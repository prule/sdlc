## Purpose

Lets a user who has forgotten their password request a reset link by email and set a new
password using a single-use, time-limited token, without revealing whether an account exists.

## ADDED Requirements

### Requirement: Request a password reset without account enumeration

The system SHALL accept a password-reset request containing an email address and SHALL
return the same successful, neutral response regardless of whether an account exists for that
email. The response MUST NOT differ in status code, body, or observable timing in a way that
reveals account existence.

Acceptance check: submit a request for a known email and an unknown email; assert identical
HTTP status (202) and identical response body for both.

#### Scenario: Email belongs to an existing account
- **WHEN** a client submits a reset request for an email with a matching account
- **THEN** the system responds 202 with a neutral confirmation message
- **AND** a reset email is dispatched via the outbound mail port

#### Scenario: Email has no matching account
- **WHEN** a client submits a reset request for an email with no matching account
- **THEN** the system responds 202 with the identical neutral confirmation message
- **AND** no reset email is dispatched

#### Scenario: Malformed email is rejected
- **WHEN** a client submits a reset request whose email fails format validation
- **THEN** the system responds 422 with an RFC 7807 problem detail and code `VALIDATION_FAILED`

### Requirement: Issue a single-use, time-limited reset token

When an account exists, the system SHALL generate a cryptographically random reset token,
persist only a hash of it (never the raw value), associate it with the account, and set it to
expire 30 minutes after issuance. The raw token SHALL appear only in the emailed reset link.

Acceptance check: after a request for a known account, assert a token record exists with an
expiry 30 minutes in the future and that the stored value is a hash, not the emailed token.

#### Scenario: Token is created with a 30-minute expiry
- **WHEN** a reset is requested for an existing account
- **THEN** a token record is stored with `expiresAt` = issuance time + 30 minutes
- **AND** the persisted token value is a hash, not the raw token

#### Scenario: Raw token is never persisted or logged
- **WHEN** a reset token is issued
- **THEN** the raw token does not appear in the database or in any log line

### Requirement: Complete a reset with a valid token

The system SHALL allow a user to set a new password by submitting a valid, unexpired,
unused token together with the new password. On success it SHALL update the account password
(hashed) and mark the token consumed.

Acceptance check: request a reset, extract the raw token, submit it with a compliant password;
assert 200 and that the account password hash changed.

#### Scenario: Valid token and compliant password
- **WHEN** a user submits an unexpired, unused token with a policy-compliant new password
- **THEN** the system updates the account's password hash
- **AND** responds 200 with a neutral success confirmation

#### Scenario: New password violates the password policy
- **WHEN** a user submits a valid token with a password that fails the password policy
- **THEN** the system responds 422 with code `VALIDATION_FAILED` and does not change the password
- **AND** the token remains usable

### Requirement: Reset tokens are single-use and time-limited

The system SHALL reject a token that has already been used, has expired, or does not exist,
and SHALL make no change to any account password in those cases. A token MUST NOT be reusable
after a successful completion.

Acceptance check: complete a reset once (assert 200), then replay the same token and assert
the second attempt is rejected with no password change.

#### Scenario: Token reuse after successful completion
- **WHEN** a user submits a token that was already consumed by a prior successful reset
- **THEN** the system responds 400 with code `RESET_TOKEN_INVALID` and does not change the password

#### Scenario: Expired token
- **WHEN** a user submits a token whose `expiresAt` is in the past
- **THEN** the system responds 400 with code `RESET_TOKEN_EXPIRED` and does not change the password

#### Scenario: Unknown or malformed token
- **WHEN** a user submits a token that matches no stored token
- **THEN** the system responds 400 with code `RESET_TOKEN_INVALID` and does not change the password

### Requirement: Rate-limit reset requests per email and per IP

The system SHALL rate-limit password-reset requests by both requesting email and client IP.
When a limit is exceeded, the system SHALL reject the request without issuing a token or email,
and the rejection MUST remain enumeration-neutral (no account existence disclosure).

Acceptance check: exceed the configured per-email (or per-IP) threshold within the window;
assert the over-limit request returns 429 and no additional token/email is produced.

#### Scenario: Per-email limit exceeded
- **WHEN** requests for the same email exceed the configured threshold within the window
- **THEN** the system responds 429 with code `RATE_LIMITED` and issues no token or email

#### Scenario: Per-IP limit exceeded
- **WHEN** requests from the same client IP exceed the configured threshold within the window
- **THEN** the system responds 429 with code `RATE_LIMITED` and issues no token or email

### Requirement: Audit-log reset request and completion events

The system SHALL write an audit-log entry for every reset request and every completion
outcome. Entries MUST include a correlation id, a timestamp, the outcome, and the account id
when known, and MUST NOT contain the raw token, the password, or full PII.

Acceptance check: drive each outcome (requested, completed, invalid-token, expired-token,
rate-limited) and assert a corresponding audit entry exists containing the outcome and
correlation id and containing neither the raw token nor the password.

#### Scenario: Request event is audited
- **WHEN** a reset request is processed (for existing or non-existing account)
- **THEN** an audit entry is recorded with outcome `RESET_REQUESTED` and a correlation id

#### Scenario: Completion outcomes are audited
- **WHEN** a completion attempt succeeds or fails (invalid, expired, or policy violation)
- **THEN** an audit entry is recorded with the specific outcome and correlation id
- **AND** the entry contains neither the raw token nor the new password
