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
