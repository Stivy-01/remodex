Here's the phase-to-commit mapping:

Phase 1 — Reconnect + Usage Polish
Already completed. Does not map to delta commits; it was the baseline pre-work.

Phase 2 — UI Parity Sweep
Commit	Focus
93cd6e2	Double-message fix, replay dedupe, timeline reducer
21769fa	Streaming delta, timeline collapsing, projection layer
3da4d17	Pass working directory into timeline rows
6c3c85b	Broad UI sweep: shell, command rows, message components
c7843aa	Turn message rendering polish
Merge boundaries: 064e72a

Phase 3 — Images, Diffs, Checkpoints
Commit	Focus
625e2b4	Safe workspace image previews (bridge + iOS)
7869bfa	Preview cache timestamp key fix
8553266	Image sync + timeline handling
82c0903	Assistant phases + workspace checkpoints (largest lift)
Merge boundaries: e637d63, 38308f3

Phase 4 — Composer + Runtime Polish
Commit	Focus
81b94a6	Plugin mentions (composer model, chips, turn payload)
8c38d50	Autocomplete + chip rendering refinements
c4bc0d8	Autocomplete icon color toning
2e19695	Token boundary parsing fixes
306ea30	Fast mode model picker
09c6b80	Fast mode review blockers
88296b8	5.3 fast mode capability matrix
Merge boundaries: 055da4d, 749328d

Phase 5 — Project/Git Flow Parity
Commit	Focus
fcf67e8	Local folder browser + bridge project-handler.js
79d2142	New chat project picker sheet
4e19459	Git init + local-only chat removal + git-handler.js
7c82966	Folder search in local project picker
Phase 6 — End-To-End Local Flow
No specific commits; this is the manual verification gate covering all phases.

Phase 7 — Delta Audit
All 32 commits. Bridge/infra commits not covered above: 2eea6eb (watchdog), 0e838d8 (history compaction), 445c595 (git-issue fixes), 1d2f232 (4 MB payload limit). CI-only: 37035d7, 8124de3, 6f3f20a — not applicable to Android.