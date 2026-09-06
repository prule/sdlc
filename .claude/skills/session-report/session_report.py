#!/usr/bin/env python3
"""
session_report.py — turn a Claude Code session log (.jsonl) into a beautiful,
self-contained HTML report focused on pipeline efficiency.

Usage:
    python3 scripts/session_report.py <session.jsonl> [-o report.html]
                                       [--compact] [--open]

The report shows:
  * a summary of the session (duration, tokens, agent runs, errors, issues caught)
  * a Gantt-style timeline of every agent/subagent run — who ran, when, how long
    (use --compact to collapse idle gaps so short runs stay visible)
  * a subagent value / efficiency table with auto-generated insights
  * a Review-gate value panel — which review agents actually caught something
  * an Errors & friction panel — failed commands, rejected tool calls, failed agents
  * a readable, filterable chronological activity feed

Background ("run_in_background") agents launch with an instant stub result; their
real duration and output arrive later in a <task-notification>. This script
correlates the two so async agents are timed and reported correctly.

No third-party dependencies — standard library only. Output is one HTML file.
"""

from __future__ import annotations

import argparse
import bisect
import html
import json
import re
import sys
import webbrowser
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

# subagents that act as review / verification gates
GATE_AGENTS = {"spec-reviewer", "senior-dev", "qa", "code-review", "reviewer"}


# --------------------------------------------------------------------------- #
# Parsing helpers
# --------------------------------------------------------------------------- #

def parse_ts(s):
    if not s:
        return None
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        return None


def load_events(path):
    events = []
    with open(path, "r", encoding="utf-8") as fh:
        for i, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                sys.stderr.write(f"warning: skipping malformed line {i}\n")
    return events


def blocks(msg):
    if not isinstance(msg, dict):
        return
    content = msg.get("content")
    if isinstance(content, list):
        for b in content:
            if isinstance(b, dict):
                yield b
    elif isinstance(content, str) and content:
        yield {"type": "text", "text": content}


def text_of(block):
    if not isinstance(block, dict):
        return str(block)
    if isinstance(block.get("text"), str):
        return block["text"]
    c = block.get("content")
    if isinstance(c, str):
        return c
    if isinstance(c, list):
        return "".join(x.get("text", "") for x in c if isinstance(x, dict))
    return ""


def msg_text(msg):
    return "".join(
        b.get("text", "") for b in blocks(msg) if b.get("type") == "text"
    )


# --------------------------------------------------------------------------- #
# Task-notification (async agent completion) parsing
# --------------------------------------------------------------------------- #

_TAG = lambda name, s: (re.search(rf"<{name}>(.*?)</{name}>", s, re.S) or [None, None])[1]


def parse_notification(text):
    if "<task-notification>" not in text:
        return None
    result = re.search(r"<result>(.*?)</result>", text, re.S)
    return {
        "task_id": _TAG("task-id", text),
        "tool_use_id": _TAG("tool-use-id", text),
        "status": (_TAG("status", text) or "").strip(),
        "summary": (_TAG("summary", text) or "").strip(),
        "result": result.group(1).strip() if result else "",
    }


# --------------------------------------------------------------------------- #
# Review-verdict classification
# --------------------------------------------------------------------------- #

def classify_review(text):
    """Return (verdict, caught?, issue_count, snippet) for a review agent result.

    Keys off *formal* verdict tokens and uppercase severity labels rather than
    loose substrings, so "failure path" / "critically" (adverbs) don't
    false-positive, and "no CRITICAL issues" is treated as clean.
    """
    t = text or ""
    tl = t.lower()

    def issue_count():
        nums = [int(m) for m in re.findall(
            r"\b(\d{1,3})\s+(?:issue|finding|defect|bug|blocker|problem|violation)s?\b", tl)]
        explicit = max(nums) if nums else 0
        sev = t.count("❌") + len(re.findall(r"\bCRITICAL\b", t))
        return min(99, max(explicit, sev))

    def snippet_near(*keys):
        for k in keys:
            i = tl.find(k.lower())
            if i != -1:
                return re.sub(r"\s+", " ", t[i:i + 240]).strip()
        return re.sub(r"\s+", " ", t[:200]).strip()

    # 1) formal "REQUEST CHANGES" gate verdict — unambiguous
    if re.search(r"request[ _-]?changes", tl):
        return "REQUEST CHANGES", True, issue_count(), snippet_near("request change")

    # 2) hard catch signals: uppercase severity labels, ❌, or "critical <noun>".
    #    Guarded so "no/0/zero critical" isn't counted as a catch.
    only_clean_critical = bool(re.search(r"\b(?:no|0|zero)\s+critical", tl)) \
        and not re.search(r"\b[1-9]\d*\s+critical", tl)
    has_critical = bool(re.search(r"\bCRITICAL\b", t)) and not only_clean_critical
    if (has_critical or "❌" in t or re.search(r"\bFAIL(?:ED)?\b", t)
            or re.search(r"critical (?:defect|issue|bug|finding|blocker)", tl)):
        return "ISSUES FOUND", True, issue_count(), snippet_near("CRITICAL", "❌", "FAIL", "defect")

    # 3) explicit approval / pass
    if re.search(r"\bapprove", tl) or "lgtm" in tl or "✅" in t or re.search(r"\bpass(?:ed)?\b", tl):
        return "APPROVED / PASS", False, 0, snippet_near("approve", "pass", "lgtm")

    return "—", False, issue_count(), re.sub(r"\s+", " ", t[:200]).strip()


REJECT_MARKERS = ("doesn't want to proceed", "tool use was rejected", "user rejected")


def classify_error(name, text):
    tl = (text or "").lower()
    if any(m in tl for m in REJECT_MARKERS):
        return "rejected"        # you declined the tool call — friction, not a bug
    return "error"               # command / tool actually failed


# --------------------------------------------------------------------------- #
# Shared aggregation helpers (reused by main log and each subagent transcript)
# --------------------------------------------------------------------------- #

READ_TOOLS = {"Read"}
WRITE_TOOLS = {"Write"}
EDIT_TOOLS = {"Edit", "MultiEdit", "NotebookEdit"}


def file_ops_from_tools(tools):
    files = defaultdict(lambda: {"read": 0, "write": 0, "edit": 0,
                                 "first": None, "last": None})
    for t in tools:
        inp = t["input"] if isinstance(t["input"], dict) else {}
        path = inp.get("file_path") or inp.get("notebook_path")
        if not path:
            continue
        rec = files[path]
        if t["name"] in READ_TOOLS:
            rec["read"] += 1
        elif t["name"] in WRITE_TOOLS:
            rec["write"] += 1
        elif t["name"] in EDIT_TOOLS:
            rec["edit"] += 1
        else:
            continue
        ts = t.get("start")
        if ts:
            rec["first"] = ts if rec["first"] is None else min(rec["first"], ts)
            rec["last"] = ts if rec["last"] is None else max(rec["last"], ts)
    return dict(files)


def tool_stats_from_tools(tools):
    stats = defaultdict(lambda: {"count": 0, "time": 0.0, "err": 0})
    for t in tools:
        s = stats[t["name"]]
        s["count"] += 1
        s["time"] += t.get("duration") or 0
        if t.get("error"):
            s["err"] += 1
    return dict(stats)


def merge_file_ops(dicts):
    out = defaultdict(lambda: {"read": 0, "write": 0, "edit": 0,
                               "first": None, "last": None})
    for d in dicts:
        for path, r in d.items():
            o = out[path]
            o["read"] += r["read"]; o["write"] += r["write"]; o["edit"] += r["edit"]
            for key in ("first", "last"):
                if r[key]:
                    o[key] = r[key] if o[key] is None else (
                        min(o[key], r[key]) if key == "first" else max(o[key], r[key]))
    return dict(out)


def merge_tool_stats(dicts):
    out = defaultdict(lambda: {"count": 0, "time": 0.0, "err": 0})
    for d in dicts:
        for name, s in d.items():
            o = out[name]
            o["count"] += s["count"]; o["time"] += s["time"]; o["err"] += s["err"]
    return dict(out)


def correlate_tools(events):
    """Pair tool_use with tool_result within one transcript; return a tools list
    plus token totals, time span, and the first real user prompt."""
    uses, results = {}, {}
    tokens = defaultdict(int)
    first = last = first_prompt = None
    for ev in events:
        ts = parse_ts(ev.get("timestamp"))
        if ts:
            first = ts if first is None else min(first, ts)
            last = ts if last is None else max(last, ts)
        m = ev.get("message")
        if ev.get("type") == "user" and isinstance(m, dict):
            for b in blocks(m):
                if b.get("type") == "tool_result":
                    results[b.get("tool_use_id")] = {
                        "ts": ts, "is_error": bool(b.get("is_error"))}
            if first_prompt is None:
                t = msg_text(m).strip()
                if t:
                    first_prompt = t
        elif ev.get("type") == "assistant" and isinstance(m, dict):
            u = m.get("usage") or {}
            for k in ("input_tokens", "output_tokens",
                      "cache_creation_input_tokens", "cache_read_input_tokens"):
                tokens[k] += u.get(k, 0) or 0
            for b in blocks(m):
                if b.get("type") == "tool_use":
                    uses[b["id"]] = {"name": b.get("name", "?"),
                                     "input": b.get("input") or {}, "ts": ts}
    tools = []
    for tid, u in uses.items():
        r = results.get(tid)
        dur = (r["ts"] - u["ts"]).total_seconds() if (r and r["ts"] and u["ts"]) else None
        tools.append({"id": tid, "name": u["name"], "input": u["input"],
                      "start": u["ts"], "duration": dur,
                      "error": r["is_error"] if r else None})
    return tools, dict(tokens), first, last, first_prompt


def load_subagents(session_path):
    """Parse every subagents/agent-*.jsonl beside the main log.
    Returns {agentId: stats} keyed by the transcript's own agentId."""
    sub_dir = Path(session_path).with_suffix("") / "subagents"
    if not sub_dir.is_dir():
        return {}
    out = {}
    for f in sorted(sub_dir.glob("agent-*.jsonl")):
        aid = f.stem[len("agent-"):]
        events = load_events(f)
        if not events:
            continue
        tools, tokens, first, last, prompt = correlate_tools(events)
        dur = (last - first).total_seconds() if first and last else None
        out[aid] = {
            "agent_id": aid, "tools": tools,
            "file_ops": file_ops_from_tools(tools),
            "tool_stats": tool_stats_from_tools(tools),
            "tool_calls": len(tools), "tokens": tokens,
            "duration": dur, "prompt": prompt,
            "errors": sum(1 for t in tools if t["error"]),
        }
    return out


def _norm_prompt(s):
    return re.sub(r"\s+", " ", s or "").strip()[:120]


def link_subagents(data, subs):
    """Attach each subagent transcript to its parent Agent record (matched by
    prompt) and build session-wide merged file/tool aggregates."""
    by_prompt = {_norm_prompt(s["prompt"]): s for s in subs.values()}
    for a in data["agents"]:
        s = by_prompt.get(_norm_prompt(a["input"].get("prompt")))
        a["sub"] = s
    data["files_all"] = merge_file_ops(
        [data["files"]] + [s["file_ops"] for s in subs.values()])
    data["tool_stats_all"] = merge_tool_stats(
        [data["tool_stats"]] + [s["tool_stats"] for s in subs.values()])
    data["subagents"] = subs
    data["has_sub"] = True


# --------------------------------------------------------------------------- #
# Core analysis
# --------------------------------------------------------------------------- #

def analyze(events):
    tool_uses = {}
    tool_results = {}
    notifications = defaultdict(list)     # tool_use_id -> [notification, ...]
    tool_counter = Counter()
    models = Counter()
    tokens = defaultdict(int)

    timeline = []
    first_ts = last_ts = None
    meta = {}

    for ev in events:
        ts = parse_ts(ev.get("timestamp"))
        if ts:
            first_ts = ts if first_ts is None else min(first_ts, ts)
            last_ts = ts if last_ts is None else max(last_ts, ts)
        for k in ("sessionId", "cwd", "gitBranch", "version"):
            if k in ev and k not in meta:
                meta[k] = ev[k]
        if ev.get("type") == "custom-title" and ev.get("customTitle"):
            meta.setdefault("title", ev["customTitle"])

        etype = ev.get("type")
        msg = ev.get("message")

        if etype == "user" and isinstance(msg, dict):
            raw = msg_text(msg)
            note = parse_notification(raw)
            if note:
                if note["tool_use_id"]:
                    note["ts"] = ts
                    notifications[note["tool_use_id"]].append(note)
                continue

            has_result = False
            for b in blocks(msg):
                if b.get("type") == "tool_result":
                    has_result = True
                    tid = b.get("tool_use_id")
                    if tid:
                        tool_results[tid] = {
                            "ts": ts,
                            "is_error": bool(b.get("is_error")),
                            "text": text_of(b),
                        }
            prompt = raw.strip()
            # skip injected/system-wrapped user content (task-notifications handled,
            # system-reminders, local-command output, etc.)
            if prompt and not has_result and not prompt.startswith("<"):
                timeline.append({"ts": ts, "kind": "user", "text": prompt})

        elif etype == "assistant" and isinstance(msg, dict):
            models[msg.get("model", "unknown")] += 1
            usage = msg.get("usage") or {}
            for k in ("input_tokens", "output_tokens",
                      "cache_creation_input_tokens", "cache_read_input_tokens"):
                tokens[k] += usage.get(k, 0) or 0
            for b in blocks(msg):
                bt = b.get("type")
                if bt == "text" and b.get("text", "").strip():
                    timeline.append({"ts": ts, "kind": "assistant",
                                     "text": b["text"].strip()})
                elif bt == "thinking" and b.get("thinking", "").strip():
                    timeline.append({"ts": ts, "kind": "thinking",
                                     "text": b["thinking"].strip()})
                elif bt == "tool_use":
                    tid = b.get("id")
                    name = b.get("name", "?")
                    tool_counter[name] += 1
                    tool_uses[tid] = {"name": name, "input": b.get("input") or {},
                                      "ts": ts}
                    timeline.append({"ts": ts, "kind": "tool", "tool": name,
                                     "input": b.get("input") or {}, "id": tid})

    # ---- correlate uses <-> results / notifications --------------------- #
    tools, agents, errors = [], [], []
    for tid, use in tool_uses.items():
        res = tool_results.get(tid)
        notes = notifications.get(tid) or []
        last_note = notes[-1] if notes else None

        # completion time & result: notification wins over the async stub
        if last_note and last_note.get("ts"):
            end = last_note["ts"]
            result_text = last_note["result"]
            status = last_note["status"]
            is_error = status not in ("completed", "", None)
        elif res:
            end = res["ts"]
            result_text = res["text"]
            is_error = res["is_error"]
            status = "error" if is_error else "completed"
        else:
            end, result_text, is_error, status = None, None, None, "pending"

        dur = (end - use["ts"]).total_seconds() if (end and use["ts"]) else None
        rec = {
            "id": tid, "name": use["name"], "input": use["input"],
            "start": use["ts"], "end": end, "duration": dur,
            "error": is_error, "result": result_text, "status": status,
            "pending": end is None,
        }
        tools.append(rec)

        # collect real errors / rejections (skip async launch stubs)
        if res and res["is_error"]:
            errors.append({
                "ts": use["ts"], "tool": use["name"],
                "kind": classify_error(use["name"], res["text"]),
                "input": use["input"], "text": res["text"],
            })

        if use["name"] == "Agent":
            inp = use["input"]
            sub = inp.get("subagent_type", "agent")
            gate = sub in GATE_AGENTS
            verdict = caught = issues = snippet = None
            if gate and result_text:
                verdict, caught, issues, snippet = classify_review(result_text)
            agents.append({
                **rec, "subagent": sub, "gate": gate,
                "description": inp.get("description", ""),
                "background": bool(inp.get("run_in_background")),
                "verdict": verdict, "caught": caught,
                "issues": issues, "snippet": snippet,
            })

    by_id = {t["id"]: t for t in tools}
    agent_by_id = {a["id"]: a for a in agents}
    for item in timeline:
        if item.get("kind") == "tool":
            t = by_id.get(item.get("id"))
            if t:
                item.update({"duration": t["duration"], "error": t["error"],
                             "result": t["result"], "status": t["status"]})
                a = agent_by_id.get(item["id"])
                if a:
                    item.update({"verdict": a["verdict"], "caught": a["caught"],
                                 "gate": a["gate"]})

    files = file_ops_from_tools(tools)
    tool_stats = tool_stats_from_tools(tools)

    agents.sort(key=lambda a: a["start"] or datetime.max.replace(tzinfo=timezone.utc))
    timeline.sort(key=lambda x: x["ts"] or datetime.max.replace(tzinfo=timezone.utc))
    errors.sort(key=lambda e: e["ts"] or datetime.max.replace(tzinfo=timezone.utc))

    return {
        "meta": meta, "first_ts": first_ts, "last_ts": last_ts,
        "models": models, "tokens": dict(tokens),
        "tool_counter": tool_counter, "tool_stats": dict(tool_stats),
        "tools": tools, "agents": agents, "files": dict(files),
        "errors": errors, "timeline": timeline,
    }


# --------------------------------------------------------------------------- #
# Idle-gap collapse for the --compact Gantt
# --------------------------------------------------------------------------- #

def build_remap(intervals, idle_cap=8.0):
    """Piecewise-linear map real-time -> compressed-seconds, capping idle gaps
    (spans where no agent is running) at idle_cap seconds."""
    pts = sorted({p for iv in intervals for p in iv})
    if len(pts) < 2:
        return (lambda t: 0.0), 1.0
    comp = {pts[0]: 0.0}
    cum = 0.0
    for i in range(1, len(pts)):
        a, b = pts[i - 1], pts[i]
        dt = (b - a).total_seconds()
        active = any(s <= a and e >= b for s, e in intervals)
        if not active and dt > idle_cap:
            dt = idle_cap
        cum += dt
        comp[b] = cum

    def remap(t):
        if t <= pts[0]:
            return 0.0
        if t >= pts[-1]:
            return cum
        i = bisect.bisect_right(pts, t) - 1
        a, b = pts[i], pts[i + 1]
        seg_real = (b - a).total_seconds()
        seg_comp = comp[b] - comp[a]
        frac = 0 if seg_real == 0 else (t - a).total_seconds() / seg_real
        return comp[a] + frac * seg_comp

    return remap, (cum or 1.0)


# --------------------------------------------------------------------------- #
# Formatting
# --------------------------------------------------------------------------- #

def fmt_dur(s):
    if s is None:
        return "—"
    s = max(0, s)
    if s < 1:
        return f"{s*1000:.0f}ms"
    if s < 60:
        return f"{s:.1f}s"
    m, sec = divmod(int(s), 60)
    if m < 60:
        return f"{m}m {sec}s"
    h, m = divmod(m, 60)
    return f"{h}h {m}m"


def fmt_ts(dt):
    return dt.strftime("%H:%M:%S") if dt else "—"


def fmt_num(n):
    return f"{n:,}"


def esc(s):
    return html.escape(str(s))


def truncate(s, n=280):
    s = (s or "").strip()
    return s if len(s) <= n else s[: n - 1] + "…"


PALETTE = ["#6366f1", "#ec4899", "#14b8a6", "#f59e0b", "#8b5cf6", "#ef4444",
           "#10b981", "#3b82f6", "#f97316", "#a855f7", "#06b6d4", "#84cc16"]


def colour_map(names):
    return {n: PALETTE[i % len(PALETTE)] for i, n in enumerate(sorted(names))}


# --------------------------------------------------------------------------- #
# Rendering
# --------------------------------------------------------------------------- #

def render(data, source_name, compact):
    meta = data["meta"]
    first, last = data["first_ts"], data["last_ts"]
    total_dur = (last - first).total_seconds() if first and last else 0
    agents = data["agents"]
    errors = data["errors"]
    colours = colour_map({a["subagent"] for a in agents})

    tok = data["tokens"]
    total_cache = (tok.get("cache_read_input_tokens", 0)
                   + tok.get("cache_creation_input_tokens", 0))
    n_err = sum(1 for e in errors if e["kind"] == "error")
    n_rej = sum(1 for e in errors if e["kind"] == "rejected")
    issues_caught = sum(1 for a in agents if a.get("caught"))

    title = meta.get("title") or source_name

    has_sub = data.get("has_sub")
    files = data["files_all"] if has_sub else data["files"]
    tool_stats = data["tool_stats_all"] if has_sub else data["tool_stats"]
    scope = " (incl. subagents)" if has_sub else ""

    cards = [
        ("Duration", fmt_dur(total_dur)),
        ("Agent runs", str(len(agents))),
        (f"Tool calls{scope}", str(sum(s["count"] for s in tool_stats.values()))),
        (f"Files touched{scope}", str(len(files))),
        ("Errors", str(n_err)),
        ("Rejections", str(n_rej)),
        ("Issues caught by gates", str(issues_caught)),
        ("Output tokens", fmt_num(tok.get("output_tokens", 0))),
    ]
    cards_html = "\n".join(
        f'<div class="card"><div class="card-val">{esc(v)}</div>'
        f'<div class="card-lbl">{esc(l)}</div></div>' for l, v in cards)

    gantt_html, note = render_gantt(agents, first, total_dur, colours, compact)
    legend = " ".join(f'<span class="legend"><i style="background:{c}"></i>{esc(n)}</span>'
                      for n, c in colours.items())

    value_html = render_value_table(agents, colours, has_sub)
    insights_html = render_insights(agents, errors)
    gates_html = render_gate_panel(agents, colours)
    errors_html = render_error_panel(errors)
    scope_note = ('<p class="note">Includes tool calls and file access from '
                  'inside every subagent transcript, not just the top-level '
                  'session.</p>' if has_sub else "")
    tools_html = scope_note + render_tools(tool_stats)
    files_html = scope_note + render_files(files)
    feed_html = render_feed(data["timeline"], colours)

    span = f"{fmt_ts(first)} → {fmt_ts(last)}" if first else "—"
    generated = datetime.now().strftime("%Y-%m-%d %H:%M")

    return HTML_TEMPLATE.format(
        title=esc(truncate(title, 90)),
        subtitle=esc(f"{source_name} · {span} · generated {generated}"),
        meta_line=esc(" · ".join(filter(None, [
            meta.get("gitBranch", ""), meta.get("cwd", ""),
            f'v{meta["version"]}' if meta.get("version") else ""]))),
        cards=cards_html, insights=insights_html,
        gantt_note=note, legend=legend, gantt=gantt_html,
        value=value_html, gates=gates_html, errors=errors_html,
        tools=tools_html, files=files_html, feed=feed_html)


def render_gantt(agents, first, total_dur, colours, compact):
    timed = [a for a in agents if a["start"]]
    if not timed:
        return '<p class="empty">No agent runs recorded.</p>', ""
    note = ""
    if compact:
        intervals = [(a["start"], a["end"] or a["start"]) for a in timed]
        remap, span = build_remap(intervals)
        def pos(a):
            s = remap(a["start"])
            e = remap(a["end"]) if a["end"] else s
            return 100 * s / span, 100 * (e - s) / span
        note = "idle gaps collapsed — bar widths still proportional to real duration"
    else:
        span = total_dur or 1
        def pos(a):
            off = (a["start"] - first).total_seconds()
            d = a["duration"] or 0
            return 100 * off / span, 100 * d / span

    rows = []
    for a in timed:
        left, width = pos(a)
        width = max(0.7, width)
        colour = colours[a["subagent"]]
        status = ("pending" if a["pending"]
                  else "caught" if a.get("caught")
                  else "error" if a["error"] else "ok")
        label = f'{a["subagent"]} · {fmt_dur(a["duration"])}'
        tip = (f'{a["subagent"]} — {a["description"]}\n'
               f'start {fmt_ts(a["start"])} · {fmt_dur(a["duration"])}'
               f'{" · BACKGROUND" if a["background"] else ""}'
               f'{" · " + a["verdict"] if a.get("verdict") and a["verdict"] != "—" else ""}'
               f'{" · FAILED/REJECTED" if a["error"] else ""}')
        rows.append(f'''
        <div class="gantt-row">
          <div class="gantt-label" title="{esc(a["description"])}">{esc(truncate(a["description"] or a["subagent"], 44))}</div>
          <div class="gantt-track">
            <div class="gantt-bar {status}" style="left:{left:.2f}%;width:{width:.2f}%;background:{colour}"
                 title="{esc(tip)}"><span>{esc(label)}</span></div>
          </div>
        </div>''')
    return "\n".join(rows), note


def render_value_table(agents, colours, has_sub=False):
    if not agents:
        return '<p class="empty">No agents.</p>'
    stats = defaultdict(lambda: {"runs": 0, "total": 0.0, "gate": False,
                                 "caught": 0, "err": 0, "calls": 0,
                                 "files": 0, "out_tok": 0})
    for a in agents:
        s = stats[a["subagent"]]
        s["runs"] += 1
        s["total"] += a["duration"] or 0
        s["gate"] = a["gate"]
        if a.get("caught"):
            s["caught"] += 1
        if a["error"]:
            s["err"] += 1
        sub = a.get("sub")
        if sub:
            s["calls"] += sub["tool_calls"]
            s["files"] += len(sub["file_ops"])
            s["out_tok"] += sub["tokens"].get("output_tokens", 0)
    rows = []
    for name, s in sorted(stats.items(), key=lambda kv: -kv[1]["total"]):
        avg = s["total"] / s["runs"] if s["runs"] else 0
        if s["gate"]:
            val = (f'<span class="pill good">caught {s["caught"]}/{s["runs"]}</span>'
                   if s["caught"] else
                   f'<span class="pill warn">0/{s["runs"]} — approved all</span>')
        else:
            val = '<span class="muted">producer</span>'
        errc = f'<span class="pill bad">{s["err"]}</span>' if s["err"] else "0"
        dot = f'<i class="dot" style="background:{colours[name]}"></i>'
        work = (f'<td class="num">{s["calls"]}</td>'
                f'<td class="num">{s["files"]}</td>'
                f'<td class="num muted">{fmt_num(s["out_tok"])}</td>') if has_sub else ""
        rows.append(f'''<tr>
          <td>{dot}{esc(name)}{' <span class="gate-tag">gate</span>' if s["gate"] else ''}</td>
          <td class="num">{s["runs"]}</td><td class="num">{fmt_dur(s["total"])}</td>
          <td class="num">{fmt_dur(avg)}</td>{work}
          <td>{val}</td><td class="num">{errc}</td></tr>''')
    work_head = ('<th class="num">Tool calls</th><th class="num">Files</th>'
                 '<th class="num">Out tokens</th>') if has_sub else ""
    hint = ('<p class="note">Tool calls / Files / Out tokens are the work done '
            '<em>inside</em> each subagent (summed across its runs).</p>'
            if has_sub else "")
    return f'''{hint}<table class="vtable">
      <thead><tr><th>Subagent</th><th class="num">Runs</th><th class="num">Total time</th>
      <th class="num">Avg</th>{work_head}<th>Gate value</th><th class="num">Errors</th></tr></thead>
      <tbody>{''.join(rows)}</tbody></table>'''


def render_insights(agents, errors):
    out = []
    gates = [a for a in agents if a["gate"]]
    by = defaultdict(list)
    for a in gates:
        by[a["subagent"]].append(a)
    for name, runs in sorted(by.items()):
        caught = [r for r in runs if r.get("caught")]
        if caught:
            total_issues = sum(r["issues"] or 0 for r in caught)
            extra = f" ({total_issues} issues)" if total_issues else ""
            out.append(("good",
                f'{name} proved its worth — caught something in '
                f'{len(caught)}/{len(runs)} runs{extra}.'))
        else:
            out.append(("warn",
                f'{name} approved all {len(runs)} runs with no findings — '
                f'either the upstream work was clean, or this gate is low-signal.'))
    if errors:
        ne = sum(1 for e in errors if e["kind"] == "error")
        nr = sum(1 for e in errors if e["kind"] == "rejected")
        if ne:
            top = Counter(e["tool"] for e in errors if e["kind"] == "error").most_common(1)[0]
            out.append(("bad", f'{ne} command/tool errors — most in {top[0]} '
                               f'({top[1]}). Worth investigating.'))
        if nr:
            out.append(("warn", f'{nr} tool calls you rejected — friction points '
                               f'where the agent guessed wrong.'))
    slow = sorted((a for a in agents if a["duration"]), key=lambda a: -a["duration"])[:1]
    if slow:
        a = slow[0]
        out.append(("info", f'Slowest agent: {a["subagent"]} '
                           f'"{truncate(a["description"], 40)}" at {fmt_dur(a["duration"])}.'))
    if not out:
        return ""
    items = "\n".join(f'<li class="ins {c}">{esc(t)}</li>' for c, t in out)
    return f'<ul class="insights">{items}</ul>'


def render_gate_panel(agents, colours):
    gates = [a for a in agents if a["gate"]]
    if not gates:
        return '<p class="empty">No review/gate agents ran.</p>'
    rows = []
    for a in gates:
        caught = a.get("caught")
        cls = "caught" if caught else "clean"
        badge = a.get("verdict") or "—"
        colour = colours[a["subagent"]]
        snip = f'<div class="gate-snip">{esc(truncate(a["snippet"], 260))}</div>' if a.get("snippet") else ""
        rows.append(f'''
        <div class="gate-item {cls}">
          <div class="gate-head">
            <span class="agent-badge" style="background:{colour}">{esc(a["subagent"])}</span>
            <span class="verdict {cls}">{esc(badge)}</span>
            <span class="muted">{esc(fmt_ts(a["start"]))} · {esc(fmt_dur(a["duration"]))}</span>
          </div>
          <div class="gate-desc">{esc(truncate(a["description"], 90))}</div>
          {snip}
        </div>''')
    return "\n".join(rows)


def render_error_panel(errors):
    if not errors:
        return '<p class="empty">No errors or rejected tool calls. 🎉</p>'
    rows = []
    for e in errors:
        cls = e["kind"]
        hint = summarize_tool_input(e["tool"], e["input"])
        rows.append(f'''
        <div class="err-item {cls}">
          <div class="err-time">{esc(fmt_ts(e["ts"]))}</div>
          <div class="err-body">
            <div><span class="tool-tag">{esc(e["tool"])}</span>
              <span class="pill {'bad' if cls=='error' else 'warn'}">{esc(cls)}</span></div>
            <div class="mono">{esc(truncate(hint, 160))}</div>
            <div class="err-msg">{esc(truncate(e["text"], 220))}</div>
          </div>
        </div>''')
    return "\n".join(rows)


def render_tools(stats):
    if not stats:
        return '<p class="empty">No tool calls.</p>'
    mx = max(s["count"] for s in stats.values())
    rows = []
    for name, s in sorted(stats.items(), key=lambda kv: -kv[1]["count"]):
        errc = f'<span class="pill bad">{s["err"]}</span>' if s["err"] else "—"
        rows.append(f'''<tr>
          <td>{esc(name)}</td>
          <td><div class="tool-bar-wrap"><div class="tool-bar" style="width:{100*s["count"]/mx:.1f}%"></div></div></td>
          <td class="num">{s["count"]}</td>
          <td class="num muted">{esc(fmt_dur(s["time"]))}</td>
          <td class="num">{errc}</td></tr>''')
    return f'''<table class="vtable">
      <thead><tr><th>Tool</th><th style="width:38%"></th><th class="num">Calls</th>
      <th class="num">Total time</th><th class="num">Errors</th></tr></thead>
      <tbody>{''.join(rows)}</tbody></table>'''


def render_files(files):
    if not files:
        return '<p class="empty">No files read, written, or edited.</p>'
    rows = []
    ordered = sorted(files.items(),
                     key=lambda kv: -(kv[1]["read"] + kv[1]["write"] + kv[1]["edit"]))
    for path, f in ordered[:60]:
        total = f["read"] + f["write"] + f["edit"]
        badges = []
        if f["write"]:
            badges.append(f'<span class="fop write">write ×{f["write"]}</span>')
        if f["edit"]:
            badges.append(f'<span class="fop edit">edit ×{f["edit"]}</span>')
        if f["read"]:
            badges.append(f'<span class="fop read">read ×{f["read"]}</span>')
        short = path.replace("/Users/", "~/").rsplit("/", 4)
        disp = "/".join(short[-4:]) if len(short) > 4 else path
        rows.append(f'''<tr>
          <td class="fpath" title="{esc(path)}">{esc(disp)}</td>
          <td>{' '.join(badges)}</td>
          <td class="num muted">{total}</td></tr>''')
    more = (f'<p class="note">…and {len(ordered) - 60} more files.</p>'
            if len(ordered) > 60 else "")
    created = sum(1 for _, f in files.items() if f["write"] and not f["read"] and not f["edit"])
    summary = (f'<p class="note">{len(files)} files touched · '
               f'{created} written fresh (no prior read).</p>')
    return f'''{summary}<table class="vtable">
      <thead><tr><th>File</th><th>Operations</th><th class="num">Total</th></tr></thead>
      <tbody>{''.join(rows)}</tbody></table>{more}'''


def render_feed(timeline, colours):
    feed = []
    for it in timeline:
        ts = fmt_ts(it["ts"])
        k = it["kind"]
        if k == "user":
            feed.append(feed_item(ts, "user", "You", truncate(it["text"], 600)))
        elif k == "assistant":
            feed.append(feed_item(ts, "assistant", "Claude", truncate(it["text"], 600)))
        elif k == "thinking":
            feed.append(feed_item(ts, "thinking", "Thinking", truncate(it["text"], 400)))
        elif k == "tool":
            feed.append(render_tool_feed(ts, it, colours))
    return "\n".join(feed) or '<p class="empty">Nothing to show.</p>'


def feed_item(ts, cls, who, body):
    return f'''<div class="feed {cls}"><div class="feed-time">{esc(ts)}</div>
      <div class="feed-body"><div class="feed-who">{esc(who)}</div>
      <div class="feed-text">{esc(body)}</div></div></div>'''


def render_tool_feed(ts, it, colours):
    tool = it["tool"]
    inp = it.get("input") or {}
    dur = fmt_dur(it.get("duration"))
    if tool == "Agent":
        sub = inp.get("subagent_type", "agent")
        colour = colours.get(sub, "#6366f1")
        badge = f'<span class="agent-badge" style="background:{colour}">{esc(sub)}</span>'
        verdict = it.get("verdict")
        vhtml = ""
        if verdict and verdict != "—":
            vc = "caught" if it.get("caught") else "clean"
            vhtml = f' <span class="verdict {vc}">{esc(verdict)}</span>'
        res = (f'<div class="feed-result">{esc(truncate(it.get("result",""), 400))}</div>'
               if it.get("result") else "")
        stat = " ⚠ failed/rejected" if it.get("error") else ""
        return f'''<div class="feed tool agent"><div class="feed-time">{esc(ts)}</div>
          <div class="feed-body">
          <div class="feed-who">{badge} ran <span class="dur">{esc(dur)}</span>{vhtml}{esc(stat)}</div>
          <div class="feed-text">{esc(truncate(inp.get("description",""), 200))}</div>{res}</div></div>'''
    stat = ' <span class="err">⚠</span>' if it.get("error") else ""
    return f'''<div class="feed tool"><div class="feed-time">{esc(ts)}</div>
      <div class="feed-body"><div class="feed-who"><span class="tool-tag">{esc(tool)}</span>
      <span class="dur">{esc(dur)}</span>{stat}</div>
      <div class="feed-text mono">{esc(summarize_tool_input(tool, inp))}</div></div></div>'''


def summarize_tool_input(tool, inp):
    if not isinstance(inp, dict):
        return truncate(str(inp), 160)
    for key in ("command", "file_path", "path", "pattern", "query",
                "description", "url", "prompt", "message", "skill", "title"):
        if inp.get(key):
            return truncate(str(inp[key]).replace("\n", " "), 200)
    return truncate(json.dumps(inp)[:200], 200)


# --------------------------------------------------------------------------- #
HTML_TEMPLATE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} — session report</title>
<style>
:root {{ --bg:#0f1117; --panel:#171a23; --panel2:#1e222d; --line:#2a2f3c;
  --fg:#e6e8ee; --muted:#9aa2b4; --accent:#6366f1;
  --good:#10b981; --warn:#f59e0b; --bad:#ef4444; }}
@media (prefers-color-scheme: light) {{ :root {{ --bg:#f6f7f9; --panel:#fff;
  --panel2:#f0f2f6; --line:#e2e5ec; --fg:#1c2027; --muted:#5c6472; }} }}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--bg); color:var(--fg);
  font:14px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }}
.wrap {{ max-width:1120px; margin:0 auto; padding:32px 20px 90px; }}
h1 {{ font-size:24px; margin:0 0 4px; }}
.sub {{ color:var(--muted); font-size:13px; }}
.meta {{ color:var(--muted); font-size:12px; margin-top:4px; word-break:break-all; }}
h2 {{ font-size:14px; text-transform:uppercase; letter-spacing:.06em;
  color:var(--muted); margin:36px 0 12px; }}
.cards {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(135px,1fr));
  gap:12px; margin-top:22px; }}
.card {{ background:var(--panel); border:1px solid var(--line); border-radius:12px; padding:15px; }}
.card-val {{ font-size:21px; font-weight:650; }}
.card-lbl {{ color:var(--muted); font-size:12px; margin-top:2px; }}
.panel {{ background:var(--panel); border:1px solid var(--line); border-radius:12px; padding:18px; }}
.insights {{ list-style:none; padding:0; margin:18px 0 0; display:grid; gap:8px; }}
.ins {{ padding:10px 14px; border-radius:10px; border-left:3px solid var(--muted);
  background:var(--panel); font-size:13px; }}
.ins.good {{ border-color:var(--good); }} .ins.warn {{ border-color:var(--warn); }}
.ins.bad {{ border-color:var(--bad); }} .ins.info {{ border-color:var(--accent); }}
.legend {{ display:inline-flex; align-items:center; gap:6px; margin:0 12px 8px 0;
  font-size:12px; color:var(--muted); }}
.legend i {{ width:11px; height:11px; border-radius:3px; display:inline-block; }}
.note {{ color:var(--muted); font-size:12px; margin:-4px 0 10px; font-style:italic; }}
.gantt-row {{ display:flex; align-items:center; gap:12px; margin:6px 0; }}
.gantt-label {{ width:220px; flex:none; font-size:12px; color:var(--muted);
  overflow:hidden; text-overflow:ellipsis; white-space:nowrap; text-align:right; }}
.gantt-track {{ position:relative; flex:1; height:24px; background:var(--panel2);
  border-radius:6px; overflow:hidden; }}
.gantt-bar {{ position:absolute; top:0; height:100%; border-radius:6px; min-width:3px;
  display:flex; align-items:center; opacity:.92; }}
.gantt-bar:hover {{ opacity:1; }}
.gantt-bar span {{ font-size:11px; color:#fff; padding:0 7px; white-space:nowrap;
  overflow:hidden; text-shadow:0 1px 1px rgba(0,0,0,.4); }}
.gantt-bar.caught {{ outline:2px solid var(--good); outline-offset:-2px; }}
.gantt-bar.error {{ outline:2px solid var(--bad); outline-offset:-2px; }}
.gantt-bar.pending {{ background-image:repeating-linear-gradient(45deg,
  rgba(255,255,255,.15) 0 6px, transparent 6px 12px)!important; }}
.vtable {{ width:100%; border-collapse:collapse; font-size:13px; }}
.vtable th {{ text-align:left; color:var(--muted); font-weight:600; font-size:12px;
  padding:8px 10px; border-bottom:1px solid var(--line); }}
.vtable td {{ padding:9px 10px; border-bottom:1px solid var(--line); }}
.dot {{ width:10px; height:10px; border-radius:3px; display:inline-block; margin-right:7px; }}
.gate-tag {{ font-size:10px; background:var(--accent); color:#fff; padding:1px 6px;
  border-radius:5px; margin-left:6px; }}
.pill {{ font-size:11px; padding:2px 9px; border-radius:20px; font-weight:600; }}
.pill.good {{ background:rgba(16,185,129,.16); color:var(--good); }}
.pill.warn {{ background:rgba(245,158,11,.16); color:var(--warn); }}
.pill.bad {{ background:rgba(239,68,68,.16); color:var(--bad); }}
.muted {{ color:var(--muted); }}
.gate-item {{ border:1px solid var(--line); border-left:3px solid var(--muted);
  border-radius:10px; padding:12px 14px; margin:8px 0; background:var(--panel); }}
.gate-item.caught {{ border-left-color:var(--good); }}
.gate-item.clean {{ border-left-color:var(--muted); opacity:.85; }}
.gate-head {{ display:flex; align-items:center; gap:10px; flex-wrap:wrap; }}
.gate-desc {{ margin-top:5px; font-size:13px; }}
.gate-snip {{ margin-top:7px; padding:8px 10px; background:var(--panel2);
  border-radius:8px; font-size:12px; color:var(--muted); }}
.verdict {{ font-size:11px; font-weight:700; padding:2px 9px; border-radius:6px; }}
.verdict.caught {{ background:rgba(16,185,129,.18); color:var(--good); }}
.verdict.clean {{ background:var(--panel2); color:var(--muted); }}
.err-item {{ display:flex; gap:12px; padding:11px 0; border-top:1px solid var(--line); }}
.err-item .err-time {{ width:66px; flex:none; color:var(--muted); font-size:12px; }}
.err-body {{ flex:1; min-width:0; }}
.err-msg {{ margin-top:5px; font-size:12px; color:var(--bad); white-space:pre-wrap; }}
.err-item.rejected .err-msg {{ color:var(--warn); }}
.tool-bar-wrap {{ background:var(--panel2); border-radius:5px; height:14px; min-width:60px; }}
.tool-bar {{ height:100%; background:var(--accent); border-radius:5px; }}
.vtable td.num {{ text-align:right; font-variant-numeric:tabular-nums; }}
.vtable th.num {{ text-align:right; }}
.fpath {{ font-family:ui-monospace,Menlo,monospace; font-size:12px; word-break:break-all; }}
.fop {{ display:inline-block; font-size:10px; font-weight:600; padding:1px 7px;
  border-radius:20px; margin-right:5px; }}
.fop.write {{ background:rgba(99,102,241,.16); color:var(--accent); }}
.fop.edit {{ background:rgba(245,158,11,.16); color:var(--warn); }}
.fop.read {{ background:var(--panel2); color:var(--muted); }}
.feed {{ display:flex; gap:14px; padding:12px 0; border-top:1px solid var(--line); }}
.feed-time {{ width:66px; flex:none; color:var(--muted); font-size:12px;
  font-variant-numeric:tabular-nums; }}
.feed-body {{ flex:1; min-width:0; }}
.feed-who {{ font-size:12px; font-weight:600; margin-bottom:3px; }}
.feed-text {{ white-space:pre-wrap; word-wrap:break-word; }}
.feed-text.mono {{ font-family:ui-monospace,Menlo,monospace; font-size:12px; color:var(--muted); }}
.feed-result {{ margin-top:6px; padding:8px 10px; background:var(--panel2);
  border-radius:8px; font-size:12px; color:var(--muted); white-space:pre-wrap; }}
.feed.user .feed-who {{ color:var(--good); }}
.feed.assistant .feed-who {{ color:var(--accent); }}
.feed.thinking {{ opacity:.7; }} .feed.thinking .feed-who {{ color:#a855f7; }}
.feed.thinking .feed-text {{ font-style:italic; }}
.agent-badge, .tool-tag {{ display:inline-block; padding:1px 8px; border-radius:6px;
  color:#fff; font-size:11px; font-weight:600; }}
.tool-tag {{ background:var(--panel2); color:var(--muted); }}
.dur {{ color:var(--muted); font-weight:400; font-size:11px; }}
.err {{ color:var(--bad); }} .empty {{ color:var(--muted); font-style:italic; }}
.filters {{ margin:10px 0 4px; display:flex; gap:8px; flex-wrap:wrap; }}
.filters button {{ background:var(--panel2); color:var(--fg); border:1px solid var(--line);
  border-radius:20px; padding:4px 13px; font-size:12px; cursor:pointer; }}
.filters button.active {{ background:var(--accent); color:#fff; border-color:var(--accent); }}
</style></head>
<body><div class="wrap">
  <h1>{title}</h1>
  <div class="sub">{subtitle}</div>
  <div class="meta">{meta_line}</div>
  <div class="cards">{cards}</div>

  <h2>Insights</h2>{insights}

  <h2>Agent timeline</h2>
  <div class="note">{gantt_note}</div>
  <div style="margin-bottom:10px">{legend}</div>
  <div class="panel">{gantt}</div>

  <h2>Subagent value &amp; efficiency</h2>
  <div class="panel">{value}</div>

  <h2>Review-gate value — what the reviewers caught</h2>
  <div class="panel">{gates}</div>

  <h2>Errors &amp; friction</h2>
  <div class="panel">{errors}</div>

  <h2>Tool usage</h2>
  <div class="panel">{tools}</div>

  <h2>Files touched</h2>
  <div class="panel">{files}</div>

  <h2>Activity feed</h2>
  <div class="filters">
    <button data-f="all" class="active">All</button>
    <button data-f="user">Prompts</button>
    <button data-f="assistant">Decisions</button>
    <button data-f="agent">Agents</button>
    <button data-f="tool">Tools</button>
    <button data-f="thinking">Thinking</button>
  </div>
  <div id="feed">{feed}</div>
</div>
<script>
const btns=[...document.querySelectorAll('.filters button')];
const items=[...document.querySelectorAll('#feed .feed')];
btns.forEach(b=>b.onclick=()=>{{
  btns.forEach(x=>x.classList.remove('active')); b.classList.add('active');
  const f=b.dataset.f;
  items.forEach(it=>{{
    let show = f==='all' || it.classList.contains(f)
      || (f==='tool' && it.classList.contains('tool') && !it.classList.contains('agent'));
    it.style.display = show ? '' : 'none';
  }});
}});
</script>
</body></html>"""


# --------------------------------------------------------------------------- #
def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("session", help="path to the session .jsonl log")
    ap.add_argument("-o", "--output", help="output HTML path (default: "
                    "reports/sessions/<session>.report.html under the current "
                    "project so reports live alongside the code)")
    ap.add_argument("--compact", action="store_true",
                    help="collapse idle gaps in the agent timeline")
    ap.add_argument("--no-subagents", action="store_true",
                    help="ignore the subagents/ transcripts (top-level only)")
    ap.add_argument("--open", action="store_true",
                    help="open the report in a browser when done")
    args = ap.parse_args()

    src = Path(args.session)
    if not src.exists():
        ap.error(f"no such file: {src}")
    events = load_events(src)
    if not events:
        ap.error("no parseable events found")

    data = analyze(events)
    subs = {} if args.no_subagents else load_subagents(src)
    if subs:
        link_subagents(data, subs)
    if args.output:
        out = Path(args.output)
    else:
        # default: keep reports with the project, under reports/sessions/
        out = Path.cwd() / "reports" / "sessions" / (src.stem + ".report.html")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render(data, src.name, args.compact), encoding="utf-8")

    n_caught = sum(1 for a in data["agents"] if a.get("caught"))
    n_err = sum(1 for e in data["errors"] if e["kind"] == "error")
    sub_note = (f" · {len(subs)} subagent transcripts "
                f"({sum(s['tool_calls'] for s in subs.values())} inner tool calls)"
                if subs else " · no subagent transcripts found")
    print(f"parsed {len(events)} events · {len(data['agents'])} agent runs · "
          f"{n_caught} issues caught by gates · {n_err} errors{sub_note}")
    print(f"wrote {out}")
    if args.open:
        webbrowser.open(out.resolve().as_uri())


if __name__ == "__main__":
    main()
