# Release firmate e aggiornamenti

La distribuzione usa le [GitHub Releases di questa repo](https://github.com/GoryNickel9/personal-inflation-android/releases). Il codice e gli APK sono pubblici; la chiave privata resta fuori da Git. L’app non contiene token GitHub o credenziali di firma.

## Come aggiorna l’app

Al ritorno in primo piano l’app controlla l’ultima release stabile, al massimo una volta ogni sei ore. Il pulsante informazioni nell’intestazione apre **Aggiornamenti** e permette un controllo immediato. Una nuova versione aggiunge un badge al pulsante. I problemi di rete durante il controllo automatico non bloccano il paniere.

Il download parte dal pulsante **Scarica aggiornamento**. Prima di abilitare l’installazione, l’app verifica dimensione, SHA-256, package `cc.baldeddu.prezzi`, versione dichiarata, compatibilità Android e certificato uguale a quello della versione installata. Rifiuta APK di debug, versioni precedenti e firme differenti. Il controllo viene ripetuto prima di aprire l’installer. Il download incompleto viene eliminato.

Il file resta nella cache privata e viene condiviso con l’installer tramite `FileProvider`, con accesso temporaneo al solo file. Android può richiedere di consentire le installazioni da questa app e chiede conferma dell’aggiornamento. Il controllo è automatico; download e installazione richiedono il comando dell’utente. Non sono previste installazioni silenziose o un servizio attivo mentre l’app è chiusa.

Il protocollo usa le API e gli asset pubblici documentati da [GitHub](https://docs.github.com/en/rest/releases/releases#get-the-latest-release). L’installazione rispetta i permessi [Android per gli APK esterni](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

## Passaggio dal primo APK debug

Il primo APK locale usava la firma debug. Android impedisce di sostituirlo con un APK firmato con la nuova chiave release. Disinstalla quella build una sola volta e installa l’APK dalla release GitHub. Questo rimuove cache e bozze locali; i prezzi conservati sul server restano disponibili.

Dalla prima installazione release in poi, gli aggiornamenti usano sempre la stessa chiave e mantengono i dati dell’app. Non disinstallare l’app per le release successive. Non sostituire la chiave di firma senza progettare una migrazione delle firme.

## Pubblicare una nuova versione

1. Aumenta `versionCode` e aggiorna `versionName` in `version.properties`. Il nome deve avere forma `1.2.0`; il codice deve essere maggiore di quello dell’ultima release.
2. Esegui i test, committa e porta il cambiamento su `main`.
3. Crea e invia il tag corrispondente:

```powershell
git tag v1.2.0
git push origin main
git push origin v1.2.0
```

Il workflow `Signed Android release` verifica che il commit appartenga a `main`, esegue i test Python e JVM, controlla il lint release, compila e firma l’APK, verifica la firma con `apksigner` e controlla tag, metadati e incremento del codice. Crea una release draft con tutti gli asset e la pubblica solo dopo il caricamento completo. Le richieste di pull eseguono il workflow CI senza accesso alla chiave.

Gli asset pubblicati sono:

| Asset | Contenuto |
| --- | --- |
| `PrezziNelTempo-<versione>.apk` | APK release firmato e ottimizzato |
| `update.json` | Schema 1, package, codice/nome versione, API minima, nome APK, dimensione e SHA-256 |
| `SHA256SUMS.txt` | Checksum dell’APK e del manifest |

Le action sono fissate a commit SHA. Le release non vengono sovrascritte: se una pubblicazione fallisce dopo aver creato la draft, controlla ed elimina solo quella draft incompleta prima di ripetere il workflow. Non spostare un tag di una release già pubblicata.

## Chiave e secret

Il workflow usa quattro GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Il keystore viene decodificato solo nella cartella temporanea del runner ed eliminato alla fine dello step. Password e chiave non entrano negli asset o nel repository.

La copia locale di questa chiave e le credenziali sono in `C:\Users\GoryNickel\.codex\signing\personal-inflation-android`, con ACL limitate all’utente e al sistema. Conserva un backup protetto di entrambi i file: GitHub non permette di rileggere il valore dei secret. Il certificato usa RSA 3072 e una validità di 10.000 giorni.

Per compilare una release locale, imposta le quattro variabili `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` nel processo e avvia `gradlew assembleRelease`. Senza tutte e quattro la build release si interrompe; non genera silenziosamente un APK non firmato. Non inserire queste variabili in file tracciati da Git.

## Limiti

Il controllo dipende dall’API pubblica GitHub e dai suoi limiti di richieste. Il download è limitato a 100 MiB e riparte dall’inizio se l’app viene terminata. La firma accetta il certificato corrente, senza rotazione della chiave. Gli acquisti e il relativo database continuano a usare il backend originale; la pubblicazione delle release non modifica i dati del paniere.
