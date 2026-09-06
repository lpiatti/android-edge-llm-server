# android-edge-llm-server — Stato

> Foto del presente: riscrivi, non accodare.

## Obiettivo

Completare la stabilizzazione del server edge Android-native (demone FGS resiliente,
Ktor CIO) ed evolvere verso le sessioni operative Fable 5: elaborazione dell'intero
array `messages`, request queue FIFO serializzata, benchmark di prefill e architettura
multi-provider con LiteRT-LM.

**Criterio di riuscita:** APK compilato via GitHub Actions CI funzionante su
dispositivo fisico Android; inferenza LiteRT-LM reattiva via API compatibili OpenAI
(`/v1/chat/completions`) in background 24/7 senza crash per Doze o LMK.

## Stato attuale

Progetto strutturato secondo il Metodo Agorà v3.3. Implementata la Sessione S2 su branch `feature/request-queue`:
1. **RequestQueue FIFO Serializzata**: Gestore single-worker della concorrenza (capacità 4 slot, timeout 120s) con esclusione mutua su `generate()` e `generateStream()`, e ritorno di HTTP 429 (`rate_limit_exceeded` / `Retry-After: 30`) sia su overflow che su timeout sia per OpenAI (`/v1/chat/completions`) che Ollama (`/api/chat`).
2. **Suite di Test Unitari**: `RequestQueueTest` con 5 casi di test JUnit 4 (singola esecuzione, sequenza FIFO, overflow a 4 slot, timeout di attesa, lock mantenuto durante lo stream).
3. **Upgrade LiteRT-LM 0.16.1**: Aggiornato `litertlm-android` da 0.11.0 a 0.16.1 in `app/build.gradle.kts`, abilitato il campionamento dinamico via `SamplerConfig(temperature, topP)` e sbloccata la gestione avanzata del KV-cache C++.
4. **Interactive Tester & Direct Shell**: Tab 3 equipaggiata con quick prompt shell per inviare frasi al modello caricato senza comporre JSON manuale, preset one-click (S1 Recall, Stream SSE live, S2 Queue Stress con 5 chiamate concorrenti, Health check) e harness raw JSON collassabile.
5. **Restyling TUI & De-cluttering**: Estetica rigorosa a console terminale (`Typeface.MONOSPACE`, bottoni a parentesi quadre `[ ... ]`, palette scura ad alto contrasto), rimozione di console e input ridondanti dai Tab 1 e 2, telemetria della coda in StatusIsland (`Inference Queue: X/4 Slots`) e centralizzazione totale dei log nel Tab 4 con comandi `[ 📋 COPY ALL ]` e `[ 🗑️ CLEAR LOGS ]`.

## Prossimo passo

- Eseguire il merge della [PR #8](https://github.com/lpiatti/android-edge-llm-server/pull/8) su `main` (build e unit test GitHub Actions passati con successo).
- Scaricare l'APK debug compilato (`edge-llm-server-debug-apk`) dall'azione GitHub Actions run 34025798796 e installarlo sul dispositivo.
- Testare su dispositivo fisico:
  - Test interattivo con la Quick Shell Prompt (`> [ Frase... ]` e `[ SEND ]`).
  - Test preset `[ S2 QUEUE ]` per verificare la risposta 429 al 5° slot concorrente.
  - Test preset `[ S1 RECALL ]` per verificare la memoria del contesto conversazionale.
  - Test streaming SSE per verificare la visualizzazione progressiva dei token.
  - Test preset `[ S2 QUEUE ]` per verificare la risposta 429 al 5° slot concorrente.
  - Test preset `[ S1 RECALL ]` per verificare la memoria del contesto conversazionale.

## Decisioni e vincoli attivi

- Nessun build locale: ambiente host privo di JDK, Gradle e Android SDK; compilazione e test esclusivamente via GitHub Actions CI (`.github/workflows/android-ci.yml`).
- Pipeline PR-based: ogni modifica al codice passa da feature branch, commit locale, push e apertura Pull Request verso `main` per innesco build remoto.
- Background FGS permanente: tipo `specialUse` (API 34) con `PARTIAL_WAKE_LOCK` e `WifiLock` ad alte prestazioni per operatività 24/7.
- Zero XML / Zero Compose: interfaccia interamente programmatica in puro Kotlin per mantenere l'APK sotto i 2.5MB e garantire riproducibilità di build.
- LiteRT-LM confermato provider primario (`.litertlm` Gemma 4); esclusi GGUF/llama.cpp.
- Coda richieste: single-worker FIFO RequestQueue (429 su overflow) al posto di SessionManager.
- Licenza: PolyForm Noncommercial 1.0.0.

## Richieste attive

Nessuna.

## Review

Nessuna.

## Riferimenti

- [Governance Metodo](../AGENTS.md)
- [Regole operative locali](AGENTS.md)
- [Decision log storico](docs/decision-log.md)
- [Stato documentale esteso](docs/project-state.md)
- [Roadmap sessioni Fable 5](fable5/roadmap-sessioni.md)
- [Contratto API Fable 5](fable5/architettura-api.md)
- [Workflow CI Android](.github/workflows/android-ci.yml)

## Ultimo aggiornamento

2026-09-06
