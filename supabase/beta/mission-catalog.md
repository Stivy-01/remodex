# Beta Mission Catalog

This catalog defines the missions to rotate during the Android beta. Keep each build focused: use 3 to 5 missions per release.

Recommended daily mix:

- 1 activation mission: open latest build or Tester HQ.
- 1 core flow mission: QR pairing, reconnect, main flow, branch selector, or background recovery.
- 1 feedback mission: send useful feedback from Tester HQ.
- 0 to 2 focused UI missions: leaderboard, settings, attachments, voice, review, git, worktree, sidebar, or design mode.

## Status Legend

- `auto-live`: Android already calls the beta event and Supabase can complete the mission automatically.
- `route-ready`: Supabase route supports the mission event, but the app does not yet have a reliable hook for automatic completion.
- `auto-core`: special built-in route behavior, not handled through `/beta/mission-event`.

Privacy boundary stays the same for all missions: Android sends only tester ID, event type, app version, coarse device model, and optional screen key. It does not send prompts, assistant output, thread IDs, local paths, relay session IDs, pairing identifiers, or logs.

## Auto-Core Missions

These are completed by existing dedicated endpoints.

| Mission ID suffix | Status | Event / endpoint | Points | Title | Description |
| --- | --- | --- | ---: | --- | --- |
| `open` | auto-core + auto-live | `POST /beta/open` + `tester_hq_opened` | 15 | Open latest beta | Open the newest Android beta build and visit Tester HQ. |
| `feedback` | auto-core | `POST /beta/feedback` | 40 | Send beta feedback | Send one useful feedback message from Tester HQ. |

## Auto-Live Missions

These have both backend route support and Android app hooks.

| Mission ID suffix | Status | Event type | Points | Title | Description |
| --- | --- | --- | ---: | --- | --- |
| `open` | auto-live | `tester_hq_opened` | 15 | Open latest beta | Open Tester HQ in the current beta build. |
| `test-main-flow` | auto-live | `main_flow_completed` | 50 | Test the main Remodex flow | Connect to your desktop session, send one message, and report if anything feels broken, slow, or confusing. |
| `send-message` | auto-live | `message_sent` | 30 | Send one message | Send one normal prompt and confirm the assistant response starts in the timeline. |
| `qr-pairing` | auto-live | `qr_pairing_completed` | 40 | Test QR pairing | Pair Android with the desktop bridge using QR and report scanner, permission, or connection issues. |
| `reconnect` | auto-live | `reconnect_completed` | 35 | Test reconnect | Reconnect with a saved pairing and verify the app returns to a usable connected state. |
| `background-foreground-session` | auto-live | `session_recovered_from_background` | 35 | Test session recovery | Start a connected session, background the app, return to foreground, and verify the conversation still updates. |
| `leaderboard-refresh` | auto-live | `leaderboard_refreshed` | 20 | Refresh leaderboard | Open Tester HQ leaderboard, refresh it, and check whether score/rank look consistent. |
| `branch-selector` | auto-live | `branch_selector_opened` | 40 | Test branch selector | Open the branch selector and verify branch/current state is readable. |
| `queued-draft` | auto-live | `queued_draft_used` | 25 | Queue a message while running | Send a second message while a turn is running and verify queued draft behavior. |
| `plan-mode` | auto-live | `plan_mode_used` | 25 | Test plan mode | Enable plan mode, send a planning request, and verify the plan accessory appears correctly. |
| `voice-input` | auto-live | `voice_input_used` | 35 | Test voice input | Record a short voice message, transcribe it, and send or edit the draft. |
| `image-attachment` | auto-live | `image_attachment_sent` | 35 | Test image attachment | Attach an image, send it with a prompt, and report upload or preview issues. |
| `file-attachment` | auto-live | `file_attachment_sent` | 35 | Test file attachment | Attach a file reference and verify it is represented clearly in the draft and timeline. |
| `review-flow` | auto-live | `review_flow_started` | 35 | Test review flow | Start a review request from the composer and verify the review accessory and errors. |
| `settings-tester-hq-entry` | auto-live | `settings_tester_hq_entry_opened` | 15 | Find Tester HQ from Settings | Open Settings and navigate to Tester HQ from the beta row. |
| `settings-whats-new` | auto-live | `settings_whats_new_opened` | 15 | Read changelog | Open Settings, read What's New, and report stale or unclear release notes. |
| `about-screen` | auto-live | `about_screen_opened` | 10 | Check About screen | Open About and verify version/build information looks correct. |
| `streaming-response` | auto-live | `streaming_response_seen` | 35 | Watch a streaming response | Send a request that produces a longer answer and check whether partial updates merge cleanly. |
| `scroll-long-thread` | auto-live | `scroll_long_thread_checked` | 25 | Test long-thread scrolling | Open a long conversation, scroll up and down, and verify the composer and timeline stay usable. |
| `file-change-card` | auto-live | `file_change_card_checked` | 30 | Inspect file-change cards | Run a task that produces a patch and check summary, file labels, and detail expansion. |
| `command-card` | auto-live | `command_card_checked` | 25 | Inspect command cards | Run a task with terminal output and verify command status, output, and failure states are readable. |

## Route-Ready Missions

These are supported by `/beta/mission-event`, but still need app-side hooks before they become automatic on device.

| Mission ID suffix | Status | Event type | Points | Title | Description |
| --- | --- | --- | ---: | --- | --- |
| `markdown-rendering` | route-ready | `markdown_rendering_checked` | 25 | Check markdown rendering | Ask for a response with bullets, code, and a link; report broken spacing or clipping. |
| `composer-basic` | route-ready | `composer_basic_used` | 20 | Test composer basics | Type, edit, send, and clear a message from the composer. |
| `composer-mentions` | route-ready | `composer_mentions_used` | 30 | Test composer mentions | Try file, skill, or command-style mentions and report autocomplete issues. |
| `composer-runtime-controls` | route-ready | `composer_runtime_controls_used` | 25 | Test runtime controls | Change available runtime/model controls and verify the selected state remains visible. |
| `git-status` | route-ready | `git_status_checked` | 25 | Check git status UI | Open git/diff status from a repo thread and verify changed files are shown clearly. |
| `git-diff-stage` | route-ready | `git_diff_stage_checked` | 40 | Test diff staging | Inspect a changed file and try staging or unstaging a safe local change. |
| `new-worktree-chat` | route-ready | `new_worktree_chat_started` | 45 | Start a worktree chat | Create a new chat using worktree mode and verify branch/cwd context is correct. |
| `worktree-handoff` | route-ready | `worktree_handoff_completed` | 50 | Test worktree handoff | Move a safe local task into a managed worktree and report transfer/conflict issues. |
| `fork-thread` | route-ready | `fork_thread_completed` | 30 | Fork a thread | Fork a safe thread and verify context, title, and project routing remain correct. |
| `notifications` | route-ready | `notifications_checked` | 25 | Test notification permission | Enable or inspect run-completion notifications and report permission issues. |
| `usage-status` | route-ready | `usage_status_checked` | 20 | Check usage status | Open the usage/rate limit surface and verify numbers and refresh behavior. |
| `sidebar-open-thread` | route-ready | `sidebar_thread_opened` | 20 | Open a recent thread | Use the sidebar to switch threads and verify the selected conversation loads correctly. |
| `sidebar-search` | route-ready | `sidebar_search_used` | 20 | Search sidebar threads | Search for an existing thread and report missing or confusing results. |
| `new-chat` | route-ready | `new_chat_started` | 25 | Start a new chat | Create a new chat from the sidebar and verify it uses the correct local project context. |
| `archive-thread` | route-ready | `archived_chats_opened` | 20 | Check archived chats | Open archived chats and verify archived threads display correctly. |
| `design-mode-open` | route-ready | `design_mode_opened` | 25 | Open design mode | Open the design workspace and verify the empty state or current project loads. |
| `design-preview` | route-ready | `design_preview_checked` | 35 | Test design preview | Generate or open a preview and report rendering, loading, or stale snapshot issues. |
| `design-export` | route-ready | `design_export_opened` | 30 | Test design export | Open export options and verify available formats and labels are understandable. |

## Suggested Build Rotations

### Build A: Onboarding And Connection

- `{version}-open`
- `{version}-qr-pairing`
- `{version}-reconnect`
- `{version}-background-foreground-session`
- `{version}-feedback`

### Build B: Daily Conversation

- `{version}-open`
- `{version}-test-main-flow`
- `{version}-send-message`
- `{version}-queued-draft`
- `{version}-feedback`

### Build C: Composer

- `{version}-open`
- `{version}-plan-mode`
- `{version}-voice-input`
- `{version}-image-attachment`
- `{version}-feedback`

### Build D: Git And Branches

- `{version}-open`
- `{version}-branch-selector`
- `{version}-review-flow`
- `{version}-feedback`

### Build E: Tester HQ And Settings

- `{version}-open`
- `{version}-leaderboard-refresh`
- `{version}-settings-tester-hq-entry`
- `{version}-settings-whats-new`
- `{version}-feedback`

## Tracking Notes

Automatic scoring depends on two things:

1. A matching mission row exists in `beta_missions`, for example `{version}-qr-pairing`.
2. Android sends the matching event, for example `qr_pairing_completed`.

For `route-ready` missions, the backend route is ready, but Android still needs a precise hook in the relevant UI or service before the mission can complete automatically.
