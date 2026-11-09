# 01: Esito del controllo testabile e cancellazione corretta

**What to build:** la decisione su cosa mostrare e se notificare viene estratta dal worker in una logica pura e coperta da test. Un controllo interrotto dal sistema (cancellazione del lavoro) non salva un risultato e non notifica un falso errore. Comportamento visibile per l'utente invariato negli altri casi.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Soglia di ritardo non raggiunta in un controllo pianificato → nessuna notifica (test)
- [x] Soglia raggiunta, o soglia 0 → notifica col titolo "<arrivo>: N min (+M traffico)" (test)
- [x] Controllo manuale → notifica sempre, anche sotto soglia (test)
- [x] Partenza o arrivo mancanti → errore notificato con il motivo (test)
- [x] Errore del client TomTom → errore notificato con il messaggio (test)
- [x] Cancellazione durante il controllo → rilanciata, nessun salvataggio né notifica (test)
- [x] "Controlla ora" su dispositivo funziona come prima
