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

