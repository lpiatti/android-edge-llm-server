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

Progetto adottato nel Metodo Agorà da repository Git autonomo esistente (branch
`feature/server-stabilization-improvements`). Il codice Android (`app/`) dispone di
motore HTTP Ktor 3.0.3 su Kotlin 2.2.0, mock e binding parziali LiteRT-LM, UI Kotlin
programmatica a tre tab, crash catcher in-app e gestione wake/wifi lock.
È presente l'esito della consulenza Fable 5 (`fable5/`) che riorganizza il lavoro in
8 sessioni operative (S0–S8) con Definition of Done e backlog esplicito, sotto licenza
PolyForm Noncommercial 1.0.0.

## Prossimo passo

- Eseguire Sessione S0: allineamento della documentazione interna secondo
  [`fable5/correzioni-doc.md`](fable5/correzioni-doc.md) (aggiornamento riferimenti
  architettura e roadmap).

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

2026-09-05
