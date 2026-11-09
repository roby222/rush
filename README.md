# Rush

App Android personale: nei giorni e all'ora che scegli (default lun/mer/ven 17:20) chiede a TomTom
il tempo di percorrenza con traffico live tra due punti e manda una notifica con tempo, ritardo e code.
Niente backend: solo AlarmManager, WorkManager e DataStore.

## Chiave TomTom

1. Registrati su <https://developer.tomtom.com> (piano gratuito, nessuna carta richiesta).
2. Dashboard → **API & SDK Keys** → *Create key*, abilita **Routing API** e **Search API**.
3. Aggiungila a `local.properties` nella root del progetto (file già ignorato da git):

   ```properties
   TOMTOM_API_KEY=la_tua_chiave
   ```
4. Ricompila: la chiave finisce in `BuildConfig.TOMTOM_API_KEY`.

## Build e installazione

Apri la cartella in Android Studio e premi Run, oppure:

```sh
./gradlew installDebug      # telefono collegato via USB con debug USB attivo
./gradlew testDebugUnitTest # test unitari (pianificazione e formattazione)
```

Al primo avvio: concedi le notifiche, apri **Impostazioni**, cerca partenza e arrivo, controlla il risultato
trovato e premi **Conferma**. Le coordinate vengono salvate una volta sola. Se l'app lo suggerisce,
escludila dall'ottimizzazione batteria.

## Testare senza aspettare l'orario

```sh
PKG=io.github.roby222.rush

# allarme programmato (cerca "RTC_WAKEUP" e l'orario "origWhen")
adb shell dumpsys alarm | grep -A4 $PKG

# far partire subito il controllo (come se scattasse l'allarme), e riprogramma il prossimo
adb shell am broadcast -n $PKG/.AlarmReceiver -a $PKG.CHECK

# simulare il riavvio del telefono (solo riprogrammazione)
adb shell am broadcast -n $PKG/.AlarmReceiver -a android.intent.action.BOOT_COMPLETED

# stato del job WorkManager
adb shell dumpsys jobscheduler | grep -A6 $PKG

# simulare Doze: l'allarme deve scattare lo stesso
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce

# log
adb logcat | grep -iE "rush|WM-"
```

Per verificare lo scatto vero: in Impostazioni metti l'ora a 2 minuti da adesso e oggi tra i giorni.

## Struttura

| File | Cosa fa |
|------|---------|
| `TomTom.kt` | chiamate Search/Routing, modelli JSON, testo della notifica |
| `Prefs.kt` | DataStore: luoghi, giorni, ora, soglia, ultimo risultato |
| `Scheduler.kt` | calcolo prossimo scatto, `AlarmManager`, receiver allarme/boot/cambio ora |
| `CheckWorker.kt` | worker espedito con rete: chiama TomTom, salva, notifica |
| `MainActivity.kt` | schermata principale e impostazioni (Compose) |

L'orario segue il fuso del telefono. Con soglia > 0 la notifica arriva solo se il ritardo la raggiunge;
"Controlla ora" e gli errori notificano sempre.
