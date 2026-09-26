# Verdetto — senso pratico del progetto

Consulenza Fable 5, 2026-07-05. Quanto segue è la mia opinione professionale, marcata come tale;
i fatti verificati sul codice o online sono segnati ✓.

## Le tue tre domande, risposte secche

**"Riuscirò nel mio scopo minimo (LLM gratis per harness agentici)?"**
Sì, con due condizioni. Il server oggi non ci arriva (scarta la history ✓, niente tool calling ✓,
niente coda richieste ✓), ma sono esattamente le sessioni S1–S6 della
[roadmap](roadmap-sessioni.md). Le condizioni: (1) il benchmark di prefill in S1 deve dare tempi
accettabili — un harness manda migliaia di token di contesto a ogni richiesta e il telefono li
ri-processa tutti ogni volta; (2) aspettative tarate sui modelli 2–4B: piccoli agenti con pochi
tool sì, un "Claude Code dei poveri" no. Gemma 4 E2B ha function calling nativo e 128K di
contesto ✓ — la materia prima c'è.

**"Può essere un repo da stelle e CV?"**
Da CV: sì, già oggi, e più delle stelle. Il repo racconta una storia rara: workflow interamente
agentico con CI-only validation, documentazione ADR disciplinata, architettura decoupled su un
dominio di nicchia (server LLM Android nativo). Per un PM/architetto è una dimostrazione di metodo,
non solo di codice. Da stelle: possibile ma tetto medio — il dominio è di nicchia e la licenza
noncommercial (tua scelta, legittima) esclude una parte di adozione. Le stelle arrivano se: README
con demo visiva (GIF del telefono che serve Open WebUI), release APK scaricabile, un post tecnico
("ho trasformato un S25 in un server OpenAI-compatibile"). Il differenziante vero rispetto a
Termux+llama.cpp e simili è l'esperienza *appliance*: installi un APK e hai un server, zero shell.

**"Potrebbe essere utile per me?"**
Sì, ed è l'uso che giustifica il progetto anche se le stelle non arrivano: endpoint locale per
Open WebUI (chat + RAG sui tuoi documenti), harness leggeri a costo zero, e — come fase 2 progetto,
fuori dall'app — l'orchestratore RAG multi-telefono. Con `embeddinggemma-300m` disponibile in
litert-community ✓, la filiera embedding+chat su due telefoni è realistica.

## Cosa è solido e cosa no (stato attuale)

Solido: architettura FGS/daemon con locks ✓, scelta Ktor CIO ✓, InferenceProvider come astrazione
multi-motore ✓, disciplina documentale sopra la media, workflow CI-only che funziona ✓ (8 sessioni,
APK reale su device fisici).

Non solido: semantica API (history scartata ✓), concorrenza assente ✓, load del modello senza
pre-check RAM ✓ (il crash del tuo S25 FE con E4B è quasi certamente OOM nativo, invisibile al
crash logger JVM), zero test automatici ✓, MainActivity al 56% del codice totale ✓.

## Rischi senza sconti

1. **Bus factor = 1.** Sei tu più agenti. Mitigazione: è esattamente ciò che questa doc e i
   criteri di accettazione verificabili servono a mitigare. Non sparisce.
2. **LiteRT-LM è bleeding edge.** Versione 0.11.0, API giovane: breaking changes probabili a ogni
   upgrade. Mitigazione: InferenceProvider isola il danno; non inseguire ogni release.
3. **Prefill su prompt agentici lunghi.** Il rischio tecnico n.1 per lo scopo minimo. Si misura in
   S1, presto e a costo quasi zero. Se i numeri sono cattivi, si pivota (sotto).
4. **Concorrenza di progetti esistenti.** Il mondo "LLM sul telefono" è affollato (llama.cpp su
   Termux, MLC, app chat varie). Pochi però fanno *server headless con API standard e zero
   configurazione*. Restare su quel posizionamento è la difesa.
5. **Policy Android future.** Google stringe periodicamente su FGS e wakelock. Il profilo
   "dedicated server su AC" regge, ma un target SDK bump futuro può richiedere lavoro non
   pianificato.

## Continuare, pivotare o chiudere?

**Continuare**, con due pivot point dichiarati:

- **Dopo S1 (benchmark prefill)**: se un contesto da 4K token impiega >30–40 secondi di prefill
  su Pixel 9 in GPU, lo scopo "harness" va ridimensionato a scopo "chat + RAG batch" (dove la
  latenza pesa meno) e S5–S6 (tool calling) scendono di priorità a favore di embeddings.
- **Dopo S6 (harness reale)**: se un piccolo agente con 1 tool non completa un task in tempi
  tollerabili, l'esperimento agentico si dichiara "riuscito ma senza uscita pratica" — che era uno
  degli esiti che avevi messo in conto — e il progetto resta un ottimo server chat/RAG locale.

Chiudere non è giustificato: il costo già investito (~8 sessioni) ha prodotto un'app funzionante
su hardware reale, e le 8 sessioni pianificate hanno ciascuna valore autonomo anche se ci si
ferma a metà.
