# Verifiche di Prezzi per Android

Esecuzioni locali del 23 ottobre 2026, Windows, JBR 21.0.4, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.1.20. Emulatore di riferimento: Android 15, API 35, x86_64.

## Push su main, aggiornamento con gesto e nome Prezzi

Il push del commit `e833177` ha avviato e concluso senza tag manuali il [workflow di release](https://github.com/GoryNickel9/personal-inflation-android/actions/runs/39957811254), pubblicando [v1.2.0](https://github.com/GoryNickel9/personal-inflation-android/releases/tag/v1.2.0), codice 3. Il tag è stato creato sul commit del push. Anche la [CI dello stesso commit](https://github.com/GoryNickel9/personal-inflation-android/actions/runs/39957811239) è passata.

| Controllo | Risultato |
| --- | --- |
| Test JVM | 33 superati |
| Test Python | 9 superati, inclusi 4 sulla generazione delle versioni |
| Test strumentali Android 15 | 9 superati nell’ultima esecuzione completa |
| Lint debug locale e lint release in CI | Superati |
| APK pubblicato v1.2.0 | 1.634.599 byte, firma e checksum verificati dopo il download da GitHub |

I nuovi test UI eseguono il gesto su Riepilogo, Paniere e Acquisto, verificando una lettura aggiuntiva anche entro la finestra di 60 secondi, la conservazione della bozza e l’assenza di scritture. Verificano anche il gesto con paniere vuoto, errore di rete e successivo recupero. L’intestazione mostra «Prezzi» e il pulsante «Aggiorna dati» è assente. I test Python coprono push consecutivi senza modifica della versione base, confronto numerico delle versioni, salti di versione, input non valido e limite Android del codice versione.

La prima esecuzione strumentale non ha installato la build debug perché nell’emulatore era presente una release con firma diversa. Le esecuzioni successive hanno incontrato una finestra ANR di System UI e un timeout di Espresso nella chiusura della tastiera. Dopo aver chiuso la finestra di sistema e stabilizzato l’emulatore, l’intera suite è stata rieseguita con successo, senza modifiche al codice o ai test per ignorare gli errori.

`actionlint` 1.7.12 controlla entrambi i workflow escludendo solo il suo errore sul nuovo campo `concurrency.queue`, che questa versione non riconosce ancora. GitHub ha accettato ed eseguito il workflow con `queue: max`. Non sono stati misurati tempi o consumi su telefoni fisici.

SHA-256 dell’APK v1.2.0: `9e94877bfa71c533a041812b8eeaba26e9317fdcf156a24305df5bb3610fc27a`. Il certificato release è lo stesso della 1.1.0, riportato sotto.

La 1.1.0 pubblica è stata installata sull’emulatore e ha rilevato e scaricato la 1.2.0 dalla nuova release automatica. Dopo verifica, permesso Android, conferma dell’aggiornamento e scansione Play Protect, l’installer ha concluso con successo mostrando il nome «Prezzi». Il pacchetto aggiornato riporta codice 3 e nome 1.2.0; `appId=10230` e `firstInstallTime=2026-10-23 13:03:21` sono rimasti identici. Non c’è stata disinstallazione tra le due release.

La release scaricata è stata riaperta e ispezionata in verticale, a 360 dp con testo al 200% e in orizzontale a 800 × 360 dp. Il nome e il controllo informazioni restano visibili; il vecchio pulsante di aggiornamento è assente. Le immagini sono in `artifacts/prezzi-v120-*.png`. Densità, dimensioni e scala del testo dell’emulatore sono state ripristinate dopo la prova. Non sono stati eseguiti test con un lettore di schermo reale.

## Release e autoupdater

La repo è pubblica. La [release v1.1.0](https://github.com/GoryNickel9/personal-inflation-android/releases/tag/v1.1.0), codice versione 2, è stata prodotta e pubblicata dal [workflow di firma](https://github.com/GoryNickel9/personal-inflation-android/actions/runs/39953953010). Anche la [CI sullo stesso commit](https://github.com/GoryNickel9/personal-inflation-android/actions/runs/39953804714) è riuscita.

| Controllo aggiornato | Risultato |
| --- | --- |
| Test JVM | 33 superati, nessun errore |
| Test Python degli asset release | 5 superati, nessun errore |
| Test strumentali Android 15 | 7 superati, nessun errore |
| Lint debug e release | Superati |
| Workflow YAML | Verificati con actionlint |
| APK pubblicato | 1.634.611 byte, firma e checksum verificati dopo il download da GitHub |
| Aggiornamento reale | Da 1.0.0/codice 1 a 1.1.0/codice 2, senza disinstallazione tra le due release |

I test aggiunti coprono parsing e rifiuto di metadati incoerenti, controllo delle versioni, errori di rete e download, esclusione delle operazioni simultanee, verifica prima dell’installazione e comandi della finestra aggiornamenti. I test Python verificano tag, metadati dell’APK, incremento del codice, API minima e checksum degli asset.

La prova reale ha usato l’emulatore dedicato `emulator-5560`, Android 15/API 35. Una build locale 1.0.0, compilata con la stessa chiave permanente, ha rilevato la release pubblica 1.1.0 e scaricato l’asset da GitHub. L’app ha verificato il file e aperto le impostazioni per consentire le installazioni dalla sorgente. Dopo il consenso, l’installer Android ha chiesto conferma dell’aggiornamento. La scansione Play Protect ha consentito di procedere. Al termine, `dumpsys package` riportava codice 2, nome 1.1.0, lo stesso `appId=10223` e la stessa `firstInstallTime=2026-10-23 12:38:41`. Il controllo manuale nella nuova versione riportava «Hai già la versione più recente disponibile».

Una prova separata con la build debug 1.0.0 ha scaricato lo stesso asset e lo ha rifiutato con il messaggio di firma diversa. L’installer non è stato aperto. Le schermate e le gerarchie UI di queste prove sono nella cartella ignorata `artifacts/`, con prefisso `updater-`.

SHA-256 dell’APK pubblicato: `8ea51918c721f7bd078b60c84f992c938bf758c513ef16f5347f3f7b1aaf818f`. SHA-256 del certificato release: `21d5ae3a375a935aef6e0d00c5459430348f388b59becbe893c8bac9a43dafea`.

Un primo tentativo della suite strumentale ha incontrato una finestra ANR del launcher Pixel dell’emulatore, che sottraeva il focus a due test. Dopo l’arresto del launcher e il risveglio del dispositivo, la suite completa è stata rieseguita: tutti i sette test sono passati. Il primo avvio della CI aveva inoltre richiesto il pacchetto SDK obsoleto `tools`; i workflow ora installano esplicitamente `platform-tools`.

Non sono state provate installazioni su telefoni fisici, rotazione della chiave o interruzioni di processo durante un download reale. I test usano gateway fittizi per gli errori di rete. La prova manuale non ha creato né rimosso dati nel backend.

## Risultati del primo port

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

Il primo APK locale usava la firma debug. La distribuzione attuale usa la chiave release e la pipeline descritte in [RELEASES.md](RELEASES.md). Le scritture richiedono Internet; non esiste una coda offline. Autenticazione e idempotenza richiedono modifiche al backend originale, come descritto nel README.
