# fable5 — Direzione progetto (consulenza 2026-07-05)

Questa directory è l'esito della consulenza Fable 5: giudizio sul progetto, piano operativo a
sessioni agentiche, contratto API target e istruzioni per allineare la documentazione esistente.

**Regola linguistica**: i documenti destinati a Luigi (umano/business) sono in italiano; i
documenti operativi destinati agli agenti di codifica sono in inglese, coerenti col resto del repo.

## File

| File | Per chi | Contenuto |
|---|---|---|
| [verdetto.md](verdetto.md) | Umano/Business (IT) | Giudizio onesto: senso pratico, rischi, continuare/pivotare |
| [roadmap-sessioni.md](roadmap-sessioni.md) | Agenti (EN) | 8 sessioni con scope, criteri di accettazione, Definition of Done |
| [architettura-api.md](architettura-api.md) | Agenti (EN) | Contratto API target: statelessness, tool calling, coda, auth |
| [backlog.md](backlog.md) | Agenti + Umano (EN) | Fase N dichiarata: tutto ciò che NON entra nelle 8 sessioni |
| [correzioni-doc.md](correzioni-doc.md) | Agenti (EN) | Istruzioni per sanare la documentazione esistente (Sessione 0) |
| [tutorial-github-actions.md](tutorial-github-actions.md) | Umano (IT) | Cosa può fare GitHub Actions per questo progetto, con esempi |

## Percorsi di lettura

**Agente esecutore** (prima di ogni sessione):
1. `AGENTS.md` (contratto generale, protocollo startup)
2. [roadmap-sessioni.md](roadmap-sessioni.md) — trova la TUA sessione, leggi scope e criteri
3. [architettura-api.md](architettura-api.md) — se la sessione tocca gli endpoint
4. Solo i file di codice elencati nella sessione. Non leggere altro.

**Umano (Luigi)**:
1. [verdetto.md](verdetto.md)
2. [roadmap-sessioni.md](roadmap-sessioni.md) (tabella riassuntiva in testa)
3. [tutorial-github-actions.md](tutorial-github-actions.md)

**Business/CV**: solo [verdetto.md](verdetto.md), sezioni "Le tue tre domande" e "Rischi".

## Principio LLM-wiki (per la documentazione futura)

- Un file = un tema, sotto le ~150 righe. Se cresce, si spezza e si linka.
- Link relativi sempre; ogni file raggiungibile dall'indice della sua directory.
- I documenti si aggiornano nella sessione che li rende obsoleti, non "dopo".
- Le decisioni non si cancellano: si superano con una nuova voce in `docs/decision-log.md`.
