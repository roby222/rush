# 02: Ricerca dell'arrivo vicino alla partenza

**What to build:** nelle impostazioni, se la partenza è già salvata, la ricerca dell'arrivo privilegia i risultati vicini alla partenza (e viceversa se è salvato solo l'arrivo). Funziona in qualunque città, senza coordinate scritte nel codice. Senza riferimenti la ricerca resta globale. L'utente continua a vedere e confermare il risultato prima del salvataggio.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Senza riferimento l'URL di ricerca non contiene parametri di posizione (test)
- [ ] Con riferimento l'URL contiene lat, lon e raggio del punto salvato (test)
- [ ] Query con spazi e accenti codificata correttamente nel path (test)
- [ ] Nessuna coordinata o indirizzo cablato nel codice
- [ ] Su dispositivo: con partenza salvata, cercare solo il nome di una via restituisce quella della stessa città
