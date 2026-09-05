# GitHub Actions per questo progetto — tutorial pratico

Per Luigi, in italiano. Cosa fa oggi il CI del repo, cosa può fare domani, e i tre
upgrade che consiglio. Repo pubblico ⇒ i minuti di GitHub Actions sono **gratis e
illimitati** (sui runner standard): usali senza ansia.

## Cosa hai oggi (verificato in `.github/workflows/android-ci.yml`)

Due job in sequenza:
1. **bootstrap-verification**: controlla che i file di documentazione obbligatori
   esistano. Fallisce il build se un agente cancella un file di contratto.
2. **android-build**: JDK 17 + Gradle 8.4, compila `assembleDebug` e carica l'APK come
   *artifact* scaricabile (conservato 7 giorni: tab Actions → run → Artifacts).

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

## I tre upgrade che consiglio (in ordine)

### 1. Job di test unitari (arriva con la sessione S1)
I test JVM girano sul runner Linux senza emulatore, in secondi:

```yaml
  unit-tests:
    needs: bootstrap-verification
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'zulu', java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
        with: { gradle-version: '8.4' }
      - run: gradle testDebugUnitTest --no-daemon
```

Da qui in poi "il test passa in CI" diventa il criterio di accettazione delle sessioni
agentiche: verificabile, non opinabile.

### 2. Release automatica su tag (serve in S8)
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

Le release sono anche la fonte da cui l'app scaricherà gli aggiornamenti della web UI
(sessione S7) ed eventualmente la lista modelli del Model Hub (backlog): GitHub diventa
il tuo canale di aggiornamento contenuti, come desideravi.

### 3. Bottone di build manuale
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
