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

Tag **`v1`** (commit `2e73844`, merge PR #8): primo traguardo raggiunto.
- Foreground Service 24/7 con Ktor CIO, wakelock, WifiLock, riavvio al boot.
- LiteRT-LM 0.16.1 (Gemma 4 E2B `.litertlm`), backend CPU/GPU, testato su Pixel 9.
- `/v1/chat/completions` e `/api/chat` con history completa (`PromptBuilder`) e streaming;
  `/v1/models`, `/api/tags`, `/health`.
- `RequestQueue` FIFO con 429; test JVM per coda e prompt.
- UI TUI a 4 tab (ENGINE, DAEMON, TEST, LOGS), onboarding permessi, audit RAM/SoC.

Lacune note (dettaglio nella matrice): tool calling assente (i `tools` vengono scartati in
silenzio), `max_tokens` accettato ma non applicato, `content` come array non supportato,
endpoint Ollama `/api/version|show|generate|ps` mancanti, nessuna autenticazione.
Prefill mai misurato. Dimensione APK reale da verificare sull'artifact CI.

M0 (riordino documentale) completato su branch `claude/llm-edge-devices-onnx-gguf-75v46c`,
in attesa di PR e CI.

## Prossimo passo

1. Aprire la PR del branch M0 verso `main` e verificare la CI (job
   `bootstrap-verification` con il nuovo elenco file).
2. Avviare **M1 — Misure affidabili** ([`docs/roadmap.md`](docs/roadmap.md)): benchmark
   in-app di TTFT e tok/s, correzione degli script in `scripts/`.

## Misure

| Device | Backend | Modello | TTFT ~500 tok | TTFT ~2000 tok | TTFT ~4000 tok | Decode tok/s |
|---|---|---|---|---|---|---|
| — | — | — | da misurare (M1) | | | |

## Decisioni e vincoli attivi

- Nessun build locale: compilazione e test solo via GitHub Actions, su PR verso `main`.
- LiteRT-LM unico motore; altri motori solo su bisogno concreto (backlog).
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
