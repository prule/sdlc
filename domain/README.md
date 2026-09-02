# Domain knowledge base

Business/domain context that the code, git history, and `standards/` **cannot** tell you — the
"what and why" of the product. Agents (especially `ticket-writer` and `architect`) read this to write
tickets and plans that use the right language, respect real business rules, and land in the right
bounded context.

Keep it **current and factual**. If a ticket reveals a new term, rule, or context, add it here as part
of finishing the work. Prefer short, precise entries over prose.

| File | What goes in it |
|------|-----------------|
| [overview.md](overview.md) | Product vision, goals, the problem being solved, primary personas |
| [glossary.md](glossary.md) | Ubiquitous language — one agreed definition per business term |
| [bounded-contexts.md](bounded-contexts.md) | The subdomains/capabilities, their responsibilities and relationships |
| [actors-and-personas.md](actors-and-personas.md) | Who uses the system (human roles) and external systems it talks to |
| [business-rules.md](business-rules.md) | Cross-cutting policies and invariants that hold across features |

**This is not:** API contracts (see `openspec/specs/`), engineering standards (see `standards/`), or
implementation detail. It is the domain — the language and rules of the business.

> Much of this file set is a **template seeded with what's known so far**. Fill the `TODO` markers
> with real domain knowledge; that is what makes the ticket-writer genuinely useful.
