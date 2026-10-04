# SDLC — Multi-Agent Software Delivery Pipeline

A spec-driven development setup where specialised Claude Code agents (architect, spec-reviewer,
junior dev, QA, senior dev) take a **use case** through the **OpenSpec** workflow — plan → review →
implement → verify → code review → archive — with human approval gates, cost controls, and full
observability.

The pipeline itself lives in the **[sdlc-pipeline](https://github.com/prule/sdlc-pipeline)** Claude Code
plugin so it can be reused and updated across projects. This repo is the product it builds (a public,
read-only movie catalog API) plus the project-specific inputs the pipeline reads.

> **New here?** Read this file top to bottom once. Day to day, you mostly write a use case, run
> `/sdlc-pipeline:build-use-case`, and answer the two gates.

---

## TL;DR — build a use case

```
/sdlc-pipeline:write-use-case <rough idea>              # draft use-cases/UC-<n>-<slug>.md, review it
/sdlc-pipeline:build-use-case use-cases/UC-<n>-<slug>.md  # run the pipeline, answer the two gates
```

`build-use-case` cuts a `feat/uc-<n>-<slug>` branch from `develop`, drives the agents, and pauses at
**Gate 1** (approve the plan) and **Gate 2** (approve the finished change). Every run ends with a
retrospective in [retrospectives/](retrospectives/) and a session report in `reports/sessions/` —
commit both with the change, then open a PR into `develop`.

---

## Setup — the sdlc-pipeline plugin

The plugin is declared in the checked-in [.claude/settings.json](.claude/settings.json)
(`extraKnownMarketplaces` + `enabledPlugins`). The first time you open the repo in Claude Code and
trust the folder, accept the prompt to install it — or install it yourself:

```
/plugin marketplace add prule/sdlc-pipeline
/plugin install sdlc-pipeline@sdlc-pipeline
```

It provides:

| | |
|---|---|
| **Skills** | `/sdlc-pipeline:write-use-case`, `/sdlc-pipeline:build-use-case`, `/sdlc-pipeline:init`, `session-report` |
| **Agents** | `sdlc-pipeline:` `architect`, `spec-reviewer`, `junior-dev`, `qa`, `senior-dev`, `use-case-writer` |
| **Hooks** | logs every tool call to `logs/pipeline-events.jsonl` (git-ignored) |

How the pipeline works — stages, gates, fix loops, budget caps, retrospectives, running a phase by
hand — is documented in the plugin:
**[docs/pipeline.md](https://github.com/prule/sdlc-pipeline/blob/main/docs/pipeline.md)**.

### What this repo gives the pipeline

The plugin's agents carry roles and judgment; this repo supplies everything project-specific:

| Input | Holds |
|-------|-------|
| [.claude/sdlc-profile.md](.claude/sdlc-profile.md) | The **profile**: verify/codegen commands, implementation rules, task order, and what each review gate checks ([reference](https://github.com/prule/sdlc-pipeline/blob/main/docs/profile.md)) |
| [openspec/config.yaml](openspec/config.yaml) | Design-time rules injected while the architect writes each artifact |
| [standards/](standards/) | The detailed house rules the profile cites |
| [domain/](domain/) | Ubiquitous language, business rules, bounded contexts, actors |
| [use-cases/](use-cases/) | The pipeline's input — one business use case per feature |
| [CLAUDE.md](CLAUDE.md) | Always-on instructions for every agent |
| [.claude/settings.json](.claude/settings.json) | Token caps, Bash timeouts and denied commands (plugins can't set these) |

To change *how work is done here*, edit `standards/` or the profile. To change *how the pipeline
works*, change the plugin (develop it locally with `claude --plugin-dir <clone>`).

The OpenSpec skills (`.claude/skills/openspec-*`, `.claude/commands/opsx/`) are installed by
`openspec init` and stay in this repo.

---

## OpenSpec cheat sheet

```
openspec list                       # active changes
openspec show <change>              # view a change
openspec validate <change> --strict # validate a change
openspec archive <change> --yes     # complete a change (updates openspec/specs/)
```

- **Plans live in** `openspec/changes/<name>/` (proposal.md, design.md, specs/…/spec.md, tasks.md).
- **Completed specs live in** `openspec/specs/<capability>/spec.md`.
- **Archived changes** go to `openspec/changes/archive/<date>-<name>/`.
- After archiving, commit the `openspec/` bookkeeping (see Git workflow).

---

## Standards (`standards/`) — the house rules every agent follows

| File | Covers |
|------|--------|
| `clean-architecture.md` | Layering (domain / application / adapters), allowed imports, request flow, per-layer tests |
| `openapi.md` | Contract-first; split-by-domain spec; success envelope + RFC 7807; **bundle→generate** pipeline |
| `error-handling.md` | Exception taxonomy, single `@RestControllerAdvice`, code↔status map, correlation ids |
| `security.md` | Public API (no auth), transport and headers, input, rate limiting, secrets |
| `clean-code.md` | Small single-responsibility classes, naming, immutability, review smells |
| `testing.md` | Useful tests for all new code; per-layer pyramid; **DB tests on the target database (Testcontainers)** |
| `formatting.md` | google-java-format via Spotless, auto-format on commit, CI enforced |

**Where rules get injected:** design-time rules live in `openspec/config.yaml` (the architect obeys
them); build and review rules live in `CLAUDE.md` (all agents) and `.claude/sdlc-profile.md` (the
pipeline agents' per-gate checklists). To change how work is done, edit the relevant
`standards/*.md` and keep the profile's one-line summary in step — the whole pipeline follows.

---

## Build & dev commands

```
./gradlew build            # compile + test (Testcontainers) + spotlessCheck
./gradlew test             # tests only
./gradlew openApiGenerate  # regenerate API stubs (runs redocly bundle first)
./gradlew spotlessApply    # auto-format
./gradlew installGitHooks  # install the pre-commit format hook
```

Requirements: **Java 25, Docker running** (Testcontainers — needed for `./gradlew build`/`test`, not for
running the app on its H2 default), **Node** (for the `redocly` bundle step — run `npm ci` once). The
**dev container** (`.devcontainer/`) provisions all of this; open the repo in it and everything (JDK 25,
Node, redocly, OpenSpec CLI, Docker-in-Docker) is ready. Changing `.devcontainer/` needs a container
**rebuild**.

### Running the app & exploring the API

**1. Start it** (zero setup — no Docker or Postgres needed):

```
./gradlew bootRun
```

Wait for the log line `Started Application in … seconds`. It boots on an **in-memory H2 database**,
migrated with the same Flyway scripts as production and pre-loaded with a small demo dataset — the
fastest way to see the API respond with real data. The data resets on every restart. The app listens
on **port 8080** with context path **`/api/v1`**.

**2. Open Swagger UI** — the interactive API explorer:

> ### 👉 http://localhost:8080/api/v1/swagger-ui/index.html

Both parts of the path matter: `/api/v1` (the app's context path) **and** `/swagger-ui/index.html`
(the UI). It's served entirely locally (no external CDN, works offline) and renders the **authored**
OpenAPI contract — the same bundled spec that drives code generation, so what you see is exactly the
contract. Every endpoint has a **Try it out** button that runs live against the demo data.

**3. Other useful URLs** (all under `http://localhost:8080/api/v1`):

| URL | What |
|-----|------|
| `/swagger-ui/index.html` | Interactive Swagger UI |
| `/openapi/openapi.bundled.yaml` | The raw authored OpenAPI spec (YAML) |
| `/movies` · `/people` | Try the read endpoints directly, e.g. `curl http://localhost:8080/api/v1/movies` |

**Run against real PostgreSQL instead** (needs a running instance):

```
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

The `postgres` profile uses a real PostgreSQL datasource (defaults to `jdbc:postgresql://localhost:5432/sdlc`,
overridable via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`) with identical behaviour and no demo seeding.
Swagger UI is available at the same URL. **`./gradlew build` and the automated test suite always run
against PostgreSQL via Testcontainers, regardless of which profile you run the app with** — Docker is
still required for those.

**Troubleshooting:** if startup fails with *"Failed to start bean 'webServerStartStop'"* or the port is
taken, a previous instance is still on 8080 — clear it with `lsof -ti tcp:8080 | xargs kill -9`, then
`./gradlew bootRun` again.

---

## Observability — see what the pipeline did

**1. Hooks (lightweight, zero infra)** — the plugin logs every event to `logs/pipeline-events.jsonl`
(git-ignored). Render it with the plugin's `scripts/pipeline-report.py` → `logs/pipeline-report.html`.
Clear the log for a fresh baseline: `: > logs/pipeline-events.jsonl`.

**2. OpenTelemetry + Grafana (cost/tokens/trends)** — Claude Code's built-in telemetry → Collector →
Prometheus + Loki → Grafana. Full guide: **[observability/otel/README.md](observability/otel/README.md)**.

```
docker compose -f observability/otel/docker-compose.yml up -d          # start
set -a && source observability/otel/telemetry.env && set +a && claude  # run pipeline with telemetry
open http://localhost:3000                                             # view (Grafana)
docker compose -f observability/otel/docker-compose.yml down           # stop
```

**3. Session reports (is the pipeline actually *good*?)** — every run's report is in
[reports/sessions/](reports/sessions/): agent timeline, per-subagent value, which gates caught what,
errors, and whether `domain/` and `standards/` are read and used. Ask Claude *"analyse this session"*
for any other session. How to use them to judge the pipeline, including A/B ablations:
[evaluating-the-pipeline.md](https://github.com/prule/sdlc-pipeline/blob/main/docs/evaluating-the-pipeline.md).

**4. Run retrospectives (what should we change?)** — one record per run in
[retrospectives/](retrospectives/): failed reviews and standards violations, each with a root cause
and a recommended fix to the pipeline's inputs, with recurring findings flagged across runs.

Hooks answer "what did this run touch?"; OTel answers "what did it cost?"; session reports answer
"did each agent and each piece of context earn its place?"; retrospectives answer "what keeps going
wrong, and what should we change?".

---

## Cost / runaway controls

- **One model: opus for every agent.** On UC-002 a Sonnet junior-dev made ~4× the tool calls of an
  Opus one and doubled the run's cost (~$19 vs ~$9.50) and tripled its time, for the same
  blind-review quality. Evidence: branches `experiment/pipeline-opus-uc-002` and
  `experiment/baseline-uc-002`.
- **No agent can spawn agents**; the build-use-case skill caps fix-loops at 2 rounds and pauses at
  ~10 agent runs.
- **`.claude/settings.json`** caps output/thinking tokens and Bash timeouts, and denies `gradle publish`.
- **Account spend limit** (Anthropic Console for API keys, or your plan cap) is the only true dollar
  ceiling — set it. Watch spend with `/cost`.

---

## Git workflow

Each use case gets its own branch from `develop`; `build-use-case` creates it for you:

```
git checkout develop && git pull
git checkout -b feat/uc-<n>-<slug>     # or chore/… fix/… docs/… for other work
# … agents implement …
git commit -m "type(scope): summary"   # Conventional Commits; pre-commit runs Spotless
git push -u origin <branch>
gh pr create --base develop --head <branch> --title "…" --body "…"
```

Commit the run's retrospective and session report with the change. After a change is archived,
commit the `openspec/` bookkeeping (spec promotion + archive move).

---

## Extending the setup

- **New standard** → add `standards/<name>.md`, link it from `CLAUDE.md`, add its review items to
  `.claude/sdlc-profile.md`, and add the design-time gist to `openspec/config.yaml`.
- **New tech-stack default** → edit `CLAUDE.md` (build rules), `.claude/sdlc-profile.md` (commands
  and implementation rules) and `openspec/config.yaml` (design rules).
- **New role or pipeline change** → change the [sdlc-pipeline](https://github.com/prule/sdlc-pipeline)
  plugin, then update it here.

---

## Where things are

The pipeline is building a **public, read-only REST API over a curated movie catalog** (see
**[domain/](domain/)**). Everything below shipped through the pipeline with both gates.

**Platform foundation** ✅
- Walking skeleton (Clean Architecture, contract-first, security/error seam, Testcontainers, CI)
- Codegen shared-component reuse (PLAT-002) · HAL hypermedia (PLAT-003)
- H2 in-memory as the zero-dependency default runtime, Postgres via profile (PLAT-004)
- Swagger UI, serving the authored contract (PLAT-005)
- Dev container ✅ · Observability (hooks + OTel/Grafana) ✅

**Product — catalog API** ✅ (read surface complete for movies & people)

| Endpoint | Capability | Ticket |
|----------|-----------|--------|
| `GET /movies` · `GET /movies/{id}` | `catalog/movies` (search + detail) | CAT-002 · CAT-001 |
| `GET /movies/{id}/credits` | `catalog/credits` (cast & crew) | CAT-003 |
| `GET /people` · `GET /people/{id}` | `catalog/people` (search + detail) | CAT-006 · CAT-004 |
| `GET /people/{id}/credits` | `catalog/people` (filmography) | CAT-005 |

**Promoted specs** (`openspec/specs/`): `platform/health-check`, `platform/api-codegen`,
`platform/hypermedia-links`, `platform/runtime-datasource`, `platform/api-docs`, `catalog/movies`,
`catalog/credits`, `catalog/people`. Archived changes: `openspec/changes/archive/`.

**Candidate next use cases** (none in progress): `/genres` browse · data ingestion/curation · rate-limiting
design · enforce the Rating 1-decimal scale. **Auth** is parked pending a human-authored use case (write
the requirement first, then pipeline it).
