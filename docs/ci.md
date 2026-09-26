# GitHub Actions per questo progetto — tutorial pratico

Per Luigi, in italiano. Cosa fa oggi il CI del repo, cosa può fare domani, e i tre
upgrade che consiglio. Repo pubblico ⇒ i minuti di GitHub Actions sono **gratis e
illimitati** (sui runner standard): usali senza ansia.

## Cosa hai oggi (verificato in `.github/workflows/android-ci.yml`)

Tre job in sequenza:
1. **bootstrap-verification**: controlla che i file di documentazione obbligatori
   esistano (elenco nel workflow e in `AGENTS.md`). Fallisce il build se un agente
   cancella un file di contratto.
2. **android-build**: JDK 17 + Gradle 8.4, esegue i test JVM (`testDebugUnitTest`) e
   compila `assembleDebug`, poi carica l'APK come *artifact* scaricabile (conservato
   7 giorni: tab Actions → run → Artifacts).

**Un dettaglio importante che forse non sai**: il trigger `push` è attivo solo su
`main` e `feature/android-skeleton`. Le push sugli altri feature branch NON compilano
nulla; il CI parte solo quando apri la **pull request** verso main. È coerente col
workflow in AGENTS.md, ma se vuoi build a ogni push su qualsiasi feature branch basta:

```yaml
on:
  push:
    branches: [ main, 'feature/**' ]
  pull_request:
    branches: [ main ]
```

### 3. publish-dev-apk: APK scaricabile dal telefono
Dopo ogni build riuscita (PR da questo repo, push su `main`, avvio manuale) la CI sostituisce
la pre-release `dev-latest` con l'APK **non zippato**. Link fisso, senza login (repo pubblico):
https://github.com/lpiatti/android-edge-llm-server/releases/download/dev-latest/edge-llm-server-debug.apk

Gli artifact di Actions restano (zip, 7 giorni) ma richiedono l'accesso a GitHub: dal telefono
danno 404.

## Upgrade possibili (in ordine di utilità)

I test JVM sono già nel job di build: "il test passa in CI" è il criterio di
accettazione dei milestone, verificabile e non opinabile.

### 1. Release automatica su tag
Quando spingi un tag `v*`, il CI compila e pubblica una GitHub Release con l'APK
allegato — il tuo canale di distribuzione senza Play Store:

```yaml
on:
  push:
    tags: [ 'v*' ]
# ... build ...
      - uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/debug/app-debug.apk
```

Le release possono diventare anche la fonte degli aggiornamenti della web UI e della
lista modelli del Model Hub (entrambi nel [backlog](backlog.md)).

### 2. Bottone di build manuale
`workflow_dispatch:` tra i trigger aggiunge un pulsante "Run workflow" nella tab
Actions: compili qualsiasi branch al volo senza aprire PR. Comodo per esperimenti.

## Altre possibilità, quando serviranno

| Cosa | A che serve qui | Costo di setup |
|---|---|---|
| **Cache Gradle** | build più veloci (già parziale con setup-gradle) | quasi zero |
| **Badge nel README** | `![CI](https://github.com/<user>/<repo>/actions/workflows/android-ci.yml/badge.svg)` — vetrina CV | 1 riga |
| **concurrency** | annulla build obsolete se spingi due volte di fila | 3 righe |
| **schedule (cron)** | build notturna periodica per scoprire rotture da dipendenze | 2 righe |
| **Emulatore Android in CI** | test strumentati — lo sconsiglio: lento e fragile, i test JVM bastano | alto |
| **Firma APK release** | serve solo se un giorno vai su Play Store; le chiavi vanno nei **Secrets** (Settings → Secrets and variables → Actions), mai nel repo | medio |

## Come leggere un fallimento CI (per dirigere gli agenti)

1. Tab **Actions** → run rosso → job fallito → step fallito.
2. Il log dello step contiene l'errore del compilatore Kotlin con file e riga.
3. Copia le ~20 righe attorno a `e: file:///...` e passale all'agente: è l'input più
   economico ed efficace per la correzione (evita che l'agente ri-esplori tutto).
