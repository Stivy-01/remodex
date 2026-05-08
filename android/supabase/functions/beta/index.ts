import "@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

const jsonHeaders = {
  ...corsHeaders,
  "Content-Type": "application/json",
};

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

if (!supabaseUrl || !serviceRoleKey) {
  console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
}

const supabase = createClient(supabaseUrl ?? "", serviceRoleKey ?? "");

const DEFAULT_REWARD_COPY =
  "Top 30 useful beta contributors will receive 1 free month after public release. " +
  "Points help us track participation, but final selection also considers useful feedback, " +
  "confirmed bugs, and testing quality. Reviews and ratings are never required or rewarded.";

const POINTS = {
  dailyOpen: 5,
  latestBuildOpened: 15,
  feedbackSent: 40,
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const url = new URL(req.url);
    const action = routeAction(url);

    if (req.method === "POST" && action === "register") {
      return json(await register(req));
    }
    if (req.method === "POST" && action === "open") {
      return json(await open(req));
    }
    if (req.method === "GET" && action === "hq") {
      return json(await hq(url));
    }
    if (req.method === "POST" && action === "feedback") {
      return json(await feedback(req));
    }
    if (req.method === "GET" && action === "leaderboard") {
      return json(await leaderboard(url));
    }

    return json({ error: "Not found" }, 404);
  } catch (error) {
    const status = error instanceof HttpError ? error.status : 500;
    const message = error instanceof Error ? error.message : "Server error";
    return json({ error: message }, status);
  }
});

async function register(req: Request) {
  const body = await readJson(req);
  const testerId = requireUuid(body.tester_id, "tester_id");
  const displayName = normalizeDisplayName(body.display_name);

  await assertOk(
    supabase.from("beta_testers").upsert(
      {
        id: testerId,
        display_name: displayName,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "id" },
    ),
  );

  return profile(testerId);
}

async function open(req: Request) {
  const body = await readJson(req);
  const testerId = requireUuid(body.tester_id, "tester_id");
  const appVersion = stringOrEmpty(body.app_version);
  const deviceModel = stringOrNull(body.device_model);

  await ensureTester(testerId);

  const today = utcDateKey(new Date());
  const openMissionId = await firstActiveMissionId(appVersion, [
    `${appVersion}-open`,
    `${appVersion}-daily-open`,
    `${appVersion}-latest-build-opened`,
  ]);
  await awardEvent({
    tester_id: testerId,
    event_type: "daily_open",
    points: POINTS.dailyOpen,
    app_version: appVersion,
    mission_id: openMissionId,
    device_model: deviceModel,
    dedupe_key: `daily_open:${today}`,
  });

  if (appVersion) {
    await awardEvent({
      tester_id: testerId,
      event_type: "latest_build_opened",
      points: POINTS.latestBuildOpened,
      app_version: appVersion,
      mission_id: openMissionId,
      device_model: deviceModel,
      dedupe_key: `latest_build_opened:${appVersion}`,
    });
  }

  return buildHq(testerId, appVersion);
}

async function hq(url: URL) {
  const testerId = requireUuid(
    url.searchParams.get("testerId") ?? url.searchParams.get("tester_id"),
    "testerId",
  );
  const appVersion = stringOrEmpty(
    url.searchParams.get("appVersion") ?? url.searchParams.get("app_version"),
  );

  await ensureTester(testerId);
  return buildHq(testerId, appVersion);
}

async function feedback(req: Request) {
  const body = await readJson(req);
  const testerId = requireUuid(body.tester_id, "tester_id");
  const type = requireFeedbackType(body.type);
  const message = stringOrEmpty(body.message).trim();

  if (!message) {
    throw new HttpError(400, "message is required");
  }

  const screen = stringOrNull(body.screen);
  const appVersion = stringOrEmpty(body.app_version);
  const deviceModel = stringOrNull(body.device_model);

  await ensureTester(testerId);

  await assertOk(
    supabase.from("beta_feedback").insert({
      tester_id: testerId,
      type,
      message,
      screen,
      app_version: appVersion,
      device_model: deviceModel,
    }),
  );

  const today = utcDateKey(new Date());
  const feedbackMissionId = await firstActiveMissionId(appVersion, [
    `${appVersion}-feedback`,
    `${appVersion}-send-feedback`,
    `${appVersion}-beta-feedback`,
  ]);
  const awarded = await awardEvent({
    tester_id: testerId,
    event_type: "feedback_sent",
    points: POINTS.feedbackSent,
    app_version: appVersion,
    screen,
    mission_id: feedbackMissionId,
    device_model: deviceModel,
    dedupe_key: `feedback_sent:${today}`,
  });

  const p = await profile(testerId);
  return {
    success: true,
    points_awarded: awarded ? POINTS.feedbackSent : 0,
    total_score: p.total_score,
    message: "Thanks - this helps improve the Android beta.",
  };
}

async function leaderboard(url: URL) {
  const testerId = requireUuid(
    url.searchParams.get("testerId") ?? url.searchParams.get("tester_id"),
    "testerId",
  );
  const appVersion = stringOrEmpty(
    url.searchParams.get("appVersion") ?? url.searchParams.get("app_version"),
  );

  await ensureTester(testerId);

  const rowsResult = await assertOk(
    supabase.from("beta_public_leaderboard")
      .select("rank,display_name,total_points")
      .order("rank", { ascending: true })
      .limit(10),
  );
  const rawRows = rowsResult.data ?? [];
  const ownProfile = await profile(testerId);

  const top30Result = await assertOk(
    supabase.from("beta_leaderboard")
      .select("total_points")
      .order("total_points", { ascending: false })
      .limit(30),
  );
  const top30Rows = top30Result.data ?? [];
  const top30Cutoff = top30Rows.length >= 30 ? top30Rows[29].total_points ?? null : null;

  return {
    profile: ownProfile,
    rows: rawRows.map((row) => ({
      rank: row.rank,
      display_name: row.display_name,
      total_points: row.total_points,
      is_current_tester: row.rank === ownProfile.rank,
    })),
    top_30_cutoff: top30Cutoff,
    updated_every_minutes: 15,
    app_version: appVersion,
  };
}

async function buildHq(testerId: string, appVersion: string) {
  const build = await activeBuild(appVersion);
  const buildVersion = build?.app_version ?? appVersion;
  const missions = await activeMissions(buildVersion);
  const completedMissionIds = await completedMissions(testerId);
  const feedbackSentToday = await hasFeedbackToday(testerId);

  return {
    profile: await profile(testerId),
    current_build: build
      ? {
        version: build.app_version,
        changelog: build.changelog ?? [],
        today_test: build.today_test ?? [],
        known_issues: build.known_issues ?? [],
      }
      : null,
    missions: missions.map((mission) => ({
      id: mission.id,
      title: mission.title,
      description: mission.description,
      points: mission.points ?? 0,
      status: completedMissionIds.has(mission.id) ? "completed" : "pending",
    })),
    reward_copy: build?.reward_copy ?? DEFAULT_REWARD_COPY,
    feedback_sent_today: feedbackSentToday,
  };
}

async function activeBuild(appVersion: string) {
  if (appVersion) {
    const exact = await assertOk(
      supabase.from("beta_builds")
        .select("*")
        .eq("app_version", appVersion)
        .maybeSingle(),
    );
    if (exact.data) return exact.data;
  }

  const now = new Date().toISOString();
  const fallback = await assertOk(
    supabase.from("beta_builds")
      .select("*")
      .lte("active_from", now)
      .or(`active_until.is.null,active_until.gte.${now}`)
      .order("active_from", { ascending: false })
      .limit(1)
      .maybeSingle(),
  );
  return fallback.data;
}

async function activeMissions(appVersion: string) {
  if (!appVersion) return [];
  const result = await assertOk(
    supabase.from("beta_missions")
      .select("*")
      .eq("app_version", appVersion)
      .eq("active", true)
      .order("sort_order", { ascending: true }),
  );
  return result.data ?? [];
}

async function firstActiveMissionId(
  appVersion: string,
  preferredIds: string[],
): Promise<string | null> {
  if (!appVersion) return null;
  const result = await assertOk(
    supabase.from("beta_missions")
      .select("id")
      .eq("app_version", appVersion)
      .eq("active", true)
      .in("id", preferredIds)
      .limit(1),
  );
  return result.data?.[0]?.id ?? null;
}

async function profile(testerId: string) {
  const tester = await assertOk(
    supabase.from("beta_testers")
      .select("display_name")
      .eq("id", testerId)
      .maybeSingle(),
  );

  const leaderboard = await assertOk(
    supabase.from("beta_leaderboard")
      .select("tester_id,total_points,last_activity")
      .order("total_points", { ascending: false })
      .order("last_activity", { ascending: true }),
  );

  const rows = leaderboard.data ?? [];
  const index = rows.findIndex((row) => row.tester_id === testerId);
  const score = index >= 0 ? rows[index].total_points ?? 0 : 0;

  return {
    tester_id: testerId,
    display_name: tester.data?.display_name ?? null,
    total_score: score,
    rank: index >= 0 ? index + 1 : null,
    streak_days: await streakDays(testerId),
  };
}

async function streakDays(testerId: string): Promise<number> {
  const result = await assertOk(
    supabase.from("beta_events")
      .select("dedupe_key")
      .eq("tester_id", testerId)
      .eq("event_type", "daily_open")
      .not("dedupe_key", "is", null)
      .order("created_at", { ascending: false })
      .limit(90),
  );

  const openedDays = new Set(
    (result.data ?? [])
      .map((row) => String(row.dedupe_key ?? "").replace(/^daily_open:/, ""))
      .filter((value) => /^\d{4}-\d{2}-\d{2}$/.test(value)),
  );

  let streak = 0;
  const cursor = new Date();
  while (openedDays.has(utcDateKey(cursor))) {
    streak++;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return streak;
}

async function completedMissions(testerId: string): Promise<Set<string>> {
  const result = await assertOk(
    supabase.from("beta_events")
      .select("mission_id")
      .eq("tester_id", testerId)
      .not("mission_id", "is", null),
  );
  return new Set((result.data ?? []).map((row) => String(row.mission_id)));
}

async function hasFeedbackToday(testerId: string): Promise<boolean> {
  const today = utcDateKey(new Date());
  const result = await assertOk(
    supabase.from("beta_events")
      .select("id")
      .eq("tester_id", testerId)
      .eq("event_type", "feedback_sent")
      .eq("dedupe_key", `feedback_sent:${today}`)
      .limit(1),
  );
  return (result.data ?? []).length > 0;
}

async function ensureTester(testerId: string) {
  await assertOk(
    supabase.from("beta_testers").upsert(
      {
        id: testerId,
        updated_at: new Date().toISOString(),
      },
      { onConflict: "id" },
    ),
  );
}

async function awardEvent(row: Record<string, unknown>): Promise<boolean> {
  const { error } = await supabase.from("beta_events").insert(row);
  if (!error) return true;
  if (error.code === "23505") return false;
  throw new Error(error.message);
}

async function assertOk<T extends { error: { message: string } | null }>(
  promise: Promise<T>,
): Promise<T> {
  const result = await promise;
  if (result.error) throw new Error(result.error.message);
  return result;
}

async function readJson(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      throw new Error("body must be an object");
    }
    return body as Record<string, unknown>;
  } catch (_error) {
    throw new HttpError(400, "Invalid JSON body");
  }
}

function routeAction(url: URL): string {
  const parts = url.pathname.split("/").filter(Boolean);
  const betaIndex = parts.lastIndexOf("beta");
  if (betaIndex < 0) return "";
  return parts[betaIndex + 1] ?? "";
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  });
}

function requireUuid(value: unknown, field: string): string {
  const text = stringOrEmpty(value);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(text)) {
    throw new HttpError(400, `${field} must be a UUID`);
  }
  return text;
}

function normalizeDisplayName(value: unknown): string | null {
  const text = stringOrEmpty(value).trim();
  return text ? text.slice(0, 20) : null;
}

function stringOrEmpty(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function stringOrNull(value: unknown): string | null {
  const text = stringOrEmpty(value);
  return text ? text.slice(0, 80) : null;
}

function requireFeedbackType(value: unknown): string {
  const text = stringOrEmpty(value);
  const allowed = new Set([
    "bug",
    "crash",
    "ux_issue",
    "confusing_flow",
    "performance",
    "feature_request",
    "other",
  ]);
  if (!allowed.has(text)) {
    throw new HttpError(400, "type is invalid");
  }
  return text;
}

function utcDateKey(date: Date): string {
  return date.toISOString().slice(0, 10);
}

class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}
