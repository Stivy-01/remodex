-- Lightweight checks to run after applying schema.sql in a disposable Supabase/Postgres database.

insert into beta_testers(id, display_name)
values ('00000000-0000-0000-0000-000000000001', 'Tester-0001')
on conflict (id) do update set display_name = excluded.display_name;

insert into beta_builds(app_version, changelog, today_test, known_issues)
values ('0.1.0', array['Initial beta HQ'], array['Open Tester HQ'], '{}')
on conflict (app_version) do update
set changelog = excluded.changelog,
    today_test = excluded.today_test;

insert into beta_missions(id, app_version, title, points)
values ('0.1.0-open', '0.1.0', 'Open Tester HQ', 5)
on conflict (id) do update set title = excluded.title;

insert into beta_events(tester_id, event_type, points, app_version, dedupe_key)
values ('00000000-0000-0000-0000-000000000001', 'daily_open', 5, '0.1.0', 'daily_open:2099-01-01')
on conflict do nothing;

-- This insert should no-op because of beta_events_unique_dedupe.
insert into beta_events(tester_id, event_type, points, app_version, dedupe_key)
values ('00000000-0000-0000-0000-000000000001', 'daily_open', 5, '0.1.0', 'daily_open:2099-01-01')
on conflict do nothing;

do $$
declare
  event_count int;
  public_columns text[];
begin
  select count(*) into event_count
  from beta_events
  where tester_id = '00000000-0000-0000-0000-000000000001'
    and event_type = 'daily_open'
    and dedupe_key = 'daily_open:2099-01-01';

  if event_count != 1 then
    raise exception 'duplicate daily_open event was not blocked';
  end if;

  select array_agg(column_name::text order by ordinal_position) into public_columns
  from information_schema.columns
  where table_name = 'beta_public_leaderboard';

  if public_columns && array['tester_id', 'device_model'] then
    raise exception 'public leaderboard exposes private columns';
  end if;
end $$;

