# OpenTelemetry observability (Grafana)

Visualise the multi-agent pipeline with Claude Code's **built-in** OpenTelemetry export:

```
Claude Code ──OTLP──▶ OpenTelemetry Collector ──▶ Prometheus (metrics)  ─┐
                                               └──▶ Loki (events/logs)  ─┴──▶ Grafana
```

This is the metrics-, cost-, and trend-oriented alternative to the lightweight hooks logger
(`.claude/hooks/`). Run both and compare — hooks answer *"what did this run touch?"*, OTel answers
*"what did it cost, how many tokens, and how does it trend across runs?"*

Everything here is **local-only and unauthenticated** — don't expose the ports beyond localhost.

---

## 1. Start the stack

From the repo root:

```bash
docker compose -f observability/otel/docker-compose.yml up -d
```

Brings up four containers: `otel-collector`, `prometheus`, `loki`, `grafana`. Check they're healthy:

```bash
docker compose -f observability/otel/docker-compose.yml ps
```

Endpoints once up:

| Service | URL | Notes |
|---------|-----|-------|
| Grafana | http://localhost:3000 | anonymous admin; creds `admin`/`admin` if prompted |
| Prometheus | http://localhost:9090 | metrics store + query UI |
| Loki | http://localhost:3100 | log/event store (via Grafana) |
| Collector OTLP | `:4317` (gRPC), `:4318` (HTTP) | where Claude Code sends telemetry |

Loki reports `503` on `/ready` for the first ~20–30s while it warms up — that's normal.

---

## 2. Run a pipeline that emits events

Telemetry is **opt-in per shell**. In the terminal you'll launch Claude Code from, load the env and
start Claude:

```bash
set -a && source observability/otel/telemetry.env && set +a
claude
```

`telemetry.env` sets `CLAUDE_CODE_ENABLE_TELEMETRY=1` and points the OTLP exporter at
`localhost:4317`. It **must** be set in the *same* shell that runs `claude`. Then drive the pipeline
as normal — e.g.:

```
/build-ticket <your ticket>
```

Every agent spawn, tool call, API request, and token/cost is exported while it runs. Metrics flush
every 10s and events every 5s (tuned in `telemetry.env`; defaults are 60s/5s).

> Prompt text is **not** exported unless you uncomment `OTEL_LOG_USER_PROMPTS=1` in `telemetry.env`.

Quick check that data is arriving (the collector echoes everything it receives):

```bash
docker compose -f observability/otel/docker-compose.yml logs -f otel-collector
```

---

## 3. View the results

### Grafana dashboard (start here)
http://localhost:3000 → **Dashboards → "Claude Code — OpenTelemetry"**
(direct: http://localhost:3000/d/claude-code-otel). Panels:

- **Total cost (USD)**, **Sessions**, **Total tokens**, **Lines of code changed** (stat tiles)
- **Tokens by type** and **Cost over time** (per model)
- **Tool / edit decisions** (accept/reject rates)
- **Active time**
- **Events** — a Loki log panel streaming user prompts, tool results, and api requests

Set the time range (top-right) to cover your run and let auto-refresh (10s) update it.

### Ad-hoc queries — Grafana Explore
http://localhost:3000/explore

- **Prometheus** (metrics): browse the metrics dropdown for `claude_code_*`, or try
  `sum by (type) (increase(claude_code_token_usage_tokens_total[5m]))`.
- **Loki** (events): `{service_name="claude-code"}` — add filters like `|= "tool_result"`.

### Raw stores (optional)
- Prometheus UI: http://localhost:9090 (Graph tab; `/targets` should show `otel-collector:8889` **UP**).
- Loki is queried through Grafana rather than a UI of its own.

---

## 4. Stop the stack

```bash
docker compose -f observability/otel/docker-compose.yml down        # stop (keeps data)
docker compose -f observability/otel/docker-compose.yml down -v     # stop AND wipe stored data
```

Stopping the stack does not affect Claude Code — telemetry export just quietly no-ops. To stop
*exporting* without stopping the stack, launch `claude` from a shell that hasn't sourced
`telemetry.env` (or set `CLAUDE_CODE_ENABLE_TELEMETRY=0`).

---

## What gets captured (that hooks don't)

| Signal | Source metric / stream |
|--------|------------------------|
| **Cost (USD)** per model, over time | `claude_code.cost.usage` |
| **Tokens** by type (input/output/cache) | `claude_code.token.usage` |
| **Sessions**, active time, lines of code | `claude_code.session.count`, `.active_time.total`, `.lines_of_code.count` |
| **Tool / edit decisions** (accept/reject) | `claude_code.code_edit_tool.decision` |
| **Events** — prompts, tool results, api requests/errors | Loki `{service_name="claude-code"}` |

Proper aggregation and cost/token accounting across many runs — the thing the per-run hooks JSONL
can't give you.

---

## Troubleshooting

- **Empty dashboard.** Confirm the env vars are set in the *same* shell that launched `claude`
  (`echo $CLAUDE_CODE_ENABLE_TELEMETRY` → `1`); check the collector received data
  (`docker compose -f observability/otel/docker-compose.yml logs otel-collector`); check the
  Prometheus target is UP (http://localhost:9090/targets). Widen the Grafana time range.
- **Metric names differ.** The dashboard queries use `__name__` regex (e.g.
  `claude_code_cost_usage.*_total`) so they survive the collector's unit-suffix naming. If your Claude
  Code version names something differently, find it in Grafana **Explore** and adjust the panel.
- **Port already in use.** Another process holds 3000/9090/3100/4317/4318 — stop it or remap the port
  in `docker-compose.yml`.
- **Bump versions.** Image tags are pinned in `docker-compose.yml`; change them there and re-`up`.

## Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | the four services (pinned images) |
| `otel-collector-config.yaml` | OTLP in → Prometheus + Loki out (+ debug echo) |
| `prometheus.yml` | scrape the collector's `:8889` |
| `loki-config.yaml` | single-binary Loki with OTLP ingest |
| `grafana/provisioning/` | datasources (Prometheus, Loki) + dashboard provider |
| `grafana/dashboards/claude-code.json` | the "Claude Code — OpenTelemetry" dashboard |
| `telemetry.env` | env that turns on Claude Code export |
