# 11: Nuovo tragitto a passi e indirizzi con autocompletamento

**What to build:** creare un tragitto diventa un percorso in tre passi, una domanda per schermata: "Da dove a dove?", "Quando?", "Quando ti avviso?". Modificare un tragitto esistente resta una schermata unica. In entrambi i casi gli indirizzi si scelgono da un elenco di suggerimenti che si apre sotto il campo mentre si scrive, al posto di "Cerca → Trovato → Conferma".

Bozza approvata: canvas "Rush – nuovo tragitto a passi" (2026-11-10).

## Studio della soluzione

**Pattern.** Niente carosello né swipe: Material 3 usa il Carousel per sfogliare contenuti, non per inserire dati. Si usano passi in sequenza con Avanti, Indietro (che torna al passo prima, anche con il tasto di sistema) e una barra di avanzamento; lo stepper di Material 1 non esiste più. Il passo corrente vive nel `SavedStateHandle` insieme alla bozza, così sopravvive alla rotazione.

**Autocompletamento.** Si usa la Search API già in uso, con `typeahead=true` e `limit=5`, vicino all'altro luogo (come oggi, 50 km). Parte da 3 lettere dopo 300 ms di pausa nella digitazione: circa 4–6 richieste per campo, trascurabili sulla quota di 2.500/giorno. Ogni suggerimento mostra nome, indirizzo e distanza (`dist` di TomTom). Si salva solo un luogo scelto dall'elenco, quindi con coordinate valide.

**Durata abituale subito.** Appena partenza e arrivo sono scelti, l'editor stima la durata (una chiamata di routing). Al passo "Quando?" può quindi dire "Di solito ci metti 22 min: Rush controlla alle 17:58 e alle 18:18". Il salvataggio riusa la stima; la richiede di nuovo solo se manca (stima fallita).

**Testi.** "Controlla alle / Arriva entro le" diventa "L'ora è quella di… Partenza / Arrivo". La soglia diventa una scelta: Sempre, almeno 5 min, almeno 10 min, Altro… (campo numerico).

**Blocked by:** 10 (Orario di arrivo)

**Status:** done

- [x] "Aggiungi tragitto" apre i tre passi; Indietro (in alto o di sistema) torna al passo precedente, dal primo esce
- [x] Avanti è attivo solo quando il passo è valido; l'ultimo passo ha riepilogo e "Salva tragitto"
- [x] La modifica di un tragitto resta una schermata unica, con gli stessi campi
- [x] Indirizzi da suggerimenti mentre si scrive (da 3 lettere, 300 ms), con distanza; niente più Cerca/Conferma (test sull'URL)
- [x] Durata abituale stimata appena i luoghi sono scelti; il passo "Quando?" mostra gli orari dei controlli (test)
- [x] Soglia a scelta (Sempre, 5, 10, Altro)
- [x] Stringhe in `values` e `values-it`; `.ai/` aggiornato
