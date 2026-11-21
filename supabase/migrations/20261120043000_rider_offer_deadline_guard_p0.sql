-- P0 #1268: REVIEW-ONLY forward migration. DO NOT APPLY TO PRODUCTION WITHOUT APPROVAL.
-- Mirrors reviewed ops/p0/rider-offer-deadline-guard.sql at integration source.
-- This guard suppresses new stale offers; it does NOT terminalize old orders,
-- refund payments, release cash locks, or restore stock. Incident remains OPEN.
-- Preflight: encrypted backup + restore proof, operator approval, privilege review,
-- migration-history parity and isolated PostgreSQL dispatch/notification tests.
-- Rollback: restore reviewed prior trigger/function definition in a NEW migration,
-- not a destructive down migration or data rewrite.
DO $preflight$
BEGIN
  IF to_regclass('public.orders') IS NULL
     OR to_regclass('public.qg_rider_order_offers') IS NULL
     OR to_regclass('public.system_settings') IS NULL
     OR to_regnamespace('qg_private') IS NULL THEN
    RAISE EXCEPTION 'P0 dispatch guard prerequisites missing; refusing migration';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='orders'
      AND column_name='rider_search_started_at'
  ) THEN
    RAISE EXCEPTION 'P0 dispatch guard requires orders.rider_search_started_at';
  END IF;
END
$preflight$;

-- QueueGo P0 candidate (source-only; NOT deployed): bounded Rider offer creation.
-- Backend Worker 2 must use 'supabase migration new' for a reviewed migration,
-- revalidate deployed function/trigger privileges, and verify financial terminal cleanup.
--
-- This guard deliberately does NOT change order status or cash/stock/settlement data.
-- It prevents new stale offers (and, because qg_dispatch_rider_offers() only
-- notifies when its INSERT FOUND a row, prevents further stale notifications).
-- The existing stranded order must be resolved through authorized operations.
--
-- Admin: system_settings.key='app', value.orderTimeout is minutes (currently 30).
-- Searching clock starts at rider_search_started_at, falling back to merchant
-- acceptance then creation when prior records lack a search start.
CREATE OR REPLACE FUNCTION qg_private.guard_rider_offer_deadline()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO ''
AS $qg$
DECLARE
  v_order record;
  v_timeout_raw text;
  v_timeout_minutes integer := 30;
  v_deadline timestamptz;
BEGIN
  SELECT o.status,
         o.rider_id,
         COALESCE(o.rider_search_started_at, o.merchant_accepted_at, o.created_at) AS started_at
    INTO v_order
    FROM public.orders o
   WHERE o.id = NEW.order_id;

  IF NOT FOUND
     OR v_order.status IS DISTINCT FROM 'searching_rider'
     OR v_order.rider_id IS NOT NULL
     OR v_order.started_at IS NULL
     OR v_order.started_at > now() THEN
    RETURN NULL;
  END IF;

  SELECT s.value->>'orderTimeout'
    INTO v_timeout_raw
    FROM public.system_settings s
   WHERE s.key = 'app';

  -- Missing, malformed and out-of-bounds settings use a finite 30-minute
  -- default; never interpret invalid configuration as unlimited dispatch.
  IF v_timeout_raw ~ '^[0-9]{1,4}$' THEN
    IF v_timeout_raw::integer BETWEEN 1 AND 1440 THEN
      v_timeout_minutes := v_timeout_raw::integer;
    END IF;
  END IF;

  -- Independent hard cap: a misconfigured long timeout must not allow
  -- unlimited retry history or notification amplification.
  IF NEW.attempt IS NULL OR NEW.attempt < 1 OR NEW.attempt > 120 THEN
    RETURN NULL;
  END IF;

  v_deadline := v_order.started_at + make_interval(mins => v_timeout_minutes);
  IF now() >= v_deadline THEN
    RETURN NULL;
  END IF;

  -- PostgreSQL LEAST ignores NULL arguments: reject missing inputs BEFORE
  -- clamping, so NULL never silently becomes the full order search deadline.
  IF NEW.offered_at IS NULL OR NEW.expires_at IS NULL THEN
    RETURN NULL;
  END IF;

  -- A future caller timestamp must not turn a 30-second offer into minutes
  -- of actionable work. The captured dispatcher uses the same transaction now().
  IF NEW.offered_at > now() THEN
    RETURN NULL;
  END IF;

  -- Keep every Rider offer <= 30 seconds even with a malformed upstream TTL,
  -- and never notify about a queued offer already expired at server time.
  NEW.expires_at := LEAST(
    NEW.expires_at,
    NEW.offered_at + interval '30 seconds',
    v_deadline
  );
  IF NEW.expires_at <= NEW.offered_at OR NEW.expires_at <= now() THEN
    RETURN NULL;
  END IF;
  RETURN NEW;
END;
$qg$;

REVOKE ALL ON FUNCTION qg_private.guard_rider_offer_deadline()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS qg_rider_offer_deadline_guard ON public.qg_rider_order_offers;
CREATE TRIGGER qg_rider_offer_deadline_guard
  BEFORE INSERT ON public.qg_rider_order_offers
  FOR EACH ROW EXECUTE FUNCTION qg_private.guard_rider_offer_deadline();
