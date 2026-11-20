-- REVIEW ONLY. Not a migration, not scheduled, not deployed.
-- P0 #1268: preserve the current cancellation helper while covering the
-- supported no_rider_available terminal state for a narrowly verified cash bill.
-- Compare deployed function/ACL and all order triggers before migration creation.
-- Baseline function MD5: 8da535eedbd36995dcd259c3474fd201 (2026-11-20 read-only).
-- This does not authorize or initiate any order transition, refund, cash transfer,
-- reservation release, notification or deletion. Keep cash-order guard markers.
DO $prerequisite$
BEGIN
  IF to_regprocedure('public.queuego_cancel_cash_dependents()') IS NULL THEN
    RAISE EXCEPTION 'existing cash cancellation helper required';
  END IF;
END
$prerequisite$;

CREATE OR REPLACE FUNCTION public.queuego_cancel_cash_dependents()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
AS $function$
BEGIN
  IF OLD.status IS NOT DISTINCT FROM NEW.status
     OR (NEW.status IS DISTINCT FROM 'cancelled' AND NEW.status IS DISTINCT FROM 'no_rider_available')
     OR NOT EXISTS(SELECT 1 FROM public.queuego_cash_order_locks WHERE order_id=NEW.id) THEN
    RETURN NEW;
  END IF;

  IF NEW.status='no_rider_available' THEN
    -- Upgrade the parent lock before reading children. Live payments have an
    -- immediate order FK: concurrent ledger inserts hold KEY SHARE on this row.
    -- FOR UPDATE waits for those writers before we count/validate their rows.
    PERFORM 1 FROM public.orders WHERE id=NEW.id FOR UPDATE;
    -- Existing child rows must also be locked against concurrent capture.
    PERFORM 1 FROM public.payments WHERE order_id=NEW.id FOR UPDATE;
    PERFORM 1 FROM public.deliveries WHERE order_id=NEW.id FOR UPDATE;
    -- A deadline scheduler must independently own/lock/authorize the transition.
    -- Unknown state fails atomically instead of turning a captured payment into
    -- a database-only refund. Market and stock lifecycle remain separately gated.
    IF OLD.status IS DISTINCT FROM 'searching_rider'
       OR OLD.rider_id IS NOT NULL OR NEW.rider_id IS NOT NULL
       OR OLD.rider_assigned_at IS NOT NULL OR NEW.rider_assigned_at IS NOT NULL
       OR OLD.market_order_id IS NOT NULL OR NEW.market_order_id IS NOT NULL
       OR OLD.paid_at IS NOT NULL OR NEW.paid_at IS NOT NULL
       OR NEW.total_amount IS DISTINCT FROM OLD.total_amount
       OR (SELECT count(*) FROM public.payments WHERE order_id=NEW.id) <> 1
       OR (SELECT count(*) FROM public.payments p WHERE p.order_id=NEW.id
           AND p.payment_method='cash' AND p.status='pending'
           AND p.paid_at IS NULL AND NULLIF(btrim(p.transaction_reference),'') IS NULL
           AND p.amount=NEW.total_amount AND p.amount>=0
           AND p.amount::text NOT IN ('NaN','Infinity','-Infinity')) <> 1
       OR EXISTS(SELECT 1 FROM public.deliveries WHERE order_id=NEW.id AND status IS DISTINCT FROM 'pending')
       OR EXISTS(SELECT 1 FROM public.merchant_cash_receipts WHERE order_id=NEW.id)
       OR EXISTS(SELECT 1 FROM public.rider_cash_advances WHERE order_id=NEW.id)
       OR EXISTS(SELECT 1 FROM public.qg_rider_cash_return_refunds WHERE order_id=NEW.id)
       OR EXISTS(SELECT 1 FROM public.grocery_stock_reservations WHERE order_id=NEW.id)
       OR EXISTS(SELECT 1 FROM public.pos_inventory_reservations WHERE order_id=NEW.id) THEN
      RAISE EXCEPTION 'cash timeout requires financial lifecycle review';
    END IF;
  END IF;

  -- Preserve the current ordinary cancellation semantics and update predicates.
  UPDATE public.payments SET status='cancelled',updated_at=now()
    WHERE order_id=NEW.id AND payment_method='cash' AND status='pending';
  UPDATE public.deliveries SET status='cancelled',updated_at=now()
    WHERE order_id=NEW.id AND status NOT IN ('delivered','cancelled');
  RETURN NEW;
END
$function$;
