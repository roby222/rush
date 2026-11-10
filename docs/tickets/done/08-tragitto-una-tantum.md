# 08: Tragitto una tantum (appuntamento)

**What to build:** oltre ai tragitti settimanali, l'utente può aggiungere un tragitto per una sola data e ora: "oggi alle 15:00 ho un appuntamento in Via X", oppure in una data futura. Rush lo controlla una volta, a quell'ora, con le stesse regole degli altri tragitti (soglia, notifica solo se non è normale). Dopo il controllo, o se la data passa con il telefono spento, il tragitto scompare da solo e non resta nella lista.

Punti da decidere nel ticket:

- Partenza predefinita: quella dell'ultimo tragitto usato, oppure la posizione attuale. La posizione attuale richiede un permesso nuovo, quindi meglio la prima.
- L'ora inserita è quella di partenza (come per i tragitti settimanali) o quella dell'appuntamento? Se è l'ora di arrivo, Rush deve stimare quando partire e controllare prima. TomTom Routing accetta `arriveAt`, ma non insieme a `departAt=now`: va verificato prima di prometterlo.
- Un tragitto una tantum ha un controllo solo, oppure anche un secondo controllo qualche minuto prima?

**Blocked by:** 07 (Più tragitti settimanali)

**Status:** done

- [x] Creare un tragitto una tantum con data (oggi come predefinita) e ora; una data o un'ora già passate non si possono salvare
- [x] Il controllo parte alla data e all'ora scelte e segue soglia e regole di notifica dei tragitti settimanali (test su `nextTrigger` o equivalente)
- [x] Dopo il controllo, o se la data è passata (anche dopo un riavvio), il tragitto viene rimosso e il suo allarme cancellato (test)
- [x] Nella lista e nella Home si distingue da un tragitto settimanale (data al posto dei giorni)
- [x] Modifica ed eliminazione prima della data
- [x] Stringhe nuove in `values` e `values-it`
- [x] Scelte (ora di partenza o di arrivo, partenza predefinita) registrate in `.ai/architecture.md`; `.ai/product.md` aggiornato
