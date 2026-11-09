# 07: Più tragitti settimanali

**What to build:** l'utente può salvare più tragitti ricorrenti, ognuno con nome, partenza, arrivo, giorni, ora e soglia di ritardo propri (es. "Palestra" lun/mer/ven alle 17:20, "Ufficio" mar/gio alle 8:00). Ogni tragitto ha il suo controllo pianificato e notifica solo quando il suo viaggio non è normale. Oggi l'app gestisce un solo tragitto: le chiavi di `Prefs.kt` sono uniche, `Scheduler` usa un solo allarme (request code 0) e la Home mostra un solo esito.

Punti da decidere nel ticket (con la scelta registrata in `.ai/architecture.md`):

- Salvataggio: lista di tragitti serializzata in JSON in una sola chiave DataStore, oppure chiavi con prefisso per id. Il tragitto di chi aggiorna l'app va convertito nel primo tragitto della lista, senza perdere niente.
- Pianificazione: un `PendingIntent` per tragitto (request code dall'id), oppure un solo allarme sul prossimo scatto fra tutti i tragitti. Due tragitti alla stessa ora devono produrre due controlli, e la coda unica `check` (REPLACE) non deve far annullare l'uno dall'altro.
- Esito: un `lastCheck` per tragitto, così la Home non mescola i risultati.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Aggiornando da una versione a tragitto singolo, il tragitto esistente resta con partenza, arrivo, giorni, ora e soglia invariati (test sulla conversione; su dispositivo con backup del datastore, vedi `.ai/workflow.md`)
- [ ] Aggiungere, modificare ed eliminare un tragitto dalle impostazioni; la ricerca di un luogo privilegia i risultati vicini all'altro luogo dello stesso tragitto
- [ ] `nextTrigger` calcolato per tragitto; due tragitti alla stessa ora → due controlli e due esiti (test)
- [ ] Notifica e Home indicano il nome del tragitto ("Palestra: 24 min (+6 traffico)")
- [ ] Home: il tragitto con il prossimo controllo o l'ultimo esito rilevante in primo piano, gli altri raggiungibili senza aprire le impostazioni (layout deciso con `.ai/design.md`)
- [ ] "Controlla ora" controlla il tragitto mostrato; un controllo manuale continua a non annullare quelli pianificati
- [ ] Riavvio, cambio d'ora o aggiornamento dell'app riprogrammano tutti i tragitti
- [ ] Stringhe nuove in `values` e `values-it`
- [ ] `.ai/product.md` (oggi dice "one recurring trip"), `.ai/architecture.md` e `.ai/design.md` aggiornati
