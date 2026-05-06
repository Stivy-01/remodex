# PRD — Remodex Android Design Mode via OpenPencil WebView

## 1. Product decision

### Recommendation

Implementare **Design Mode** come modulo sperimentale dentro Remodex Android.

Non creare un prodotto separato nella prima fase.

### Why inside Remodex

Remodex ha già:

```txt
- remote coding context
- Codex/GPT/provider integration
- chat UI
- project management
- session state
- mobile-first dev workflow
```

Design Mode aggiunge:

```txt
- visual canvas preview
- AI-driven UI generation
- design-to-code workflow
- snapshot preview
- optional manual selection/editing
```

Questa feature ha senso come estensione naturale:

> "Controlla il tuo coding agent da mobile, inclusa la generazione/modifica visuale delle UI."

### Why not standalone yet

Un prodotto separato richiederebbe:

```txt
- nuovo nome
- nuovo Play Store listing
- nuova identità visiva
- nuova landing
- nuovo onboarding
- nuovo pricing
- nuovo supporto
- nuova analytics funnel
```

Troppo overhead prima di validare se gli utenti Remodex vogliono davvero progettare UI da mobile.

### Future split condition

Valutare prodotto separato solo se almeno una di queste condizioni diventa vera:

```txt
- >30% utenti attivi usano Design Mode
- utenti non-Codex chiedono solo design-to-code
- export Jetpack Compose/React Native diventa il valore principale
- la UI design richiede uno stile incompatibile con Remodex
- la feature diventa troppo grande per stare nel navigation model di Remodex
```

---

# 2. Product scope

## Product name interno

```txt
Remodex Design Mode
```

Alternative UI label:

```txt
Canvas
Design
Preview
UI Builder
```

Nome consigliato nell'app:

```txt
Design
```

Non usare per ora:

```txt
OpenPencil Android
Figma Mobile
Mobile Design IDE
```

Motivo: evita problemi di confusione prodotto/licensing/aspettative.

---

# 3. One-liner

Design Mode permette all'utente Remodex di generare, visualizzare e modificare UI tramite agent AI usando un canvas web embedded, con snapshot statici per ridurre consumo, payload e complessità mobile.

---

# 4. Core principle

```txt
Remodex remains a remote agent control app.
Design Mode is a visual preview/editing surface for agent-generated UI.
It is not a full native Figma replacement.
```

---

# 5. User problem

L'utente usa Remodex per controllare Codex/agent da mobile, ma quando lavora su UI non basta leggere testo o diff di codice.

Problemi attuali:

```txt
- difficile capire se una UI generata è corretta solo da chat/diff
- impossibile fare review visuale rapida da telefono
- modifiche UI richiedono troppe istruzioni testuali
- preview web/app non sempre comoda dentro workflow mobile
- l'utente vuole dire "sposta questo", "rendi questo più grande", "rifai questo screen"
```

---

# 6. Target users

## Primary

```txt
Mobile/web developers using Remodex to control Codex remotely.
```

## Secondary

```txt
Indie developers generating app screens from prompt.
```

## Not target for MVP

```txt
- professional designers replacing Figma
- collaborative design teams
- users who need pixel-perfect vector editing
- users who need offline full design editor
```

---

# 7. Product positioning

## In Remodex

```txt
Design Mode is a visual workbench for UI tasks.
```

## External description later

```txt
Generate UI screens with your coding agent, preview them visually, and export implementation-ready code.
```

---

# 8. Non-goals

The agent must not implement these in MVP:

```txt
- full native Android renderer for .op files
- complete Figma-like editor
- real-time multi-user collaboration
- full layer panel with all vector operations
- native boolean path operations
- native text layout engine
- full OpenPencil desktop UI
- MCP server running on Android
- Git merge UI for design files
- local LLM inference
- always-on live canvas
- offline complete design editing
```

Hard rule:

```txt
Do not rewrite OpenPencil in Kotlin.
```

---

# 9. High-level architecture

## Recommended architecture

```txt
Remodex Android app
├── Existing Compose UI
│   ├── Chat
│   ├── Projects
│   ├── Providers
│   ├── Sessions
│   └── Settings
│
├── New Design Mode module
│   ├── Native Compose shell
│   ├── WebView canvas surface
│   ├── Snapshot viewer
│   ├── Bottom prompt/action bar
│   ├── Minimal inspector
│   └── Export panel
│
└── Remote server / local bridge
    ├── Agent orchestration
    ├── OpenPencil-compatible renderer
    ├── .op document storage
    ├── Snapshot generation
    ├── Patch stream
    └── Code export
```

---

# 10. Rendering philosophy

Design Mode has two render states:

```txt
1. Edit Mode
2. View Mode
```

## Edit Mode

Expensive, temporary, interactive.

Used when:

```txt
- user enters Design Mode
- user is actively editing
- agent is generating/modifying canvas
- user selects/moves/inspects elements
```

Implementation:

```txt
- mount WebView
- load minimal mobile canvas route
- stream patches
- render visible viewport only if possible
- degrade quality during pan/zoom
- request snapshot when stable
```

## View Mode

Cheap, default, static.

Used when:

```txt
- user is only reviewing
- generation has completed
- app is backgrounded
- user leaves screen
- memory pressure occurs
```

Implementation:

```txt
- show snapshot image
- destroy or pause WebView
- close streams if not needed
- keep .op document state on server/local project
```

---

# 11. Source of truth

The screenshot/snapshot must never be the document source of truth.

Correct model:

```txt
Source of truth:
- .op JSON document
- project files
- patch history

Visual cache:
- thumbnail.webp
- viewport_snapshot.webp
- optional tile snapshots
```

Never:

```txt
snapshot image → used as editable document
```

Always:

```txt
.op document → render → snapshot
```

---

# 12. Data model

## DesignProject

```kotlin
data class DesignProject(
    val id: String,
    val remodexProjectId: String,
    val name: String,
    val currentDocumentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSnapshotUrl: String?,
    val mode: DesignMode
)
```

## DesignDocument

```kotlin
data class DesignDocument(
    val id: String,
    val projectId: String,
    val version: Int,
    val opFileUrl: String?,
    val localOpJson: String?,
    val snapshotUrl: String?,
    val thumbnailUrl: String?,
    val status: DesignDocumentStatus
)
```

## DesignMode

```kotlin
enum class DesignMode {
    VIEW,
    EDIT
}
```

## DesignDocumentStatus

```kotlin
enum class DesignDocumentStatus {
    EMPTY,
    GENERATING,
    READY,
    ERROR,
    OUTDATED_SNAPSHOT
}
```

## CanvasViewport

```kotlin
data class CanvasViewport(
    val centerX: Float,
    val centerY: Float,
    val zoom: Float,
    val widthPx: Int,
    val heightPx: Int,
    val devicePixelRatio: Float
)
```

## CanvasPatch

```kotlin
data class CanvasPatch(
    val id: String,
    val documentId: String,
    val versionFrom: Int,
    val versionTo: Int,
    val type: String,
    val payloadJson: String,
    val author: PatchAuthor,
    val createdAt: Long
)
```

## PatchAuthor

```kotlin
enum class PatchAuthor {
    AI,
    USER,
    SYSTEM
}
```

## ManualOverlayEdit

```kotlin
data class ManualOverlayEdit(
    val id: String,
    val documentId: String,
    val affectedNodeIds: List<String>,
    val operation: String,
    val payloadJson: String,
    val committed: Boolean,
    val createdAt: Long
)
```

---

# 13. Navigation spec

Add a new entry only if feature flag is enabled.

## Existing project screen

Add button/chip:

```txt
Design
```

Placement:

```txt
Project detail screen
├── Chat
├── Files
├── Terminal / Sessions
└── Design
```

Alternative:

```txt
Inside chat toolbar:
[Code] [Terminal] [Design]
```

Recommended for MVP:

```txt
Project detail → tab: Design
```

---

# 14. UX flow

## Flow A — Create design from prompt

```txt
User opens project
→ taps Design
→ sees empty state
→ enters prompt: "Create onboarding screen for..."
→ app starts generation
→ backend/agent creates .op document
→ app shows generation progress
→ app receives first snapshot or live canvas
→ user reviews result
→ user can ask for changes
→ final snapshot shown in View Mode
```

## Flow B — Modify existing design

```txt
User opens existing design
→ app shows snapshot View Mode
→ user taps Edit
→ WebView canvas mounts
→ user selects area/node
→ asks: "Make this card more compact"
→ AI patch applied
→ snapshot refreshed
→ WebView can be destroyed
```

## Flow C — Export code

```txt
User opens design
→ taps Export
→ selects target:
   - Jetpack Compose
   - React Native
   - Flutter
   - React + Tailwind
→ backend generates files
→ app shows file list/code
→ user copies or sends to agent/project
```

## Flow D — Send design task to Codex

```txt
User opens chat
→ says: "Implement the current design in the Android app"
→ Remodex attaches:
   - design document reference
   - exported Compose files
   - snapshot
   - user prompt context
→ Codex works in project
```

---

# 15. Screens

## Screen 1 — Design Empty State

Purpose:

```txt
Let user create first design.
```

UI:

```txt
Top bar:
- Back
- Project name
- Mode badge: Design

Body:
- Empty illustration or simple card
- Title: "Create a UI design"
- Subtitle: "Describe a screen and let the agent generate a visual draft."

Prompt box:
- Placeholder: "Describe the screen..."
- Button: Generate

Quick templates:
- Onboarding
- Paywall
- Settings
- Chat screen
- Dashboard
- Mobile landing
```

Do not include:

```txt
- complex toolbars
- layer panel
- vector tools
- too many OpenPencil controls
```

---

## Screen 2 — Design Workspace

Purpose:

```txt
Preview and modify current design.
```

Layout:

```txt
Top bar:
- Back
- Document name
- Status chip: View / Edit / Generating
- More menu

Canvas area:
- Snapshot viewer in View Mode
- WebView canvas in Edit Mode
- Loading overlay during transitions

Floating controls:
- Edit / View toggle
- Fit to screen
- Snapshot refresh
- Export

Bottom prompt bar:
- Text input
- Attach selected element indicator
- Send button
```

Top bar actions:

```txt
- Rename
- Export
- Reset snapshot
- Open in browser
- Debug render stats
```

Debug visible only in dev builds.

---

## Screen 3 — Inspector Bottom Sheet

Purpose:

```txt
Minimal selected element info.
```

Show:

```txt
- selected element name/id
- type: frame/text/button/image/shape
- size
- position
- quick actions:
  - Ask AI to edit
  - Hide
  - Duplicate
  - Delete
```

MVP can implement only:

```txt
- Ask AI to edit selected
- Delete selected
```

Do not implement full property editor yet.

---

## Screen 4 — Generation Progress

Purpose:

```txt
Tell the user what the agent is doing.
```

Status steps:

```txt
- Understanding prompt
- Creating layout
- Adding components
- Styling screen
- Rendering preview
- Creating snapshot
```

Show partial result as soon as available.

---

## Screen 5 — Export Panel

Purpose:

```txt
Export generated design into code.
```

Targets:

```txt
- Jetpack Compose
- React Native
- Flutter
- React + Tailwind
- HTML + CSS
```

MVP target priority:

```txt
1. Jetpack Compose
2. React Native
3. React + Tailwind
```

Actions:

```txt
- Copy code
- Save to project
- Send to agent
- Share ZIP
```

---

# 16. WebView canvas route

Do not load the whole OpenPencil UI.

Create a dedicated minimal route:

```txt
/mobile-canvas
```

This route must include only:

```txt
- document renderer
- viewport handler
- selection handler
- patch receiver
- snapshot generator
- Android bridge
```

Do not include:

```txt
- desktop sidebar
- desktop command palette
- full file explorer
- account system
- marketing pages
- Electron-specific logic
- unnecessary routes
```

---

# 17. Android ↔ WebView bridge

## Android to JS

Required methods:

```txt
loadDocument(documentJsonOrUrl)
applyPatch(patchJson)
setViewport(viewportJson)
setRenderQuality(level)
setMode(mode)
selectNode(nodeId)
clearSelection()
requestSnapshot()
requestRenderStats()
disposeCanvas()
```

## JS to Android

Required callbacks:

```txt
onCanvasReady()
onDocumentLoaded(documentId, version)
onPatchApplied(patchId, version)
onNodeSelected(nodeJson)
onSelectionCleared()
onSnapshotReady(snapshotUrlOrBase64, version)
onRenderStats(statsJson)
onCanvasError(errorJson)
onMemoryWarning(statsJson)
```

## Quality levels

```txt
LOW
MEDIUM
HIGH
```

Behavior:

```txt
LOW:
- while panning/zooming
- disable expensive shadows/blur if possible
- lower image resolution

MEDIUM:
- after interaction settles
- normal text
- normal bounds

HIGH:
- idle
- snapshot generation
- final render
```

---

# 18. Snapshot lifecycle

## When to create snapshot

```txt
- after generation completes
- after patch stream ends
- when user exits Edit Mode
- before destroying WebView
- when app goes background, if document changed
```

## Snapshot storage

```txt
Local cache:
- latest_snapshot.webp
- thumbnail.webp

Remote/server:
- documentId/version/snapshot.webp
```

## Snapshot invalidation

Snapshot is invalid if:

```txt
document.version > snapshot.version
```

State:

```txt
OUTDATED_SNAPSHOT
```

UI should show:

```txt
"Preview may be outdated"
```

Action:

```txt
Refresh snapshot
```

---

# 19. Performance requirements

## Memory

Target:

```txt
Design Mode should not keep WebView alive when user is only viewing a static result.
```

Rules:

```txt
- destroy WebView after leaving Edit Mode if memory pressure is high
- pause streams when View Mode is active
- never keep multiple canvas WebViews alive
- clear previous document before loading new one
```

## Network payload

Rules:

```txt
- do not send full document on every update
- use patch stream for updates
- send snapshot URL instead of base64 when possible
- compress snapshots as WebP
- keep thumbnails separate from full snapshots
```

## Rendering

Rules:

```txt
- default to View Mode
- enter Edit Mode only on explicit user action or active generation
- lower quality during gestures
- debounce viewport updates
```

Suggested debounce:

```txt
viewport updates while moving: 50–100ms
snapshot generation after idle: 500–1000ms
```

---

# 20. Feature flag

Add a feature flag:

```kotlin
FeatureFlags.designModeEnabled
```

Default:

```txt
false in production
true in debug/internal builds
```

Remote config optional later.

All Design Mode entry points must check this flag.

---

# 21. Agent task boundaries

The coding agent must follow these constraints.

## Must do

```txt
- keep Remodex existing chat/project/provider flows intact
- add Design Mode behind feature flag
- implement Compose shell first
- implement WebView bridge incrementally
- prefer snapshot View Mode over live canvas
- keep OpenPencil-specific code isolated
- make the module removable without breaking Remodex
- add clear TODOs for server-side integration points
```

## Must not do

```txt
- refactor entire Remodex navigation
- rewrite app theme globally
- rename Remodex
- replace existing chat UI
- implement full Figma-like tools
- port OpenPencil renderer to Kotlin
- hardcode one provider
- make canvas always active
- block normal Codex remote control when Design Mode fails
```

---

# 22. Proposed package structure

Use a self-contained module/package.

```txt
app/src/main/java/.../design/
├── DesignModeFeature.kt
├── DesignRoutes.kt
├── DesignViewModel.kt
├── DesignRepository.kt
├── DesignModels.kt
├── DesignModeScreen.kt
├── DesignWorkspaceScreen.kt
├── DesignEmptyState.kt
├── DesignExportSheet.kt
├── DesignInspectorSheet.kt
├── Canvas/
│   ├── CanvasWebView.kt
│   ├── CanvasBridge.kt
│   ├── CanvasBridgeModels.kt
│   ├── CanvasSnapshotViewer.kt
│   └── CanvasRenderState.kt
└── data/
    ├── DesignApi.kt
    ├── MockDesignApi.kt
    └── RemoteDesignApi.kt
```

If project uses different architecture, adapt names but keep isolation.

---

# 23. Implementation phases

## Phase 0 — Branch setup

Branch:

```txt
feature/design-mode-webview
```

Goals:

```txt
- add feature flag
- add navigation entry in project screen
- add empty Design tab
- no backend dependency
```

Acceptance criteria:

```txt
- app builds
- existing Remodex flows unchanged
- Design tab hidden when flag disabled
- Design tab visible when flag enabled
```

---

## Phase 1 — Native shell

Implement:

```txt
- DesignWorkspaceScreen
- Empty state
- Prompt box
- View/Edit toggle
- Mock generation state
- Mock snapshot viewer
```

No WebView yet.

Acceptance criteria:

```txt
- user can open Design tab
- user can type prompt
- mock generation status appears
- mock snapshot image/card appears
- no crash when rotating/backgrounding
```

---

## Phase 2 — Snapshot View Mode

Implement:

```txt
- CanvasSnapshotViewer
- zoom/pan static image
- loading/error/outdated states
- thumbnail handling
```

Acceptance criteria:

```txt
- app can show snapshot URL/local file
- user can pinch zoom and pan
- View Mode consumes no WebView
- switching projects clears previous snapshot state
```

---

## Phase 3 — WebView skeleton

Implement:

```txt
- CanvasWebView composable
- AndroidView wrapper
- JS bridge object
- load local test HTML asset
- onCanvasReady callback
- requestSnapshot mock callback
```

Acceptance criteria:

```txt
- WebView loads only in Edit Mode
- WebView is removed in View Mode
- JS can call Android bridge
- Android can call JS methods
- errors are shown without crashing app
```

---

## Phase 4 — Mobile canvas route integration

Implement against hosted/local route:

```txt
/mobile-canvas
```

Required:

```txt
- load document
- set viewport
- select node
- receive selected node
- request snapshot
```

Acceptance criteria:

```txt
- app loads a test .op document
- user can enter Edit Mode
- canvas appears
- user can exit Edit Mode
- snapshot appears after exit
- WebView destroyed or paused correctly
```

---

## Phase 5 — Agent prompt integration

Implement:

```txt
- prompt submit from Design Mode
- create design task
- stream progress states
- receive document version updates
- receive snapshot update
```

MVP API can be mocked.

Acceptance criteria:

```txt
- prompt creates generation session
- progress updates shown
- final snapshot appears
- failed generation shows retry
```

---

## Phase 6 — Patch stream

Implement:

```txt
- applyPatch from server
- update document version
- mark snapshot outdated
- refresh snapshot after idle/completion
```

Acceptance criteria:

```txt
- patch stream can update canvas in Edit Mode
- View Mode shows outdated indicator until snapshot refresh
- patch errors do not break project chat
```

---

## Phase 7 — Minimal selection and inspector

Implement:

```txt
- tap/select element in WebView
- JS sends selected node metadata
- Compose bottom sheet shows selected info
- prompt can include selected node context
```

Acceptance criteria:

```txt
- selected element appears in inspector
- user can ask AI to modify selected element
- selected node id is included in request payload
```

---

## Phase 8 — Export panel

Implement:

```txt
- export target selector
- request export
- show generated files
- copy code
- send to agent/project
```

Acceptance criteria:

```txt
- user can export Jetpack Compose mock/real output
- generated code can be copied
- export failure is recoverable
```

---

# 24. API contract draft

## Create design generation

```http
POST /api/design/projects/{projectId}/generate
```

Request:

```json
{
  "prompt": "Create a clean onboarding screen for a motorcycle ride tracker",
  "target": "jetpack_compose",
  "context": {
    "remodexProjectId": "abc",
    "currentFiles": [],
    "styleHints": []
  }
}
```

Response:

```json
{
  "generationId": "gen_123",
  "documentId": "doc_123",
  "status": "generating"
}
```

## Get generation status

```http
GET /api/design/generations/{generationId}
```

Response:

```json
{
  "generationId": "gen_123",
  "status": "rendering_snapshot",
  "steps": [
    {
      "label": "Creating layout",
      "status": "done"
    },
    {
      "label": "Rendering preview",
      "status": "active"
    }
  ],
  "documentId": "doc_123",
  "documentVersion": 3,
  "snapshotUrl": null
}
```

## Get document

```http
GET /api/design/documents/{documentId}
```

Response:

```json
{
  "documentId": "doc_123",
  "version": 3,
  "opFileUrl": "https://...",
  "snapshotUrl": "https://...",
  "thumbnailUrl": "https://..."
}
```

## Apply prompt edit

```http
POST /api/design/documents/{documentId}/edit
```

Request:

```json
{
  "prompt": "Make the CTA button larger and more premium",
  "selectedNodeId": "node_123",
  "mode": "ai_patch"
}
```

Response:

```json
{
  "editId": "edit_123",
  "status": "queued"
}
```

## Export code

```http
POST /api/design/documents/{documentId}/export
```

Request:

```json
{
  "target": "jetpack_compose"
}
```

Response:

```json
{
  "exportId": "exp_123",
  "files": [
    {
      "path": "OnboardingScreen.kt",
      "language": "kotlin",
      "content": "..."
    }
  ]
}
```

---

# 25. WebView bridge protocol draft

## Android calls JS

```javascript
window.OpenPencilMobile.loadDocument({
  documentId: "doc_123",
  version: 1,
  opJson: {}
})
```

```javascript
window.OpenPencilMobile.applyPatch({
  id: "patch_123",
  type: "update_node",
  payload: {}
})
```

```javascript
window.OpenPencilMobile.setRenderQuality("LOW")
```

```javascript
window.OpenPencilMobile.requestSnapshot({
  format: "webp",
  quality: 0.82
})
```

## JS calls Android

```javascript
window.AndroidCanvasBridge.onCanvasReady(JSON.stringify({
  renderer: "openpencil-mobile",
  version: "0.1"
}))
```

```javascript
window.AndroidCanvasBridge.onSnapshotReady(JSON.stringify({
  documentId: "doc_123",
  version: 4,
  dataUrl: "data:image/webp;base64,..."
}))
```

```javascript
window.AndroidCanvasBridge.onNodeSelected(JSON.stringify({
  id: "node_123",
  type: "text",
  name: "CTA Label",
  bounds: {
    x: 100,
    y: 200,
    width: 180,
    height: 48
  }
}))
```

---

# 26. UI design direction

Because Remodex is dev/remote-coding oriented, do not suddenly make the whole app look like a design portfolio tool.

## Keep

```txt
- dark technical interface
- chat-first workflow
- project/session mental model
- terminal/code-adjacent feel
- compact controls
```

## Add only in Design Mode

```txt
- softer canvas background
- floating glass controls
- visual preview cards
- selected-node inspector
- export target chips
```

## Avoid

```txt
- colorful Figma-like full toolbar
- skeuomorphic design tools
- huge bottom nav redesign
- onboarding that sells a separate design product
```

---

# 27. Error states

Implement these explicitly.

## Canvas failed to load

Message:

```txt
Canvas failed to load.
You can still continue from the static preview or retry Edit Mode.
```

Actions:

```txt
Retry
Use snapshot
```

## Snapshot outdated

Message:

```txt
Preview may be outdated.
```

Action:

```txt
Refresh
```

## Generation failed

Message:

```txt
Design generation failed.
```

Actions:

```txt
Retry
Show logs
Back to chat
```

## Memory pressure

Message:

```txt
Live canvas was closed to save memory.
Static preview is still available.
```

Action:

```txt
Reopen Edit Mode
```

---

# 28. Analytics events

Add lightweight events.

```txt
design_mode_opened
design_prompt_submitted
design_generation_started
design_generation_completed
design_generation_failed
design_edit_mode_entered
design_edit_mode_exited
design_snapshot_created
design_export_requested
design_export_completed
design_export_failed
design_selected_node_edit_requested
```

Track properties:

```txt
project_id
document_id
target_platform
generation_duration_ms
snapshot_size_bytes
webview_memory_warning
export_target
```

Do not track prompt content unless user consent/logging policy allows it.

---

# 29. Security/privacy

Rules:

```txt
- do not expose provider API keys to WebView
- WebView must not have broad file access unless required
- disable arbitrary navigation outside trusted canvas URL
- restrict JS bridge to trusted origin/local asset
- sanitize document payloads before injecting into JS
- avoid storing sensitive prompts in logs
```

WebView settings:

```kotlin
webView.settings.javaScriptEnabled = true
webView.settings.domStorageEnabled = true
webView.settings.allowFileAccess = false // unless local asset mode requires it
webView.settings.allowContentAccess = false
```

Override navigation:

```kotlin
shouldOverrideUrlLoading = true for non-trusted URLs
```

Trusted origins:

```txt
https://your-canvas-host.com/mobile-canvas
file:///android_asset/mobile-canvas.html
http://10.0.2.2:<dev-port>/mobile-canvas for debug only
```

---

# 30. Testing checklist

## Existing Remodex regression

```txt
- chat still works
- project list still works
- provider settings still work
- Codex remote session still works
- app starts with feature flag disabled
```

## Design Mode

```txt
- open empty state
- submit mock prompt
- show generation state
- show snapshot
- enter Edit Mode
- load WebView
- receive JS callback
- exit Edit Mode
- destroy WebView
- reopen Edit Mode
- switch project
- background/foreground app
- rotate screen if supported
- low-memory simulation
```

## WebView security

```txt
- external links blocked
- bridge unavailable to untrusted pages
- no provider API key visible in JS
```

---

# 31. Definition of Done for MVP

MVP is done when:

```txt
- Design Mode is behind feature flag
- user can open Design tab from project
- user can generate or mock-generate a design from prompt
- user can view static snapshot
- user can enter live WebView Edit Mode
- user can exit Edit Mode and return to snapshot
- app can request/export Jetpack Compose code, even if initially mocked
- existing Remodex workflows remain unchanged
```

Not required for MVP:

```txt
- perfect OpenPencil integration
- complete .op editing
- production backend
- full layer editor
- full export quality
- payment/paywall
```

---

# 32. Suggested first task for the AI coding agent

Use this as the first prompt to the agent:

```txt
You are working on the Android Remodex app. Add an experimental Design Mode behind a feature flag without changing existing chat, provider, or project behavior.

Task 1:
- Create a self-contained design module/package.
- Add FeatureFlags.designModeEnabled.
- Add a Design tab or button inside the project detail screen only when the flag is enabled.
- Implement a placeholder DesignWorkspaceScreen in Jetpack Compose.
- The screen must include:
  - top bar with project name and "Design" label
  - empty state
  - prompt input
  - Generate button
  - mock generation progress
  - mock static snapshot card after generation
  - View/Edit toggle that currently only changes local UI state
- Do not add WebView yet.
- Do not refactor existing navigation globally.
- Do not change Remodex branding or app theme.
- Keep all new files isolated under a design package.
- Ensure the app builds.
```

---

# 33. Second task for the AI coding agent

```txt
Task 2:
Add Snapshot View Mode.

Implement:
- CanvasSnapshotViewer composable.
- It should accept either a remote image URL or local placeholder drawable.
- Support pinch zoom and pan if the app already has gesture utilities; otherwise implement basic static display first.
- Add states:
  - loading
  - ready
  - error
  - outdated
- Keep WebView out of this task.
- Do not implement real backend yet.
- Add mock data in DesignViewModel.
- Ensure switching between mock documents clears old state.
```

---

# 34. Third task for the AI coding agent

```txt
Task 3:
Add WebView Edit Mode skeleton.

Implement:
- CanvasWebView composable using AndroidView.
- CanvasBridge class with @JavascriptInterface methods:
  - onCanvasReady(json: String)
  - onSnapshotReady(json: String)
  - onNodeSelected(json: String)
  - onCanvasError(json: String)
- Load a local HTML asset called mobile_canvas_stub.html.
- The stub must call onCanvasReady after load.
- Add Android -> JS methods:
  - requestSnapshot()
  - setRenderQuality(level)
  - disposeCanvas()
- In DesignWorkspaceScreen:
  - View Mode shows CanvasSnapshotViewer.
  - Edit Mode shows CanvasWebView.
- Exiting Edit Mode should remove WebView from composition.
- Do not connect OpenPencil yet.
- Do not enable arbitrary external navigation.
```

---

# 35. Fourth task for the AI coding agent

```txt
Task 4:
Prepare for OpenPencil mobile canvas integration.

Implement:
- CanvasDocumentPayload model.
- CanvasViewport model.
- CanvasPatch model.
- Methods in CanvasBridge:
  - loadDocument(payload)
  - applyPatch(patch)
  - setViewport(viewport)
  - selectNode(nodeId)
  - clearSelection()
- These methods should call evaluateJavascript safely.
- Add logging only in debug builds.
- Add error handling when WebView is not ready.
- Do not implement actual OpenPencil rendering in Android.
```

---

# 36. Fifth task for the AI coding agent

```txt
Task 5:
Add DesignRepository and mock API contract.

Implement:
- DesignRepository interface.
- MockDesignRepository implementation.
- Methods:
  - generateDesign(projectId, prompt, target)
  - getGenerationStatus(generationId)
  - getDocument(documentId)
  - editDocument(documentId, prompt, selectedNodeId)
  - exportDocument(documentId, target)
- Wire DesignViewModel to repository.
- Keep all responses mocked.
- Show progress steps from repository in UI.
- Do not add real network layer unless existing Remodex networking abstraction already exists.
```

---

# 37. Sixth task for the AI coding agent

```txt
Task 6:
Add Export Panel.

Implement:
- bottom sheet or screen for export
- target selector:
  - Jetpack Compose
  - React Native
  - Flutter
  - React + Tailwind
  - HTML + CSS
- show generated files from MockDesignRepository
- allow copy to clipboard
- allow "Send to Agent" button as placeholder
- Do not write files to project yet unless existing project file APIs already exist.
```

---

# 38. Seventh task for the AI coding agent

```txt
Task 7:
Add selected node inspector.

Implement:
- Receive onNodeSelected from CanvasBridge.
- Store selected node in DesignViewModel.
- Show inspector bottom sheet/card with:
  - node name
  - node type
  - bounds
  - Ask AI to edit button
- When user asks AI to edit selected node, include selectedNodeId in editDocument request.
- Do not implement full property editing.
```

---

# 39. Decision after MVP

After this branch works, decide:

## Keep inside Remodex if:

```txt
- users use it as part of coding workflow
- most prompts are "implement this UI"
- export-to-code is the main value
- Design Mode increases Remodex retention
```

## Split into new app if:

```txt
- users use it without Codex/remote coding
- design generation becomes the primary product
- UI needs a totally different onboarding/style
- non-dev users start asking for it
```

---

# Final recommendation

Fai così:

```txt
1. Non creare subito nuovo prodotto.
2. Crea branch Remodex: feature/design-mode-webview.
3. Implementa Design Mode dietro feature flag.
4. Parti da snapshot mode + WebView stub.
5. Non portare OpenPencil in Kotlin.
6. Usa OpenPencil come renderer/engine/reference lato web/server.
7. Valida se gli utenti Remodex vogliono davvero questa feature.
```

Questa è la strada più pragmatica: minimo rischio di branding, massimo riuso della tua app esistente, e possibilità reale di trasformarla dopo in un prodotto separato se i segnali sono forti.

[1]: https://github.com/Emanuele-web04/remodex?utm_source=chatgpt.com "Emanuele-web04/remodex: Remote Control for Codex."
[2]: https://github.com/ZSeven-W/openpencil/blob/main/README.md?utm_source=chatgpt.com "README.md - ZSeven-W/openpencil"
