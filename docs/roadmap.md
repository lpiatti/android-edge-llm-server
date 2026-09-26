# Roadmap

Unica pianificazione del progetto. Sostituisce `fable5/roadmap-sessioni.md` e le fasi 0–6
(archiviate in [`archive/`](archive/)). Ciò che non è qui sta nel [backlog](backlog.md) e
non si avvia senza una decisione del proprietario registrata in `DECISIONI.md`.

## Obiettivo

Server LLM su Android, sempre attivo, con API **OpenAI e Ollama compatibili al punto che
client reali** (SDK `openai`, Open WebUI, un harness agentico) **funzionano senza
adattamenti**; ottimizzato per la latenza su contesti lunghi; UI Android minimale per
controllo e collaudo.

"Completo" è definito dalla matrice in [api-contract.md](api-contract.md) §0.

## Punto di partenza — tag `v1`

Server Ktor CIO in Foreground Service 24/7, LiteRT-LM (Gemma 4 `.litertlm`, CPU/GPU),
`/v1/chat/completions` e `/api/chat` con history completa e streaming, `RequestQueue`
FIFO con 429, onboarding permessi, UI TUI a 4 tab, CI GitHub Actions con test JVM e APK.

## Definition of Done comune

- Un milestone = un branch = una PR verso `main`, CI verde.
- Logica pura coperta da test JVM; comportamento verificabile coperto da un test in-app
  (tab TEST) quando ha senso.
- Evidenza nella PR: output del test in-app o del client esterno indicato. "Dovrebbe
  funzionare" non è evidenza.
- `STATE.md` aggiornato; decisioni durevoli in `DECISIONI.md`; matrice di
  `api-contract.md` aggiornata se cambia un endpoint.
- Niente scope creep: ciò che emerge va nel backlog.

## Milestone

M1–M7 sono sviluppati insieme nella PR #9 (vedi `DECISIONI.md`, 2026-09-26).

| M | Obiettivo | Stato | Verifica |
|---|---|---|---|
| M0 | Riordino documentale | fatto | Struttura unica, link validi, CI verde |
| M1 | Misure affidabili | codice pronto | `[ BENCHMARK ]` nel tab TEST → numeri in `STATE.md` |
| M2 | Compatibilità OpenAI | codice pronto | test JVM; opzionale: SDK `openai` Python |
| M3 | Compatibilità Ollama | codice pronto | test JVM; opzionale: Open WebUI come Ollama |
| M4 | Tool calling OpenAI + Ollama | codice pronto | `[ TOOL CALL ]` nel tab TEST: 4/4 PASS |
| M5 | Riuso KV-cache | codice pronto | riga "warm" del benchmark con `Cached tok` > 0 e TTFT basso |
| M6 | API key + caricamento sicuro | codice pronto | chiave nel tab DAEMON → 401 senza, 200 con; rifiuto RAM |
| M7 | UI riordinata | parziale | tab TEST con test di milestone; split di `MainActivity.kt` rinviato |

Il pivot di `api-contract.md` §6 (TTFT a ~4000 token) si valuta con i numeri del benchmark.

### M1 — Misure affidabili

- Pulsante `[ BENCHMARK ]` nel tab TEST: prompt sintetici ~500/~2000/~4000 token inviati
  via HTTP a `localhost` in streaming; misura TTFT (primo chunk) e tok/s in decode;
  tabella con `[ COPY ]`. Da eseguire con GPU ON e OFF.
- Correggere `scripts/benchmark_prefill.{sh,ps1}`: oggi usano `stream:false` (misurano il
  tempo totale, non il TTFT) e il testo si ferma a ~1000 token.
- Registrare device, backend, modello, numeri in `STATE.md`.

### M2 — Compatibilità OpenAI

Righe OpenAI ❌/🟡 della matrice: `content` come array, `max_tokens` realmente applicato +
`max_completion_tokens`, `stop`, `usage` e `stream_options.include_usage`,
`response_format` best-effort, errori conformi, `n>1` → 400.
Verificare il doppio template (prompt già formattato passato a `Conversation`), vedi
`api-contract.md` §2. Verifica: script Python con SDK `openai` (chat, stream, stop,
usage) nella PR.

### M3 — Compatibilità Ollama

`/api/version`, `/api/show`, `/api/generate`, `/api/ps`; `/api/tags` con valori reali;
`options.stop`, `num_ctx`; 501 per gli endpoint di gestione modelli.
Verifica: Open WebUI collegato come Ollama (e come OpenAI) — screenshot.

### M4 — Tool calling

1. Verificare se LiteRT-LM espone il function calling nativo di Gemma 4 → decisione.
2. Non-streaming OpenAI + Ollama, parser robusto con test JVM.
3. Streaming `tool_calls` delta.
4. Self-test in-app `[ TOOL CALL ]`: giro completo con un tool reale in sola lettura
   eseguito dall'app-client (es. `get_battery_status`); il server non esegue tool.
5. Harness reale collegato via base URL custom: GO/NO-GO sull'uso agentico in `DECISIONI.md`.

### M5 — Ottimizzazione prefill

Riuso del KV-cache tra richieste che condividono il prefisso (oggi ogni richiesta crea
una `Conversation` nuova). Dipende da cosa espone LiteRT-LM: prima indagine, poi
implementazione o decisione motivata di rinuncia. Semantica stateless invariata.

### M6 — API key e caricamento sicuro

API key opzionale (`api-contract.md` §5) con campo nel tab DAEMON. Pre-flight RAM
prima del caricamento (dimensione × fattore vs RAM disponibile, con "forza comunque"),
crash marker per diagnosticare OOM nativi al riavvio successivo.

### M7 — UI riordinata

Da **conservare** (scelte del proprietario):
- stile grafico TUI/terminale monospazio;
- percorso di avvio dopo l'installazione (onboarding permessi → vista principale);
- caricamento diretto del modello dal file con scelta CPU/GPU;
- separazione netta tra **motore** (tab ENGINE) e **server HTTP** (tab DAEMON).

Da **riordinare**:
- tab TEST come unico punto dei test: benchmark (M1), self-test tool (M4), preset API,
  quick shell; eliminare preset obsoleti legati alle vecchie sessioni (`S1 RECALL`, `S2 QUEUE`);
- ridurre il rumore del tab ENGINE (audit RAM e guida Samsung in una sezione comprimibile);
- spezzare `MainActivity.kt` (~3000 righe) in un file per tab, senza cambiare stile né
  comportamento. **Rinviato**: refactoring ampio senza compilatore locale, da fare in una PR
  dedicata dopo il collaudo di v2 (rischio alto, beneficio solo di manutenzione).

Fatto in PR #9: `[ BENCHMARK ]` e `[ TOOL CALL ]` nel tab TEST, preset `S1 RECALL` rimosso,
`S2 QUEUE` → `QUEUE`; selettore `CTX` (dimensione contesto) nel tab ENGINE; API key nel tab
DAEMON; TTFT e token in cache nella telemetria.

## Fuori roadmap (vedi backlog)

Web UI servita da Ktor, embeddings, altri motori (ONNX/GGUF), model hub, federazione
multi-telefono, tool eseguiti sul telefono, TTS.
