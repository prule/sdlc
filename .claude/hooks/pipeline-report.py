#!/usr/bin/env python3
"""Render logs/pipeline-events.jsonl into a self-contained HTML dashboard.

Usage:  python3 .claude/hooks/pipeline-report.py [path/to/events.jsonl]
Writes logs/pipeline-report.html and prints a short summary to stdout.
"""
import sys, json, os, pathlib, html
from collections import Counter, defaultdict

ROOT = pathlib.Path(os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd())
LOG = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "logs" / "pipeline-events.jsonl"
OUT = ROOT / "logs" / "pipeline-report.html"


def load(path):
    events = []
    if not path.exists():
        return events
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            events.append(json.loads(line))
        except Exception:
            pass
    return events


def summarize(events):
    tools = Counter()
    skills = Counter()
    reads, writes, cmds = [], [], []
    agents = []  # spawn events
    stops = 0
    for e in events:
        if e.get("event") == "PreToolUse":
            t = e.get("tool")
            if t:
                tools[t] += 1
            if t in ("Agent", "Task") and e.get("subagent_type"):
                agents.append(e)
            if t == "Skill" and e.get("skill"):
                skills[e["skill"]] += 1
            if t in ("Read",) and e.get("file_path"):
                reads.append(e["file_path"])
            if t in ("Write", "Edit", "NotebookEdit") and e.get("file_path"):
                writes.append(e["file_path"])
            if t == "Bash" and e.get("command"):
                cmds.append(e["command"])
        elif e.get("event") == "SubagentStop":
            stops += 1
    return {
        "total": len(events),
        "tools": tools,
        "skills": skills,
        "reads": reads,
        "writes": writes,
        "cmds": cmds,
        "agents": agents,
        "stops": stops,
    }


def render(events, s):
    data = json.dumps(events, ensure_ascii=False)
    rows_tools = "".join(
        f"<tr><td>{html.escape(k)}</td><td class='n'>{v}</td></tr>"
        for k, v in s["tools"].most_common()
    ) or "<tr><td colspan=2 class='muted'>none yet</td></tr>"
    rows_skills = "".join(
        f"<tr><td>{html.escape(k)}</td><td class='n'>{v}</td></tr>"
        for k, v in s["skills"].most_common()
    ) or "<tr><td colspan=2 class='muted'>none yet</td></tr>"
    rows_agents = "".join(
        f"<tr><td><span class='pill'>{html.escape(a.get('subagent_type',''))}</span></td>"
        f"<td>{html.escape((a.get('description') or a.get('prompt') or '')[:80])}</td>"
        f"<td class='muted'>{html.escape(a.get('iso',''))}</td></tr>"
        for a in s["agents"]
    ) or "<tr><td colspan=3 class='muted'>no agents spawned yet</td></tr>"

    def file_list(paths):
        seen = list(dict.fromkeys(paths))
        return "".join(f"<li>{html.escape(p)}</li>" for p in seen) or "<li class='muted'>none</li>"

    return f"""<!doctype html><html><head><meta charset=utf-8>
<title>Pipeline Observability</title>
<style>
:root{{--bg:#fbfbfd;--fg:#1d1d21;--mut:#8a8a94;--card:#fff;--line:#e6e6ec;--acc:#4f46e5}}
@media(prefers-color-scheme:dark){{:root{{--bg:#0f0f12;--fg:#e9e9ee;--mut:#8a8a94;--card:#17171c;--line:#26262e;--acc:#8b85ff}}}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--bg);color:var(--fg);font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;padding:24px}}
h1{{font-size:20px;margin:0 0 4px}}.sub{{color:var(--mut);margin:0 0 20px}}
.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:16px;margin-bottom:20px}}
.card{{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px}}
.card h2{{font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:var(--mut);margin:0 0 10px}}
.big{{font-size:28px;font-weight:600}}
table{{width:100%;border-collapse:collapse}}td{{padding:4px 0;border-bottom:1px solid var(--line)}}
.n{{text-align:right;color:var(--acc);font-variant-numeric:tabular-nums}}
.pill{{background:var(--acc);color:#fff;border-radius:6px;padding:1px 8px;font-size:12px;font-weight:600}}
.muted{{color:var(--mut)}}ul{{margin:0;padding-left:18px}}li{{padding:1px 0;font-size:13px;word-break:break-all}}
.timeline{{max-height:420px;overflow:auto;font:12px/1.5 ui-monospace,Menlo,monospace}}
.timeline .r{{display:flex;gap:10px;padding:2px 0;border-bottom:1px solid var(--line)}}
.timeline .t{{color:var(--mut);white-space:nowrap}}.timeline .e{{color:var(--acc);width:120px;flex:none}}
input{{width:100%;padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:var(--card);color:var(--fg);margin-bottom:8px}}
</style></head><body>
<h1>Pipeline Observability</h1>
<p class=sub>{s['total']} events · {len(s['agents'])} agent spawns · {s['stops']} completions · source: logs/pipeline-events.jsonl</p>
<div class=grid>
  <div class=card><h2>Agents spawned</h2><div class=big>{len(s['agents'])}</div></div>
  <div class=card><h2>Tool calls</h2><div class=big>{sum(s['tools'].values())}</div></div>
  <div class=card><h2>Files written</h2><div class=big>{len(dict.fromkeys(s['writes']))}</div></div>
  <div class=card><h2>Skills invoked</h2><div class=big>{sum(s['skills'].values())}</div></div>
</div>
<div class=grid>
  <div class=card><h2>Agents</h2><table>{rows_agents}</table></div>
  <div class=card><h2>Tools used</h2><table>{rows_tools}</table></div>
  <div class=card><h2>Skills used</h2><table>{rows_skills}</table></div>
</div>
<div class=grid>
  <div class=card><h2>Files written / edited</h2><ul>{file_list(s['writes'])}</ul></div>
  <div class=card><h2>Files read</h2><ul>{file_list(s['reads'])}</ul></div>
</div>
<div class=card><h2>Event timeline</h2>
  <input id=f placeholder="filter (e.g. Skill, Agent, Edit, .java) …">
  <div class=timeline id=tl></div>
</div>
<script>
const EV = {data};
const tl = document.getElementById('tl'), f = document.getElementById('f');
function detail(e){{return e.subagent_type||e.skill||e.file_path||e.command||e.user_prompt||'';}}
function draw(q=''){{
  q=q.toLowerCase();
  tl.innerHTML = EV.filter(e=>!q||JSON.stringify(e).toLowerCase().includes(q)).map(e=>
    `<div class=r><span class=t>${{(e.iso||'').slice(11)}}</span><span class=e>${{e.event||''}} ${{e.tool||''}}</span><span>${{(detail(e)+'').slice(0,140).replace(/</g,'&lt;')}}</span></div>`
  ).join('')||'<div class=muted>no matching events</div>';
}}
f.addEventListener('input',()=>draw(f.value)); draw();
</script>
</body></html>"""


def main():
    events = load(LOG)
    s = summarize(events)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(render(events, s), encoding="utf-8")
    print(f"events={s['total']} agents={len(s['agents'])} tool_calls={sum(s['tools'].values())} "
          f"skills={sum(s['skills'].values())} files_written={len(dict.fromkeys(s['writes']))}")
    print("top tools:", ", ".join(f"{k}={v}" for k, v in s['tools'].most_common(6)) or "none")
    print("report:", OUT)


if __name__ == "__main__":
    main()
