# DECISIONI.md — android-edge-llm-server

Registro append-only delle decisioni durevoli del progetto. Ci va ciò che il
diff non spiega: il problema che ha motivato la scelta e l'alternativa scartata.

Lo storico dettagliato delle decisioni architetturali precedenti (ADR 1–14) è
archiviato in [`docs/archive/decision-log-adr-1-14.md`](docs/archive/decision-log-adr-1-14.md);
quelle ancora vigenti sono riassunte nella voce del 2026-09-26.
Le voci superate non si cancellano: una voce successiva lo dichiara.

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

---

## 2026-09-26 — Tag `v1` e riordino documentale (M0)
- **Decisione:** tag `v1` sul commit `2e73844` (merge PR #8) come primo traguardo. Documentazione ridotta a una fonte per tema: stato solo in `STATE.md`, decisioni solo qui, pianificazione in `docs/roadmap.md` + `docs/backlog.md`, contratto API in `docs/api-contract.md`; cartella `fable5/` sciolta, documenti storici in `docs/archive/`. Elenco dei file obbligatori della CI aggiornato di conseguenza.
- **Perché:** tre file di stato, due registri decisioni e tre roadmap si contraddicevano (es. `project-state.md` ancora in fase 0, MediaPipe come primo motore, APK < 2.5 MB); attività risultavano chiuse senza evidenza (benchmark di prefill mai registrato, script che non misurava il TTFT).
- **Alternativa scartata:** ritocchi puntuali ai documenti esistenti mantenendo i file imposti dalla CI. Supera l'"alternativa scartata" della voce 2026-09-05: la CI si adegua alla struttura, non il contrario.

---

## 2026-09-26 — ADR 1–14: sintesi di quelle vigenti
- **Decisione:** restano in vigore: minSdk 29 / targetSdk 34 (ADR 1); profilo server dedicato su AC e Wi-Fi (ADR 2); UI programmatica Kotlin senza XML/Compose (ADR 3); Foreground Service `specialUse` con wakelock, WifiLock, `START_STICKY`, `BootReceiver` (ADR 4); separazione UI/motore (ADR 5); build solo via GitHub Actions (ADR 6); Ktor CIO (ADR 7, 10); selettore interfaccia di bind (ADR 8); crash catcher in-app (ADR 11); LiteRT-LM motore primario (ADR 12); RequestQueue al posto di SessionManager (ADR 13); licenza PolyForm Noncommercial 1.0.0 (ADR 14). ADR 9 (console di dump HTTP) confluisce nel tab TEST di M7.
- **Perché:** rendere leggibile in un punto solo ciò che vincola il codice oggi.
- **Alternativa scartata:** mantenere due registri paralleli (causa delle contraddizioni).

---

## 2026-09-26 — Motivazione di ADR 3 corretta: niente vincolo "APK < 2.5 MB"
- **Decisione:** la UI resta programmatica senza XML/Compose per riproducibilità di build e controllo, non per la dimensione dell'APK. Il limite "< 2.5 MB" non è più un vincolo; la dimensione reale dell'APK va misurata dall'artifact CI e riportata in `STATE.md`.
- **Perché:** l'AAR `litertlm-android` include librerie native; il limite è con ogni probabilità già superato e usarlo come argomento contro altre scelte (es. llama.cpp) era fuorviante.
- **Alternativa scartata:** continuare a citare il limite senza verificarlo.

---

## 2026-09-26 — Motori di inferenza: LiteRT-LM unico, altri solo su bisogno concreto
- **Decisione:** nessuna generalizzazione multi-motore preventiva. GGUF/llama.cpp passa da "escluso" a primo candidato di backlog, da attivare solo davanti a un bisogno che LiteRT-LM non copre. ONNX Runtime ed ExecuTorch restano candidati per NPU/embeddings. Valutazione in `docs/backlog.md` §2.
- **Perché:** il rischio tecnico principale (prefill su contesti lunghi) non migliora cambiando motore: la GPU di LiteRT-LM è la via più rapida disponibile sui device di test; la scelta di modelli non-Gemma è già possibile in `.litertlm` (litert-community). Un'interfaccia progettata su un solo caso reale verrebbe sbagliata.
- **Alternativa scartata:** refactoring del provider per più motori ora; esclusione definitiva di GGUF (motivata da un limite APK non più valido).

---

## 2026-09-26 — Obiettivo e roadmap M0–M7
- **Decisione:** obiettivo: server con API OpenAI e Ollama compatibili al punto che client reali funzionano senza adattamenti, ottimizzato per la latenza, con UI minimale. "Completo" = matrice di `docs/api-contract.md` §0. Le sessioni Fable 5 S3–S8 confluiscono nei milestone M1–M7; la Web UI (ex S7) passa al backlog. Esclusi da "completo": embeddings, `/v1/completions` legacy, input multimodale, gestione modelli Ollama.
- **Perché:** il piano a sessioni era parzialmente eseguito senza evidenze e mescolava API, UI web e release; mancava una definizione verificabile di "finito".
- **Alternativa scartata:** proseguire con S3 così com'era pianificata.

---

## 2026-09-26 — UI Android: cosa si conserva
- **Decisione:** in M7 si conservano lo stile TUI/terminale, il percorso di avvio dopo l'installazione (onboarding permessi), il caricamento diretto del modello con scelta CPU/GPU e la separazione tra tab ENGINE (motore) e DAEMON (server HTTP). Si riordina il tab TEST come unico punto dei test (benchmark, self-test tool, preset) e si spezza `MainActivity.kt` per tab.
- **Perché:** sono le parti che il proprietario usa e apprezza; il resto è diventato rumore.
- **Alternativa scartata:** riprogettazione in 4 tab SERVER/MODELLI/TEST/LOG che fondeva motore e server.

