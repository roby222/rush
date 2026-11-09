# Spec: manutenzione v1

**Status:** ready-for-agent
**Ticket:** [manutenzione-v1/issues](manutenzione-v1/issues/)

## Problem Statement

L'app controlla il traffico all'orario scelto e notifica il risultato, ma alcuni casi non sono gestiti bene:
la logica che decide cosa notificare non è coperta da test, un controllo interrotto dal sistema può
comportarsi in modo scorretto, la ricerca dell'arrivo può restituire un luogo omonimo in un'altra città,
se le notifiche sono negate l'utente non riceve nulla senza saperlo, e premere "Controlla ora" vicino
all'orario pianificato annulla il controllo programmato.

## Solution

- La decisione "cosa mostrare e se notificare" diventa una logica isolata e testata; un controllo interrotto
  dal sistema non produce né risultato né notifica.
- Se la partenza è già salvata, la ricerca dell'arrivo privilegia i risultati vicini alla partenza, in qualunque città.
- La schermata principale avvisa quando le notifiche sono disattivate e porta alle impostazioni di sistema.
- Controllo manuale e controllo pianificato sono indipendenti: uno non cancella l'altro.

## User Stories

1. Come utente, voglio ricevere la notifica solo se il ritardo raggiunge la soglia impostata, così non vengo disturbato quando il traffico è normale.
2. Come utente, voglio ricevere sempre la notifica quando la soglia è 0, così so ogni volta quanto ci metto.
3. Come utente, voglio che "Controlla ora" notifichi sempre, anche sotto soglia, così vedo subito il risultato richiesto.
4. Come utente, voglio una notifica d'errore chiara se partenza o arrivo non sono impostati, così so cosa configurare.
5. Come utente, voglio una notifica d'errore col motivo se TomTom risponde con un errore, così capisco se è la chiave o il servizio.
6. Come utente, voglio che l'ultimo risultato in app corrisponda all'ultimo controllo davvero completato, così non vedo errori fittizi.
7. Come utente, non voglio notifiche "errore" quando il sistema interrompe un controllo per motivi suoi, così le notifiche restano affidabili.
8. Come utente in qualunque città, voglio che cercando l'arrivo l'app privilegi luoghi vicini alla partenza, così non mi propone una via omonima altrove.
9. Come utente, voglio poter cercare la partenza anche senza nessun punto di riferimento, così posso configurare l'app da zero.
10. Come utente, voglio comunque vedere e confermare il risultato trovato prima del salvataggio, così resto io a decidere.
11. Come utente che ha negato le notifiche, voglio un avviso nella schermata principale, così capisco perché non ricevo nulla.
12. Come utente, voglio un pulsante che apra le impostazioni notifiche dell'app, così le riattivo in un tocco.
13. Come utente con notifiche attive, non voglio vedere l'avviso, così la schermata resta pulita.
14. Come utente, voglio poter premere "Controlla ora" alle 17:19 senza perdere il controllo delle 17:20, così la pianificazione resta affidabile.
15. Come utente, voglio che premere più volte "Controlla ora" non accumuli controlli manuali duplicati, così ricevo un solo risultato.

## Implementation Decisions

- **Esito del controllo**: nuova funzione pura (sospendibile) che riceve le impostazioni, un flag "manuale" e una funzione che restituisce il percorso; restituisce titolo, testo e se notificare. Il worker diventa un guscio: legge impostazioni, chiama la funzione passando il client TomTom reale, salva e notifica.
- **Cancellazione**: l'eccezione di cancellazione delle coroutine non va trattata come errore: va rilanciata, senza salvare né notificare.
- **Ricerca vicino alla partenza**: la ricerca accetta un punto di riferimento opzionale; se presente aggiunge alla query di TomTom Search il bias di posizione (lat/lon) con un raggio di qualche decina di km. La costruzione dell'URL di ricerca diventa una funzione pura testabile. Nessuna coordinata cablata nel codice. La ricerca della partenza usa come riferimento l'arrivo se salvato, altrimenti nessuno.
- **Avviso notifiche**: condizione basata su "notifiche abilitate per l'app" (valida su tutte le versioni Android supportate), ricontrollata quando l'utente torna nell'app. Il pulsante apre le impostazioni notifiche dell'app.
- **Code di lavoro separate**: il controllo pianificato e quello manuale usano due nomi di lavoro unico distinti; ciascuno sostituisce solo un proprio duplicato.

## Testing Decisions

- I test verificano comportamento esterno (input → titolo/testo/notifica, input → URL), non dettagli interni.
- Test unitari JVM, nello stesso stile dei test esistenti su calcolo del prossimo scatto e formattazione del percorso.
- **Esito del controllo**: soglia non raggiunta → non notifica; soglia raggiunta → notifica; manuale → notifica sempre; partenza/arrivo mancanti → errore notificato; errore del client → errore col motivo; cancellazione → rilanciata.
- **URL di ricerca**: senza riferimento nessun parametro di posizione; con riferimento lat, lon e raggio presenti; la query con spazi e caratteri accentati è codificata nel path.
- Avviso notifiche e code separate: verifica manuale su dispositivo (vedi comandi adb nel README).

## Out of Scope

- Notifica di "rete assente" o controllo in ritardo: il telefono è sempre online.
- Rendere il receiver non esportato: scelta consapevole per poter testare via adb.
- Mostrare "prossimo controllo" leggendo l'allarme reale invece di calcolarlo dalle impostazioni.
- Gestione delle impostazioni di avvio automatico dei produttori (Xiaomi, Huawei, ecc.).

## Further Notes

Nessun indirizzo specifico va scritto nel codice: l'app resta generica, partenza e arrivo si configurano solo da impostazioni.
