# Roadmap Android (Remodex)

Piano di porting **local-first** da `CodexMobile` (Swift) verso `android/`. Obiettivo: paritÃ  di protocollo e UX dove ha senso, senza assumere servizi hosted.

---

## Stato per fase

| Fase | Tema | Stato |
|------|------|--------|
| **F** | Trasporto sicuro, JSON-RPC, `thread/list` + merge attivi/archiviati, `IncomingEventRouter`, persistenza messaggi, `CodexRepository` | Fatto |
| **G** | Onboarding (welcome â†’ scanner) | Fatto |
| **H** | QR pairing, `PairingConnectHelper`, `MainShell`, auto-connect | Fatto |
| **I** | Sidebar raggruppata, impostazioni, archivio, `thread/start` | **Fatto** |
| **J** | Schermata turn / conversazione | **In corso** (baseline chiusa; riallineata alle J originali sotto) |
| **K+** | Follow-up parity iOS oltre J22: notifiche, allegati avanzati, automazioni locali | Da definire |
| **8** | Test parity Swift → Android (mirati, pure/model/service) | **Fatto** (`:app:testDebugUnitTest` verde) |
| **9** | Igiene repo + launch audit (artifact, domini, log sessionId) | **Fatto** (inventario + proposta cleanup; nessuna cancellazione automatica) |

---

## Fase I â€” checklist implementazione (completata)

- [x] **I1â€“I9** Package `ui/sidebar/`: `SidebarScreen`, `SidebarThreadGrouping`, `SidebarRelativeTimeFormatter`, `SidebarSearchField`, `SidebarHeader`, `SidebarThreadRow`, `SidebarNewChatButton`, `SidebarThreadRunBadge` (UI pronta; vedi debiti sotto).
- [x] **I10** Drawer: `MainShell` usa `SidebarScreen` con `weight(1f)` + voci Scan QR / Archived / Settings.
- [x] **I11** `ArchivedChatsScreen` + `AppRoutes.Archived` + `AppNavHost`.
- [x] **I12** `SettingsScreen` (font `AppFontStyle` in `SharedPreferences` via `AppFontPreferences`, stato bridge, versione app).
- [x] **Nuova chat** `CodexRepository.startThread` + `CodexServiceStartThread.kt` (`thread/start`, merge lista, `activeThreadId`).
- [x] **I13 (shared)** Completata: introdotti `TrustedPairSummary`, `ThreadRenameDialog`, `UsageStatusSummary` e badge worktree/glass condiviso, con integrazione Sidebar/Settings.
- [x] Stringhe in `res/values/strings.xml` per sidebar, archivio, settings.
- [x] Rimossi `ThreadListSection` e `SettingsPlaceholderScreen` (sostituiti).

### Debiti / follow-up Fase I

- [x] **Badge â€œRunningâ€**: `SidebarScreen` / `ArchivedChatsScreen` usano `runningTurnIdByThread` + `protectedRunningFallbackThreadIds` su `SidebarThreadRow` (parity iOS).
- [x] **`thread/start` sandbox fallback**: Android usa il fallback condiviso `sandboxPolicy` â†’ `sandbox` â†’ minimal anche su `thread/start` / `thread/resume`.
- [x] **Font in Compose**: `RemodexTheme` applica ora la tipografia da `AppFontPreferences` (System/Geist -> SansSerif, JetBrains Mono -> Monospace) con listener runtime alle preferenze.

---

## Fase J â€” blocchi funzionali (incrementali)

Non implementare tutto insieme: un blocco = una PR / un merge logico.

| Blocco | Contenuto | Stato |
|--------|-----------|--------|
| **J.1** | **Guscio + timeline read-only**: titolo thread, `syncThreadHistory` al cambio thread / sessione pronta, `LazyColumn` da `messagesByThread`, bolle minime user/assistant/system + etichette grezze per altri `kind` | **Fatto** |
| **J.2** | **Composer + invio**: campo testo, `CodexRepository.startTurn` â†’ `turn/start` (item `type: text`), riga utente ottimistica + `pending`/`confirmed`/`failed`, errore sotto al composer | **Fatto** |
| **J.3** | **Stop turno**: `runningTurnIdByThread` + `turn/interrupt`; refresh `thread/read` se manca turnId; retry snake_case; Stop in `MainShell` top bar | **Fatto** |
| **J.4** | **Timeline ricca**: thinking unificato, delta streaming, righe tool/command/fileChange senza testo unico | **Fatto** |
| **J.5** | **Markdown / codice**: rendering leggibile (libreria Compose o WebView), copy code block | **Fatto** |
| **J.6** | **Allegati composer** (immagini, picker) | **Fatto** |
| **J.7** | **Runtime controls / approvals-input / git-worktree / usage / voice** allineati a Turn iOS | **Fatto** (baseline; dettagli mappati in J7-J22 sotto) |

File **J.1**: `ui/turn/TurnConversationPane.kt`, `ui/turn/TurnMessageRow.kt`; `HomeMainContent` incolla la conversazione al posto del placeholder.

File **J.2**: `ui/turn/TurnComposerBar.kt`; `services/CodexServiceTurn.kt`; `data/TurnRpcSupport.kt` (`extractTurnIdFromRpcResult`); `MessageTimelineStore.appendPendingUserMessage` / `markUserMessageOutcome`; `CodexRepository.startTurn`.

File **J.3**: `CodexRepository.runningTurnIdByThread` + `interruptTurn`; `IncomingEventRouter` (`turn/started`, `turn/completed`, `turn/failed` â†’ `noteTurnFinished`); `data/ThreadTurnSnapshot.kt`; estensioni in `CodexServiceTurn.kt` (`interruptTurnInternal`, `thread/read` snapshot); `MainShell` azione **Stop**.

**J.3 follow-up (parity iOS)**: `protectedRunningFallbackThreadIds` + `IncomingNotificationParsers.extractTurnIdForTurnLifecycleEvent` (`turn/started` senza turnId â†’ fallback; con id â†’ `noteTurnStarted` e clear fallback); **Stop** e badge sidebar considerano running **o** fallback; reset in `resetBridgeSession`.

File **J.5**: `ui/turn/TurnMarkdownBody.kt` (mikepenz `0.39.2`: core + `m3` + `code`, `rememberMarkdownState(..., retainState = true)` + highlight + **Copy code**); `TurnMessageRow` (`shouldRenderMarkdownBody` per chat/plan/thinking); `app/build.gradle.kts` dipendenze; Kotlin **2.2.0**; `turn_markdown_copy_code`.

File **J.4**: `IncomingNotificationParsers` (`extractTextDelta`, `extractIncomingItemObject`, item id estesi); `ThreadHistoryDecoder.decodeCompletedItem`; `MessageTimelineStore` (`appendStreamingSystemItemDelta`, `mergeLateReasoningDelta`, `completeSystemItem`, `appendSystemLine` con kind); `IncomingEventRouter` (reasoning late merge + `isTurnStreamingActive`, delta file/tool/command/plan, `item/completed` strutturato, fallback `codex/event/*`); `TurnMessageRow` (etichette kind + tint); stringhe `turn_timeline_kind_*`.

File **J.6**: allegati composer completati per picker immagini e invio locale nel flusso `turn/start`; mantenere il percorso local-first e non introdurre storage remoto implicito.

### J7-J22 - riallineamento roadmap originale

Stato audit: **baseline Turn Android chiusa fino a J7 compatto**, ma la roadmap originale divide ancora la paritÃ  iOS in J7-J22. Le righe sotto sono la fonte corrente per i prossimi batch.

- [x] **J7 â€” Composer core**: input testo multilinea, attach immagini, invio, plan toggle e runtime chips presenti. Baseline voce presente lato transport/core; mic composer UI in corso K+.
- [x] **J8 â€” Stato composer**: completo â€” `TurnComposerStateModel`/`TurnComposerReducer` ora wired nel ciclo UI (`TurnConversationPane` -> `TurnComposerBar`) e usati per gating unificato send/text/voice/runtime.
- [x] **J9 â€” Azioni composer**: completo â€” menu compatto `Chat Runtime` (Reasoning/Speed) integrato nel composer con azioni collegate ai selector runtime.
- [x] **J10 â€” Allegati**: baseline immagini fatto â€” picker library/camera, preview/rimozione e limite 4 immagini. Restano allegati non-immagine in K+ dopo conferma contratto bridge.
- [x] **J11 â€” Autocomplete**: completo â€” panel autocomplete nel composer (`/` comandi, `$` skill via `skills/list`, `@` file candidates dal thread) + wiring chip mention (add/remove) e serializzazione nel payload testuale inviato.
- [x] **J12 â€” Code comment / thinking parser**: completo â€” parser/hints centralizzati in modulo condiviso (`TurnTextDirectiveParsing`) e applicati sia al rendering timeline sia alla decodifica storico/completed item (`ThreadHistoryDecoder`), con test dedicati.
- [x] **J13 â€” Plan mode UI**: completo baseline+interaction â€” `TurnPlanAccessoryCard` con snapshot status/progress, rail step status, toggle details, azione `Use plan` (precompila `Implement plan.` con gating) e `Plan details` bottom sheet dedicata con progress/steps/apply.
- [x] **J14 â€” Command execution UI**: baseline fatto â€” righe command/tool/fileChange e output delta sono visualizzate; polish dettagli command resta non bloccante.
- [x] **J15 â€” Subagent / structured input**: baseline fatto â€” subagent/timeline kind, dialog structured input e marker `userInputPrompt` con cleanup. Restano card strutturate/azioni inline.
- [x] **J16 â€” Git toolbar e branch**: baseline fatto â€” branch accessory, picker, checkout guardato, branch elsewhere path routing e git status primitives. Restano diff summary/file summary avanzati.
- [x] **J17 â€” Worktree e handoff**: baseline fatto â€” `moveThreadToProjectPath`, `WorktreeFlowCoordinator`, new worktree chat e branch-elsewhere rebind. Restano overlay handoff completo, recovery avanzata e persistenza associated-worktree.
- [x] **J18 â€” Thread fork UI**: completo baseline+polish â€” oltre a foundation service/repository, ora c'Ã¨ action sheet dedicata di conferma fork (accessory card + trigger da `/fork`) con gestione stato/progress/error e switch automatico al thread forkato.
- [x] **J19 - Sheet status / revert**: completato - `TurnUsageStatusSheet` ora esegue revert runtime via bridge (`workspace/revertPatchApply`) con persistenza stato (`reverted/failed`) e messaggistica di errore locale.
- [x] **J20 - Code e skill formatter**: completo - formatter/test estesi (`$skill`, `skill:` e path `/.codex/skills|/.agents/skills/.../Skill.md`), markdown code block polish (`TurnMarkdownBody`: header language + copy action + resolver fence-language) e migrazione clipboard Compose nuova (`LocalClipboard` + `setClipEntry`).
- [x] **J21 â€” Codee / context window**: baseline fatto â€” strip/sheet usage, live context updates e token progress; resta solo polish/real payload validation.
- [x] **J22 â€” Queue drafts**: completo baseline+polish â€” queue con preview/id/remove, restore/remove da card composer, remove anche in usage sheet, auto-drain con pausa quando composer non Ã¨ vuoto (steered UX), restore gated quando idle+composer vuoto, metadata draft (attachments/plan) e retry prepend su failure.

### K+ - oltre J22

- [x] **K+ Notifications**: complete - notifiche locali per turn completati/falliti e richieste approvazione/input, con gating foreground/background e tap intent verso il thread corretto.
- [x] **K+ Allegati avanzati**: completati - picker file non immagine nel composer, preview/rimozione in strip allegati, limiti dimensione per item con errori locali, e serializzazione dei contenuti testuali nel payload turn come contesto allegato.
- [x] **K+ Runtime polish**: completato - componenti condivisi (`TrustedPairSummary`, `ThreadRenameDialog`, `UsageStatusSummary`, badge worktree/glass) integrati dove riducono duplicazione reale tra view.

---

## Riferimenti file (Fase I)

| Area | Path principali |
|------|------------------|
| Sidebar | `android/app/src/main/kotlin/com/remodex/mobile/ui/sidebar/` |
| Archivio | `android/.../ui/archived/ArchivedChatsScreen.kt` |
| Impostazioni | `android/.../ui/settings/SettingsScreen.kt`, `android/.../data/AppFontPreferences.kt` |
| Navigazione | `android/.../ui/navigation/AppNavHost.kt`, `AppRoutes.kt` |
| Shell drawer | `android/.../ui/shell/MainShell.kt` |
| Nuovo thread | `android/.../services/CodexServiceStartThread.kt`, `data/CodexRepository.kt` |
| Gruppi thread (modello) | `android/.../core/model/CodexThread.kt` (giÃ  allineato a iOS) |

---

## Verifica build

```bash
cd android
./gradlew :app:compileDebugKotlin
```

Su Windows: `gradlew.bat :app:compileDebugKotlin`.

---

*Ultimo aggiornamento roadmap: 2026-04-28 - roadmap riallineata alle J originali J7-J22. In questo batch: I13 completata (shared components + integrazione Sidebar/Settings), J12 completata (parser condiviso + path storico/cache), J13 completata (plan interaction + details sheet), J18 completata (fork action sheet + trigger dedicato), J19 completata (revert runtime wiring + persistence stato), J20 completata (formatter skill path + code-block header/language/copy + migrazione clipboard Compose), J22 completata (steered queue UX + restore gating + metadata drafts), font Compose completato, K+ Notifications/Runtime polish completati e K+ Allegati avanzati completati.*

Il piano dettagliato con YAML todo e mapping I1â€“I22 / J / K / L vive in **Cursor**: `.cursor/plans/roadmap_android_remodex_614dfa0b.plan.md` (percorso tipico sotto la home utente). Questo file in `docs/` resta lâ€™indice versionato nel repo.
