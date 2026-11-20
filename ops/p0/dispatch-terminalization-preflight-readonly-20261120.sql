-- P0 #1268: READ-ONLY operator preflight. No terminalization, no side effects.
-- Run only as a read-only transaction, with an authorized incident operator.
-- Never use this output alone to cancel a real order.
BEGIN READ ONLY;
-- Keep the entire snapshot consistent while concurrent dispatch is running.
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET LOCAL statement_timeout = '10s';
WITH overdue AS (
 SELECT o.id, o.market_order_id, o.status, o.payment_status, o.payment_method,
        o.merchant_accepted_at, o.rider_search_started_at, o.created_at,
        COALESCE(o.rider_search_started_at,o.merchant_accepted_at,o.created_at) AS search_start
 FROM public.orders o
 WHERE o.status='searching_rider' AND o.rider_id IS NULL
), facts AS (
 SELECT o.*,
        EXISTS (SELECT 1 FROM public.queuego_cash_order_locks l WHERE l.order_id=o.id) AS cash_lock_exists,
        EXISTS (SELECT 1 FROM public.qg_rider_order_offers f WHERE f.order_id=o.id) AS live_offer_row_exists,
        (SELECT count(*) FROM public.qg_rider_offer_history h WHERE h.order_id=o.id) AS offer_history_count,
        (SELECT count(*) FROM public.orders sibling WHERE sibling.market_order_id=o.market_order_id AND sibling.id<>o.id AND sibling.status NOT IN ('cancelled')) AS active_market_siblings
 FROM overdue o
)
SELECT count(*) AS searching_orders,
       count(*) FILTER (WHERE search_start < now()-interval '30 minutes') AS overdue_at_30m,
       count(*) FILTER (WHERE search_start IS NULL) AS unknown_search_start,
       count(*) FILTER (WHERE search_start > now()) AS future_search_start,
       count(*) FILTER (WHERE cash_lock_exists) AS with_cash_lock,
       count(*) FILTER (WHERE payment_status IS DISTINCT FROM 'unpaid') AS payment_needs_review,
       count(*) FILTER (WHERE payment_status IS NULL) AS unknown_payment_status,
       count(*) FILTER (WHERE market_order_id IS NOT NULL) AS market_group_needs_review,
       count(*) FILTER (WHERE live_offer_row_exists) AS with_offer_row,
       count(*) FILTER (WHERE search_start < now()-interval '30 minutes' AND (cash_lock_exists OR payment_status IS DISTINCT FROM 'unpaid' OR live_offer_row_exists OR active_market_siblings>0)) AS overdue_blocked_for_manual_review,
       coalesce(max(offer_history_count),0) AS max_offer_history,
       count(*) FILTER (WHERE active_market_siblings>0) AS with_active_siblings
FROM facts;
COMMIT;
-- This is an aggregate-only snapshot, NOT proof that a cancellation is safe.
-- Stock reservation, payment processor captures/refunds, merchant notification
-- and settlement ledgers must be separately reconciled before any write.
