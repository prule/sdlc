# reports/sessions

Generated **session reports** — one self-contained HTML file per Claude Code
session, kept with the project so a run can be analysed alongside the code it
produced.

Create one with the `session-report` skill (just ask Claude to *"analyse this
session"*) or directly:

```bash
python3 .claude/skills/session-report/session_report.py \
    ~/.claude/projects/-Users-paulrule-projects-current-sdlc/<session-id>.jsonl \
    --compact
```

Files here are named `<session-id>.report.html`. See
[`.claude/skills/session-report/README.md`](../../.claude/skills/session-report/README.md)
for what the report contains and how to read it.
