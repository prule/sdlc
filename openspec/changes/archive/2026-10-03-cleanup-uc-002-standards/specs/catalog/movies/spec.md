## MODIFIED Requirements

### Requirement: Standalone mode offers sample movies
So that an evaluator can see successful answers without curating data, standalone mode (no profile) SHALL start with a small fixed set of sample movies. Their identifiers SHALL be fixed and published in the interface description's operation `description`. The set SHALL include at least:
- `11111111-1111-4111-8111-111111111111`: a movie with runtime, synopsis, rating and at least two genres.
- `22222222-2222-4222-8222-222222222222`: a movie with no runtime, synopsis or rating recorded and no genres.

Persistent mode (`postgres` profile) SHALL NOT contain the sample movies. Its catalog holds only curated data. They SHALL be re-created identically at every standalone start. Acceptance check, primary: a plain unit test loads the configuration without booting the service and makes two assertions. The persistent-mode configuration's schema-migration locations are exactly the schema location, with no sample-data location. The standalone configuration's locations include the sample-data location. Tests that boot against PostgreSQL override the migration locations, so they cannot prove the persistent configuration excludes the samples. Acceptance check, standalone: the single H2 smoke test requests both sample identifiers. It asserts `200`, the presence of all optional members and a non-empty `genres` for the first. It asserts `genres` `[]` and no `runtimeMinutes`, `synopsis` or `rating` for the second. It SHALL NOT assert the order or the values of `genres`: genre ordering is verified against PostgreSQL by the requirement "Genres are listed by name in alphabetical order". Secondary check: the PostgreSQL integration test with the `postgres` profile asserts `404` `NOT_FOUND` for the first sample identifier.

#### Scenario: Fully curated sample movie in standalone mode
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies/11111111-1111-4111-8111-111111111111`
- **THEN** the response status is `200` and `data` contains `runtimeMinutes`, `synopsis`, `rating` and a non-empty `genres` array

#### Scenario: Minimal sample movie in standalone mode
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies/22222222-2222-4222-8222-222222222222`
- **THEN** the response status is `200`, `data.genres` is `[]`, and `data` has no `runtimeMinutes`, `synopsis` or `rating` key

#### Scenario: No sample movies in persistent mode
- **WHEN** the persistent-mode (`postgres` profile) configuration is loaded
- **THEN** its schema-migration locations are exactly `classpath:db/migration` and do not include `classpath:db/demo`, so a client requesting `GET /api/v1/movies/11111111-1111-4111-8111-111111111111` against a curated database without that movie gets `404` `NOT_FOUND`

### Requirement: Title term matches anywhere in the title, ignoring case
When `title` is given and contains at least one non-whitespace character, a movie SHALL match only if the term appears as a contiguous substring anywhere in its title, compared ignoring letter case. The term SHALL be used as supplied, and is not trimmed. The characters `%`, `_` and `\` in the term SHALL be matched literally, never as wildcards. A quote character (`'`) in the term SHALL be matched literally like any other character, and SHALL NOT cause a refusal or a failure. An empty or whitespace-only `title` SHALL be treated as no title criterion, and SHALL NOT be refused. Acceptance check: a Testcontainers-backed test with the movies `The Grand Heist`, `Heist Night`, `Arrival`, `100%_Real` and `Ocean's Eleven` asserts the matches for `heist`, `HEIST`, `rand h`, `%`, `_`, `n's e` and `title=` (blank).

#### Scenario: Case-insensitive substring
- **WHEN** a client sends `GET /api/v1/movies?title=HEIST` while the catalog holds `The Grand Heist`, `Heist Night` and `Arrival`
- **THEN** exactly `The Grand Heist` and `Heist Night` are returned and `totalElements` is `2`

#### Scenario: Substring spanning a space
- **WHEN** a client sends `GET /api/v1/movies?title=rand%20h` while the catalog holds `The Grand Heist`, `Heist Night` and `Arrival`
- **THEN** only `The Grand Heist` is returned

#### Scenario: Wildcard characters are literal
- **WHEN** a client sends `GET /api/v1/movies?title=%25` while the catalog holds `100%_Real` and `Arrival`
- **THEN** only `100%_Real` is returned

#### Scenario: Quote in the term is matched literally
- **WHEN** a client sends `GET /api/v1/movies?title=n's%20e` while the catalog holds `Ocean's Eleven` and `Arrival`
- **THEN** the response status is `200` and only `Ocean's Eleven` is returned

#### Scenario: Blank title term browses
- **WHEN** a client sends `GET /api/v1/movies?title=%20%20`
- **THEN** the response status is `200` and every movie in the catalog matches
