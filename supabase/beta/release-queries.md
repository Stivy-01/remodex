# Beta Release Queries

Use these SQL snippets in the Supabase SQL editor when publishing a new Android beta build or updating Tester HQ missions.

## Current Model

`beta_builds` does not use an `is_current` column. The Edge Function chooses the build like this:

1. If the app sends `app_version`, it loads the matching `beta_builds.app_version`.
2. If there is no exact match, it falls back to the most recent active build where:
   - `active_from <= now()`
   - `active_until is null` or `active_until >= now()`

For a new Play release, always create a matching `beta_builds` row for the new `versionName`.

## Add A New Beta Build

Replace `0.1.2` and the text arrays with the new release content.

```sql
insert into beta_builds (
  app_version,
  changelog,
  today_test,
  known_issues,
  reward_copy,
  active_from,
  active_until
)
values (
  '0.1.2',
  array[
    'Fixed hydration processes during qr failed scan and allow camera notification',
    'Cleaner completed-turn chat layout with the final assistant summary kept in the main timeline',
    'Plan mode now generates a plan card with gold shimmering expandable and consultable during plan execution',
    'Activity details now contains intermediate assistant notes, tool calls, command output, file edits, and grouped work batches',
    'Added subtle live shimmer states while the assistant is reading, editing, running checks, or applying patches',
    'Improved grouping for consecutive command runs and file edits inside Activity details',
    'Upgraded scroll UX for steadier jump controls, timeline fading, and long-chat navigation'
  ],
  array[
    'Open Tester HQ',
    'Check today''s missions',
    'Send one useful feedback message'
  ],
  array[
    'Beta leaderboard may update after refresh'
  ],
  'Top 30 useful beta contributors will receive 1 free month after public release. Points help us track participation, but final selection also considers useful feedback, confirmed bugs, and testing quality. Reviews and ratings are never required or rewarded.',
  now(),
  null
)
on conflict (app_version) do update set
  changelog = excluded.changelog,
  today_test = excluded.today_test,
  known_issues = excluded.known_issues,
  reward_copy = excluded.reward_copy,
  active_from = excluded.active_from,
  active_until = excluded.active_until;
```

## Close Older Active Builds

Use this if you want fallback `/beta/hq` calls to resolve only to the latest build.

```sql
update beta_builds
set active_until = now()
where app_version <> '0.1.2'
  and active_until is null;
```

## Add Missions For A Build

Mission points are displayed by the app, but awarded only by the Edge Function rules. Android never sends point values.

The current Edge Function automatically completes these mission ID patterns:

- App open mission: `VERSION-open`, `VERSION-daily-open`, or `VERSION-latest-build-opened`
- Feedback mission: `VERSION-feedback`, `VERSION-send-feedback`, or `VERSION-beta-feedback`

Recommended IDs:

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
  (
    '0.1.2-open',
    '0.1.2',
    'Open latest beta',
    'Open the newest Android beta build and visit Tester HQ.',
    15,
    10,
    true
  ),
  (
    '0.1.2-test-main-flow',
    '0.1.2',
    'Test the main Remodex flow',
    'Connect to your desktop session, send one message, and report if the completed chat view, Activity details, or live work states feel broken, slow, or confusing.',
    50,
    20,
    true
  ),
  (
    '0.1.2-streaming-response',
    '0.1.2',
    'Watch a streaming response',
    'Send a request that produces a longer answer and check whether partial updates stream cleanly before the final summary remains in the main chat.',
    35,
    30,
    true
  ),
  (
    '0.1.2-scroll-long-thread',
    '0.1.2',
    'Test long-thread scrolling',
    'Open a long conversation, scroll up and down, and verify jump controls, timeline fading, composer placement, and Activity details stay usable.',
    25,
    40,
    true
  ),
  (
    '0.1.2-command-card',
    '0.1.2',
    'Inspect command activity',
    'Run a task with terminal output and verify command status, output, grouped command batches, and Activity details are readable.',
    25,
    50,
    true
  ),
  (
    '0.1.2-file-change-card',
    '0.1.2',
    'Inspect file-change activity',
    'Run a safe task that produces a patch and verify file labels, grouped edit batches, and Activity details look correct.',
    30,
    60,
    true
  ),
  (
    '0.1.2-feedback',
    '0.1.2',
    'Send beta feedback',
    'Send one useful feedback message from Tester HQ after checking the 0.1.2 chat and scroll changes.',
    40,
    70,
    true
  )
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Add Display-Only Missions

These appear in Tester HQ but will stay pending until the backend has a matching event rule.

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
values (
  '0.1.2-test-qr-pairing',
  '0.1.2',
  'Test QR pairing',
  'Pair the Android app with your local desktop session and report anything confusing.',
  30,
  30,
  true
)
on conflict (id) do update set
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Disable A Mission

```sql
update beta_missions
set active = false
where id = '0.1.2-test-qr-pairing';
```

## Copy Missions From Previous Build

Use this when a new build keeps the same mission structure.

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
select
  replace(id, '0.1.1', '0.1.2') as id,
  '0.1.2' as app_version,
  title,
  description,
  points,
  sort_order,
  active
from beta_missions
where app_version = '0.1.1'
on conflict (id) do update set
  app_version = excluded.app_version,
  title = excluded.title,
  description = excluded.description,
  points = excluded.points,
  sort_order = excluded.sort_order,
  active = excluded.active;
```

## Check What Tester HQ Will Show

```sql
select *
from beta_builds
where app_version = '0.1.2';

select id, title, description, points, sort_order, active
from beta_missions
where app_version = '0.1.2'
order by sort_order asc;
```

## Version Checklist

1. Bump Android `versionName`.
2. Build and upload the new AAB.
3. Insert or update the matching `beta_builds` row.
4. Add missions for the same `app_version`.
5. Close old active builds if needed.
6. Open Tester HQ on device and tap refresh.
