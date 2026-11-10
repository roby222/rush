# 10: Orario di arrivo ("voglio essere in palestra alle 18:50")

**What to build:** per ogni tragitto l'utente sceglie se l'ora inserita è quella del controllo (come oggi) o quella in cui vuole arrivare. Con l'ora di arrivo Rush calcola da solo quando controllare e dice a che ora partire: "Palestra: parti entro le 18:14". Vale per i tragitti settimanali e per quelli una tantum. L'ora di controllo resta, per chi sa già a che ora parte di solito.

## Studio della soluzione

**Traffico live, non previsione.** TomTom Routing accetta `arriveAt`, ma calcola con il traffico storico previsto e non si combina con `departAt=now`. Rush resta sul traffico live: controlla poco prima della partenza e da lì ricava l'ora a cui partire, `partenza = arrivo − durata live`.

**Durata abituale.** Per sapere quando controllare serve la durata abituale (`usualMin`). Al salvataggio Rush la stima con una chiamata a TomTom, quando partenza o arrivo sono nuovi; se la chiamata fallisce (offline) il tragitto non si salva e l'editor lo dice. Va usato il tempo tipico (`durata − ritardo`), non la strada vuota (`noTrafficTravelTimeInSeconds`): sul percorso di prova la strada vuota diceva 25 min e il tempo tipico 32, e avrebbe mangiato un quarto del margine. Il tempo tipico dipende dall'ora, quindi il primo controllo di ogni arrivo, vicino all'ora vera del tragitto, aggiorna `usualMin` e sposta il secondo controllo.

**Due controlli.** Partenza abituale = arrivo − `usualMin`.

- Primo controllo 30 min prima della partenza abituale: copre ritardi fino a 30 min.
- Secondo controllo 10 min prima: coglie il traffico nato dopo il primo.

Esempio: arrivo 18:50, 22 min abituali → controlli alle 17:58 e alle 18:18.

**Quando notifica.**

- Il primo controllo segue la soglia come oggi.
- Il secondo notifica solo se aggiunge qualcosa: il primo non aveva avvisato (ritardo sotto soglia o controllo fallito) e ora la soglia è superata, oppure il ritardo è cresciuto di almeno 3 min rispetto al primo.
- Se l'ora di partenza è già passata: "parti subito".

**Allarmi.** Resta un allarme esatto per tragitto: `nextCheck` restituisce il primo dei due controlli ancora futuro, e dopo il primo `scheduleAll` programma il secondo. Un tragitto una tantum viene rimosso dopo il suo ultimo controllo (`doneAfterCheck`).

**Controllo manuale.** "Controlla ora" su un tragitto con arrivo calcola la partenza per il prossimo arrivo e non notifica.

**Conflitti.** Due tragitti nello stesso minuto sullo stesso giorno sono in conflitto solo se usano la stessa modalità (controllo o arrivo).

**Blocked by:** —

**Status:** done

- [x] Nell'editor un selettore "Controlla alle / Arriva entro le"; i tragitti esistenti restano "Controlla alle"
- [x] Al salvataggio con arrivo si stima la durata senza traffico; un errore di rete lo dice e non salva
- [x] Due controlli, 30 e 10 min prima della partenza abituale, per i tragitti settimanali e una tantum (test)
- [x] Il secondo controllo notifica solo se il primo non aveva avvisato o il ritardo è cresciuto di 3 min (test)
- [x] Notifica, Home e widget dicono "parti entro le HH:MM" (o "parti subito")
- [x] La lista mostra "arrivo 18:50"
- [x] Stringhe in `values` e `values-it`; `.ai/` aggiornato
