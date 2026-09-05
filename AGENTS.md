# AGENTS.md — android-edge-llm-server
Metodo: Agorà v3.3 — 2026-09-02

Il metodo comune è in `../AGENTS.md`. Questo file conserva soltanto obiettivo,
confini, sicurezza e regole specifiche del progetto.

- **Stato:** [`STATE.md`](STATE.md)
- **Decisioni:** [`DECISIONI.md`](DECISIONI.md)
- **Versionamento:** [`../_agora/versionamento.md`](../_agora/versionamento.md)

Se il metodo padre non è disponibile, lavora e crea checkpoint soltanto in
questo repository; vietate operazioni cross-progetto.

## Cos'è

Server edge Android-native per inferenza locale con modelli linguistici
(LiteRT-LM Gemma 4) esposti via API HTTP compatibili OpenAI e Ollama,
implementato come demone background resiliente 24/7 (FGS, Wakelock, WifiLock) con
UI di controllo e diagnostica in puro Kotlin programmatico.

**Criterio di riuscita:** Il server Ktor CIO esegue in background su dispositivo
fisico Android o emulatore, risponde correttamente alle richieste API OpenAI
(`/v1/chat/completions`) e Ollama (`/api/chat`) senza interruzioni per sleep o
Doze mode, preservando la completa separazione tra UI e motore di inferenza.
**Verifica rapida:** Nessuna locale. L'ambiente host Windows è privo di Java JDK,
Gradle e Android SDK. Compilazione, test e packaging APK avvengono esclusivamente
tramite GitHub Actions CI (`.github/workflows/android-ci.yml`) su feature branch / PR.

## Confini

- Il repository governa il codice Android (`app/`), la configurazione Gradle, la
  documentazione architetturale (`docs/`), i piani operativi (`fable5/`), le
  istruzioni agenti (`.agents/`, `CLAUDE.md`, `CODEX.md`) e le pipeline CI (`.github/`).
- Nessun accesso o dipendenza da altri progetti della workspace; operatività e
  checkpoint rimangono rigorosamente all'interno di questo repository.
- Non modificare il codice sorgente Android o i file Gradle se non specificamente
  richiesto dal task.
- **Perimetro operativo sessioni:** Lavorare esclusivamente sulla sessione assegnata
  definita in [`fable5/roadmap-sessioni.md`](fable5/roadmap-sessioni.md). Qualsiasi altra
  modifica richiede l'approvazione del proprietario del progetto. Non avviare voci
  del backlog ([`fable5/backlog.md`](fable5/backlog.md)) in modo opportunistico.
- **Riferimenti documentali obbligatori:** Prima di proporre modifiche o piani,
  consultare in ordine: `README.md`, `docs/project-state.md`, `docs/roadmap.md`,
  `docs/architecture.md`, `docs/index.md`, [`fable5/index.md`](fable5/index.md),
  [`fable5/roadmap-sessioni.md`](fable5/roadmap-sessioni.md) e `.agents/README.md`.

## Regole specifiche

### Vincolo critico di ambiente e verifica CI/CD
- **Nessun build locale**: divieto assoluto di eseguire `./gradlew assembleDebug`,
  `./gradlew test` o comandi locali Java/Gradle.
- **Validazione esclusivamente remota via GitHub Actions**: per validare il codice
  o generare l'APK, le modifiche vanno committate su feature branch, spinte al
  remoto `origin` e validate tramite Pull Request verso `main`.
- **Interazione guidata**: l'agente che modifica codice o configurazioni di build
  deve fornire a Luigi istruzioni chiare passo-passo per aprire la PR su GitHub
  e attendere il riscontro dell'esito della CI (compilazione APK o log errori)
  prima di considerare completato il passaggio.

### Integrità dei file verificati dalla CI
- Il workflow `.github/workflows/android-ci.yml` (step `Verify Mandatory Bootstrap & Rules Files`)
  verifica tassativamente la presenza dei seguenti file:
  - `README.md`
  - `docs/index.md`
  - `docs/project-state.md`
  - `docs/roadmap.md`
  - `docs/architecture.md`
  - `AGENTS.md`
  - `.agents/README.md`
  - `.agents/bootstrap-phase-0.md`
  - `.agents/create-android-skeleton.md`
  - `CLAUDE.md`
  - `.claude/README.md`
  Nessuno di questi file deve essere rimosso o rinominato senza coordinamento.

### Guardrail architetturali e di design (ADR 1–14)
- **Disaccoppiamento UI e Runtime Engine**: `MainActivity` e `LlmServerService`
  sono modulari e indipendenti. La chiusura o lo swipe via della UI non deve
  arrestare il server né rilasciare CPU/Wi-Fi lock.
- **Resilienza Daemon (Foreground Service)**: il servizio HTTP opera come
  Foreground Service (`specialUse` su API 34), con `PowerManager.PARTIAL_WAKE_LOCK`,
  `WifiManager.WifiLock` ad alte prestazioni e riavvio automatico (`START_STICKY`, `BootReceiver`).
- **Zero XML / Zero Compose**: interfaccia Android interamente programmatica in
  puro Kotlin (`MainActivity.kt`) per garantire bundle APK microscopic (< 2.5 MB)
  ed evitare problemi di compatibilità dei compilatori.
- **Provider di inferenza**: LiteRT-LM confermato provider primario (`.litertlm`
  Gemma 4). Escluso GGUF/llama.cpp.
- **Coda richieste (RequestQueue)**: serializzazione FIFO delle richieste di
  inferenza su singolo worker con HTTP 429 su overflow (sostituisce SessionManager).
- **Direzione operativa sessioni**: seguire le 8 sessioni definite in
  [`fable5/roadmap-sessioni.md`](fable5/roadmap-sessioni.md) e il contratto API in
  [`fable5/architettura-api.md`](fable5/architettura-api.md).

### Disciplina delle modifiche
- Prima di intervenire sul codice, verificare lo stato effettivo del branch e del filesystem.
- Proporre modifiche incrementali minime e mirate.
- Al termine di un task, riportare: esito (OK/KO), sintesi modifiche, file toccati,
  istruzioni PR/CI e prossimo passo raccomandato.
