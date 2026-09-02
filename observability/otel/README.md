# OpenTelemetry observability (Grafana)

Claude Code's **built-in** telemetry → OpenTelemetry Collector → Prometheus (metrics) + Loki
(events) → Grafana. This is the heavier, metrics-and-cost-oriented alternative to the lightweight
hooks logger (`.claude/hooks/`). Run both and compare.

## Quick start

```bash
# 1. Bring up the stack (from repo root)
docker compose -f observability/otel/docker-compose.yml up -d

# 2. In the terminal where you'll run Claude Code, enable telemetry, then launch it
set -a && source observability/otel/telemetry.env && set +a
claude          # run your pipeline (e.g. /build-ticket …) as normal

# 3. Watch it
open http://localhost:3000        # Grafana → dashboard "Claude Code — OpenTelemetry"
```

Grafana is anonymous-admin (no login); creds are `admin`/`admin` if prompted.
Tear down with `docker compose -f observability/otel/docker-compose.yml down` (add `-v` to wipe data).

## What you get (that hooks don't)

| Signal | Where |
|--------|-------|
| **Cost (USD)** per model, over time | `claude_code.cost.usage` → Prometheus |
| **Tokens** by type (input/output/cache) | `claude_code.token.usage` |
| **Sessions**, active time, lines of code | `claude_code.session.count`, `.active_time.total`, `.lines_of_code.count` |
| **Tool / edit decisions** (accept/reject) | `claude_code.code_edit_tool.decision` |
| **Events** — user prompts, tool results, api requests/errors | Loki (`{service_name="claude-code"}`) |

Proper metric aggregation and cost/token accounting across many runs — the thing the hooks JSONL
can't give you. The hooks log is still better for a quick per-run "what files did it touch" view.

## Ports

| Service | URL |
|---------|-----|
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Loki | http://localhost:3100 |
| Collector OTLP | grpc `:4317`, http `:4318` |

## Notes / troubleshooting

- **Metric names:** the dashboard queries use `__name__` regex (e.g. `claude_code_cost_usage.*_total`)
  so it survives the exact unit-suffix the collector adds. After your first telemetry run, confirm
  names in Prometheus (http://localhost:9090 → metrics explorer) or Grafana **Explore**; adjust panels
  if your Claude Code version names them differently.
- **No data?** Check `docker compose -f observability/otel/docker-compose.yml logs otel-collector`
  (the `debug` exporter echoes everything received), confirm the env vars are set in the *same* shell
  that launched `claude`, and that Prometheus target `otel-collector:8889` is UP
  (http://localhost:9090/targets).
- **Privacy:** prompt text is NOT exported unless you set `OTEL_LOG_USER_PROMPTS=1` in `telemetry.env`.
- This stack is local-only and unauthenticated — don't expose these ports beyond localhost.
- Pinned image versions live in `docker-compose.yml`; bump them there.
