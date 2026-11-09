# 06: Pubblicazione per un gruppo di utenti (App Store e Google Play)

**What to build:** permettere a un gruppo di persone non tecniche (es. chi va in palestra) di installare Rush da un link, su iPhone e su Android, senza file APK e senza usare la chiave TomTom personale dell'autore.

Oggi l'app Android è installata a mano perché usa `USE_EXACT_ALARM` e la richiesta diretta di esenzione dalla batteria, entrambe vietate su Google Play per un'app come Rush. La chiave TomTom è dentro l'APK (`BuildConfig`): chi la estrae la può usare, e tutti gli utenti si dividono la stessa quota gratuita di circa 2.500 richieste al giorno.

Canali previsti:

- **iPhone:** Apple Developer Program (99 €/anno). Prima TestFlight con link pubblico, poi eventualmente App Store "non in elenco" (si installa solo da link).
- **Android:** account Google Play Console (25 $ una tantum). Track di test chiuso, poi produzione. I nuovi account personali devono fare un test chiuso con almeno 12 tester per 14 giorni prima di andare in produzione.

I limiti dei programmi (tester, durata delle build, regole sui test chiusi) vanno ricontrollati sulla documentazione ufficiale prima di iniziare.

**Blocked by:** 05 (Porting su iPhone), solo per la parte iOS; la parte Android può partire subito

**Status:** ready-for-agent

- [ ] Chiave TomTom non più condivisa in chiaro: dietro il backend del ticket 05 (scelta A), oppure una chiave per utente inserita nelle impostazioni
- [ ] Android: variante per Play senza `USE_EXACT_ALARM` né la richiesta diretta di esenzione batteria (es. `SCHEDULE_EXACT_ALARM` con la richiesta all'utente, o un allarme non esatto); la variante installata a mano resta come oggi
- [ ] Android: AAB firmato, scheda dello store (descrizione en/it, screenshot con il percorso demo), modulo Data safety
- [ ] Android: test chiuso con i requisiti richiesti, poi produzione
- [ ] iOS: account sviluppatore, app su App Store Connect, build caricata su TestFlight con link pubblico
- [ ] iOS: questionario sulla privacy e scheda (en/it)
- [ ] Privacy policy pubblica (cosa viene salvato e dove, uso di TomTom, eventuale backend), linkata da entrambi gli store
- [ ] Istruzioni d'installazione da mandare al gruppo (link Play, link TestFlight)
- [ ] `.ai/workflow.md` aggiornato con il processo di rilascio sugli store; `.ai/product.md` non dice più "Sideloaded"
