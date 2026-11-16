-- Read-only structural regression contract; run against an isolated migrated DB.
-- Runtime replay, revocation, expiry, and concurrency tests are still required.
DO $audit$
DECLARE
  body text;
  lock_pos integer;
  guard_pos integer;
  replay_pos integer;
  duplicate_pos integer;
  payload_guard_pos integer;
  recent_cart_pos integer;
  order_insert_pos integer;
BEGIN
  SELECT lower(pg_get_functiondef(
    'public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision)'::regprocedure
  )) INTO body;

  lock_pos := strpos(body, 'from public.qg_table_sessions');
  guard_pos := strpos(body, 'if v.revoked_at is not null or now()>=v.expires_at then');
  replay_pos := strpos(body, 'from public.qg_table_requests');
  duplicate_pos := strpos(body, '''duplicate'',true');
  payload_guard_pos := strpos(body, 'if v_old.payload_hash<>v_hash then');
  recent_cart_pos := strpos(body, 'created_at>now()-interval ''8 seconds''');
  order_insert_pos := strpos(body, 'insert into public.orders(');

  IF lock_pos = 0 OR guard_pos = 0 OR replay_pos = 0 OR duplicate_pos = 0 THEN
    RAISE EXCEPTION 'QR checkout required authorization or replay markers missing';
  END IF;
  IF NOT (lock_pos < guard_pos AND guard_pos < replay_pos AND replay_pos < duplicate_pos) THEN
    RAISE EXCEPTION 'QR checkout must lock and authorize session before idempotent replay';
  END IF;
  IF payload_guard_pos = 0 OR recent_cart_pos = 0 OR order_insert_pos = 0
     OR NOT (replay_pos < payload_guard_pos AND payload_guard_pos < duplicate_pos
             AND guard_pos < recent_cart_pos AND recent_cart_pos < order_insert_pos) THEN
    RAISE EXCEPTION 'QR checkout must reject mismatched request replay and authorize recent-cart deduplication';
  END IF;
  IF strpos(substring(body FROM lock_pos FOR guard_pos-lock_pos), 'for update') = 0 THEN
    RAISE EXCEPTION 'QR checkout session row must be locked before authorization';
  END IF;
  IF strpos(substring(body FROM lock_pos FOR guard_pos-lock_pos), 'device_hash=') = 0 THEN
    RAISE EXCEPTION 'QR checkout must bind session to device hash';
  END IF;
END $audit$;

-- Structural prerequisite for race-safe request-id deduplication.
-- The session row lock serializes checkouts per session, while the primary key
-- must independently reject a duplicate (session_id, request_id) write.
DO $constraints$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c
    WHERE c.conrelid='public.qg_table_requests'::regclass
      AND c.contype='p'
      AND (SELECT array_agg(a.attname ORDER BY k.ord)
           FROM unnest(c.conkey) WITH ORDINALITY k(attnum,ord)
           JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum)
          = ARRAY['session_id','request_id']::name[]
  ) THEN
    RAISE EXCEPTION 'QR checkout request idempotency requires (session_id, request_id) primary key';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c
    WHERE c.conrelid='public.qg_table_requests'::regclass
      AND c.contype='f' AND c.confrelid='public.orders'::regclass
  ) THEN
    RAISE EXCEPTION 'QR checkout request must reference an existing order';
  END IF;
END $constraints$;
