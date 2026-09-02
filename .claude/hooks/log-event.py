#!/usr/bin/env python3
"""Pipeline observability hook logger.

Reads a Claude Code hook payload as JSON on stdin and appends a compact event
line to logs/pipeline-events.jsonl. Wired to PreToolUse / PostToolUse /
SubagentStop / UserPromptSubmit / Stop / SessionStart in .claude/settings.json.

Must never break the pipeline: it always exits 0 and swallows its own errors.
"""
import sys, json, time, os, pathlib

RAW = sys.stdin.read()


def main() -> None:
    try:
        data = json.loads(RAW)
    except Exception:
        data = {"hook_event_name": "unparsed", "_raw": RAW[:500]}

    ev = {
        "ts": round(time.time(), 3),          # epoch seconds (sub-second) for durations
        "iso": time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()),
        "event": data.get("hook_event_name"),
        "tool": data.get("tool_name"),
        "session": data.get("session_id"),
    }

    # Pull the interesting bits out of tool_input (what makes each tool meaningful).
    ti = data.get("tool_input") or {}
    if isinstance(ti, dict):
        for k in ("file_path", "command", "skill", "subagent_type",
                  "description", "pattern", "url", "prompt", "args"):
            v = ti.get(k)
            if v is None:
                continue
            if isinstance(v, str) and len(v) > 300:
                v = v[:300] + "…"
            ev[k] = v

    # Lightweight response marker (never store full payloads).
    tr = data.get("tool_response")
    if isinstance(tr, dict):
        ev["ok"] = tr.get("success", tr.get("is_error") is not True)
    elif isinstance(tr, str):
        ev["response_len"] = len(tr)

    if data.get("prompt") and "prompt" not in ev:      # UserPromptSubmit
        p = data["prompt"]
        ev["user_prompt"] = p[:200] + "…" if len(p) > 200 else p

    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    logdir = pathlib.Path(root) / "logs"
    logdir.mkdir(parents=True, exist_ok=True)
    with open(logdir / "pipeline-events.jsonl", "a") as f:
        f.write(json.dumps(ev, ensure_ascii=False) + "\n")


try:
    main()
except Exception:
    pass  # observability must never block the pipeline
sys.exit(0)
