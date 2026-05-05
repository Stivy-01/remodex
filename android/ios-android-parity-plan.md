**Instructions** 
- Android parity target is iOS upstream delta c3a1c14..131f8bb.
- Source of truth for behavior is Swift/iOS plus bridge origin/main, but Android should not copy iOS 1:1 when platform rules differ.
- Preserve local-first behavior: local bridge, QR pairing, saved relay pairing, local cwd/thread metadata.
- Do not reintroduce hosted-service assumptions or hardcoded production domains.
- Do not clear saved relay/pairing state during transient reconnect races.
- Keep shared logic in services/coordinators, not duplicated in views.
- Use ccc search / code inspection before parity batches, and run ccc index after meaningful changes.
- Use Android tests/compile checks:
  - cd android; .\gradlew.bat :app:testDebugUnitTest
  - cd android; .\gradlew.bat :app:compileDebugKotlin
- Run bridge tests when bridge/RPC behavior changes:
  - cd phodex-bridge; npm test
- Do not run Xcode tests unless explicitly requested.
- Subagents are allowed, but only with gpt-5.4-mini.

**Completed Phase 1: Reconnect + Usage Polish**
Implemented/verified:
- Active/running turn recovery after relaunch/reconnect.
- Stop visibility fallback through thread/read.
- Protected running fallback when turn/started lacks turnId.
- Combined usage/status refresh policy.
- Context-window missing usage fallback to zero to avoid refresh loops.
- Reconnect recovery UI card.
- Inactive running thread refresh after reconnect.
- Trusted reconnect failure budget.
- Windows desktop handoff capability fix in bridge.

Verified:
- Android focused tests.
- Android full unit tests.
- Android Kotlin compile.
- Bridge full test suite after handoff fix.
- ccc index.

**Completed Phase 2: UI Parity Sweep**
Goal: compare Android timeline rendering against iOS after c3a1c14.

Scope:
- Command rows.
- File-change merging.
- Thinking/reasoning display.
- Final answer display.
- Generated image layout.
- Working-directory-relative links/previews.
- Timeline collapse/coalescing behavior.
- Scroll-state stabilization where relevant.

Approach:
1. Use ccc search and source reads to map iOS timeline reducer/rendering behavior.
2. Compare Android timeline models/reducers/composables.
3. Patch smallest behavior gaps first.
4. Add focused tests for pure reducers/builders where possible.
5. Compile and run focused Android tests.
6. Re-index with ccc index.

**Completed Phase 3: Images, Diffs, Checkpoints**
Scope:
- Workspace image preview RPC/event handling.
- Preview cache keys and stale preview suppression.
- Command-output image rendering.
- Generated image sync into history/timeline.
- Merge adjacent file changes.
- Suppress generic turn-diff rows unless backed by real file evidence.
- Workspace checkpoint lifecycle.
- Revert state invalidation.

**Completed Phase 4: Composer + Runtime Polish**
Scope:
- Structured $skill mentions.
- skills/list fallback shape handling.
- turn/start structured skill input items plus fallback retry.
- fuzzyFileSearch for @file mentions.
- Runtime overrides: model, reasoning effort, service tier.
- Unsupported service-tier fallback.
- Autocomplete parsing/icon/color polish.

**Completed Phase 5: Project/Git Flow Parity**
Scope:
- Local project picker.
- Quick locations.
- Directory list/search/create/select.
- Git init setup action for non-repo cwd.
- Recent/local project grouping.
- Managed worktree chat/no-project chat.
- Local-only remove for threads/project groups.

**Phase 6: End-To-End Local Flow**
Manual flow:
1. Run bridge locally.
2. Pair Android.
3. Pick folder.
4. git init.
5. Start chat.
6. Test plugin mention.
7. Test image preview.
8. Test checkpoint revert.
9. Reconnect/relaunch recovery.
10. Verify Stop/status visibility during reconnect.

**Completed Phase 7: Delta Audit**
Checklist source: `IOS_UPSTREAM_DELTA_C3A1C14.md`.

Audit evidence:
- Android source/tests under `android/app/src/main/kotlin` and `android/app/src/test/kotlin`.
- Shared bridge source/tests under `phodex-bridge/src` and `phodex-bridge/test`.
- Code search via ccc plus direct PowerShell inspection because `rg.exe` is blocked by local Windows permissions.

Status roll-up:
- Ported: 24 commits.
- Partially ported: 1 commit.
- Missing: 0 commits.
- Not applicable to Android: 7 commits.

| Commit | Status | Delta audit notes / test coverage |
|--------|--------|------------------------------------|
| `37035d7` | Not applicable to Android | iOS unsigned IPA workflow only; no Android runtime or bridge action. |
| `fcf67e8` | Ported | Android has `ProjectFolderService` RPCs for quick locations, directory listing, directory creation, and picker start flow; bridge has `project-handler.js` and `project-handler.test.js`; Android coverage includes `ProjectFolderServiceTest`. |
| `79d2142` | Ported | Android has a dedicated `SidebarProjectPickerSheet` with its own state, browse, create, no-project chat, and start-busy handling. Covered indirectly through project service/sidebar flow tests. |
| `81b94a6` | Ported | Android has plugin-aware `CodexSkillMetadata`, composer mention kind/payload conversion, structured `plugin://...` mention items, and turn/start fallback compatibility. Covered by `TurnConversationPaneAutocompleteTest`, `TurnComposerTrailingTokensTest`, `StructuredInputTimelineFormatterTest`, and `TurnStartRpcCompatTest`. |
| `4e19459` | Ported | Android has `GitActionsService.initializeRepository()` for `git/init`, git action models, git preflight/errors, local-only thread removal/project grouping behavior, and bridge `git-handler.js` parity. Covered by `GitActionsServiceTest`, `GitActionModelsTest`, `TurnGitPreflightPolicyTest`, and `git-handler.test.js`. |
| `055da4d` | Ported | Merge boundary for plugin mentions; substantive Android coverage is listed under `81b94a6`, `8c38d50`, `c4bc0d8`, and `2e19695`. |
| `8c38d50` | Ported | Android autocomplete/chip model distinguishes file, skill, plugin, and slash command suggestions; visible prose formatting handles skill/plugin references. Covered by composer autocomplete and `SkillReferenceFormatterTest`. |
| `c4bc0d8` | Ported | Compose autocomplete/icon theming exists in the project picker/composer UI; no separate behavioral risk beyond UI parity. Covered by compile and UI model tests. |
| `2e19695` | Ported | Android trailing token parser covers `@`, `$`, and `/` mention boundaries and paths with spaces/line suffixes. Covered by `TurnComposerTrailingTokensTest`. |
| `93cd6e2` | Ported | Android has item-scoped message IDs, history merge rules, command detail storage keyed by item id, and replay/dedupe-oriented merge tests; bridge rollout live mirror tests cover replay semantics. Covered by `HistoryMessageMergeTest`, `MessageTimelineStoreTest`, command execution router tests, and `rollout-live-mirror.test.js`. |
| `21769fa` | Ported | Android timeline projection equivalent is implemented through rich content parsing/caches, grouped run rows, item-aware rows, and command preview merging. Covered by `TurnTimelineRichContentParserTest`, `TurnTimelineRichContentCacheTest`, `TurnCommandExecutionPreviewMergeTest`, `TimelineMessageGroupingTest`, and render cache tests. |
| `064e72a` | Ported | Streaming merge checkpoint; Android parity is covered by `93cd6e2` and `21769fa` tests. |
| `625e2b4` | Ported | Android has `WorkspaceImageService`, workspace image result models, safe bridge `workspace/readImage`, command/timeline preview handling, and bridge scoped-size tests. Covered by `WorkspaceImageServiceTest`, `WorkspaceArtifactsModelsTest`, and `workspace-image.test.js`. |
| `7869bfa` | Ported | Android preview cache key includes path and requested preview dimension, and revalidation uses byte length and mtime to suppress stale thumbnails. Covered by `WorkspaceImageServiceTest` and bridge cache revalidation in `workspace-image.test.js`. |
| `e637d63` | Ported | Image preview merge boundary; substantive Android coverage is listed under `625e2b4` and `7869bfa`. |
| `3da4d17` | Ported | Android decodes and preserves `CodexThread.cwd`, passes cwd through command execution details and working-directory-aware routing, and groups sidebar/project context by cwd. Covered by `CodexThreadDisplayTitleTest`, `SidebarThreadGroupingTest`, `TurnWorktreePathRoutingTest`, and command execution parser tests. |
| `8553266` | Ported | Android handles generated image sync/timeline artifacts via incoming image generation routing, workspace image reads, and bridge generated image preview sanitation. Covered by `IncomingEventRouterImageGenerationTest`, `WorkspaceImageServiceTest`, and `rollout-live-mirror.test.js`. |
| `38308f3` | Ported | Image-generation merge checkpoint; Android notification/event handling is covered by incoming image generation and live mirror tests. |
| `306ea30` | Ported | Android has model/reasoning/service-tier runtime models and composer runtime controls; turn/start sends selected runtime overrides with fallback behavior. Covered by `CodexModelOptionTest`, `CodexServiceTierTests` equivalent coverage in `RateLimitPayloadCodecTest`/runtime tests, `TurnComposerRuntimeToolbarMenuBuilderTest`, and `TurnStartRpcCompatTest`. |
| `09c6b80` | Ported | Android has runtime gating, unsupported service-tier compatibility, and review composer blocker parity: review selection is allowed from a bare slash token but blocked by existing prose, mentions, plan mode, or attachments. Coverage: `CodexServiceReviewTest`, `TurnReviewAccessoryTest`, `TurnComposerStateModelTest`, runtime toolbar tests. |
| `88296b8` | Ported | Android model catalog parsing supports default reasoning effort and supported efforts, including current fast-mode capability fields supplied by bridge/runtime config. Covered by `CodexModelOptionTest` and runtime toolbar tests. |
| `749328d` | Ported | Fast-mode menu merge checkpoint; substantive Android coverage is listed under `306ea30`, `09c6b80`, and `88296b8`. |
| `2eea6eb` | Ported | Host-side bridge parity is present: watchdog stale window, ping timer, launch-agent relaunch cleanup, and tests. Android depends on bridge behavior but needs no app-specific code. Covered by `bridge.test.js` and `macos-launch-agent.test.js`. |
| `0e838d8` | Ported | Bridge compacts older relay history before trimming newest turns and marks compacted payloads; Android has thread/history decoding that tolerates compacted/sanitized history payloads. Covered by `bridge.test.js`, `ThreadHistoryDecoderSanitizationTest`, and `HistoryMessageMergeTest`. |
| `6c3c85b` | Ported | Major UI sweep is represented in Android timeline rows, command rows, file-change rendering, recovery card, sidebar/search, caches, and the late desktop/phone history ordering fix that reassigns display order from decoded history time. Coverage: timeline rich content, command UI builder, render caches, connection recovery, sidebar grouping, `HistoryMessageMergeTest`, and compile. |
| `8124de3` | Not applicable to Android | iOS workflow trigger change only. |
| `82c0903` | Ported | Android has assistant phase metadata on `CodexMessage`, workspace checkpoint models/service, AI change set checkpoint metadata, revert service, usage/status restore affordances, and bridge `workspace-checkpoints.js`. Covered by `AiChangeSetRevertServiceTest`, `WorkspaceArtifactsModelsTest`, `TurnUsageSheetLogicTest`, and bridge workspace checkpoint/image tests. |
| `6f3f20a` | Not applicable to Android | iOS workflow merge only. |
| `445c595` | Ported | Bridge git/worktree issue fixes are present in `bridge.js`, `macos-launch-agent.js`, and git tests; Android uses cwd-scoped git service and handoff models. Covered by bridge launch/git tests plus Android worktree/handoff tests. |
| `1d2f232` | Ported | Bridge soft relay thread payload limit is 4 MB; payload trimming/compaction keeps newest turn and sanitizes bulky blobs. Android JSON decoding uses structured values and history sanitization tests. Covered by `bridge.test.js` and thread history decoder tests. |
| `c7843aa` | Partially ported | Android has markdown, directive cards, plan/subagent/thinking/file-change rows, and skill-reference prose formatting; remaining risk is pixel-level message polish. Covered by `TurnMarkdownBody` adjacent tests, directive parsing, plan/subagent accessory tests, and compile. |
| `7c82966` | Ported | Android project picker has folder search via `project/searchDirectories`; bridge has search/list compatibility and account/compat hooks. Covered by `ProjectFolderServiceTest`, `project-handler.test.js`, and `ios-app-compatibility.test.js`. |

Residual phase-7 risks:
- Phase 6 remains a manual device/bridge runbook; this audit did not perform Android device pairing or visual screenshot verification.
- `c7843aa` remains partially ported because final markdown/message polish needs visual comparison on device.
- Xcode tests were intentionally not run.
