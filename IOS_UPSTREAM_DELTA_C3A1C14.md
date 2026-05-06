# iOS upstream delta after `c3a1c14` — Android catch-up trace

This file maps **every commit on `origin/main` after** merge-base **`c3a1c14`** (`Optimize streaming timeline updates and local thread renames`, 2026-04-27) through **`7c82966`** (tip at time of writing). Use it as a **checklist** for bringing **local Android** in line with **upstream iOS + shared bridge behavior**.

- **Baseline (your port target):** `c3a1c14` — iOS snapshot the Android work was built against.
- **Upstream tip:** `7c82966` — *Add folder search to local project picker*.
- **Scope:** `CodexMobile/` (iOS app), **`phodex-bridge/`**, **`.github/workflows/`** where relevant. Commits that only merge PRs may duplicate file stats from feature commits already merged on the branch; those rows still document **integration points**.

**Android reminders**

- **Payments / RevenueCat** — intentionally out of scope for your port (confirmed).
- Many iOS commits also touch **`phodex-bridge`** (e.g. `project-handler.js`, `workspace-checkpoints.js`). Android must stay compatible with the **same JSON-RPC and bridge behaviors** if you merge those bridge changes.

---

## Roll-up: themes vs Android

| Theme | Upstream commits (rough) | Android catch-up angle |
|--------|---------------------------|-------------------------|
| New chat + local project UX | `fcf67e8`, `79d2142`, `7c82966` (+ `project-handler` / `CodexService+ProjectFolders`) | Folder browser, project picker, **folder search**; bridge **project-handler** parity |
| Plugin mentions (not only `$skill`) | `81b94a6`, `8c38d50`, `c4bc0d8`, `2e19695`, merges | Composer mention model, autocomplete, chips, **turn payload** for plugin refs |
| Git + sidebar | `4e19459` | `git init`, **local-only chat removal**, toolbar/git flows |
| Streaming timeline | `93cd6e2`, `21769fa`, `6c3c85b`, `c7843aa` | **Replay dedupe**, **double-message** fix, **projection/collapsing**, message component polish |
| Workspace images | `625e2b4`, `7869bfa`, `8553266`, `38308f3` | **WorkspaceImages** service analog, safe previews, cache keys, timeline sync |
| Fast mode / model UI | `306ea30`, `09c6b80`, `88296b8` | Model picker, tier/capability bits, composer/runtime menus |
| Assistant phases + checkpoints | `82c0903` | **`CodexService+WorkspaceCheckpoints.swift`** + **`workspace-checkpoints.js`** — largest lift |
| Bridge / infra | `2eea6eb`, `0e838d8`, `445c595`, `1d2f232`, throughout | Watchdog, history compaction, git-issue fixes, **4 MB** thread payload limit |
| CI | `37035d7`, `8124de3`, `6f3f20a` | N/A for Android runtime; useful for iOS release hygiene |

---

## Commit-by-commit (oldest → newest)

### 1. `37035d7` — 2026-04-26 — Add unsigned IPA workflow

| Area | What changed |
|------|----------------|
| **iOS** | — |
| **Other** | Adds `.github/workflows/build-unsigned-ipa.yml`. |
| **Android** | No app work. |

---

### 2. `fcf67e8` — 2026-04-28 — Add local folder browser for new chats

| Area | What changed |
|------|----------------|
| **iOS** | **`CodexService+ProjectFolders`**, **`SidebarLocalFolderBrowserSheet`**, **`SidebarView`** wiring — browse local filesystem for new chat cwd. |
| **Bridge** | **`project-handler.js`** (large), **`bridge.js`** hook, **`project-handler.test.js`**. |
| **Android** | New chat from **sidebar project groups** exists; **full folder browser + bridge project RPC** likely still needed. |

---

### 3. `79d2142` — 2026-04-28 — Extract new chat project picker sheet

| Area | What changed |
|------|----------------|
| **iOS** | New **`SidebarNewChatProjectPickerSheet`**, **`SidebarView`** refactor. |
| **Android** | Dedicated **project picker sheet** UX + state machine. |

---

### 4. `81b94a6` — 2026-04-28 — Add plugin mentions to the composer

| Area | What changed |
|------|----------------|
| **iOS** | **`CodexSkillMetadata`**, **`CodexService+ThreadsTurns`**, **`FileAutocompletePanel`**, **`FileMentionChip`**, **`TurnComposerHostView`**, **`TurnComposerView`**, **`TurnViewModel`**, skill/plugin tests, **`CodexTurnInputPayloadSkillTests`**. |
| **Android** | **`$skill`** / **`/`** exists; **Codex plugin** mention type + payload path may be missing. |

---

### 5. `4e19459` — 2026-04-28 — Support git initialization and local-only chat removal

| Area | What changed |
|------|----------------|
| **iOS** | **`GitActionModels`**, **`CodexService+Sync`**, **`GitActionsService`**, **ArchivedChats**, **sidebar grouping/list/rows**, **`SidebarView`**, **`TurnGitActionsToolbar`**, **`TurnView`**, **`TurnViewModel`** (+ git/worktree extension). |
| **Bridge** | **`git-handler.js`** + tests (substantial). |
| **Android** | Align **git init** UX and **remove local-only chat** rules with iOS + bridge. |

---

### 6. `055da4d` — 2026-04-28 — Merge PR #84 (codex-plugin-mentions)

| Area | What changed |
|------|----------------|
| **iOS** | Merge integration for plugin-mention work (same surface as `81b94a6` / follow-ups). |
| **Android** | Treat as **batch boundary** for plugin mention parity. |

---

### 7. `8c38d50` — 2026-04-28 — Refine plugin mention autocomplete and rendering

| Area | What changed |
|------|----------------|
| **iOS** | **`FileAutocompletePanel`**, **`FileMentionChip`**, skill/slash panels, **`TurnComposerView`**, **`TurnMessageComponents`**, **`TurnViewModel`**, **`VoiceRecordingCapsule`**. |
| **Android** | Polish autocomplete rows, chip rendering, message body rules for plugin tokens. |

---

### 8. `c4bc0d8` — 2026-04-28 — Tone down file autocomplete icon color

| Area | What changed |
|------|----------------|
| **iOS** | **`FileAutocompletePanel`** styling. |
| **Android** | Minor Compose theming parity. |

---

### 9. `2e19695` — 2026-04-28 — Tighten trailing file autocomplete token parsing

| Area | What changed |
|------|----------------|
| **iOS** | **`TurnViewModel`**, **`TurnFileAutocompleteTokenTests`**. |
| **Android** | Match **@ / $ /** token boundary** behavior in composer logic + tests. |

---

### 10. `93cd6e2` — 2026-04-28 — Double message fix

| Area | What changed |
|------|----------------|
| **iOS** | New **`AssistantReplayDeduper.swift`**, **`CodexService+History`**, large **`CodexService+Messages`**, **`TurnTimelineReducer`**, **`TurnTimelineView`**, command/run-indicator tests. |
| **Bridge** | **`rollout-live-mirror.js`** + test updates. |
| **Android** | **Dedup assistant replay**, history merge rules, timeline reducer alignment; bridge **rollout mirror** semantics. |

---

### 11. `21769fa` — 2026-04-28 — Refine streaming delta and timeline collapsing

| Area | What changed |
|------|----------------|
| **iOS** | **`CodexService+Messages`**, new **`TurnTimelineRenderProjection.swift`**, **`TurnTimelineView`** refactor, reducer/run-indicator tests. |
| **Android** | **Projection layer** for collapsing/streaming; keep item-scoped rows consistent with iOS guards. |

---

### 12. `064e72a` — 2026-04-28 — Merge PR #85 (streaming-improvement)

| Area | What changed |
|------|----------------|
| **iOS** | Merge commit; substantive deltas are in `93cd6e2` / `21769fa` (and related). |
| **Android** | Use as **sync checkpoint** with streaming PR. |

---

### 13. `625e2b4` — 2026-04-28 — Add safe workspace image previews

| Area | What changed |
|------|----------------|
| **iOS** | **`CodexService+WorkspaceImages`**, large **`CommandExecutionViews`**, **`TurnConversationContainerView`**, **`TurnMessageCaches`**, **`TurnMessageComponents`**, **`TurnTimelineView`**, **`TurnView`**, **`TurnMessageCachesTests`**. |
| **Bridge** | **`workspace-handler.js`** (workspace image paths). |
| **Android** | **Workspace ↔ timeline image** pipeline (not only user-picked composer images). |

---

### 14. `7869bfa` — 2026-04-29 — Fix image preview cache timestamp key

| Area | What changed |
|------|----------------|
| **iOS** | Cache key / invalidation fixes around image previews (message cache / components). |
| **Android** | Match **cache identity** for image previews to avoid stale thumbnails. |

---

### 15. `e637d63` — 2026-04-29 — Merge PR #86 (images)

| Area | What changed |
|------|----------------|
| **iOS** | Merge boundary for image preview work (`625e2b4` / `7869bfa`). |
| **Android** | Batch checkpoint. |

---

### 16. `3da4d17` — 2026-04-29 — Pass working directory into turn timeline rows

| Area | What changed |
|------|----------------|
| **iOS** | Thread/timeline row context gets **cwd** for correct relative paths / labels. |
| **Android** | Pass **git cwd / thread cwd** into timeline row models and parsers where iOS does. |

---

### 17. `8553266` — 2026-04-29 — Improve image sync and timeline handling

| Area | What changed |
|------|----------------|
| **iOS** | **`CodexService+WorkspaceImages`**, **`CodexService+Messages`**, **`TurnMessageComponents`**, **`TurnTimelineView`**, workspace handler hooks. |
| **Bridge** | **`workspace-handler.js`** extensions + **`workspace-image.test.js`**. |
| **Android** | Image **sync** events and timeline reconciliation. |

---

### 18. `38308f3` — 2026-04-29 — Merge PR #87 (image-gen)

| Area | What changed |
|------|----------------|
| **iOS** | Merge for image-generation / image pipeline follow-ups. |
| **Android** | Confirm **image-gen** notifications match `IncomingNotification` handling. |

---

### 19. `306ea30` — 2026-04-29 — Refine fast mode model picker

| Area | What changed |
|------|----------------|
| **iOS** | **`CodexModelOption`**, **`CodexReasoningEffortOption`**, **`CodexService+RuntimeConfig`**, **`ComposerBottomBar`**, composer runtime mappers/menus/state, **`TurnComposerView`**, **`CodexServiceTierTests`**. |
| **Android** | **Fast mode** in runtime/model menus and **tier tests** parity. |

---

### 20. `09c6b80` — 2026-04-29 — Fix fast mode review blockers

| Area | What changed |
|------|----------------|
| **iOS** | Fast mode + review / runtime gating fixes (composer + services). |
| **Android** | Same **gating** when “fast” capability or review state interacts with send/stop. |

---

### 21. `88296b8` — 2026-04-29 — Correct 5.3 fast mode capability

| Area | What changed |
|------|----------------|
| **iOS** | Model/runtime capability matrix for **5.3 fast** path. |
| **Android** | Align **model catalog + flags** with bridge/app-server expectations. |

---

### 22. `749328d` — 2026-04-29 — Merge PR #89 (fast-mode model menu)

| Area | What changed |
|------|----------------|
| **iOS** | Merge for fast-mode menu work (`306ea30`–`88296b8`). |
| **Android** | Checkpoint. |

---

### 23. `2eea6eb` — 2026-04-30 — Extend relay watchdog and relaunch launch agent cleanly

| Area | What changed |
|------|----------------|
| **iOS** | — |
| **Bridge** | **`bridge.js`**, **`macos-launch-agent.js`**, tests. |
| **Android** | Behavior mostly **host-side**; be aware of **bridge lifecycle** changes if you rely on auto-relaunch. |

---

### 24. `0e838d8` — 2026-04-30 — Compact older relay history before trimming newest turns

| Area | What changed |
|------|----------------|
| **iOS** | — |
| **Bridge** | Large **`bridge.js`** change + **`bridge.test.js`** — **history compaction** before trimming turns. |
| **Android** | **`thread/read` / history** payloads may differ in shape or size; test **large thread** reopen. |

---

### 25. `6c3c85b` — 2026-04-30 — UI improvements

| Area | What changed |
|------|----------------|
| **iOS** | Broad sweep: **`ContentView`**, **`CodexThread`**, **`CodexService+History`**, **`IncomingAssistant`**, **`Messages`**, **`ThreadsTurns`**, **`CodexService`**, **`AdaptiveGlassModifier`**, sidebar search/rows/shell, **`CommandExecutionViews`**, **`TurnComposerView`**, connection recovery, **`TurnMessageCaches`**, **`TurnMessageComponents`** (large), **`TurnTimelineReducer`**, **`TurnTimelineRenderProjection`**, **`TurnTimelineView`**, toolbar, **`TurnView`**, many tests. |
| **Bridge** | **`workspace-handler.js`**, **`workspace-image.test.js`**. |
| **Android** | **High-effort parity pass** across shell, timeline, command rows, message components. |

---

### 26. `8124de3` — 2026-04-30 — Run unsigned IPA build on main pushes

| Area | What changed |
|------|----------------|
| **Other** | Tweaks **`build-unsigned-ipa.yml`**. |
| **Android** | — |

---

### 27. `82c0903` — 2026-05-01 — Track assistant phases and workspace checkpoints

| Area | What changed |
|------|----------------|
| **iOS** | **`CodexService+WorkspaceCheckpoints.swift`** (new, large), **`ContentView`**, **`AIChangeSet`**, **`CodexMessage`**, **`AIChangeSets`**, **`Connection`**, **`History`**, **`Incoming`**, **`IncomingAssistant`**, **`Messages`**, **`Sync`**, **`ThreadsTurns`**, **`CodexService`**, glass/sidebar, **`TurnComposerView`**, **`TurnConversationContainerView`**, **`TurnMessageComponents`**, **`TurnTimelineRenderProjection`**, **`TurnTimelineView`**, **`TurnView`**, **`AIChangeSetTests`**, run-indicator / message cache / reducer tests. |
| **Bridge** | **`workspace-checkpoints.js`** (new), **`workspace-handler.js`** glue. |
| **Android** | **Largest missing feature batch:** assistant **phase** UI/state, **checkpoint** RPCs, timeline integration, AI change set hooks as on iOS. |

---

### 28. `6f3f20a` — 2026-05-01 — Merge PR #79 (unsigned IPA workflow only-clean)

| Area | What changed |
|------|----------------|
| **Other** | Workflow merge; **no `CodexMobile/` app diff** on merge commit. |
| **Android** | — |

---

### 29. `445c595` — 2026-05-01 — Merge PR #91 (git-issues)

| Area | What changed |
|------|----------------|
| **iOS** | — (on merge commit stat) |
| **Bridge** | **`bridge.js`**, **`macos-launch-agent.js`**, **`bridge.test.js`**, launch-agent tests — **git/worktree issue** fixes. |
| **Android** | Retest **thread/git** flows against updated bridge. |

---

### 30. `1d2f232` — 2026-05-01 — Raise relay thread payload soft limit to 4 MB

| Area | What changed |
|------|----------------|
| **Bridge** | **`bridge.js`** constant + **`bridge.test.js`** (subject says “relay”; change is in bridge). |
| **Android** | Ensure **large `thread/read` / thread payloads** don’t assume old limit; OOM guards if you buffer whole JSON. |

---

### 31. `c7843aa` — 2026-05-01 — Polish turn message rendering

| Area | What changed |
|------|----------------|
| **iOS** | **`TurnMessageComponents.swift`** — rendering polish. |
| **Android** | **`TurnMessageRow` / markdown / components** alignment pass. |

---

### 32. `7c82966` — 2026-05-01 — Add folder search to local project picker

| Area | What changed |
|------|----------------|
| **iOS** | **`SidebarLocalFolderBrowserSheet`** search UX, **`CodexService+ProjectFolders`**, **`CodexService+Account`**, **`CodexService`**, Xcode project, **`CodexGPTAccountTests`**. |
| **Bridge** | **`project-handler.js`** search/list APIs, **`ios-app-compatibility.js`**, package bump, tests. |
| **Android** | **Folder search** in picker/browser + bridge RPC parity + any **account/compat** prompts. |

---

## Aggregates (for scoping)

```text
git diff c3a1c14..origin/main --shortstat -- CodexMobile/
 76 files changed, 10538 insertions(+), 952 deletions(-)
```

Refresh this file after **`git fetch origin main`** by re-running the same `git log c3a1c14..origin/main` range and updating the tip hash if `main` moves.

---

*Generated for the Remodex Android port trace workflow. Baseline: `c3a1c14`. Upstream tip documented: `7c82966`.*
