# Architettura Android

## Scelta della piattaforma

Kotlin e Jetpack Compose implementano interfaccia, grafici, form e navigazione Android. Non ci sono WebView, JavaScript incorporato o runtime multipiattaforma. Android 8.0 è il minimo per usare direttamente `java.time` e mantenere piccolo il progetto.

Il backend Next.js esistente resta responsabile di transazioni, riconoscimento concorrente dei prodotti, validazione server e PostgreSQL. Portare quelle credenziali sul telefono esporrebbe il database; duplicare un server nell’app aggiungerebbe un secondo archivio da sincronizzare. Il client usa le API HTTP già presenti.

## Componenti

| Percorso | Responsabilità |
| --- | --- |
| `domain/Tracker.kt` | Modelli, identità, centesimi e validazione del form |
| `domain/Analytics.kt` | Confronti temporali, storici, indice del paniere e YoY |
| `data/TrackerRepository.kt` | HTTP, parsing JSON esplicito e cache atomica versionata |
| `TrackerViewModel.kt` | Stato, aggiornamenti, mutazioni e ricevute confermate |
| `ui/` | Schermate Compose, grafico Canvas e form con stato salvabile |

Il dominio non dipende da Android. `TrackerGateway` permette di provare il ViewModel e le schermate con dati fittizi. La rete usa `HttpURLConnection` su `Dispatchers.IO`: timeout espliciti, HTTPS, redirect disabilitati, risposta limitata a 8 MiB. Non ci sono SDK di analytics, pubblicità o permessi di archiviazione.

Le mutazioni cancellano e attendono la lettura in corso prima di inviare la richiesta. Il pulsante di salvataggio e il ViewModel impediscono due invii contemporanei. Un errore di rete conserva il form; una ricevuta confermata aggiorna immediatamente la schermata. La successiva lettura riconcilia anche modifiche di altri client.

## Invarianti dei calcoli

1. I prezzi unitari sono `Int` in centesimi; la quantità moltiplica i centesimi senza arrotondamento intermedio. Medie, indici e percentuali usano `Double`, come il codice TypeScript originale.
2. Le finestre sono `[inizio, fine)`, in giorni di calendario; il giorno dell’ultimo acquisto è incluso. Non si sostituiscono i mesi di calendario alle durate fisse originali.
3. Il grafico usa l’osservazione con ID maggiore per ciascuna data. Il confronto tra medie conserva tutte le osservazioni. Le marche restano separate per ID prodotto.
4. L’indice concatenato usa solo prodotti presenti in entrambe le date consecutive: `I_t = I_(t-1) × costo_corrente_condiviso / costo_precedente_condiviso`. L’effetto in euro somma le differenze sugli stessi prodotti. Il costo d’ingresso di un prodotto non è inflazione.
5. Il confronto annuo usa l’ultimo punto non successivo all’anniversario; il 29 febbraio passa al 28 febbraio nell’anno precedente. I prezzi futuri non vengono usati per riempire il passato.

## Limiti

L’app legge offline una copia dei dati; non offre creazione o sincronizzazione di acquisti offline. Non conserva versioni precedenti delle quantità o dei prodotti eliminati, come il sito. Modifiche e cancellazioni aggiornano l’archivio condiviso.

L’interfaccia è in italiano e il tema è scuro, come nel progetto originale. I grafici permettono di selezionare euro o percentuale e una data tramite tocco o slider; i valori testuali restano disponibili ai servizi di accessibilità. Non riproducono il doppio asse del grafico web.

Il backend non ha idempotenza per i POST né autenticazione. Queste due funzioni richiedono interventi coordinati nel repository web. Nessuna modifica al server è necessaria per questa versione Android.
