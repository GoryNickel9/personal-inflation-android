# Verifiche del port Android

Esecuzioni locali del 23 ottobre 2026, Windows, JBR 21.0.4, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.1.20. Emulatore di riferimento: Android 15, API 35, x86_64.

## Risultati

| Controllo | Risultato |
| --- | --- |
| Test originali su inflazione, storico e identità | 32 superati |
| Test JVM Android | 24 superati, nessun errore |
| Test strumentali Android 15 | 6 superati, nessun errore |
| Confronto con TypeScript | 60 dataset deterministici, 420 confronti temporali e 150 storici prodotto |
| Lint Android | 0 errori, 9 avvisi sulle versioni delle dipendenze |
| APK debug | 10.872.065 byte, firma APK v2 verificata, installato sull’emulatore |
| Lettura del backend esistente | Risposta HTTP 200 e dati caricati dall’app |

Le fixture di confronto derivano dalle funzioni originali `lib/inflation.ts` e `lib/price-history.ts`, nel repository `personal-inflation`, commit `53195ddfc05c2f075bf7015f7221e739510e6bce`. Il generatore usa seed `20261023`. I test confrontano costi, indice concatenato, effetto prezzi, percentuali, date e conteggi. La tolleranza è il maggiore fra `1e-8` e `abs(atteso) × 1e-12`. Il test sui formati verifica l’arrotondamento a metà verso l’esterno, come `Intl.NumberFormat`.

## Come ripetere

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\scripts\build.ps1 -DeviceTests
```

Il seriale va sostituito con quello del dispositivo scelto in `adb devices`. L’esecuzione conclusa con successo ha svolto tutti i sei test sul solo emulatore API 35. Un primo tentativo aveva incluso anche un altro emulatore API 31, che ha perso il focus e si è disconnesso: quel tentativo non costituisce una validazione di Android 12.

I report generati da Gradle sono in:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

## Verifiche funzionali

I test JVM coprono i confini di tutte le sette finestre, le marche separate, medie e prezzi giornalieri, quantità frazionarie, nuovi prodotti, anniversari e anni bisestili. Verificano anche centesimi, date di Roma durante il cambio dell’ora, valori non validi, parsing JSON e arrotondamenti mostrati nell’interfaccia.

I test del ViewModel verificano che i dati restino disponibili dopo un errore di rete, che un acquisto non venga inviato due volte contemporaneamente, che una scrittura incerta non venga ritentata e che una rimozione elimini prodotto e osservazioni dallo stato.

I sei test sull’emulatore verificano navigazione, grafico prodotto, conferma e annullamento della rimozione, compilazione libera del form, conservazione al cambio di schermata e ripristino dello stato, salvataggio singolo, cache offline, scrittura atomica, rilettura e rifiuto di un file troncato. Le mutazioni usano gateway e trasporti fittizi. Non sono stati inviati POST o DELETE di prova al servizio condiviso.

## Controllo visivo

Sono state ispezionate schermate dell’app reale con i dati del sito, su emulatore ridimensionato per:

- telefono da 360 dp in verticale, con testo al 100% e al 200%;
- telefono in orizzontale, 800 × 360 dp;
- tablet da 800 × 1280 dp.

L’intestazione cresce con il testo e non taglia il sottotitolo al 200%. Barre di stato e navigazione rispettano gli inset del sistema. Le schermate possono scorrere sotto l’intestazione e sopra la navigazione. Le animazioni del sistema erano disattivate durante queste verifiche.

Rapporti di contrasto calcolati sui token della palette: testo principale/sfondo `14,78:1`, testo secondario/scheda `7,39:1`, testo di aumento prezzi/scheda `5,99:1`. Queste misure non costituiscono una certificazione completa di accessibilità.

Le immagini locali della verifica sono nella cartella ignorata `artifacts/`. Il pacchetto consegnato è `artifacts/PrezziNelTempo-debug.apk`; il checksum è in `artifacts/SHA256SUMS.txt`.

## Avvisi e limiti

Il lint segnala versioni più recenti di alcune dipendenze. Le versioni sono fissate alla combinazione compilata e verificata. Il manifest disabilita `allowBackup` e `fullBackupContent`; le regole Android 12 escludono i dati dell’app dal backup e dal trasferimento. Nessun controllo lint è stato disabilitato.

Non sono stati verificati telefoni fisici, Android 8–14, lettori di schermo o pubblicazione su Play Store. Non sono stati misurati consumi, batteria o prestazioni su hardware fisico; i tempi del runner e dell’emulatore non sono benchmark del prodotto.

L’APK consegnato usa la firma debug. Per una distribuzione pubblica servono una chiave release privata e la configurazione della relativa build. Le scritture richiedono Internet; non esiste una coda offline. Autenticazione e idempotenza richiedono modifiche al backend originale, come descritto nel README.
