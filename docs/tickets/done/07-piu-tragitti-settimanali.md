# 07: Più tragitti settimanali

**What to build:** l'utente può salvare più tragitti ricorrenti, ognuno con nome, partenza, arrivo, giorni, ora e soglia di ritardo propri (es. "Palestra" lun/mer/ven alle 17:20, "Ufficio" mar/gio alle 8:00). Ogni tragitto ha il suo controllo pianificato e notifica solo quando il suo viaggio non è normale. Oggi l'app gestisce un solo tragitto: le chiavi di `Prefs.kt` sono uniche, `Scheduler` usa un solo allarme (request code 0) e la Home mostra un solo esito.

Punti da decidere nel ticket (con la scelta registrata in `.ai/architecture.md`):

- Salvataggio: lista di tragitti serializzata in JSON in una sola chiave DataStore, oppure chiavi con prefisso per id. Nessuna conversione dal tragitto singolo: l'app ha un solo utente, che reinserisce i tragitti (deciso il 2026-11-09).
- Pianificazione: un `PendingIntent` per tragitto (request code dall'id), oppure un solo allarme sul prossimo scatto fra tutti i tragitti. Due tragitti alla stessa ora e nello stesso giorno non sono ammessi: l'editor lo segnala e non salva.
- Esito: un `lastCheck` per tragitto, così la Home non mescola i risultati.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Dopo l'aggiornamento dalla v0.2.0 l'app parte senza tragitti e invita ad aggiungerne uno; i dati della v0.2.0 sono ignorati e il vecchio allarme non scatta più
- [x] Aggiungere, modificare ed eliminare un tragitto dalle impostazioni; la ricerca di un luogo privilegia i risultati vicini all'altro luogo dello stesso tragitto
- [x] `nextTrigger` calcolato per tragitto, ognuno col suo allarme ed esito (test)
- [x] Salvando un tragitto alla stessa ora:minuto di un altro, con almeno un giorno in comune, l'editor mostra l'errore sotto l'ora col nome dell'altro tragitto e non salva (test)
- [x] Notifica e Home indicano il nome del tragitto ("Palestra: 24 min (+6 traffico)")
- [x] Home: il tragitto con il prossimo controllo o l'ultimo esito rilevante in primo piano, gli altri raggiungibili senza aprire le impostazioni (layout deciso con `.ai/design.md`)
- [x] "Controlla ora" controlla il tragitto mostrato; un controllo manuale continua a non annullare quelli pianificati
- [x] Riavvio, cambio d'ora o aggiornamento dell'app riprogrammano tutti i tragitti
- [x] Stringhe nuove in `values` e `values-it`
- [x] `.ai/product.md` (oggi dice "one recurring trip"), `.ai/architecture.md` e `.ai/design.md` aggiornati

## Piano di implementazione

### Decisioni

| Punto | Scelta | Perché |
|---|---|---|
| Salvataggio dei tragitti | Lista `List<Trip>` in JSON in una sola chiave DataStore `trips` | Aggiunta, modifica, ordine ed eliminazione sono una sola scrittura atomica; la lista resta piccola. Proto DataStore porterebbe un plugin in più senza vantaggi qui. |
| Salvataggio degli esiti | Una chiave per tragitto, `result_<id>`, con `TripResult(lastAt, title, text, check?)` in JSON | Gli esiti li scrive il worker, la configurazione la scrive l'utente: chiavi separate evitano che un controllo sovrascriva una modifica in corso. Due controlli contemporanei scrivono chiavi diverse. |
| Id dei tragitti | `Int`, da un contatore `next_trip_id` che non riusa mai un id | Serve come request code del `PendingIntent` e nei nomi dei lavori; non riusarlo evita che un allarme o un esito vecchio finisca su un tragitto nuovo. |
| Dati della v0.2.0 | Nessuna conversione: le chiavi vecchie restano ignorate e la prima scrittura dei tragitti le elimina | L'app ha un solo utente, che reinserisce il suo tragitto; lo storico si perde. |
| Pianificazione | Un allarme esatto per tragitto, request code = id, extra `trip` = id | Ogni tragitto si programma e si cancella da solo, senza logica per "chi scatta adesso". |
| Stessa ora | Vietata in inserimento: funzione pura `timeConflict(draft, trips): Trip?` = un altro tragitto con la stessa ora:minuto e almeno un giorno in comune | Due controlli nello stesso minuto non servono. I giorni contano: 17:20 il lunedì e 17:20 il martedì non scattano mai insieme. L'errore sta sotto il campo ora ("C'è già «Palestra» alle 17:20") e "Salva" resta disattivato. |
| Allarmi da cancellare | Chiave `alarm_ids` con gli id programmati; `scheduleAll` cancella quelli non più in lista, più il vecchio request code 0 della v0.2.0 | Un tragitto eliminato non deve più scattare, anche se l'app è stata chiusa a metà dell'eliminazione. |
| Code di lavoro | Pianificato `check-<id>` (REPLACE), manuale `check-manual-<id>` (KEEP) | Restano valide le invarianti di oggi, ma per tragitto: un controllo non ne annulla mai un altro di un tragitto diverso. |
| Notifiche | Id notifica = 1000 + id del tragitto; titolo col nome del tragitto | Due tragitti non si sovrascrivono la notifica; "Palestra: 24 min (+6 traffico)". Toccandola si apre la Home su quel tragitto (extra `trip` nell'intent). |
| Tragitto in primo piano nella Home | Funzione pura `featuredTrip(trips, results, now)` | Se un tragitto ha un problema (livello > 0 o errore) controllato nelle ultime 3 ore, il più recente di questi; altrimenti quello col prossimo controllo più vicino; altrimenti il primo. |

### Modello

```kotlin
@Serializable data class Place(val name: String, val lat: Double, val lon: Double)
@Serializable data class Trip(
    val id: Int, val name: String, val from: Place?, val to: Place?,
    val days: Set<DayOfWeek>, val hour: Int, val minute: Int, val minDelayMin: Int,
)
@Serializable data class TripResult(val lastAt: Long, val title: String, val text: String, val check: Check? = null)
```

`Settings` sparisce: chi lo usa (`checkOutcome`, `HomeScreen`, `SettingsScreen`, `Scheduler`) passa a `Trip` + `TripResult?`. `Trip` resta aperto all'aggiunta di un campo opzionale per il ticket 08 (data singola), senza aggiungerlo ora.

### Passi (ognuno chiude con test verdi e app funzionante)

1. **Modello e dati.** `Trip`, `TripResult`, funzioni `tripsFlow()`, `saveTrip()` (che toglie anche le chiavi della v0.2.0), `deleteTrip()`, `resultFlow(id)`. La UI legge il primo tragitto. Test: JSON di `Trip` andata e ritorno; `saveTrip` assegna id crescenti e non riusa quelli eliminati (su `mutablePreferencesOf`, se la funzione è scritta sulle `MutablePreferences`).
2. **Controlli per tragitto.** `Scheduler.scheduleAll()` e `cancel(id)`, `AlarmReceiver` legge l'extra `trip` (senza extra: solo riprogrammazione), `CheckWorker` riceve l'id, salva in `result_<id>`, notifica con id e nome del tragitto; un tragitto eliminato nel frattempo termina senza fare nulla. Funzione pura `alarmPlan(trips, now): Map<Int, ZonedDateTime>`. Test: un orario per tragitto; tragitto senza giorni → nessuna voce; `checkOutcome` con `Trip`; nomi dei lavori distinti per tragitto.
3. **Impostazioni.** Due livelli: elenco tragitti (nome, giorni e ora, tocca per modificare, "Aggiungi tragitto") ed editor (campo nome, partenza, arrivo, giorni, ora, soglia, "Salva", "Elimina" con dialog di conferma). L'editor lavora su una bozza: "Conferma" sulla ricerca aggiorna la bozza, solo "Salva" scrive; la ricerca usa come riferimento l'altro luogo della bozza. Indietro: editor → elenco → Home; stato salvato con `rememberSaveable`. Validazione dell'ora con `timeConflict` contro gli altri tragitti. Test: stessa ora e giorno in comune → conflitto col nome dell'altro; stessa ora in giorni diversi → nessun conflitto; il tragitto in modifica non entra in conflitto con sé stesso.
4. **Home.** Riga di chip con i nomi dei tragitti (scorrevole, nascosta con un solo tragitto) sopra la scheda del verdetto; ogni chip ha il pallino colorato col livello dell'ultimo esito. Selezione iniziale = extra della notifica, altrimenti `featuredTrip`; la scelta dell'utente sopravvive alla rotazione. "Controlla ora" e lo stato "Controllo in corso…" riguardano il tragitto mostrato. Senza tragitti: scheda "Aggiungi il tuo primo tragitto" che apre l'editor. Test: `featuredTrip` (problema recente batte prossimo controllo; problema di 4 ore fa no; nessun esito → prossimo controllo).
5. **Chiusura.** Stringhe nuove in `values` e `values-it` (lint `MissingTranslation` a zero); `.ai/product.md`, `.ai/architecture.md` (le decisioni sopra), `.ai/design.md` (chip e editor), README (funzioni e comando adb con `--ei trip <id>`).

### Verifica su dispositivo

- Sull'emulatore, con percorsi demo: installare sopra la v0.2.0 → Home vuota con "Aggiungi il tuo primo tragitto" e nessun allarme vecchio in `dumpsys alarm`.
- Due tragitti a orari diversi → `adb shell dumpsys alarm | grep -A4 io.github.roby222.rush` mostra due allarmi; `am broadcast … -a io.github.roby222.rush.CHECK --ei trip <id>` per ciascuno → due esiti e due notifiche col nome del tragitto.
- Un terzo tragitto alla stessa ora di un altro, con un giorno in comune → errore sotto l'ora, "Salva" disattivato.
- Sul telefono si installa solo alla fine: l'utente reinserisce il suo tragitto.
- Eliminare un tragitto → il suo allarme sparisce da `dumpsys alarm`.
- `BOOT_COMPLETED` simulato → tutti gli allarmi riprogrammati.
