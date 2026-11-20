-- P0 #1268: READ-ONLY operator preflight. No terminalization, no side effects.
-- Run only as a read-only transaction, with an authorized incident operator.
-- Never use this output alone to cancel a real order.
BEGIN READ ONLY;
-- Keep the entire snapshot consistent while concurrent dispatch is running.
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET LOCAL statement_timeout = '10s';
WITH overdue AS (
 SELECT o.id, o.market_order_id, o.status, o.payment_status, o.payment_method, o.paid_at, o.cancelled_at, o.rider_assigned_at,
        o.merchant_accepted_at, o.rider_search_started_at, o.created_at,
        COALESCE(o.rider_search_started_at,o.merchant_accepted_at,o.created_at) AS search_start
 FROM public.orders o
 WHERE o.status='searching_rider' AND o.rider_id IS NULL
), facts AS (
 SELECT o.*,
        EXISTS (SELECT 1 FROM public.queuego_cash_order_locks l WHERE l.order_id=o.id) AS cash_lock_exists,
        EXISTS (SELECT 1 FROM public.qg_rider_order_offers f WHERE f.order_id=o.id) AS live_offer_row_exists,
        EXISTS (SELECT 1 FROM public.payments p WHERE p.order_id=o.id) AS payment_ledger_exists,
        EXISTS (SELECT 1 FROM public.merchant_cash_receipts r WHERE r.order_id=o.id) AS merchant_cash_receipt_exists,
        EXISTS (SELECT 1 FROM public.rider_cash_advances a WHERE a.order_id=o.id) AS rider_cash_advance_exists,
        EXISTS (SELECT 1 FROM public.qg_rider_cash_return_refunds r WHERE r.order_id=o.id) AS rider_cash_refund_exists,
        EXISTS (SELECT 1 FROM public.grocery_stock_reservations g WHERE g.order_id=o.id) AS grocery_reservation_exists,
        EXISTS (SELECT 1 FROM public.grocery_stock_reservations g WHERE g.order_id=o.id AND g.released IS DISTINCT FROM true) AS unreleased_grocery_reservation_exists,
        EXISTS (SELECT 1 FROM public.pos_inventory_reservations p WHERE p.order_id=o.id) AS pos_reservation_exists,
        EXISTS (SELECT 1 FROM public.pos_inventory_reservations p WHERE p.order_id=o.id AND p.released IS DISTINCT FROM true) AS unreleased_pos_reservation_exists,
        EXISTS (SELECT 1 FROM public.payments p WHERE p.order_id=o.id AND (p.status IS NULL OR lower(p.status) NOT IN ('failed','cancelled','canceled'))) AS payment_requires_reconciliation,
        (SELECT count(*) FROM public.qg_rider_offer_history h WHERE h.order_id=o.id) AS offer_history_count,
        (SELECT count(*) FROM public.orders sibling WHERE sibling.market_order_id=o.market_order_id AND sibling.id<>o.id AND sibling.status IS DISTINCT FROM 'cancelled') AS active_market_siblings
 FROM overdue o
)
SELECT count(*) AS searching_orders,
       count(*) FILTER (WHERE search_start < now()-interval '30 minutes') AS overdue_at_30m,
       count(*) FILTER (WHERE search_start IS NULL) AS unknown_search_start,
       count(*) FILTER (WHERE search_start > now()) AS future_search_start,
       count(*) FILTER (WHERE cash_lock_exists) AS with_cash_lock,
       count(*) FILTER (WHERE payment_ledger_exists) AS with_payment_ledger,
       count(*) FILTER (WHERE merchant_cash_receipt_exists) AS with_merchant_cash_receipt,
       count(*) FILTER (WHERE rider_cash_advance_exists) AS with_rider_cash_advance,
       count(*) FILTER (WHERE rider_cash_refund_exists) AS with_rider_cash_refund,
       count(*) FILTER (WHERE grocery_reservation_exists) AS with_grocery_reservation,
       count(*) FILTER (WHERE pos_reservation_exists) AS with_pos_reservation,
       count(*) FILTER (WHERE unreleased_grocery_reservation_exists) AS with_unreleased_grocery_reservation,
       count(*) FILTER (WHERE unreleased_pos_reservation_exists) AS with_unreleased_pos_reservation,
       count(*) FILTER (WHERE payment_requires_reconciliation) AS with_payment_requiring_reconciliation,
       count(*) FILTER (WHERE payment_status IS DISTINCT FROM 'unpaid') AS payment_needs_review,
       count(*) FILTER (WHERE payment_status IS NULL) AS unknown_payment_status,
       count(*) FILTER (WHERE payment_method IS NULL OR btrim(payment_method) = '') AS unknown_payment_method,
       count(*) FILTER (WHERE paid_at IS NOT NULL) AS with_paid_at,
       count(*) FILTER (WHERE rider_assigned_at IS NOT NULL) AS previously_assigned,
       count(*) FILTER (WHERE cancelled_at IS NOT NULL) AS contradictory_cancelled_timestamp,
       count(*) FILTER (WHERE market_order_id IS NOT NULL) AS market_group_needs_review,
       count(*) FILTER (WHERE live_offer_row_exists) AS with_offer_row,
       count(*) FILTER (WHERE search_start < now()-interval '30 minutes' AND search_start IS NOT NULL AND search_start <= now() AND payment_status='unpaid' AND payment_method IS NOT NULL AND btrim(payment_method)<>'' AND paid_at IS NULL AND rider_assigned_at IS NULL AND cancelled_at IS NULL AND NOT cash_lock_exists AND NOT live_offer_row_exists AND NOT payment_ledger_exists AND NOT merchant_cash_receipt_exists AND NOT rider_cash_advance_exists AND NOT rider_cash_refund_exists AND NOT grocery_reservation_exists AND NOT pos_reservation_exists AND active_market_siblings=0 AND market_order_id IS NULL) AS financial_preflight_only_candidates,
       count(*) FILTER (WHERE search_start < now()-interval '30 minutes' AND (cash_lock_exists OR payment_ledger_exists OR merchant_cash_receipt_exists OR rider_cash_advance_exists OR rider_cash_refund_exists OR grocery_reservation_exists OR pos_reservation_exists OR payment_status IS DISTINCT FROM 'unpaid' OR payment_method IS NULL OR btrim(payment_method) = '' OR paid_at IS NOT NULL OR rider_assigned_at IS NOT NULL OR cancelled_at IS NOT NULL OR live_offer_row_exists OR active_market_siblings>0 OR market_order_id IS NOT NULL)) AS overdue_blocked_for_manual_review,
       coalesce(max(offer_history_count),0) AS max_offer_history,
       count(*) FILTER (WHERE active_market_siblings>0) AS with_active_siblings
FROM facts;
COMMIT;
-- Candidate and blocked counts are intentionally disjoint for the financial flags;
-- any merchant receipt, rider cash advance/refund, or market group is fail-closed.
-- financial_preflight_only_candidates is NOT authorization to cancel: stock movement,
-- merchant notification outbox, settlement, and payment gateway state remain unverified.
-- This is an aggregate-only snapshot, NOT proof that a cancellation is safe.
-- A NULL/blank payment_method is always manual-review: Production legacy rows
-- do not reliably populate it, so unpaid status alone cannot prove cash safety.
-- Stock reservation, payment processor captures/refunds, merchant notification
-- and settlement ledgers must be separately reconciled before any write.
