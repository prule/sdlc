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

## The team (`.claude/agents/`)

| Agent | Model | Can write? | Job | OpenSpec verb |
|-------|-------|-----------|-----|---------------|
| **architect** | opus | yes | Ticket → plan (proposal, design, spec delta, tasks) | `opsx:propose` |
| **spec-reviewer** | opus | no (read-only) | Plan gate: standards conformance + design/feasibility | — |
| **junior-dev** | sonnet | yes | Implement the tasks | `opsx:apply` |
| **qa** | sonnet | yes | Verify every requirement is tested; run the suite | `opsx:verify` |
| **senior-dev** | opus | no (read-only) | Final code review of the diff | — |

Principle: **OpenSpec owns the workflow mechanics; agents own judgment + standards.** Reviewers
have no write tools by design — they report, the author fixes.

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

Requirements: **Java 25, Docker running** (Testcontainers), **Node** (for the `redocly` bundle
step — run `npm ci` once). The **dev container** (`.devcontainer/`) provisions all of this; open the
repo in it and everything (JDK 25, Node, redocly, OpenSpec CLI, Docker-in-Docker) is ready. Changing
`.devcontainer/` needs a container **rebuild**.

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
- **`.claude/settings.json`** caps output/thinking tokens, Bash timeouts, and denies `git push`/publish from agents.
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

- **Phase 0 — walking skeleton** ✅ (foundation: Clean Architecture, contract-first, security/error seam, Testcontainers, CI)
- **Dev container** ✅ · **Codegen shared-component reuse (PLAT-002)** ✅ · **Observability** ✅
- **Next: Phase 1 — foundational auth** (`user_account` + password policy + hashing) — prerequisite for the password-reset feature (AUTH-142).

Specs so far: `platform/health-check`, `platform/api-codegen`.
