# Remodex Android Beta Edge Function Contracts

These endpoints are beta-only hosted surfaces. They must not be required for normal local-first Remodex operation.

Base URL is supplied to Android as `BETA_API_BASE_URL`; Android appends the paths below.

## Shared Rules

- Authenticate requests with the configured Supabase anon/API key at the Edge Function layer.
- Never trust point values from Android.
- Validate `testerId` as UUID.
- Do not accept prompts, assistant output, local paths, relay session IDs, pairing identifiers, or logs.
- Server decides points, mission completion, streak, rank, and duplicate handling.

## POST `/beta/register`

Request:

```json
{
  "tester_id": "00000000-0000-0000-0000-000000000000",
  "display_name": "Tester-042",
  "app_version": "0.1.0",
  "device_model": "Google Pixel 8"
}
```

Behavior:

- Upsert `beta_testers.id`.
- Store `display_name` only when non-empty and at most 20 characters.
- Return profile without private device information.

Response:

```json
{
  "tester_id": "00000000-0000-0000-0000-000000000000",
  "display_name": "Tester-042",
  "total_score": 0,
  "rank": null,
  "streak_days": 0
}
```

## POST `/beta/open`

Request:

```json
{
  "tester_id": "00000000-0000-0000-0000-000000000000",
  "app_version": "0.1.0",
  "device_model": "Google Pixel 8"
}
```

Behavior:

- Ensure tester exists.
- Insert `daily_open` with dedupe key `daily_open:{yyyy-mm-dd}` and server-defined points.
- Insert `latest_build_opened` with dedupe key `latest_build_opened:{app_version}` when applicable.
- Return the same payload shape as `/beta/hq`.

## GET `/beta/hq?testerId=...&appVersion=...`

Behavior:

- Ensure tester exists.
- Select active `beta_builds` row for `appVersion`, falling back to latest active build.
- Return score, rank, streak, current build metadata, mission statuses, and reward copy.

Response:

```json
{
  "profile": {
    "tester_id": "00000000-0000-0000-0000-000000000000",
    "display_name": "Tester-042",
    "total_score": 285,
    "rank": 12,
    "streak_days": 4
  },
  "current_build": {
    "version": "0.1.0",
    "changelog": ["QR reconnect issue fixed"],
    "today_test": ["Pair with QR", "Send one prompt"],
    "known_issues": []
  },
  "missions": [
    {
      "id": "0.1.0-feedback",
      "title": "Send feedback",
      "description": "Submit feedback from Tester HQ.",
      "points": 40,
      "status": "pending"
    }
  ],
  "reward_copy": "Top 30 useful beta contributors will receive 1 free month after public release. Points help us track participation, but final selection also considers useful feedback, confirmed bugs, and testing quality. Reviews and ratings are never required or rewarded.",
  "feedback_sent_today": false
}
```

## POST `/beta/feedback`

Request:

```json
{
  "tester_id": "00000000-0000-0000-0000-000000000000",
  "type": "bug",
  "message": "The reconnect card stayed visible after pairing.",
  "screen": "tester_hq",
  "app_version": "0.1.0",
  "device_model": "Google Pixel 8"
}
```

## GET `/beta/leaderboard?testerId=...&appVersion=...`

Behavior:

- Ensures tester exists.
- Returns the current tester profile, top public leaderboard rows, top-30 cutoff when available, and refresh cadence.
- Does not expose tester IDs, device model, feedback content, or event details in public rows.

Response:

```json
{
  "profile": {
    "tester_id": "00000000-0000-0000-0000-000000000000",
    "display_name": "Tester-042",
    "total_score": 285,
    "rank": 12,
    "streak_days": 4
  },
  "rows": [
    {
      "rank": 1,
      "display_name": "pixelwolf",
      "total_points": 1290,
      "is_current_tester": false
    }
  ],
  "top_30_cutoff": 410,
  "updated_every_minutes": 15
}
```

Behavior:

- Ensure tester exists.
- Insert into `beta_feedback`.
- Insert `feedback_sent` event with server-defined points and dedupe rules.
- Return points awarded and updated score.

Response:

```json
{
  "success": true,
  "points_awarded": 40,
  "total_score": 325,
  "message": "Thanks - this helps improve the Android beta."
}
```
