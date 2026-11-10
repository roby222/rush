# 12: Chiave TomTom inserita dall'utente

**What to build:** l'APK di release non contiene nessuna chiave TomTom. Al primo avvio chiede all'utente la sua chiave (gratuita, da https://my.tomtom.com/keys), la verifica con una chiamata di prova e la salva sul telefono. La build di debug, installata da Android Studio, continua a leggere `local.properties`.

## Studio della soluzione

**Perché.** Oggi la chiave è dentro l'APK: si estrae in pochi minuti, e chiunque installi l'app consuma la stessa quota di circa 2.500 richieste al giorno. Con la chiave di ognuno, la quota è sua e nell'APK non c'è niente da rubare. È il passo che serve prima di dare l'APK ad altri (ticket 06).

**Quale chiave si usa.** La chiave salvata dall'utente, se c'è; altrimenti quella della build. In debug quindi non cambia niente, ma si può provare il flusso inserendo una chiave. In release la build non ha chiave (`buildConfigField` vuoto per `release`), quindi serve quella dell'utente.

**Verifica.** "Verifica e salva" fa una ricerca di prova. 401/403 = chiave rifiutata, la chiave non si salva; un errore di rete lo dice e non salva.

**Dove si salva.** In un DataStore separato (`secrets`), escluso dal backup e dal trasferimento su un nuovo telefono: la chiave resta sul telefono. Non serve il Keystore: è la chiave gratuita dell'utente, sul suo telefono.

**Chiave rifiutata dopo.** Se TomTom rifiuta la chiave durante un controllo (chiave revocata o scaduta), l'errore lo dice in parole ("TomTom ha rifiutato la chiave: cambiala da ⚙ → Chiave TomTom") invece di "HTTP 403".

**Blocked by:** —

**Status:** done

- [x] La build `release` non contiene la chiave; `debug` la legge da `local.properties`
- [x] Senza chiave, l'app apre la schermata "Chiave TomTom" (link a my.tomtom.com/keys, campo, Verifica e salva) e non si va avanti senza
- [x] La chiave si verifica prima di salvarla; rifiutata o errore di rete lo dicono (test sulla scelta della chiave e sull'errore)
- [x] Dalla lista dei tragitti si cambia la chiave
- [x] Una chiave rifiutata durante un controllo lo dice in parole
- [x] La chiave è esclusa dal backup
- [x] Stringhe in `values` e `values-it`; README e `.ai/` aggiornati
