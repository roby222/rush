# 04: "Controlla ora" non annulla il controllo pianificato

**What to build:** controllo manuale e controllo pianificato sono indipendenti. Premere "Controlla ora" mentre il controllo pianificato è in coda o in esecuzione non lo cancella; premere più volte "Controlla ora" produce un solo controllo manuale.

**Blocked by:** 01 (Esito del controllo testabile e cancellazione corretta)

**Status:** ready-for-agent

- [ ] Trigger del controllo pianificato via adb seguito subito da "Controlla ora" → arrivano entrambi i risultati
- [ ] Più tocchi rapidi su "Controlla ora" → un solo risultato manuale
- [ ] Il controllo pianificato continua a riprogrammare lo scatto successivo
