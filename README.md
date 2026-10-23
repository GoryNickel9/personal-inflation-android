# Prezzi · Android

App Android nativa in Kotlin e Jetpack Compose, portata da `personal-inflation`. Usa lo stesso backend del sito e conserva formule, centesimi, quantità di riferimento e identità dei prodotti per nome e marca.

## Funzioni

- Riepilogo con confronti su 30, 90, 180, 365, 730, 1095 e 1825 giorni. Le finestre sono consecutive, includono il giorno dell’ultimo acquisto e confrontano le medie dei soli prodotti presenti in entrambe.
- Paniere con costo, indice concatenato dei prezzi, effetto in euro, confronto annuo, grafici nativi selezionabili per data e storico completo. Ogni prodotto può mostrare il prezzo unitario o il costo della quantità di riferimento.
- Registrazione di acquisti con testo libero, suggerimenti per prodotto e marca, prezzi con virgola o punto, calendario, luogo e nota. I prodotti esistenti mantengono unità e quantità; i nuovi usano kg o litri. I prodotti precedenti per pezzo restano utilizzabili.
- Tema scuro, navigazione inferiore, conferma delle rimozioni e conservazione dei campi durante la navigazione e la ricreazione della schermata.
- Cache privata atomica degli ultimi dati caricati. Senza rete si possono consultare i dati già salvati; gli acquisti richiedono una risposta dal server. Le scritture non vengono ritentate automaticamente.
- Controllo automatico delle nuove release GitHub e download verificato degli aggiornamenti. Il pulsante informazioni apre il controllo manuale e l’installazione Android.
- Aggiornamento dei dati trascinando verso il basso dall’inizio di Riepilogo, Paniere e Acquisto, con indicatore di caricamento e senza cancellare la bozza. Disponibile anche come azione accessibile «Aggiorna pagina».

## Build

Richiede JDK 17 o 21, Android SDK 36 e accesso ai repository Maven per il primo download delle dipendenze. Android Studio può installare SDK e JDK necessari. L’app richiede Android 8.0 (API 26) o successivo.

1. Apri questa cartella in Android Studio e completa la sincronizzazione Gradle.
2. Crea `local.properties` con `sdk.dir=C\:/percorso/Android/Sdk`, se Android Studio non lo crea.
3. Esegui i controlli e genera l’APK:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Sul computer usato per il port, il JDK segnala `Unable to establish loopback connection` aprendo socket UNIX locali. `scripts/build.ps1` sceglie il JDK di Android Studio, applica un fallback TCP limitato al processo e lancia gli stessi controlli. Non modifica impostazioni globali. Usa `-DeviceTests` per includere i test sull’emulatore o sul telefono collegato.

L’APK di sviluppo si trova in `app/build/outputs/apk/debug/app-debug.apk`. Ogni push su `main` pubblica automaticamente un [APK firmato nelle GitHub Releases](https://github.com/GoryNickel9/personal-inflation-android/releases/latest), con versione e tag generati dalla pipeline. La build release richiede la chiave configurata nelle variabili d’ambiente; la pipeline la riceve da GitHub Secrets. Versioni e prima installazione sono descritte in [RELEASES.md](RELEASES.md).

Per installare su un dispositivo collegato o un emulatore:

```powershell
.\gradlew.bat installDebug
.\gradlew.bat connectedDebugAndroidTest
```

Se sono collegati più dispositivi, imposta `ANDROID_SERIAL` al seriale restituito da `adb devices` per limitare i test a quello scelto.

## Collegamento ai dati

L’endpoint è `https://inflation.baldeddu.cc`, definito in `app/build.gradle.kts`. L’app usa:

| Operazione | Endpoint |
| --- | --- |
| Caricamento | `GET /api/tracker` |
| Nuovo prodotto o nuovo prezzo | `POST /api/products` |
| Rimozione prodotto e relativo storico | `DELETE /api/products/{id}` |

Il database resta sul server. Non servono credenziali Neon nel telefono e non sono state copiate variabili d’ambiente dal progetto web. La cache è nei file privati dell’app e il backup Android è disabilitato. Gli aggiornamenti automatici al ritorno nell’app o al cambio di schermata rispettano una finestra di 60 secondi; il trascinamento verso il basso forza una nuova lettura. Durante il caricamento o il salvataggio non parte una seconda lettura dal gesto.

Il backend originale non richiede autenticazione: chi raggiunge le API può leggere e modificare il paniere. Questa app conserva quel contratto. Per rendere privato il servizio bisogna aggiungere l’autenticazione al backend e poi al client.

Un timeout dopo una scrittura può lasciare l’esito incerto: l’app avvisa di aggiornare il paniere prima di ripetere l’acquisto. L’API originale non offre una chiave di idempotenza. Dopo una scrittura confermata la cache precedente viene invalidata e ricaricata; se la rilettura fallisce, la schermata conserva il risultato confermato in memoria e il prossimo avvio richiede rete.

## Verifiche

I test JVM coprono finestre e confini temporali, quantità frazionarie, marche, inserimento di prodotti nel paniere, confronto annuo, 29 febbraio, centesimi, date di Roma, validazione e protocollo JSON. I test del ViewModel verificano cache offline, errori, mancati ritentativi delle scritture, aggiornamento e rimozione.

`ParityTest` confronta il port Kotlin con risultati prodotti dalle funzioni TypeScript originali su 60 dataset deterministici. Per rigenerarli con Node.js che supporta il type stripping:

```powershell
node scripts/generate-parity.mjs ../personal-inflation
```

I test strumentali usano dati fittizi: esercitano navigazione, form, salvataggio, conferma rimozione, cache e protocollo delle mutazioni. Non registrano acquisti nel database condiviso.

Dettagli tecnici e limiti sono in [ARCHITECTURE.md](ARCHITECTURE.md). I risultati delle esecuzioni locali sono in [VALIDATION.md](VALIDATION.md).
