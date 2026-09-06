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
3. **Stabilizzazione LiteRT-LM 0.16.1 & Coroutines 1.11.0**: Risolto il crash fatale `NoSuchMethodError: SendChannel.close$default` su `sendMessageAsync.onDone()` allineando esplicitamente `kotlinx-coroutines-core:1.11.0` e `kotlinx-coroutines-android:1.11.0` in `app/build.gradle.kts`. Inferenza funzionante con successo su Google Pixel 9 (8 token generati: "Ciao! Come posso aiutarti oggi?").
4. **Parsing Stream SSE & Quick Shell Response Card**: Risolto l'output dei chunk SSE grezzi (`data: {"choices":[{"delta":...}]}`). Implementato `extractContentFromChunk` e creata la `quickShellResponseCard` con streaming testo in tempo reale, conteggio token/latenza, pulsante `[ COPY ]` e formattazione evidenziata `<<< ASSISTANT OUTPUT:` nella console test.
5. **Bonifica Totale Cartella Modelli**: Estesa la scansione in `ModelManager.purgeCacheFiles()` affinché ogni file non `.litertlm` (inclusi residui `.bin`, `.tmp`, `.cache` lasciati da vecchie esecuzioni) presente in `/sdcard/Download/llm-server/models/` venga rimosso incondizionatamente sia all'avvio che alla chiusura.
6. **Telemetria RAM Chiara & Trasparenza GC**: Riformattata la visualizzazione della RAM fisica in GB con indicazione della cache Linux del sistema operativo (`%.1f GB free / %.1f GB total (%d%% OS cached)`), affiancata all'Heap del processo JVM (`$usedMem MB / $maxMem MB`). Azione Trim aggiornata per quantificare con precisione i kilobyte/megabyte recuperati dal garbage collector, con nota esplicativa sul rilascio dinamico della memoria da parte del kernel Linux.

## Prossimo passo

- Eseguire commit e push su branch `feature/request-queue` (PR #8).
- Verificare il passaggio dei test e la compilazione dell'APK debug su GitHub Actions CI (`android-ci.yml`).
- Collaudo su Google Pixel 9:
  - Invio di "ciao" da Quick Shell con verifica del testo in chiaro renderizzato live nell'output box.
  - Verifica della scomparsa dei file `.bin` residui dalla cartella `/sdcard/Download/llm-server/models/`.
  - Verifica delle metriche RAM espresse in GB e del feedback numerico sul trim GC.

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
