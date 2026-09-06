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

Progetto strutturato secondo il Metodo Agorà v3.3. Implementata la Sessione S2 e stabilizzazione motore su branch `feature/request-queue`:
1. **RequestQueue FIFO Serializzata**: Gestore single-worker della concorrenza (capacità 4 slot, timeout 120s) con esclusione mutua su `generate()` e `generateStream()`, e ritorno di HTTP 429 (`rate_limit_exceeded` / `Retry-After: 30`) sia su overflow che su timeout per OpenAI (`/v1/chat/completions`) e Ollama (`/api/chat`).
2. **Suite di Test Unitari**: `RequestQueueTest` con 5 casi di test JUnit 4 passati con successo.
3. **Stabilizzazione LiteRT-LM 0.16.1 & Coroutines 1.11.0**: Risolto il crash fatale `NoSuchMethodError: SendChannel.close$default` su `sendMessageAsync.onDone()` allineando esplicitamente `kotlinx-coroutines-core:1.11.0` e `kotlinx-coroutines-android:1.11.0` in `app/build.gradle.kts`. Mantenuto il supporto a `SamplerConfig(temperature, topP)` e Gemma 4.
4. **Governance Cache Privata & Pulizia Deterministica**: Configurato `cacheDir` verso directory privata interna (`context.cacheDir/litertlm_cache`), con creazione automatica della cartella; implementato `purgeCacheFiles()` eseguito automaticamente all'avvio (`onCreate`), alla chiusura dell'applicazione (`onDestroy` di Activity e Daemon Service) e allo scaricamento del modello (`unloadActiveModel`), con bonifica automatica della directory modelli pubblica da residui `*_mldrift_*`, preservando integralmente i file `.litertlm`.
5. **Diagnostica RAM Fisica & Azione Trim Memory**: Monitoraggio della RAM reale di sistema (`ActivityManager.MemoryInfo`) integrato in `StatusIsland`, nelle schermate di avvio (Crash Recovery / Permission Gate) e in un pannello dedicato `> MEMORY & CACHE MANAGEMENT` in Tab 1, con pulsante `[ 🧹 TRIM SYSTEM RAM & GC ]` e `[ 🗑️ PURGE CACHE ]`.
6. **Interactive Tester & Direct Shell**: Tab 3 con Quick Shell Prompt (`> [ Frase... ]`), preset one-click (S1 Recall, Stream SSE live, S2 Queue Stress 5x, Health check) ed estetica terminale TUI coerente.

## Prossimo passo

- Attendere l'esito della pipeline GitHub Actions CI sulla PR #8 (`testDebugUnitTest` e `assembleDebug`).
- Scaricare l'APK debug compilato ed eseguire il collaudo su Google Pixel 9:
  - Invio di "ciao" tramite la Quick Prompt Shell per verificare l'assenza del crash `NoSuchMethodError`.
  - Verifica della pulizia della cartella modelli `/sdcard/Download/llm-server/models/` da residui di cache.
  - Verifica del monitor RAM e del comando `[ 🧹 TRIM RAM & GC ]` nelle schermate di avvio e nel Tab 1.
  - Test preset di coda `[ S2 QUEUE ]` (HTTP 429) e streaming SSE live.

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
