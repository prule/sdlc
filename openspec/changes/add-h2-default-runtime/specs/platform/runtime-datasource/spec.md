## Purpose

Defines how the application selects its runtime database so it can be demonstrated with zero external
dependencies by default, while preserving PostgreSQL as the tested and production-capable target and
guaranteeing tests never silently run against the in-memory engine.

## ADDED Requirements

### Requirement: Default runtime uses a zero-dependency in-memory database

With no Spring profile active, the system SHALL start using an in-memory database that requires no
external process (no Docker, no PostgreSQL install). On startup it SHALL apply the complete Flyway
migration set successfully and SHALL load the demo dataset, so the documented read endpoints return
populated results immediately. The in-memory database SHALL be reset on each restart (not persisted to
disk).

Acceptance check: with no profile active and no PostgreSQL running, start the application (e.g.
`./gradlew bootRun`) or load the default-profile application context without Testcontainers; assert the
context starts, Flyway reports all migrations applied, and `GET /api/v1/movies` and `GET /api/v1/people`
each return `200` with a non-empty `data` collection.

#### Scenario: Default boot with no external database
- **WHEN** the application starts with no profile active and no external PostgreSQL available
- **THEN** it starts successfully on the in-memory database
- **AND** all Flyway migrations apply without error
- **AND** the demo dataset is loaded

#### Scenario: Documented read endpoints are populated at first boot
- **WHEN** the application has started on the default in-memory database
- **AND** a client issues `GET /api/v1/movies` and `GET /api/v1/people`
- **THEN** each responds `200` with a non-empty `data` collection

#### Scenario: In-memory database resets on restart
- **WHEN** the application is restarted on the default in-memory database
- **THEN** the database is empty of prior-run data before migration and seeding
- **AND** it is re-migrated and re-seeded to the same demonstrable baseline

### Requirement: PostgreSQL is selectable via a profile with unchanged behaviour

The system SHALL provide a dedicated profile that selects a PostgreSQL datasource. When that profile is
active, the system SHALL behave exactly as it did before this change — honouring the same datasource
environment-variable overrides for URL, username, and password — and SHALL run the same Flyway migration
set against PostgreSQL. The HTTP contract, success envelope, HAL links, and observable behaviour SHALL be
unchanged regardless of which datasource is active.

Acceptance check: activate the PostgreSQL profile against a real PostgreSQL, overriding the datasource via
the documented environment variables; assert the application starts, Flyway migrations apply, and the API
responses are identical in shape to the default-profile responses; confirm `GeneratedApiCodegenTest` and
the existing contract/behaviour tests remain green.

#### Scenario: PostgreSQL profile runs against real PostgreSQL
- **WHEN** the PostgreSQL profile is active and the datasource env-vars point at a real PostgreSQL
- **THEN** the application starts and Flyway migrations apply against that PostgreSQL
- **AND** the datasource URL/username/password env-var overrides are honoured

#### Scenario: Behaviour is identical across datasources
- **WHEN** the same request is served under the default in-memory datasource and under the PostgreSQL profile
- **THEN** the HTTP status, success envelope, and HAL links are the same
- **AND** the API contract and generated code are unchanged

### Requirement: Tests always run against PostgreSQL, never the in-memory engine

Automated tests SHALL execute against real PostgreSQL via Testcontainers. The in-memory runtime default
SHALL NOT be used as the datasource for any test. This guarantee SHALL be enforced by an explicit guard:
a test that asserts the active test datasource is PostgreSQL, so that any accidental fallback to the
in-memory engine fails the build loudly. The demo seed SHALL NOT run under the test datasource.

Acceptance check: run the full test suite; assert the guard test passes by confirming the live datasource
reports PostgreSQL (e.g. connection metadata product name `PostgreSQL` or the resolved Hibernate dialect is
the PostgreSQL dialect); confirm no test's datasource resolves to the in-memory engine and that the demo
seed bean is not active under the test datasource.

#### Scenario: Guard fails if the in-memory engine leaks into tests
- **WHEN** a database-backed test runs
- **THEN** the active datasource is PostgreSQL (verified by the guard assertion)
- **AND** if the datasource were the in-memory engine instead, the guard test fails

#### Scenario: Demo seed does not run under the test datasource
- **WHEN** the test context starts
- **THEN** the demo seed does not populate the test database
- **AND** tests supply and assert against their own fixtures
