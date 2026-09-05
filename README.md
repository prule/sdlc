# SDLC — Multi-Agent Software Delivery Pipeline

A spec-driven development setup where specialised Claude Code agents (architect, spec-reviewer,
junior dev, QA, senior dev) take a ticket through the **OpenSpec** workflow — plan → review →
implement → verify → code review → archive — with human approval gates, cost controls, and full
observability.

> **New here?** Read this file top to bottom once. Day to day, you mostly run `/build-ticket`
> and answer the two gates.

---

## TL;DR — run a ticket

```
/build-ticket <paste the ticket / description here>
```

That drives the whole pipeline and pauses at two gates for your approval. Everything below explains
what happens and how to do each piece by hand.

---

## Step 0 — write the ticket first

For anything non-trivial (especially features), author the ticket before running the pipeline — the
pipeline serves the requirement, it shouldn't invent it.

```
/write-ticket <rough idea>          # interactive: asks you the gaps, saves to tickets/
```

or delegate a one-shot draft: `Use the ticket-writer agent to draft a ticket for: <idea>`.

Both read the **[domain/](domain/)** knowledge base (ubiquitous language, bounded contexts, actors,
business rules) and **[standards/](standards/)**, so tickets use the right language and NFRs. Tickets
live in **[tickets/](tickets/)** ([template](tickets/TEMPLATE.md)); they capture **what & why**, not
**how**. Keep `domain/` current — it's what makes the tickets (and plans) good.

---

## The team (`.claude/agents/`)

| Agent | Model | Can write? | Job | OpenSpec verb |
|-------|-------|-----------|-----|---------------|
| **ticket-writer** | opus | yes (tickets) | Rough idea → a well-formed ticket (from `domain/` + `standards/`) | — (pre-pipeline) |
| **architect** | opus | yes | Ticket → plan (proposal, design, spec delta, tasks) | `opsx:propose` |
| **spec-reviewer** | opus | no (read-only) | Plan gate: standards conformance + design/feasibility | — |
| **junior-dev** | sonnet | yes | Implement the tasks | `opsx:apply` |
| **qa** | sonnet | yes | Verify every requirement is tested; run the suite | `opsx:verify` |
| **senior-dev** | opus | yes (fixes directly) | Final code review of the diff; fixes what it finds, hands back design/scope calls | — |

Principle: **OpenSpec owns the workflow mechanics; agents own judgment + standards.** The
**spec-reviewer** is read-only by design (plan gate — it reports, the architect revises). The
**senior-dev** (the strongest model) fixes what it finds in the code review directly, and hands back
only design/plan/scope calls. Agents never format code — the pre-commit hook does (they build with
`-x spotlessCheck`).

---

## The pipeline (`/build-ticket`)

```
architect ─▶ spec-reviewer ─▶ 🚦 GATE 1 ─▶ junior-dev ─▶ qa ─▶ senior-dev ─▶ 🚦 GATE 2 ─▶ archive
  (plan)      (plan gate)      (you)        (implement)  (verify) (code review)  (you)
                   ▲                              │          │         │
                   └──── revise (max 2 rounds) ───┴──────────┴─────────┘
```

- **🚦 GATE 1 — proposal approval.** You review the plan + spec-reviewer verdict and say
  proceed / revise / stop.
- **🚦 GATE 2 — merge/archive approval.** You review QA evidence + code-review verdict + the diff
  and approve the merge/archive.
- Fix-loops are capped at **2 rounds**, then the pipeline stops and escalates to you.

### Run phases by hand

```
Use the architect agent to plan: <ticket>
Use the spec-reviewer agent to review the <change-name> change
Use the junior-dev agent to implement the <change-name> change
Use the qa agent to verify <change-name>
Use the senior-dev agent to review the diff for <change-name>
```

Or drive OpenSpec directly, no agents:

```
/opsx:propose "<idea>"     # plan
/opsx:apply <change>       # implement
/opsx:verify <change>      # verify
/opsx:archive <change>     # fold spec delta into openspec/specs, move to archive
```

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
| `security.md` | Stateless JWT, required claims, ownership/tenant authz, secrets handling |
| `clean-code.md` | Small single-responsibility classes, naming, immutability, review smells |
| `testing.md` | Useful tests for all new code; per-layer pyramid; **Testcontainers, no H2** |
| `formatting.md` | google-java-format via Spotless, auto-format on commit, CI enforced |

**Where rules get injected:** design-time rules live in `openspec/config.yaml` (the architect obeys
them); build/review rules live in `CLAUDE.md` (all agents) + the per-agent files. To change how work
is done, edit the relevant `standards/*.md` — the whole pipeline follows.

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

Two complementary options (run both, compare):

**1. Hooks (lightweight, zero infra)** — log every event to `logs/pipeline-events.jsonl` (git-ignored):

```
python3 .claude/hooks/pipeline-report.py     # → logs/pipeline-report.html
```

Shows agents spawned (+ roles), tool-usage counts, skills invoked, files read/written, and a
filterable event timeline. Clear the log for a fresh baseline: `: > logs/pipeline-events.jsonl`.
Hooks load at session start, so config changes take effect next `claude` session.

**2. OpenTelemetry + Grafana (cost/tokens/trends)** — Claude Code's built-in telemetry → Collector →
Prometheus + Loki → Grafana. Full guide: **[observability/otel/README.md](observability/otel/README.md)**.

```
docker compose -f observability/otel/docker-compose.yml up -d          # start
set -a && source observability/otel/telemetry.env && set +a && claude  # run pipeline with telemetry
open http://localhost:3000                                             # view (Grafana)
docker compose -f observability/otel/docker-compose.yml down           # stop
```

Hooks answer "what did this run touch?"; OTel answers "what did it cost, how many tokens, how does it trend?"

---

## Cost / runaway controls

- **Model tiering** — opus only for architect + reviewers; sonnet for implement/QA.
- **No agent can spawn agents** — only the orchestrator (you) spawns; no fan-out.
- **`.claude/settings.json`** caps output/thinking tokens and Bash timeouts, and denies `gradle publish`.
- **`/build-ticket`** caps fix-loops at 2 rounds and pauses at ~10 total agent runs.
- **Account spend limit** (Anthropic Console for API keys, or your plan cap) is the only true dollar ceiling — set it.
- Watch spend with `/cost`.

---

## Git workflow

Work on the default branch is avoided for changes:

```
git checkout -b <type>/<slug>          # feat/… fix/… chore/… docs/…
# … agents implement …
git commit -m "type(scope): summary"   # Conventional Commits; pre-commit runs Spotless
git push -u origin <branch>
gh pr create --base main --head <branch> --title "…" --body "…"
gh pr merge <n> --squash --delete-branch
```

After a change is archived, commit the `openspec/` bookkeeping (spec promotion + archive move).

---

## Extending the setup

- **New role** → add `.claude/agents/<name>.md` (frontmatter: `name`, `description`, `model`,
  `tools`); wire it into `.claude/commands/build-ticket.md` if it joins the main pipeline.
- **New standard** → add `standards/<name>.md`, link it from `CLAUDE.md`, reference it from the
  agents that must enforce it, and add the design-time gist to `openspec/config.yaml`.
- **New tech-stack default** → edit `CLAUDE.md` (build rules) and `openspec/config.yaml` (design rules).

---

## Where things are

The pipeline is building a **public, read-only REST API over a curated movie catalog** (see
**[domain/](domain/)**). Everything below shipped through `/build-ticket` with both gates.

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

**Candidate next tickets** (none in progress): `/genres` browse · data ingestion/curation · rate-limiting
design · enforce the Rating 1-decimal scale. **Auth** is parked pending a human-authored ticket (write
the requirement first, then pipeline it).
