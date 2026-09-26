# Documentation index

One topic per file; each fact lives in exactly one place.

| File | Contenuto | Lingua |
|---|---|---|
| [`../STATE.md`](../STATE.md) | Stato attuale, prossimo passo (unica fonte di stato) | IT |
| [`../DECISIONI.md`](../DECISIONI.md) | Registro unico delle decisioni durevoli | IT |
| [roadmap.md](roadmap.md) | Obiettivo, milestone M0–M7, criteri di accettazione | IT |
| [backlog.md](backlog.md) | Everything valuable not in the roadmap | EN |
| [api-contract.md](api-contract.md) | HTTP contract and compatibility matrix = definition of "complete" | EN |
| [architecture.md](architecture.md) | Components and boundaries as they are | EN |
| [daemon-stability-guidelines.md](daemon-stability-guidelines.md) | FGS, locks, Doze, LMK rules | EN |
| [ci.md](ci.md) | GitHub Actions: cosa fa, come leggere un fallimento | IT |
| [archive/](archive/README.md) | Historical documents (phases 0–4, Fable 5 consultancy) — not operational | — |

Agent rules: [`../AGENTS.md`](../AGENTS.md) (entry points `CLAUDE.md`, `CODEX.md`, `.agents/`, `.claude/`).

## Reading order

1. `AGENTS.md`
2. `STATE.md`
3. `docs/roadmap.md` — find the current milestone
4. `docs/api-contract.md` — if the milestone touches endpoints
5. `docs/architecture.md`
6. Only the code files the milestone touches.

## Rules

- State only in `STATE.md` (rewrite, don't append). Decisions only in `DECISIONI.md`
  (append; superseded entries are marked, never deleted).
- A document is updated in the same PR that makes it obsolete.
- Files in `archive/` are never updated and never cited as current truth.
