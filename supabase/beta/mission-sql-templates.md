# Beta Mission SQL Templates

These queries are ready to paste into the Supabase SQL editor.

Before running, replace every `__VERSION__` with the Android `versionName`, for example `0.1.1` or `0.1.2`.

## Insert All Missions

```sql
insert into beta_missions (
  id,
  app_version,
  title,
  description,
  points,
  sort_order,
  active
)
values
  ('__VERSION__-open', '__VERSION__', 'Open latest beta', 'Open the newest Android beta build and visit Tester HQ.', 15, 10, true),
  ('__VERSION__-feedback', '__VERSION__', 'Send beta feedback', 'Send one useful feedback message from Tester HQ.', 40, 20, true),
  ('__VERSION__-test-main-flow', '__VERSION__', 'Test the main Remodex flow', 'Connect to your desktop session, send one message, and report if anything feels broken, slow, or confusing.', 50, 30, true),
  ('__VERSION__-send-message', '__VERSION__', 'Send one message', 'Send one normal prompt and confirm the assistant response starts in the timeline.', 30, 40, true),
  ('__VERSION__-qr-pairing', '__VERSION__', 'Test QR pairing', 'Pair Android with the desktop bridge using QR and report scanner, permission, or connection issues.', 40, 50, true),
  ('__VERSION__-reconnect', '__VERSION__', 'Test reconnect', 'Reconnect with a saved pairing and verify the app returns to a usable connected state.', 35, 60, true),
  ('__VERSION__-background-foreground-session', '__VERSION__', 'Test session recovery', 'Start a connected session, background the app, return to foreground, and verify the conversation still updates.', 35, 70, true),
  ('__VERSION__-leaderboard-refresh', '__VERSION__', 'Refresh leaderboard', 'Open Tester HQ leaderboard, refresh it, and check whether score/rank look consistent.', 20, 80, true),
  ('__VERSION__-branch-selector', '__VERSION__', 'Test branch selector', 'Open the branch selector and verify branch/current state is readable.', 40, 90, true),
  ('__VERSION__-queued-draft', '__VERSION__', 'Queue a message while running', 'Send a second message while a turn is running and verify queued draft behavior.', 25, 100, true),
  ('__VERSION__-plan-mode', '__VERSION__', 'Test plan mode', 'Enable plan mode, send a planning request, and verify the plan accessory appears correctly.', 25, 110, true),
  ('__VERSION__-voice-input', '__VERSION__', 'Test voice input', 'Record a short voice message, transcribe it, and send or edit the draft.', 35, 120, true),
  ('__VERSION__-image-attachment', '__VERSION__', 'Test image attachment', 'Attach an image, send it with a prompt, and report upload or preview issues.', 35, 130, true),
  ('__VERSION__-file-attachment', '__VERSION__', 'Test file attachment', 'Attach a file reference and verify it is represented clearly in the draft and timeline.', 35, 140, true),
  ('__VERSION__-review-flow', '__VERSION__', 'Test review flow', 'Start a review request from the composer and verify the review accessory and errors.', 35, 150, true),
  ('__VERSION__-settings-tester-hq-entry', '__VERSION__', 'Find Tester HQ from Settings', 'Open Settings and navigate to Tester HQ from the beta row.', 15, 160, true),
  ('__VERSION__-settings-whats-new', '__VERSION__', 'Read changelog', 'Open Settings, read What''s New, and report stale or unclear release notes.', 15, 170, true),
  ('__VERSION__-about-screen', '__VERSION__', 'Check About screen', 'Open About and verify version/build information looks correct.', 10, 180, true),
  ('__VERSION__-streaming-response', '__VERSION__', 'Watch a streaming response', 'Send a request that produces a longer answer and check whether partial updates merge cleanly.', 35, 190, true),
  ('__VERSION__-scroll-long-thread', '__VERSION__', 'Test long-thread scrolling', 'Open a long conversation, scroll up and down, and verify the composer and timeline stay usable.', 25, 200, true),
  ('__VERSION__-markdown-rendering', '__VERSION__', 'Check markdown rendering', 'Ask for a response with bullets, code, and a link; report broken spacing or clipping.', 25, 210, true),
  ('__VERSION__-file-change-card', '__VERSION__', 'Inspect file-change cards', 'Run a task that produces a patch and check summary, file labels, and detail expansion.', 30, 220, true),
  ('__VERSION__-command-card', '__VERSION__', 'Inspect command cards', 'Run a task with terminal output and verify command status, output, and failure states are readable.', 25, 230, true),
  ('__VERSION__-composer-basic', '__VERSION__', 'Test composer basics', 'Type, edit, send, and clear a message from the composer.', 20, 240, true),
  ('__VERSION__-composer-mentions', '__VERSION__', 'Test composer mentions', 'Try file, skill, or command-style mentions and report autocomplete issues.', 30, 250, true),
  ('__VERSION__-composer-runtime-controls', '__VERSION__', 'Test runtime controls', 'Change available runtime/model controls and verify the selected state remains visible.', 25, 260, true),
  ('__VERSION__-git-status', '__VERSION__', 'Check git status UI', 'Open git/diff status from a repo thread and verify changed files are shown clearly.', 25, 270, true),
  ('__VERSION__-git-diff-stage', '__VERSION__', 'Test diff staging', 'Inspect a changed file and try staging or unstaging a safe local change.', 40, 280, true),
  ('__VERSION__-new-worktree-chat', '__VERSION__', 'Start a worktree chat', 'Create a new chat using worktree mode and verify branch/cwd context is correct.', 45, 290, true),
  ('__VERSION__-worktree-handoff', '__VERSION__', 'Test worktree handoff', 'Move a safe local task into a managed worktree and report transfer/conflict issues.', 50, 300, true),
  ('__VERSION__-fork-thread', '__VERSION__', 'Fork a thread', 'Fork a safe thread and verify context, title, and project routing remain correct.', 30, 310, true),
  ('__VERSION__-notifications', '__VERSION__', 'Test notification permission', 'Enable or inspect run-completion notifications and report permission issues.', 25, 320, true),
  ('__VERSION__-usage-status', '__VERSION__', 'Check usage status', 'Open the usage/rate limit surface and verify numbers and refresh behavior.', 20, 330, true),
  ('__VERSION__-sidebar-open-thread', '__VERSION__', 'Open a recent thread', 'Use the sidebar to switch threads and verify the selected conversation loads correctly.', 20, 340, true),
  ('__VERSION__-sidebar-search', '__VERSION__', 'Search sidebar threads', 'Search for an existing thread and report missing or confusing results.', 20, 350, true),
  ('__VERSION__-new-chat', '__VERSION__', 'Start a new chat', 'Create a new chat from the sidebar and verify it uses the correct local project context.', 25, 360, true),
  ('__VERSION__-archive-thread', '__VERSION__', 'Check archived chats', 'Open archived chats and verify archived threads display correctly.', 20, 370, true),
  ('__VERSION__-design-mode-open', '__VERSION__', 'Open design mode', 'Open the design workspace and verify the empty state or current project loads.', 25, 380, true),
  ('__VERSION__-design-preview', '__VERSION__', 'Test design preview', 'Generate or open a preview and report rendering, loading, or stale snapshot issues.', 35, 390, true),
  ('__VERSION__-design-export', '__VERSION__', 'Test design export', 'Open export options and verify available formats and labels are understandable.', 30, 400, true)
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Insert Recommended Build A: Onboarding And Connection

```sql
insert into beta_missions (id, app_version, title, description, points, sort_order, active)
values
  ('__VERSION__-open', '__VERSION__', 'Open latest beta', 'Open the newest Android beta build and visit Tester HQ.', 15, 10, true),
  ('__VERSION__-qr-pairing', '__VERSION__', 'Test QR pairing', 'Pair Android with the desktop bridge using QR and report scanner, permission, or connection issues.', 40, 20, true),
  ('__VERSION__-reconnect', '__VERSION__', 'Test reconnect', 'Reconnect with a saved pairing and verify the app returns to a usable connected state.', 35, 30, true),
  ('__VERSION__-background-foreground-session', '__VERSION__', 'Test session recovery', 'Start a connected session, background the app, return to foreground, and verify the conversation still updates.', 35, 40, true),
  ('__VERSION__-feedback', '__VERSION__', 'Send beta feedback', 'Send one useful feedback message from Tester HQ.', 40, 50, true)
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Insert Recommended Build B: Daily Conversation

```sql
insert into beta_missions (id, app_version, title, description, points, sort_order, active)
values
  ('__VERSION__-open', '__VERSION__', 'Open latest beta', 'Open the newest Android beta build and visit Tester HQ.', 15, 10, true),
  ('__VERSION__-test-main-flow', '__VERSION__', 'Test the main Remodex flow', 'Connect to your desktop session, send one message, and report if anything feels broken, slow, or confusing.', 50, 20, true),
  ('__VERSION__-send-message', '__VERSION__', 'Send one message', 'Send one normal prompt and confirm the assistant response starts in the timeline.', 30, 30, true),
  ('__VERSION__-queued-draft', '__VERSION__', 'Queue a message while running', 'Send a second message while a turn is running and verify queued draft behavior.', 25, 40, true),
  ('__VERSION__-feedback', '__VERSION__', 'Send beta feedback', 'Send one useful feedback message from Tester HQ.', 40, 50, true)
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Insert Recommended Build C: Composer

```sql
insert into beta_missions (id, app_version, title, description, points, sort_order, active)
values
  ('__VERSION__-open', '__VERSION__', 'Open latest beta', 'Open the newest Android beta build and visit Tester HQ.', 15, 10, true),
  ('__VERSION__-plan-mode', '__VERSION__', 'Test plan mode', 'Enable plan mode, send a planning request, and verify the plan accessory appears correctly.', 25, 20, true),
  ('__VERSION__-voice-input', '__VERSION__', 'Test voice input', 'Record a short voice message, transcribe it, and send or edit the draft.', 35, 30, true),
  ('__VERSION__-image-attachment', '__VERSION__', 'Test image attachment', 'Attach an image, send it with a prompt, and report upload or preview issues.', 35, 40, true),
  ('__VERSION__-feedback', '__VERSION__', 'Send beta feedback', 'Send one useful feedback message from Tester HQ.', 40, 50, true)
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Insert Recommended Build D: Git And Branches

```sql
insert into beta_missions (id, app_version, title, description, points, sort_order, active)
values
  ('__VERSION__-open', '__VERSION__', 'Open latest beta', 'Open the newest Android beta build and visit Tester HQ.', 15, 10, true),
  ('__VERSION__-branch-selector', '__VERSION__', 'Test branch selector', 'Open the branch selector and verify branch/current state is readable.', 40, 20, true),
  ('__VERSION__-review-flow', '__VERSION__', 'Test review flow', 'Start a review request from the composer and verify the review accessory and errors.', 35, 30, true),
  ('__VERSION__-git-status', '__VERSION__', 'Check git status UI', 'Open git/diff status from a repo thread and verify changed files are shown clearly.', 25, 40, true),
  ('__VERSION__-feedback', '__VERSION__', 'Send beta feedback', 'Send one useful feedback message from Tester HQ.', 40, 50, true)
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Insert Recommended Build E: Tester HQ And Settings

```sql
insert into beta_missions (id, app_version, title, description, points, sort_order, active)
values
  ('__VERSION__-open', '__VERSION__', 'Open latest beta', 'Open the newest Android beta build and visit Tester HQ.', 15, 10, true),
  ('__VERSION__-leaderboard-refresh', '__VERSION__', 'Refresh leaderboard', 'Open Tester HQ leaderboard, refresh it, and check whether score/rank look consistent.', 20, 20, true),
  ('__VERSION__-settings-tester-hq-entry', '__VERSION__', 'Find Tester HQ from Settings', 'Open Settings and navigate to Tester HQ from the beta row.', 15, 30, true),
  ('__VERSION__-settings-whats-new', '__VERSION__', 'Read changelog', 'Open Settings, read What''s New, and report stale or unclear release notes.', 15, 40, true),
  ('__VERSION__-feedback', '__VERSION__', 'Send beta feedback', 'Send one useful feedback message from Tester HQ.', 40, 50, true)
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Disable All Missions For A Version

```sql
update beta_missions
set active = false
where app_version = '__VERSION__';
```

## Enable Only A Selected Set

Replace the IDs with the missions you want active for the day.

```sql
update beta_missions
set active = id in (
  '__VERSION__-open',
  '__VERSION__-test-main-flow',
  '__VERSION__-leaderboard-refresh',
  '__VERSION__-feedback'
)
where app_version = '__VERSION__';
```

## Check Active Missions

```sql
select id, title, points, sort_order, active
from beta_missions
where app_version = '__VERSION__'
order by sort_order asc;
```
