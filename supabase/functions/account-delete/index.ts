import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.95.0";

const url = Deno.env.get("SUPABASE_URL")!;
const secretKeys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}");
const secretKey = secretKeys.default || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if (!secretKey) throw new Error("Supabase secret key unavailable");

const admin = createClient(url, secretKey, {
  auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false }
});

const allowedOrigins = new Set([
  "https://chatchairins-source.github.io",
  "http://localhost",
  "https://localhost",
  "capacitor://localhost"
]);

function cors(origin: string | null) {
  const safe = origin && allowedOrigins.has(origin)
    ? origin
    : "https://chatchairins-source.github.io";
  return {
    "Access-Control-Allow-Origin": safe,
    "Vary": "Origin",
    "Access-Control-Allow-Headers": "authorization,apikey,content-type",
    "Access-Control-Allow-Methods": "POST,OPTIONS"
  };
}

async function removePrefix(bucket: string, prefix: string, depth = 0): Promise<number> {
  if (depth > 5) throw new Error("Storage path depth unavailable");
  let removed = 0;
  while (true) {
    const { data, error } = await admin.storage.from(bucket).list(prefix, {
      limit: 100,
      offset: 0,
      sortBy: { column: "name", order: "asc" }
    });
    if (error) {
      // A missing/non-readable empty bucket must not block account deletion.
      if (/not found/i.test(error.message || "")) return removed;
      throw error;
    }
    const rows = data || [];
    const files: string[] = [];
    for (const item of rows) {
      const path = prefix ? `${prefix}/${item.name}` : item.name;
      if (item.id) files.push(path);
      else removed += await removePrefix(bucket, path, depth + 1);
    }
    if (files.length) {
      const result = await admin.storage.from(bucket).remove(files);
      if (result.error) throw result.error;
      removed += files.length;
    }
    if (!rows.length) break;
  }
  return removed;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const headers = cors(origin);
  const respond = (body: unknown, status = 200) =>
    Response.json(body, { status, headers });

  if (origin && !allowedOrigins.has(origin)) return respond({ error: "ORIGIN_NOT_ALLOWED" }, 403);
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
  if (req.method !== "POST") return respond({ error: "POST_REQUIRED" }, 405);

  try {
    const body = await req.json().catch(() => ({}));
    if (body?.confirm !== "DELETE_ACCOUNT") {
      return respond({ error: "CONFIRMATION_REQUIRED" }, 400);
    }

    const token = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
    if (!token) return respond({ error: "LOGIN_REQUIRED" }, 401);

    const authResult = await admin.auth.getUser(token);
    const authUser = authResult.data.user;
    if (authResult.error || !authUser) return respond({ error: "LOGIN_REQUIRED" }, 401);

    // If DB deletion completed but Auth deletion previously failed, safely resume only
    // the remaining Auth cleanup instead of running the data scrub twice.
    const prior = await admin
      .from("qg_account_deletion_requests")
      .select("id,state")
      .eq("auth_user_id", authUser.id)
      .eq("state", "completed")
      .order("completed_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (prior.error) throw prior.error;
    if (prior.data) {
      const authDelete = await admin.auth.admin.deleteUser(authUser.id);
      if (authDelete.error) throw authDelete.error;
      await admin
        .from("qg_account_deletion_requests")
        .update({ auth_user_id: null })
        .eq("id", prior.data.id);
      return respond({ ok: true, resumed: true });
    }

    const eligibility = await admin.rpc("queuego_account_deletion_eligibility", {
      p_auth_user_id: authUser.id
    });
    if (eligibility.error) throw eligibility.error;
    if (!eligibility.data?.eligible) {
      const code = eligibility.data?.code || "ACTIVE_WORK";
      const status = code === "ADMIN_ACCOUNT" ? 403 : code === "ACCOUNT_NOT_FOUND" ? 404 : 409;
      return respond({
        error: code,
        blockers: eligibility.data?.blockers || 0,
        details: eligibility.data || {}
      }, status);
    }

    const begin = await admin.rpc("queuego_account_deletion_begin", {
      p_auth_user_id: authUser.id
    });
    if (begin.error) throw begin.error;
    const requestId = begin.data?.request_id;
    if (!requestId) throw new Error("Deletion request unavailable");

    try {
      // QueueGo currently stores owned media below <auth-user-id>/... .
      // Use Storage API removal so object metadata and backing files are both removed.
      await Promise.all([
        removePrefix("merchant-media", authUser.id),
        removePrefix("qg-evidence", authUser.id),
        removePrefix("gp-slips", authUser.id)
      ]);
    } catch (storageError) {
      await admin.rpc("queuego_account_deletion_abort", { p_request_id: requestId });
      throw storageError;
    }

    const finalize = await admin.rpc("queuego_account_deletion_finalize", {
      p_request_id: requestId,
      p_auth_user_id: authUser.id
    });
    if (finalize.error) {
      await admin.rpc("queuego_account_deletion_abort", { p_request_id: requestId });
      throw finalize.error;
    }

    try {
      await admin.auth.admin.signOut(token);
    } catch (_) {
      // The public-user tombstone already revokes app authorization; continue with Auth deletion.
    }
    const authDelete = await admin.auth.admin.deleteUser(authUser.id);
    if (authDelete.error) throw authDelete.error;

    await admin
      .from("qg_account_deletion_requests")
      .update({ auth_user_id: null })
      .eq("id", requestId);

    return respond({ ok: true });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error || "");
    if (message.includes("ACCOUNT_HAS_ACTIVE_WORK")) {
      return respond({ error: "ACTIVE_WORK" }, 409);
    }
    return respond({ error: "ACCOUNT_DELETE_FAILED" }, 503);
  }
});
