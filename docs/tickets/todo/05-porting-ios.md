# 05: Porting su iPhone

**What to build:** una versione iOS di Rush con lo stesso comportamento della versione Android: un tragitto, giorni e ora del controllo, soglia di ritardo, "Controlla ora", e un avviso solo quando il viaggio non è normale, con dove si trova il problema. Testi in inglese (fallback) e italiano.

Il vincolo principale è che iOS non permette all'app di svegliarsi a un'ora precisa per chiamare TomTom: `BGAppRefreshTask` parte quando lo decide il sistema. Il controllo pianificato va quindi spostato fuori dall'app. La scelta da fare prima di scrivere codice:

- **A (consigliata):** un piccolo backend (cron o worker serverless) che chiama TomTom all'ora di ogni utente e manda una push APNs solo se serve. Così l'orario è affidabile e la chiave TomTom resta sul server. In cambio partenza, arrivo e orari escono dal telefono: va aggiornato "No backend" in `.ai/product.md` e `.ai/architecture.md`.
- **B:** nessun backend. Un App Intent "Controlla tragitto" che l'utente lancia con un'automazione "Ora del giorno" dell'app Comandi e che mostra una notifica locale. Costa zero, ma ogni utente deve configurare l'automazione a mano.

Stack: SwiftUI nativo (circa 1000 righe da riscrivere). Va riportata la logica pura (`checkOutcome`, parsing TomTom, unione delle sezioni, `trafficLevel`) con gli stessi casi di `LogicTest`. Kotlin Multiplatform conviene solo se si decide di mantenere a lungo una logica condivisa.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Scelta A o B registrata in `.ai/architecture.md`, con il motivo
- [ ] Progetto Xcode nel repo (es. `ios/`), bundle id `io.github.roby222.rush`, chiave TomTom fuori da git come oggi
- [ ] Impostazioni: ricerca di partenza e arrivo (con l'arrivo cercato vicino alla partenza), giorni, ora, soglia; dati salvati sul dispositivo
- [ ] Home: ultimo controllo con durata, ritardo, livello (verde/arancio/rosso) e rallentamenti rilevanti, come su Android
- [ ] "Controlla ora" esegue un controllo e mostra sempre l'esito
- [ ] Controllo pianificato nei giorni e all'ora scelti; avvisa solo se ritardo ≥ soglia, in caso di chiusura o di errore (verificato su un iPhone reale, non sul simulatore)
- [ ] Notifiche negate → avviso nell'app, come su Android
- [ ] Logica pura coperta da test XCTest con gli stessi casi di `LogicTest`
- [ ] Stringhe in `en` e `it`, la lingua segue quella del dispositivo
- [ ] `.ai/` aggiornato (piattaforme, come buildare e testare iOS)
