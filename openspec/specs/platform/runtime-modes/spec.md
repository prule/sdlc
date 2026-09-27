# runtime-modes Specification

## Purpose

Allows the catalog service to be evaluated standalone, with no external infrastructure, and to be run in a persistent mode for real operation, with identical behaviour in both (UC-000 BR-6).

## Requirements

### Requirement: Standalone mode needs no external infrastructure
When started with no profile active, the service SHALL start and serve every UC-000 behaviour without any external database or other infrastructure. In this mode, catalog data SHALL NOT be retained across restarts. UC-000 stores no data, so non-retention becomes testable only with the first catalog slice that stores data; until then it is guaranteed by configuration (an in-memory database with no file backing), which is checked at Gate 2 review. Acceptance check: the single permitted H2 smoke test boots the application with no profile. It asserts `GET /api/v1/ping` returns `200` and the tool-readable description returns `200`.

#### Scenario: Standalone start answers the availability check
- **WHEN** the service is started with no profile and no database provisioned, and a client sends `GET /api/v1/ping`
- **THEN** the response status is `200` with `data.status` equal to `"UP"`

### Requirement: Persistent mode for real operation
When started with the `postgres` profile, the service SHALL use the configured PostgreSQL database, with its connection supplied through environment variables and no secrets in source. It SHALL serve the same UC-000 behaviours. Acceptance check: a Testcontainers PostgreSQL integration test with the `postgres` profile asserts `/ping`, the tool-readable description, a `404`, a `405` and a `406`.

#### Scenario: Persistent start answers the availability check
- **WHEN** the service is started with the `postgres` profile against a reachable PostgreSQL instance, and a client sends `GET /api/v1/ping`
- **THEN** the response status is `200` with `data.status` equal to `"UP"`

### Requirement: Identical behaviour across modes
For each UC-000 scenario (availability check, both forms of the description, not-found, method-not-allowed, not-acceptable), the status code, `Content-Type` and body structure SHALL be the same in standalone and persistent modes. Only `meta.timestamp` and correlation ids may differ. Acceptance check: the H2 smoke test and the PostgreSQL integration test run the same shared set of assertions.

#### Scenario: Same answers in both modes
- **WHEN** the shared UC-000 assertion set runs against a standalone instance and against a persistent instance
- **THEN** all assertions pass in both
