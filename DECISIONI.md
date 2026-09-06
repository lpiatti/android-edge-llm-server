# DECISIONI.md — android-edge-llm-server

Registro append-only delle decisioni durevoli del progetto. Ci va ciò che il
diff non spiega: il problema che ha motivato la scelta e l'alternativa scartata.

Lo storico dettagliato delle decisioni architetturali precedenti (ADR 1–14) è
conservato e consultabile in [`docs/decision-log.md`](docs/decision-log.md).

---

## 2026-09-05 — Adozione nel Metodo Agorà v3.3
- **Decisione:** adozione del progetto nel Metodo Agorà v3.3 mantenendo il repository Git autonomo esistente, la pipeline remota di compilazione GitHub Actions CI e i vincoli di non build locale.
- **Perché:** integrare la governance della workspace senza alterare la struttura e i file richiesti dal job di verifica bootstrap della CI (`.github/workflows/android-ci.yml`) né forzare ambienti locali non presenti.
- **Alternativa scartata:** riscrittura o rilocazione dei file di documentazione e istruzioni agenti, che avrebbe rotto la verifica vincolante di GitHub Actions CI.

---

## 2026-09-06 — RequestQueue (S2), LiteRT-LM 0.16.1 e Terminal UX
- **Decisione:** Implementazione di `RequestQueue` single-worker FIFO serializzata (capacità 4 slot, timeout 120s) con ritorno di HTTP 429 (`rate_limit_exceeded` / `Retry-After: 30`) sia su overflow che timeout; aggiornamento dell'SDK `litertlm-android` a `0.16.1` con applicazione di `SamplerConfig(temperature, topP)`; introduzione in Tab 3 di Quick Shell Prompt interattiva, preset di collaudo e console SSE; adozione rigorosa dell'estetica TUI/terminale monospazio a contrasto elevato, con rimozione delle console ridondanti da Tab 1 e 2 e centralizzazione nel Tab 4 con strumenti di copia e pulizia.
- **Perché:** L'acceleratore edge non supporta inferenze concorrenti e crasha per LMK; la serializzazione FIFO con backpressure 429 garantisce stabilità operativa 24/7. LiteRT-LM 0.16.1 sblocca il controllo sui parametri di campionamento e ottimizza il rilascio KV-cache C++. L'interfaccia a terminale mantiene la reattività, un APK compatto (<2.5MB) e un'esperienza sviluppatore chiara e priva di sovrastrutture grafiche.
- **Alternativa scartata:** Concorrenza multi-thread a livello motore (insostenibile su SoC mobile per VRAM/RAM), UI a card arrotondate stile web/Bootstrap (rifiutate esplicitamente per incoerenza con l'anima console dell'app).

---

## 2026-09-06 — Stabilizzazione LiteRT-LM 0.16.1, Allineamento Coroutines 1.11.0 e Governance Cache/RAM
- **Decisione:** Mantenimento di `litertlm-android:0.16.1` con forzatura esplicita di `kotlinx-coroutines-core:1.11.0` e `kotlinx-coroutines-android:1.11.0` in `app/build.gradle.kts`; isolamento della compilazione cache MLDrift in cartella privata dedicata (`context.cacheDir/litertlm_cache`); implementazione di `purgeCacheFiles()` con esecuzione automatica sia all'avvio dell'app (`onCreate`) sia alla chiusura (`onDestroy` di Activity e Service) e allo scaricamento del modello (`unloadActiveModel`), con scansione bonificatrice della cartella pubblica `/sdcard/Download/llm-server/models/` dai file orfani `*_mldrift_*`, preservando tassativamente i file `.litertlm`; integrazione di diagnostica RAM fisica reale (`ActivityManager.MemoryInfo`) e azione di trim memory sia nelle schermate di avvio che nel Tab 1 e nella `StatusIsland`.
- **Perché:** Il crash `NoSuchMethodError: SendChannel.close$default` su `onDone()` (Issue #2812 / #3334) era dovuto a una discrepanza ABI tra il bytecode dell'AAR 0.16.1 (compilato contro coroutines 1.11.0) e il POM Maven che dichiarava 1.9.0; l'allineamento a 1.11.0 risolve il crash alla radice senza rinunciare ai `SamplerConfig` e ai vantaggi prestazionali della 0.16.1. La gestione esplicita di `cacheDir` previene l'inquinamento della directory modelli con file temporanei pesanti e ne garantisce la pulizia deterministica all'avvio e alla chiusura.
- **Alternativa scartata:** Rollback a `0.11.0` o `0.13.1` (avrebbe privato il server dei parametri di campionamento dinamico temperatura/topP e del supporto avanzato Gemma 4).

