# iOS upstream delta after `7c82966` — Android catch-up trace

This file maps **every commit on `origin/main` after** prior tip **`7c82966`** (`Add folder search to local project picker`, 2026-05-01) through **`b50f016`** (current tip, 2026-05-04). Use it as a **checklist** for bringing **local Android** in line with **upstream iOS + shared bridge behavior**.

- **Prior baseline:** `c3a1c14` → `7c82966` (32 commits, covered by Phase 1-5)
- **Upstream tip:** `b50f016` — *Show completed plan items inline until turn resolves*
- **Scope:** `CodexMobile/` (iOS app), `phodex-bridge/`, `.github/workflows/` where relevant.
- **Aggregate:** 83 files changed, 6749 insertions, 1086 deletions in `CodexMobile/`

**Android reminders**

- **Payments / RevenueCat** — intentionally out of scope.
- Many commits touch `phodex-bridge`; Android must stay compatible with the same JSON-RPC and bridge behaviors.
- Commits that only merge PRs may duplicate file stats from feature commits already merged on the branch.

---

## Roll-up: themes vs Android

| Theme | Upstream commits (rough) | Android catch-up angle |
|--------|---------------------------|-------------------------|
| Timeline hydration + history | `d524115`, `bc25084`, `acf8a0d`, `67c28b0`, `c93a473`, `296d691`, `26405ad`, `1108f4b`, `89403cc`, `6d0e7eb` | Paginated `thread/read`, baseline deferral, large-turn fixes |
| Sidebar project icons + spacing | `a251a94` | Icon and spacing parity for sidebar groups |
| Completed plan items inline | `b50f016` | Show completed plan items in timeline |
| Skill autocomplete | `d524115` | Composer autocomplete refinements |
| Git actions + progress | `9ea2c2b`, `38f2fa6` | Git action live progress toasts, stacked publish |
| Diff + file changes | `ea0d45f`, `cc9dce6`, `131f8bb`, `d355a1c` | Merge adjacent file changes, markdown diff handling, keep file changes last |
| Usage + scroll | `e678be3` | Local usage and scroll-state stabilization |
| Reconnect recovery | `33f05c3`, `cc9dce6` | Trusted reconnect fix, reconnect messaging |
| Windows bridge + handoff | `6b92220`, `ff2196c`, `4a033c9`, `d4af4c7`, `1c8744b`, `3828072` | Windows handoff RPC, relay bind, git buffers |
| Onboarding / manual pairing | `aea3b3b` | Manual pairing path in onboarding |
| Image previews | `993bd1e`, `131f8bb` | Non-git workspace image reads, diff preview handling |
| Desktop IPC / file-read | `5eb4228`, `9824206` | Forward desktop file-read approvals, IPC action following |
| User bubble colors | `fd73ff4`, `c80f1ff`, `8bc24a6`, `668400c` | Customizable user bubble colors (UI polish) |
| Pet companion overlay | `23284ad`, `84a19cb`, `4da9dd6` | Local pet companion overlay + bridge handlers |
| Markdown regression | `0ceda7f` | Pinned Textual version, markdown test |
| Local relay URL | `8208d37` | Local relay URL override |
| Context section | `c605f8d` | Return context section view explicitly |
| Slash commands | `d355a1c` | Slash compact command |

---

## Commit-by-commit (oldest → newest)

### 1. `cc9dce6` — 2026-05-01 — Merge adjacent file changes and refine reconnect messaging

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Messages`, `CodexService+Sync`, `TurnTimelineReducer`, `TurnTimelineView`, turnaround/connection recovery |
| **Android** | Merge adjacent file changes in timeline reducer; reconnect messaging polish |

### 2. `33f05c3` — 2026-05-01 — Fix trusted reconnect recovery

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Sync`, `SidebarView`, connection recovery |
| **Android** | Trusted reconnect flow fixes |

### 3. `e1b1f18` — 2026-05-01 — Merge PR #94 (connectivity)

| Area | What changed |
|------|----------------|
| **iOS** | Merge boundary for connectivity fixes |
| **Android** | Checkpoint |

### 4. `e678be3` — 2026-05-01 — Stabilize local usage and scroll state handling

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Usage`, scroll-state stabilization |
| **Android** | Usage refresh / scroll-state parity |

### 5. `c605f8d` — 2026-05-01 — Return context section view explicitly

| Area | What changed |
|------|----------------|
| **iOS** | Context section view handling |
| **Android** | Context window / section view parity |

### 6. `131f8bb` — 2026-05-01 — Refine diff and image preview handling

| Area | What changed |
|------|----------------|
| **iOS** | `CommandExecutionViews`, `TurnConversationContainerView`, `TurnMessageComponents`, `TurnTimelineView`, `TurnView` |
| **Android** | Diff rendering + image preview UI parity |

### 7. `6b92220` — 2026-05-01 — Bridge + relay: iOS with Windows host (handoff, relay bind, git buffers)

| Area | What changed |
|------|----------------|
| **iOS** | — |
| **Bridge** | Large change — Windows host support for handoff, relay binding, git buffers |
| **Android** | Already handled in Phase 1 (Windows desktop handoff capability fix) |

### 8. `38f2fa6` — 2026-05-02 — Add stacked git publish actions and progress toast

| Area | What changed |
|------|----------------|
| **iOS** | `GitActionsService`, git toolbar, progress toast |
| **Android** | Stacked git publish + live progress UI |

### 9. `9824206` — 2026-05-02 — Add desktop IPC action following to bridge

| Area | What changed |
|------|----------------|
| **Bridge** | `bridge.js` — desktop IPC action following |
| **Android** | Verify bridge IPC forwarding works |

### 10. `5eb4228` — 2026-05-02 — Forward desktop file-read approvals

| Area | What changed |
|------|----------------|
| **Bridge** | `bridge.js` — forward desktop file-read approvals |
| **Android** | Verify file-read approval forwarding |

### 11. `73cb1f8` — 2026-05-02 — Merge PR #96 (desktop IPC)

| Area | What changed |
|------|----------------|
| **iOS** | Merge boundary |
| **Android** | Checkpoint |

### 12. `993bd1e` — 2026-05-02 — Allow image reads from non-git workspace CWDs

| Area | What changed |
|------|----------------|
| **Bridge** | `workspace-handler.js` — allow image reads outside git repos |
| **Android** | Verify image preview works in non-repo directories |

### 13. `1c8744b` — 2026-05-02 — Merge PR #95 (Windows bridge relay)

| Area | What changed |
|------|----------------|
| **Bridge** | Merge boundary for Windows bridge relay work |
| **Android** | Already handled |

### 14. `ff2196c` — 2026-05-02 — Fix desktop handoff RPC for Windows bridge

| Area | What changed |
|------|----------------|
| **Bridge** | `desktop-handler.js` — Windows handoff RPC fix |
| **Android** | Already handled in Phase 1 |

### 15. `4a033c9` — 2026-05-02 — Harden Windows handoff and pairing output

| Area | What changed |
|------|----------------|
| **Bridge** | `bridge.js`, `macos-launch-agent.js` — Windows handoff hardening |
| **Android** | Already handled |

### 16. `d4af4c7` — 2026-05-02 — Update desktop handoff unsupported platform copy

| Area | What changed |
|------|----------------|
| **iOS** | Handoff unsupported platform messaging |
| **Android** | Copy/messaging parity |

### 17. `3828072` — 2026-05-02 — Merge PR #97 (Windows bridge)

| Area | What changed |
|------|----------------|
| **iOS** | Merge boundary |
| **Android** | Checkpoint |

### 18. `ea0d45f` — 2026-05-02 — Refine turn header and diff markdown handling

| Area | What changed |
|------|----------------|
| **iOS** | `TurnMessageComponents`, turn header, diff markdown |
| **Android** | Turn header + diff markdown rendering |

### 19. `d355a1c` — 2026-05-02 — Add slash compact command and keep file changes last

| Area | What changed |
|------|----------------|
| **iOS** | `TurnTimelineReducer`, `TurnTimelineView`, slash commands |
| **Android** | Keep file changes at end of timeline; slash compact command parity |

### 20. `fd73ff4` — 2026-05-02 — Add customizable user bubble colors

| Area | What changed |
|------|----------------|
| **iOS** | `TurnMessageComponents`, bubble color settings |
| **Android** | User bubble color customization (UI polish — lower priority) |

### 21. `23284ad` — 2026-05-03 — Add local pet companion overlay and bridge handlers

| Area | What changed |
|------|----------------|
| **iOS** | New pet companion overlay + `pet-companion-handler.js` in bridge |
| **Android** | Pet companion support (UI polish — lower priority) |

### 22. `8bc24a6` — 2026-05-03 — Remove generated bubble color artifact

| Area | What changed |
|------|----------------|
| **iOS** | Cleanup |
| **Android** | N/A |

### 23. `668400c` — 2026-05-03 — Merge PR #99 (bubble colors)

| Area | What changed |
|------|----------------|
| **iOS** | Merge boundary |
| **Android** | Checkpoint |

### 24. `c80f1ff` — 2026-05-03 — Expand bubble colors and split settings cards

| Area | What changed |
|------|----------------|
| **iOS** | Settings UI |
| **Android** | Settings polish (lower priority) |

### 25. `4da9dd6` — 2026-05-03 — Merge PR #100 (pet companion)

| Area | What changed |
|------|----------------|
| **iOS** | Merge boundary |
| **Android** | Checkpoint |

### 26. `84a19cb` — 2026-05-03 — Reject oversized pet spritesheets

| Area | What changed |
|------|----------------|
| **iOS** | Pet companion validation |
| **Android** | N/A or lower priority |

### 27. `9ea2c2b` — 2026-05-03 — Add live progress and success to git actions

| Area | What changed |
|------|----------------|
| **iOS** | `GitActionsService`, git toolbar, progress/success indicators |
| **Android** | **Important** — git action progress toasts + success states |

### 28. `aea3b3b` — 2026-05-03 — Add manual pairing path to onboarding

| Area | What changed |
|------|----------------|
| **iOS** | Onboarding views — manual pairing flow |
| **Android** | Manual pairing path in onboarding |

### 29. `26405ad` — 2026-05-03 — Add paginated thread history loading

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+History`, paginated `thread/read` |
| **Bridge** | `bridge.js` — paginated history with cursor |
| **Android** | **Important** — paginated history loading parity |

### 30. `296d691` — 2026-05-03 — Increase initial thread history page size

| Area | What changed |
|------|----------------|
| **iOS** | History page size increase |
| **Android** | Match page size constant |

### 31. `0ceda7f` — 2026-05-03 — Pin Textual and add markdown rendering regression test

| Area | What changed |
|------|----------------|
| **iOS** | Markdown rendering test, Textual pin |
| **Android** | Not directly applicable (different markdown engine) |

### 32. `c93a473` — 2026-05-03 — Defer thread baseline reads until needed

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Sync`, deferred thread reads |
| **Android** | **Important** — defer thread/read until chat is opened |

### 33. `67c28b0` — 2026-05-03 — Hydrate thread metadata from full list responses

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Sync`, thread metadata hydration |
| **Android** | Hydrate thread metadata from `thread/list` |

### 34. `8208d37` — 2026-05-03 — Add local relay URL override

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Account`, local relay URL setting |
| **Android** | Local relay URL override setting |

### 35. `6d0e7eb` — 2026-05-03 — Improve thread sync bootstrap and relay turns sanitization

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Sync`, thread sync bootstrap, relay turns sanitization |
| **Android** | Thread sync bootstrap improvements |

### 36. `18553b7` — 2026-05-03 — Merge PR #102 (local relay URL)

| Area | What changed |
|------|----------------|
| **iOS** | Merge boundary |
| **Android** | Checkpoint |

### 37. `1108f4b` — 2026-05-03 — Fix large turns list relay loading

| Area | What changed |
|------|----------------|
| **Bridge** | `bridge.js` — large turns list fix |
| **Android** | Verify large thread loading |

### 38. `89403cc` — 2026-05-04 — Adapt turns list relay pagination

| Area | What changed |
|------|----------------|
| **Bridge** | `bridge.js` — turns list pagination |
| **Android** | Verify paginated turns list |

### 39. `436d68e` — 2026-05-04 — Merge PR #103 (fix turns list timeout)

| Area | What changed |
|------|----------------|
| **Bridge** | Merge boundary |
| **Android** | Checkpoint |

### 40. `a251a94` — 2026-05-04 — Refine sidebar project icons and spacing

| Area | What changed |
|------|----------------|
| **iOS** | `SidebarView`, project icons and spacing |
| **Android** | **Already partially done** (project path label + collapse) — verify icon/spacing parity |

### 41. `acf8a0d` — 2026-05-04 — Handle unmaterialized thread history as empty state

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+History`, unmaterialized thread handling |
| **Android** | Handle empty/unmaterialized thread history |

### 42. `bc25084` — 2026-05-04 — Defer running-thread hydration and quiet recovery noise

| Area | What changed |
|------|----------------|
| **iOS** | `CodexService+Sync`, deferred hydration, recovery noise reduction |
| **Android** | **Important** — running-thread hydration deferral + noise reduction |

### 43. `d524115` — 2026-05-04 — Refine timeline hydration and skill autocomplete

| Area | What changed |
|------|----------------|
| **iOS** | `TurnTimelineView`, `TurnViewModel`, skill autocomplete |
| **Android** | Timeline hydration + skill autocomplete refinements |

### 44. `b50f016` — 2026-05-04 — Show completed plan items inline until turn resolves

| Area | What changed |
|------|----------------|
| **iOS** | `TurnTimelineRenderProjection`, `TurnTimelineView`, plan item display |
| **Android** | Show completed plan items inline in timeline |

---

## Aggregates

```text
git diff 7c82966..origin/main --shortstat -- CodexMobile/
  83 files changed, 6749 insertions(+), 1086 deletions(-)
```

---

## Priority grouping for Android

### High priority (functional gaps)
- `26405ad` — Paginated thread history loading
- `c93a473` — Defer thread baseline reads
- `bc25084` — Defer running-thread hydration
- `acf8a0d` — Unmaterialized history as empty
- `9ea2c2b` — Git action progress toasts
- `38f2fa6` — Stacked git publish actions
- `b50f016` — Completed plan items inline

### Medium priority (UI/UX)
- `a251a94` — Sidebar icons + spacing (partially done)
- `aea3b3b` — Manual pairing onboarding path
- `8208d37` — Local relay URL override
- `ea0d45f` — Turn header + diff markdown
- `d355a1c` — Slash compact command
- `d524115` — Skill autocomplete refinements

### Low priority (cosmetic / platform-specific)
- `fd73ff4`, `c80f1ff` — Bubble colors
- `23284ad`, `84a19cb` — Pet companion (skip)
- `0ceda7f` — Markdown engine test (not applicable)
- `d4af4c7` — Unsupported platform copy

### Already handled / bridge-only
- `6b92220`, `ff2196c`, `4a033c9` — Windows bridge (Phase 1)
- `33f05c3` — Reconnect fix (Phase 1)
- `993bd1e` — Non-git image reads (bridge)
- `1108f4b`, `89403cc` — Bridge pagination fixes

---

*Generated for the Remodex Android port trace workflow. Baseline: `7c82966`. Upstream tip: `b50f016`.*
