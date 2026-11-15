-- Read-only regression contract for QR checkout replay authorization.
DO $audit$
DECLARE body text; replay_pos integer; guard_pos integer;
BEGIN
 SELECT pg_get_functiondef('public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision)'::regprocedure) INTO body;
 replay_pos := strpos(body, 'select * into v_old');
 guard_pos := strpos(body, 'if v.revoked_at is not null or now()>=v.expires_at then');
 IF guard_pos = 0 OR replay_pos = 0 OR guard_pos > replay_pos THEN
  RAISE EXCEPTION 'QR session expiry/revocation guard must precede request replay lookup';
 END IF;
END $audit$;
