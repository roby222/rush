# 09: Widget Android con l'ultimo esito

**What to build:** un widget per la schermata Home di Android che mostra l'esito dell'ultimo controllo di un tragitto, senza aprire l'app: livello scritto in parole e colorato (come nella Home), minuti di viaggio e ritardo, la via del problema se c'è, l'ora del controllo e quella del prossimo. Toccando il widget si apre l'app su quel tragitto. Un pulsante "Controlla ora" avvia un controllo manuale.

Il widget non chiama TomTom da solo: Android lo aggiorna al massimo ogni 30 minuti, e ogni chiamata consuma la quota gratuita (circa 2.500 richieste al giorno). Mostra il `lastCheck` già salvato e si aggiorna quando un controllo finisce. Il rischio da evitare è un "Tutto regolare" di ore prima che sembri attuale.

Stack: Jetpack Glance (`androidx.glance:glance-appwidget`, dipendenza nuova), che legge gli stessi dati della Home da DataStore.

**Blocked by:** 07 (Più tragitti settimanali)

**Status:** ready-for-agent

- [ ] Aggiungendo il widget l'utente sceglie il tragitto (schermata di configurazione); con un solo tragitto la scelta si salta
- [ ] Mostra livello in parole e colore (`trafficColor`), minuti, ritardo, via del primo rallentamento rilevante, ora del controllo e del prossimo controllo
- [ ] Esito più vecchio di una soglia (es. 2 ore) o di un giorno precedente → mostrato sbiadito con l'ora in evidenza, mai come se fosse attuale
- [ ] Nessun controllo ancora fatto, o ultimo controllo fallito → stesso testo della Home per quello stato
- [ ] `CheckWorker` aggiorna i widget a fine controllo, sia pianificato sia manuale; modificare o eliminare un tragitto aggiorna il suo widget (e un tragitto eliminato mostra lo stato "tragitto rimosso")
- [ ] Toccare il widget apre l'app sul tragitto; "Controlla ora" dal widget usa la coda manuale e mostra lo stato "in corso"
- [ ] Tema chiaro e scuro con i token di `Theme.kt`, leggibile nelle dimensioni 2x1 e 4x2; contrasto ≥ 4,5:1
- [ ] Nessuna chiamata di rete dal widget (verificato)
- [ ] Stringhe nuove in `values` e `values-it`
- [ ] `.ai/design.md` (sezione widget) e `.ai/architecture.md` (chi aggiorna il widget) aggiornati
