-- Remodex Android beta engagement schema.
-- Hosted beta-only artifact. This must not become a required dependency for local-first app use.

create extension if not exists pgcrypto;

create table if not exists beta_testers (
  id uuid primary key,
  display_name text check (display_name is null or char_length(display_name) <= 20),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now()
);

create table if not exists beta_builds (
  app_version text primary key,
  changelog text[] not null default '{}',
  today_test text[] not null default '{}',
  known_issues text[] not null default '{}',
  reward_copy text,
  active_from timestamp with time zone not null default now(),
  active_until timestamp with time zone
);

create table if not exists beta_missions (
  id text primary key,
  app_version text not null references beta_builds(app_version) on delete cascade,
  title text not null,
  description text,
  points int not null default 0 check (points >= 0),
  sort_order int not null default 0,
  active boolean not null default true
);

create table if not exists beta_events (
  id uuid primary key default gen_random_uuid(),
  tester_id uuid not null references beta_testers(id) on delete cascade,
  event_type text not null,
  points int not null default 0 check (points >= 0),
  app_version text,
  screen text,
  mission_id text references beta_missions(id),
  dedupe_key text,
  device_model text,
  created_at timestamp with time zone not null default now()
);

create unique index if not exists beta_events_unique_dedupe
  on beta_events(tester_id, event_type, dedupe_key)
  where dedupe_key is not null;

create table if not exists beta_tester_devices (
  device_key text primary key check (char_length(device_key) >= 16),
  tester_id uuid not null references beta_testers(id) on delete cascade,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now()
);

create index if not exists beta_tester_devices_tester_id_idx on beta_tester_devices(tester_id);

-- RLS enabled with other tables below
  id uuid primary key default gen_random_uuid(),
  tester_id uuid not null references beta_testers(id) on delete cascade,
  type text not null check (
    type in (
      'bug',
      'crash',
      'ux_issue',
      'confusing_flow',
      'performance',
      'feature_request',
      'other'
    )
  ),
  message text not null check (char_length(trim(message)) > 0),
  screen text,
  app_version text,
  device_model text,
  created_at timestamp with time zone not null default now()
);

create or replace view beta_leaderboard as
select
  tester_id,
  sum(points)::int as total_points,
  count(*)::int as total_events,
  max(created_at) as last_activity
from beta_events
group by tester_id
order by total_points desc, last_activity asc;

create or replace view beta_public_leaderboard as
select
  row_number() over (order by l.total_points desc, l.last_activity asc) as rank,
  coalesce(t.display_name, 'Tester-' || right(t.id::text, 4)) as display_name,
  l.total_points,
  l.total_events,
  l.last_activity
from beta_leaderboard l
join beta_testers t on t.id = l.tester_id;

alter table beta_testers enable row level security;
alter table beta_builds enable row level security;
alter table beta_missions enable row level security;
alter table beta_events enable row level security;
alter table beta_feedback enable row level security;
alter table beta_tester_devices enable row level security;

-- Expected deployment model: Edge Functions use the service role key.
-- Public table access should remain closed unless a separate read-only policy is intentionally added.

