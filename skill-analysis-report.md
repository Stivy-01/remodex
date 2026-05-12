# Skill Analysis Report

Date: 2026-05-12

Scope: Remodex Android/Kotlin codebase review using the new Compose/Kotlin skill router, focused Compose/Kotlin skills, and `ccc search --lang kotlin`. This is an exploration report only; no production code fixes were applied.

## Method

- Used `remodex-compose-kotlin-skill-router` to avoid loading every skill indiscriminately.
- Used `ccc search --lang kotlin` for semantic code search over Kotlin production and test code.
- Split exploration into non-overlapping sections:
  - Compose UI/state/side effects
  - Services/data coroutine and Flow modeling
  - Tests and repository contracts
- Checked exact source locations for the highest-risk findings before writing this report.

Representative `ccc` searches:

```bash
ccc search --lang kotlin "CancellationException swallowed runCatching suspend call viewModelScope launch repository service"
ccc search --lang kotlin "MutableStateFlow value copy concurrent update race update function"
ccc search --lang kotlin "Compose screen collects StateFlow collectAsState lifecycle Android"
ccc search --lang kotlin "CodexRepository interface default StateFlow MutableStateFlow getter empty hides implementation"
ccc search --lang kotlin "Compose screen local remember state many coordinated UI state holder shell sidebar"
ccc search --lang kotlin "CoroutineScope stored property event router launches lifecycle cancellation"
```

## Findings

### High: thread running/fallback state can lose concurrent updates

File: `android/app/src/main/kotlin/com/remodex/mobile/services/CodexService.kt:365`

`noteProtectedRunningFallback`, `noteTurnStarted`, and `noteTurnFinished` update `_runningTurnIdByThread` and `_protectedRunningFallbackThreadIds` with separate read-modify-write assignments.

Why it matters: these methods are reached from async turn lifecycle paths. Interleavings can drop a running turn, restore stale fallback state, or briefly expose the wrong Stop/interrupt state.

Suggested fix: model running turn state and fallback state as one atomic state object, or guard both flows with one mutex and update related fields together. Prefer `MutableStateFlow.update { ... }` for single-flow updates.

### High: context-window usage state is split across non-atomic flows

File: `android/app/src/main/kotlin/com/remodex/mobile/services/CodexServiceContextWindow.kt:24`

Context usage, loading, and error state are stored in separate maps and updated independently in `applyLiveContextWindowUsage` and `refreshContextWindowUsageInternal`.

Why it matters: a refresh coroutine and live token-usage push can interleave, leaving stale errors, clearing loading before the visible usage update, or overwriting a newer live value with an older RPC result.

Suggested fix: use one per-thread `ContextWindowUsageState` snapshot, or serialize updates through a single lock/update path. Also avoid catching cancellation as a normal load failure in the refresh path.

### High: composer state can leak between threads

File: `android/app/src/main/kotlin/com/remodex/mobile/ui/turn/TurnConversationPane.kt:159`

Several thread-specific values are not keyed by `threadId`: `sending`, `lastError`, `draft`, and `composerAttachments`. Nearby state such as plan mode, mentions, voice state, and git pane state is keyed by `threadId`.

Why it matters: switching active threads in the same composition can carry draft text, attachments, or an in-flight error into the wrong thread.

Suggested fix: hoist composer state per thread, or key all thread-scoped mutable state by `threadId`. If drafts must intentionally survive thread switches, make that persistence explicit in the repository/draft queue rather than accidental composition state.

### Medium: repo diff prefetch can publish stale results

File: `android/app/src/main/kotlin/com/remodex/mobile/ui/shell/MainShell.kt:292`

`enqueueRepoDiffFullTreePrefetch()` captures `activeThreadId` and `gitCwd`, launches a coroutine, and writes `cachedFullWorkingTreeDiff`, `repoDiffSheetFullPatch`, and loading/error fields when the request finishes.

Why it matters: a slower diff from an old thread, cwd, or refresh nonce can overwrite the current sheet state after navigation or refresh.

Suggested fix: convert this into a keyed `LaunchedEffect`, or attach a request token/thread/cwd check before publishing results.

### Medium: command output chunks can be silently dropped

Files:

- `android/app/src/main/kotlin/com/remodex/mobile/data/IncomingEventRouter.kt:919`
- `android/app/src/main/kotlin/com/remodex/mobile/data/CommandExecutionDetailsStore.kt:46`

`handleCommandExecutionDelta` appends output using `state.itemId`, and `appendOutput` returns when no existing row is present.

Why it matters: if output arrives before the state row is created, or if the parser misses an id available elsewhere in the envelope, command output tail is lost with no recovery path.

Suggested fix: resolve one canonical item id before both upsert and output append. If output arrives first, create a placeholder record or buffer the chunk until metadata arrives.

### Medium: design generation jobs race and swallow cancellation

File: `android/app/src/main/kotlin/com/remodex/mobile/ui/design/DesignViewModel.kt:111`

`onSubmitPrompt()` starts a new `viewModelScope.launch` on every submit and mutates `_generationState` via read-modify-write copies. It also wraps suspend repository calls in `runCatching` without a `CancellationException` guard.

Why it matters: concurrent generations can overwrite each other's progress and final state. Cancellation can be converted into an error/no-op path instead of propagating normally.

Suggested fix: keep a current generation `Job` and cancel or reject overlap. Use `_generationState.update { ... }` for state transformations. Replace broad `runCatching` around suspend calls with `try/catch` that rethrows `CancellationException`.

### Medium: Android Compose screen uses non-lifecycle-aware collection

File: `android/app/src/main/kotlin/com/remodex/mobile/ui/design/DesignWorkspaceScreen.kt:56`

`DesignWorkspaceScreen` collects multiple ViewModel `StateFlow`s with `collectAsState()` while most Android screens in the repo use `collectAsStateWithLifecycle()`.

Why it matters: collection can continue while the lifecycle is stopped, doing unnecessary work and diverging from the app's established Android Compose pattern.

Suggested fix: use `collectAsStateWithLifecycle()` for ViewModel/repository state on Android UI.

### Medium: repository interface defaults create fresh flows per access

File: `android/app/src/main/kotlin/com/remodex/mobile/data/CodexRepository.kt:46`

Several interface properties return fresh `MutableStateFlow(...)` instances from default getters, including `threadHistoryPaginationByThread`, `loadingOlderHistoryThreadIds`, `olderHistoryErrorByThread`, `commandExecutionDetailsByItemId`, `turnDraftQueueDepthByThread`, `turnDraftQueuePreviewByThread`, and `pendingBranchPickerThreadId`.

Why it matters: each access can observe a different flow instance, and fake repositories can omit contract members while still compiling. This hides contract drift and can make tests pass against default empty streams rather than realistic shared state.

Suggested fix: make these properties abstract, or move defaults into a shared test fake/base class with stable owned flows.

### Medium: diff row edit state can survive content refreshes

File: `android/app/src/main/kotlin/com/remodex/mobile/ui/shell/GitRepoDiffBottomSheet.kt:189`

The inline patch `edits` map is remembered and cleared only when `scope` changes.

Why it matters: reopening the same scope or refreshing rows can reuse stale edit text for matching row keys even when the underlying diff content changed.

Suggested fix: clear or version edit state by the diff content/session identity, not only by scope.

### Medium: git action base branch can overwrite user input

File: `android/app/src/main/kotlin/com/remodex/mobile/ui/shell/GitActionBottomSheet.kt:86`

`baseBranch` is remembered with `(mode, defaultBaseBranch)`, so a background default branch refresh can reseed the text field while the user is editing.

Why it matters: user-entered branch text can be lost mid-entry.

Suggested fix: seed once on sheet open/mode change, or update from `defaultBaseBranch` only while the field is pristine.

### Low/Medium: coroutine tests use runBlocking and Unconfined patterns

Files include:

- `android/app/src/test/kotlin/com/remodex/mobile/data/IncomingEventRouterServerRequestTest.kt:24`
- `android/app/src/test/kotlin/com/remodex/mobile/data/IncomingEventRouterCommandExecutionTest.kt`
- `android/app/src/test/kotlin/com/remodex/mobile/data/IncomingEventRouterImageGenerationTest.kt`
- `android/app/src/test/kotlin/com/remodex/mobile/data/IncomingEventRouterFileChangeTest.kt`

Why it matters: `runBlocking`, `Dispatchers.Unconfined`, and timeout-driven waits make coroutine tests depend on real scheduling and wall-clock behavior.

Suggested fix: use `runTest`, test dispatchers, deterministic advancement, and explicit awaits.

## Recommended Fix Order

1. Fix running turn/fallback state atomicity in `CodexService`.
2. Fix thread-scoped composer state in `TurnConversationPane`.
3. Fix context-window usage snapshot consistency and cancellation handling.
4. Fix `DesignViewModel` generation job overlap, `StateFlow.update`, and cancellation propagation.
5. Fix stale async UI result publishing in repo diff and git action sheets.
6. Tighten `CodexRepository` interface defaults and update test fakes.
7. Migrate coroutine tests from `runBlocking` to `runTest`.

## Skills Applied

- `remodex-compose-kotlin-skill-router`
- `compose-state-hoisting`
- `compose-state-holder-ui-split`
- `compose-side-effects`
- `compose-state-authoring`
- `kotlin-flow-state-event-modeling`
- `kotlin-coroutines-structured-concurrency`
- `ccc` with `--lang kotlin`
