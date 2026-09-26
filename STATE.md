# android-edge-llm-server — Stato

> Foto del presente: riscrivi, non accodare.

## Obiettivo

Server LLM su Android, sempre attivo, con API OpenAI e Ollama compatibili al punto che
client reali (SDK `openai`, Open WebUI, un harness agentico) funzionano senza
adattamenti; ottimizzato per la latenza; UI minimale per controllo e collaudo.

**Criterio di riuscita:** matrice di [`docs/api-contract.md`](docs/api-contract.md) §0
tutta ✅ (o ⛔ dichiarato), verificata con client reali su dispositivo fisico, APK
compilato dalla CI.

## Stato attuale

Tag **`v1`** (commit `2e73844`, merge PR #8) = primo traguardo. In corso **v2** (app 2.0.0)
nella PR #9 sul branch `claude/llm-edge-devices-onnx-gguf-75v46c`:
- M0 riordino documentale: fatto.
- M1–M6 codice pronto, coperto da test JVM, **non ancora collaudato su device**:
  API OpenAI/Ollama complete secondo la matrice di `docs/api-contract.md`, tool calling
  nativo LiteRT-LM, `stop`/`max_tokens`/JSON/usage, riuso del KV-cache tra richieste,
  API key opzionale, pre-flight RAM e crash marker, selettore CTX.
- M7 parziale: tab TEST con `[ BENCHMARK ]` e `[ TOOL CALL ]`; split di `MainActivity.kt` rinviato.

Da verificare: CI verde sulla PR #9, poi collaudo su device (Pixel 9, Galaxy S20 FE).

## Prossimo passo

1. CI verde sulla PR #9 (l'agente corregge finché serve).
2. Collaudo di Luigi con l'APK della CI: tab TEST → `[ TOOL CALL ]` (atteso 4/4 PASS) e
   `[ BENCHMARK ]` con GPU ON e OFF; incollare le tabelle nella PR.
3. Con i numeri: aggiornare "Misure" e decidere sul pivot (`docs/api-contract.md` §6).

## Misure

| Device | Backend | Modello | TTFT ~500 tok | TTFT ~2000 tok | TTFT ~4000 tok | Decode tok/s |
|---|---|---|---|---|---|---|
| — | — | — | da misurare (M1) | | | |

## Decisioni e vincoli attivi

- Nessun build locale: compilazione e test solo via GitHub Actions, su PR verso `main`.
- LiteRT-LM 0.16.1 unico motore, usato con l'API nativa (storico, tool, JSON); altri motori solo su bisogno concreto (backlog).
- Server stateless; una sola inferenza alla volta (RequestQueue).
- UI programmatica Kotlin, stile TUI; separazione ENGINE/DAEMON conservata.
- Licenza PolyForm Noncommercial 1.0.0.

## Richieste attive

Nessuna.

## Review

Nessuna.

## Riferimenti

- [Roadmap](docs/roadmap.md) · [Contratto API](docs/api-contract.md) · [Backlog](docs/backlog.md)
- [Decisioni](DECISIONI.md) · [Architettura](docs/architecture.md) · [Indice docs](docs/index.md)
