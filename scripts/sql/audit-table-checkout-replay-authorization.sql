-- Read-only structural regression contract; run against an isolated migrated DB.
-- Runtime replay, revocation, expiry, and concurrency tests are still required.
DO $audit$
DECLARE
  body text;
  lock_pos integer;
  guard_pos integer;
  replay_pos integer;
  duplicate_pos integer;
BEGIN
  SELECT lower(pg_get_functiondef(
    'public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision)'::regprocedure
  )) INTO body;

  lock_pos := strpos(body, 'from public.qg_table_sessions');
  guard_pos := strpos(body, 'if v.revoked_at is not null or now()>=v.expires_at then');
  replay_pos := strpos(body, 'from public.qg_table_requests');
  duplicate_pos := strpos(body, '''duplicate'',true');

  IF lock_pos = 0 OR guard_pos = 0 OR replay_pos = 0 OR duplicate_pos = 0 THEN
    RAISE EXCEPTION 'QR checkout required authorization or replay markers missing';
  END IF;
  IF NOT (lock_pos < guard_pos AND guard_pos < replay_pos AND replay_pos < duplicate_pos) THEN
    RAISE EXCEPTION 'QR checkout must lock and authorize session before idempotent replay';
  END IF;
  IF strpos(substring(body FROM lock_pos FOR guard_pos-lock_pos), 'for update') = 0 THEN
    RAISE EXCEPTION 'QR checkout session row must be locked before authorization';
  END IF;
  IF strpos(substring(body FROM lock_pos FOR guard_pos-lock_pos), 'device_hash=') = 0 THEN
    RAISE EXCEPTION 'QR checkout must bind session to device hash';
  END IF;
END $audit$;
