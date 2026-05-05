# PRD — Remodex Host Desktop App

## Versione Tauri

## 1. Obiettivo

Creare una desktop app minimale, cross-platform, basata su **Tauri**, che permetta all’utente di avviare e gestire `relay` e `phodex-bridge` senza usare terminali visibili.

L’app deve comportarsi come una **tray utility**: piccola, sempre disponibile, con icona Remodex nella system tray, QR generator integrato, rilevamento automatico IPv4 LAN, gestione processi, log essenziali e diagnostica.

L’obiettivo non è riscrivere Remodex. L’obiettivo è creare un **desktop companion** che rende usabile Remodex su PC in modo pulito.

---

# 2. Nome prodotto

Nome consigliato:

```text
Remodex Host
```

Alternative:

```text
Remodex Desktop
Remodex Local Host
Remodex Companion
Remodex Bridge Manager
```

La scelta migliore è **Remodex Host**, perché comunica chiaramente che il PC “ospita” la sessione locale usata dall’app mobile.

---

# 3. Problema

Attualmente, per usare Remodex in modalità sviluppo/local-first, bisogna tenere aperti terminali separati per:

```text
relay
phodex-bridge
eventuale QR/pairing flow
```

Questo crea vari problemi:

```text
- UX brutta
- terminali sempre aperti
- errori difficili da capire
- IPv4 da trovare manualmente
- variabile REMODEX_RELAY da impostare a mano
- QR non integrato in una UI
- bridge/relay non monitorati
- se qualcosa crasha, l’utente deve capirlo da solo
```

Per lo sviluppo va bene. Per una versione distribuibile no.

---

# 4. Soluzione

Creare una app desktop Tauri che:

```text
- vive nella system tray
- mostra una mini dashboard
- rileva IPv4 LAN
- avvia relay
- genera REMODEX_RELAY
- avvia phodex-bridge con env corretta
- genera QR di pairing
- raccoglie log
- mostra stato dei processi
- permette restart/stop
- mostra errori leggibili
```

L’utente finale non deve più aprire terminali.

---

# 5. Target utente

## 5.1 Utente MVP

Sviluppatore o early adopter che vuole usare Remodex da mobile controllando il PC.

Ha bisogno di:

```text
- avvio rapido
- QR pairing
- bridge locale
- relay locale
- zero terminali visibili
```

## 5.2 Utente futuro

Utente pagante non tecnico.

Ha bisogno di:

```text
- installazione one-click
- pairing zero-config
- relay hosted ufficiale
- login/paywall
- auto reconnect
- messaggi di errore chiari
```

---

# 6. Scope MVP

## Dentro l’MVP

```text
- Tauri desktop app
- tray icon Remodex
- mini dashboard
- auto-detect IPv4
- selezione manuale IPv4 se multipli
- start relay locale
- start phodex-bridge locale
- iniezione automatica REMODEX_RELAY
- QR generator
- log viewer minimale
- restart bridge
- restart relay
- stop all
- start minimized opzionale
- launch at startup opzionale
```

## Fuori dall’MVP

```text
- login utente
- paywall
- relay VPS ufficiale
- auto updater
- multi-device management avanzato
- cloud sync
- notifiche push mobile
- gestione account
- marketplace
- analytics avanzate
```

---

# 7. Architettura alto livello

```text
Remodex Host / Tauri
│
├── Frontend UI
│   ├── Dashboard
│   ├── QR view
│   ├── Logs view
│   ├── Settings view
│   └── Error diagnostics
│
├── Rust backend
│   ├── Process Manager
│   ├── Network Detector
│   ├── Config Manager
│   ├── QR Payload Manager
│   ├── Log Collector
│   ├── Health Checker
│   └── Tray Controller
│
├── Managed child processes
│   ├── relay
│   └── phodex-bridge
│
└── Local config
    ├── selected IPv4
    ├── relay port
    ├── mode
    ├── startup preference
    └── last known session
```

---

# 8. Modalità operative

## 8.1 Local Dev Mode

Modalità principale dell’MVP.

```text
Relay: locale
Bridge: locale
Network: LAN
Pairing: QR locale
Auth: nessuna
Costo: zero
```

Esempio:

```text
IPv4: 192.168.1.45
Relay URL: ws://192.168.1.45:8787
Env bridge: REMODEX_RELAY=ws://192.168.1.45:8787
```

## 8.2 Self-hosted Relay Mode

Modalità successiva, utile per utenti tecnici.

```text
Relay: esterno/custom
Bridge: locale
Network: internet/LAN
Pairing: QR con URL custom
```

L’utente inserisce:

```text
wss://my-relay.example.com
```

L’app avvia solo il bridge usando quella URL.

## 8.3 Official Hosted Mode

Modalità futura dopo paywall.

```text
Relay: VPS ufficiale Remodex
Bridge: locale
Network: internet
Pairing: automatico/QR
Auth: account/licenza
```

Questa modalità non va implementata nell’MVP, ma l’architettura deve prevederla.

---

# 9. User flow principale

## Primo avvio

```text
1. Utente installa Remodex Host
2. Apre app
3. App rileva IPv4 locali
4. App sceglie IPv4 più probabile
5. App sceglie porta relay
6. App avvia relay
7. App costruisce REMODEX_RELAY
8. App avvia phodex-bridge
9. App genera QR
10. Utente scansiona QR dall’app mobile
11. App mostra stato: Paired / Connected
```

## Flow giornaliero

```text
1. Utente accende PC
2. Remodex Host parte minimized nella tray
3. Relay e bridge partono automaticamente
4. Utente apre app mobile
5. Connessione pronta
```

## Flow errore

```text
1. Bridge crasha
2. App lo rileva
3. Tray passa a stato warning
4. Dashboard mostra errore leggibile
5. Utente preme Restart Bridge
```

---

# 10. Funzionalità dettagliate

## 10.1 Tray icon

La tray icon è la feature centrale.

### Stati icona

```text
Idle
Running
Warning
Error
Updating/future
```

### Menu tray

Click destro:

```text
Show Remodex Host
Show QR
Start
Stop
Restart Bridge
Restart Relay
Open Logs
Settings
Quit
```

### Click sinistro

Apre la mini dashboard.

---

# 11. Dashboard UI

La dashboard deve essere piccola, scura, leggibile, utility-like.

Dimensione consigliata:

```text
Width: 420-520 px
Height: 560-680 px
```

Layout:

```text
Header
Status cards
Connection info
QR card
Actions
Recent logs
```

---

## 11.1 Header

Contenuto:

```text
Remodex Host
Local bridge manager
```

Stato globale:

```text
Ready
Starting
Running
Waiting for phone
Connected
Warning
Error
Stopped
```

Esempio:

```text
Remodex Host
● Running locally
```

---

## 11.2 Status cards

Mostrare 3-4 righe semplici:

```text
Relay       Running
Bridge      Running
Network     192.168.1.45
Phone       Waiting / Connected
```

Stati possibili:

```text
Not started
Starting
Running
Connected
Crashed
Stopped
Unreachable
Unknown
```

---

## 11.3 Connection card

Mostrare:

```text
Mode
Local LAN

IPv4
192.168.1.45

Relay URL
ws://192.168.1.45:8787
```

Azioni:

```text
Copy Relay URL
Change Network
Restart with this IP
```

---

## 11.4 QR card

Mostrare QR grande e leggibile.

Testo:

```text
Scan with Remodex mobile app
```

Sotto:

```text
Relay: ws://192.168.1.45:8787
```

Azioni:

```text
Refresh QR
Copy pairing payload
```

---

## 11.5 Actions

Pulsanti principali:

```text
Start All
Stop All
Restart All
Restart Bridge
Restart Relay
```

Nel caso normale, mostrare solo:

```text
Restart
Stop
Logs
```

Non sovraccaricare la UI.

---

## 11.6 Logs preview

Mostrare ultime 20-50 righe.

Esempio:

```text
12:01:04 Relay started on 0.0.0.0:8787
12:01:05 Selected IPv4: 192.168.1.45
12:01:06 Bridge started
12:01:09 QR generated
12:01:18 Mobile client connected
```

Azioni:

```text
Open full logs
Copy logs
Clear logs
```

---

# 12. Settings

## 12.1 General

```text
Launch at startup
Start minimized
Auto-start relay and bridge
Minimize to tray on close
```

## 12.2 Network

```text
Auto-detect IPv4
Manual IPv4 selection
Relay port
Use random available port
```

## 12.3 Mode

```text
Local LAN
Self-hosted relay
Official hosted relay — disabled/future
```

## 12.4 Advanced

```text
Path to relay executable
Path to bridge executable
Custom environment variables
Restart crashed processes automatically
Verbose logs
```

---

# 13. Process manager

Il backend Rust deve gestire i processi figli.

## 13.1 Responsabilità

```text
- avviare relay
- avviare bridge
- fermare relay
- fermare bridge
- riavviare singolo processo
- riavviare tutto
- rilevare crash
- raccogliere stdout
- raccogliere stderr
- mantenere stato aggiornato per UI
```

## 13.2 Sequenza corretta di startup

```text
1. Load config
2. Detect network
3. Resolve relay mode
4. Pick relay port
5. Start relay
6. Wait for relay ready
7. Build REMODEX_RELAY
8. Start bridge with env
9. Wait for bridge ready
10. Generate QR payload
11. Emit app state: Running
```

## 13.3 Sequenza corretta di shutdown

```text
1. Stop bridge
2. Wait graceful shutdown
3. Kill bridge if needed
4. Stop relay
5. Wait graceful shutdown
6. Kill relay if needed
7. Emit app state: Stopped
```

Bridge prima, relay dopo.

---

# 14. Network detector

L’app deve trovare l’IPv4 corretto senza chiedere all’utente nella maggior parte dei casi.

## 14.1 Deve rilevare

```text
Wi-Fi IPv4
Ethernet IPv4
VPN IPv4
Loopback
Docker/WSL adapters
VirtualBox adapters
Tailscale/ZeroTier adapters
```

## 14.2 Regole di preferenza

Priorità consigliata:

```text
1. Wi-Fi LAN private IPv4
2. Ethernet LAN private IPv4
3. Tailscale/ZeroTier, solo se esplicitamente scelto
4. VPN, solo se esplicitamente scelto
5. Loopback mai come default per pairing mobile
```

Range validi comuni:

```text
192.168.x.x
10.x.x.x
172.16.x.x - 172.31.x.x
```

## 14.3 Caso con IP multipli

Mostrare scelta:

```text
Multiple networks found

Wi-Fi
192.168.1.45

Ethernet
10.0.0.12

VPN
100.84.31.8

Choose the network your phone is connected to.
```

---

# 15. Relay URL builder

L’app deve costruire automaticamente:

```text
ws://{selected_ipv4}:{relay_port}
```

Esempio:

```text
ws://192.168.1.45:8787
```

In futuro:

```text
wss://relay.remodex.app/session/...
```

---

# 16. Env injection per bridge

Il bridge non dovrebbe richiedere configurazione manuale.

La GUI deve lanciare `phodex-bridge` con env:

```text
REMODEX_RELAY=ws://192.168.1.45:8787
```

Possibili env future:

```text
REMODEX_MODE=local
REMODEX_HOST_ID=...
REMODEX_LOG_LEVEL=info
REMODEX_CONFIG_DIR=...
```

---

# 17. QR generator

## 17.1 Requisito base

La dashboard deve mostrare un QR scansionabile dall’app mobile.

## 17.2 Payload QR

Il payload dovrebbe contenere almeno:

```json
{
  "type": "remodex_pairing",
  "relayUrl": "ws://192.168.1.45:8787",
  "hostName": "Andry-PC",
  "mode": "local"
}
```

Se il bridge ha già un formato ufficiale, usare quello.

## 17.3 Regola importante

La GUI non dovrebbe inventare protocollo se esiste già nel bridge.

La soluzione migliore:

```text
Bridge espone pairing payload
GUI lo legge
GUI renderizza QR
```

Soluzione MVP accettabile:

```text
GUI costruisce payload minimo con relayUrl
```

Soluzione temporanea, meno pulita:

```text
GUI legge il QR/payload dallo stdout del bridge
```

---

# 18. Health checks

L’app deve sapere se relay e bridge sono vivi.

## 18.1 Relay health

Possibili metodi:

```text
- controllare processo attivo
- controllare porta aperta
- endpoint health se disponibile
- websocket connection test se disponibile
```

## 18.2 Bridge health

Possibili metodi:

```text
- controllare processo attivo
- leggere stdout/stderr
- endpoint locale se disponibile
- heartbeat IPC futuro
```

## 18.3 Stati globali

```text
Stopped
Starting
Relay running
Bridge starting
Running
Waiting for phone
Connected
Warning
Error
```

---

# 19. Error handling

Questa è una parte fondamentale del prodotto.

## 19.1 Porta occupata

Messaggio:

```text
Port 8787 is already in use.
```

Azioni:

```text
Use another port
Retry
Open logs
```

## 19.2 Nessun IPv4 valido

Messaggio:

```text
No LAN IPv4 address found.
Connect this computer to Wi-Fi or Ethernet, then retry.
```

Azioni:

```text
Retry network scan
Use manual address
```

## 19.3 Firewall Windows

Messaggio:

```text
Relay is running, but your phone may not reach it.
Windows Firewall could be blocking the port.
```

Azioni:

```text
Show firewall instructions
Copy relay URL
Retry check
```

## 19.4 Bridge crash

Messaggio:

```text
Bridge crashed after startup.
```

Mostrare:

```text
Exit code
Last stderr lines
Restart Bridge
Copy logs
```

## 19.5 Relay crash

Messaggio:

```text
Relay stopped unexpectedly.
```

Azioni:

```text
Restart Relay
Restart All
Copy logs
```

## 19.6 IP sbagliato

Messaggio:

```text
Your phone could not reach this host.
Make sure both devices are on the same network.
```

Azioni:

```text
Change network
Show QR again
Copy relay URL
```

---

# 20. Logs

## 20.1 Tipi di log

```text
App logs
Relay stdout
Relay stderr
Bridge stdout
Bridge stderr
Health check logs
Network detection logs
```

## 20.2 UI logs

La UI deve distinguere:

```text
Info
Warning
Error
Debug
```

Ma visivamente senza diventare una console gigante.

## 20.3 Full logs view

Schermata separata:

```text
Filter:
All / App / Relay / Bridge / Errors

Actions:
Copy all
Export logs
Clear
```

---

# 21. Config storage

L’app deve salvare una config locale.

Contenuto:

```json
{
  "mode": "local",
  "selectedIp": "192.168.1.45",
  "relayPort": 8787,
  "autoStart": true,
  "startMinimized": true,
  "launchAtStartup": false,
  "autoRestart": true,
  "relayPath": null,
  "bridgePath": null,
  "logLevel": "info"
}
```

Non salvare segreti nell’MVP.

---

# 22. Packaging

## 22.1 Windows

Output:

```text
RemodexHostSetup.exe
```

Requisiti:

```text
- tray icon funzionante
- launch at startup
- minimize to tray
- firewall warning
- bundled relay/bridge se possibile
```

## 22.2 macOS

Output:

```text
Remodex Host.app
```

Requisiti:

```text
- menu bar icon
- permissions gestite chiaramente
- notarization futura
```

## 22.3 Linux

Output:

```text
.AppImage
.deb
.rpm opzionale
```

Requisiti:

```text
- tray compatibile dove possibile
- fallback window mode se tray non disponibile
```

Per il tuo caso, Windows è probabilmente la priorità iniziale.

---

# 23. Distribuzione dei binari relay/bridge

Ci sono due strategie.

## Strategia A — Bundle interno

La app include già:

```text
relay executable
phodex-bridge executable
```

Pro:

```text
- UX migliore
- installazione semplice
- zero setup
```

Contro:

```text
- packaging più delicato
- build per ogni OS/architettura
```

## Strategia B — Path configurabile

L’utente indica dove sono relay e bridge.

Pro:

```text
- MVP più facile
- non devi impacchettare tutto subito
```

Contro:

```text
- meno user-friendly
- ancora troppo da developer tool
```

## Scelta consigliata

Per MVP tecnico:

```text
Path configurabile + auto-detect repo locale
```

Per MVP pubblico:

```text
Bundle interno
```

---

# 24. Auto-detect repo locale

In sviluppo, l’app può cercare:

```text
./relay
./phodex-bridge
../relay
../phodex-bridge
```

Oppure chiedere all’utente:

```text
Select Remodex repo folder
```

Poi salva i path.

Flow:

```text
1. First launch
2. App asks: Select Remodex repository folder
3. App validates relay folder
4. App validates phodex-bridge folder
5. App stores paths
6. App enables Start
```

Per una versione distribuita, invece, non dovrebbe servire.

---

# 25. UI design direction

Stile:

```text
dark
minimal
native-feeling
utility
soft glass cards
piccole animazioni
zero clutter
```

Ispirazione:

```text
Tailscale
Docker Desktop
Raycast
LocalSend
Linear-style dark cards
```

Palette consigliata:

```text
Background: #0B0D10
Surface: #15181D
Surface elevated: #1C2027
Border: #2A2F38
Text primary: #F4F7FA
Text secondary: #9AA4B2
Accent blue: #4F8CFF
Success green: #35C759
Warning amber: #FFB020
Error red: #FF5C5C
```

---

# 26. Schermate MVP

## 26.1 Home / Dashboard

Contenuto:

```text
- app title
- global status
- relay status
- bridge status
- selected network
- QR
- primary actions
- recent logs
```

## 26.2 Network picker

Contenuto:

```text
- list of detected interfaces
- recommended badge
- manual IP input
- save/restart
```

## 26.3 Logs

Contenuto:

```text
- filter tabs
- log list
- copy/export
```

## 26.4 Settings

Contenuto:

```text
- startup behavior
- relay mode
- paths
- advanced options
```

## 26.5 Error details

Contenuto:

```text
- human readable error
- technical details collapsed
- suggested fixes
- action buttons
```

---

# 27. Stati UI

## Stopped

```text
Remodex Host is stopped.
[Start]
```

## Starting

```text
Starting local relay...
Starting bridge...
```

## Waiting for phone

```text
Ready to pair.
Scan the QR code from Remodex mobile.
```

## Connected

```text
Connected to mobile device.
```

## Warning

```text
Relay is running, but phone reachability is unknown.
```

## Error

```text
Bridge failed to start.
```

---

# 28. Sicurezza

## 28.1 Local mode

Il relay locale deve essere accessibile dalla LAN, quindi bisogna evitare di esporre più del necessario.

Requisiti:

```text
- mostrare chiaramente la URL usata
- evitare binding pubblico non necessario se possibile
- non salvare token sensibili in chiaro
- rigenerare pairing/session se necessario
```

## 28.2 Pairing

Il QR dovrebbe idealmente includere un token temporaneo.

Requisito consigliato:

```text
Pairing token expires after N minutes
```

Per MVP può essere statico se il protocollo attuale lo richiede, ma la direzione giusta è token temporaneo.

## 28.3 Official mode futura

Serviranno:

```text
- auth
- license validation
- relay session tokens
- revocation
- device trust
```

Non implementarli ora.

---

# 29. Non-goals

Non fare nell’MVP:

```text
- IDE dentro desktop app
- chat UI completa
- file explorer
- gestione GitHub completa
- clone repo
- editor codice
- agent dashboard complessa
- cloud account
```

La app deve restare un **host manager**, non diventare una seconda Remodex.

---

# 30. Metriche di successo MVP

## Tecniche

```text
- relay parte senza terminale
- bridge parte senza terminale
- QR viene generato correttamente
- app mobile riesce a connettersi
- crash bridge rilevato
- restart funziona
- IPv4 rilevato correttamente nella maggior parte dei casi
```

## UX

```text
- setup completato in meno di 2 minuti
- nessun comando manuale dopo installazione/config iniziale
- utente capisce subito se è connesso o no
- errore principale spiegato in linguaggio umano
```

## Validazione

MVP considerato riuscito se:

```text
3 utenti riescono a:
1. installare Remodex Host
2. avviare relay + bridge
3. scansionare QR
4. collegare mobile
5. usare Remodex
senza aprire terminali
```

---

# 31. Roadmap

## Phase 0 — Spike tecnico

Obiettivo: verificare Tauri + process spawning + tray.

Deliverable:

```text
- app Tauri vuota
- tray icon
- start/stop processo dummy
- log stdout in UI
```

Success criteria:

```text
processo child avviabile e stoppabile da UI
```

---

## Phase 1 — Local Host MVP

Obiettivo: eliminare terminali.

Feature:

```text
- start relay
- start bridge
- env injection
- IPv4 detection
- QR display
- logs
- restart
- tray menu
```

Success criteria:

```text
Remodex funziona localmente usando solo la desktop app
```

---

## Phase 2 — Robustness

Obiettivo: renderlo affidabile.

Feature:

```text
- crash detection
- auto restart
- port conflict handling
- network picker
- firewall warning
- config persistence
- improved logs
```

Success criteria:

```text
errori comuni spiegati e recuperabili dalla UI
```

---

## Phase 3 — Packaging

Obiettivo: renderlo installabile.

Feature:

```text
- Windows installer
- macOS app
- Linux AppImage/deb
- icons
- startup setting
- bundled binaries
```

Success criteria:

```text
utente installa e usa senza setup manuale del repo
```

---

## Phase 4 — Hosted Relay / Paywall

Obiettivo: monetizzazione.

Feature:

```text
- official relay mode
- account/login
- subscription/license check
- session relay hosted
- multi-device pairing
```

Success criteria:

```text
utente pagante usa Remodex fuori dalla LAN senza configurare VPS
```

---

# 32. Requisiti tecnici Tauri

## Frontend

Consigliato:

```text
React + TypeScript + Tailwind
```

Perché:

```text
- veloce
- facile creare UI moderna
- coerente con molte app Remodex-like
- buona compatibilità Tauri
```

## Backend

```text
Rust commands exposed to frontend
```

Comandi concettuali:

```text
start_all()
stop_all()
restart_all()
restart_bridge()
restart_relay()
get_status()
get_logs()
detect_networks()
set_selected_network()
get_pairing_payload()
open_settings()
```

Eventi dal backend al frontend:

```text
status_changed
log_received
process_crashed
network_changed
pairing_ready
error_received
```

---

# 33. Data model

## AppState

```text
status:
  stopped | starting | running | waiting_for_phone | connected | warning | error

relay:
  status
  pid
  port
  url
  lastError

bridge:
  status
  pid
  env
  lastError

network:
  selectedIp
  interfaces

pairing:
  qrPayload
  createdAt
  expiresAt optional
```

## NetworkInterface

```text
name
address
kind
isRecommended
isVirtual
isVpn
```

## LogEntry

```text
timestamp
source: app | relay | bridge
level: info | warning | error | debug
message
```

---

# 34. Diagnostica minima

La dashboard dovrebbe avere un bottone:

```text
Run checks
```

Check:

```text
- relay process alive
- bridge process alive
- relay port listening
- selected IP still available
- phone likely same subnet — se possibile
- firewall risk — Windows only, se rilevabile
```

Output:

```text
✓ Relay process running
✓ Bridge process running
✓ Relay reachable locally
⚠ Phone reachability unknown
```

---

# 35. UX copy

## Ready

```text
Ready to pair
Scan this QR code from the Remodex mobile app.
```

## Connected

```text
Mobile device connected
Your local bridge is ready.
```

## Stopped

```text
Remodex Host is stopped
Start the local relay and bridge to pair your phone.
```

## Error

```text
Bridge failed to start
The local bridge exited unexpectedly. Check logs or restart it.
```

## Multiple IPs

```text
Choose the network your phone is connected to
```

---

# 36. Decisioni consigliate

## Decisione 1

Usare Tauri solo come host/tray/process manager.

Non inserire logica Remodex complessa dentro Tauri.

## Decisione 2

Mantenere `relay` e `phodex-bridge` come processi separati.

Questo riduce rischio e permette di aggiornare i componenti indipendentemente.

## Decisione 3

Per MVP, supportare prima Windows.

Poi macOS/Linux.

## Decisione 4

Non fare paywall subito.

Prima validare:

```text
“la gente vuole una Remodex tray app invece dei terminali?”
```

## Decisione 5

La UI deve essere minuscola.

Non creare un “desktop client”. Crea un “host controller”.

---

# 37. MVP finale atteso

Al termine dell’MVP, l’esperienza deve essere questa:

```text
1. Apro Remodex Host
2. Vedo icona nella tray
3. Clicco Show QR
4. Scansiono da mobile
5. Uso Remodex
6. Non vedo terminali
```

Il prodotto è riuscito se sembra una utility di sistema, non un progetto dev fragile.

---

# 38. Versione breve per AI-IDE

```text
Build a Tauri desktop tray app called Remodex Host.

Its job is to manage the local Remodex relay and phodex-bridge processes without visible terminals.

The app must:
- show a system tray icon
- open a small dark dashboard
- detect the machine LAN IPv4
- allow choosing the correct network interface
- start the local relay
- build REMODEX_RELAY using ws://{ip}:{port}
- start phodex-bridge with that env var
- generate and display a QR pairing code
- show process status for relay and bridge
- collect stdout/stderr logs
- allow start, stop, restart relay, restart bridge, restart all
- detect crashes and show readable errors
- persist settings locally
- support launch at startup and start minimized later

Do not reimplement Remodex. Do not build a full code/chat client. This is only a local host manager for relay + bridge.
```
