-- Read-only/aggregate P0 Rider dispatch release gate (safe for authorized QA).
-- No order IDs, customer/rider IDs, addresses or personal data in result.
-- Run on a dedicated QA/staging connection; never use this query to modify live orders.
-- Expected release criterion: overdue_searching_orders = 0, late_active_offers = 0,
-- and no newly targeted offers to orders whose search window has expired.
WITH timeout_config AS (
  SELECT COALESCE(
    (SELECT CASE
      WHEN (value->>'orderTimeout') ~ '^[0-9]{1,4}$'
       AND (value->>'orderTimeout')::integer BETWEEN 1 AND 1440
      THEN (value->>'orderTimeout')::integer
      ELSE NULL
    END FROM public.system_settings WHERE key='app'),
    30
  ) AS timeout_minutes
),
searching AS (
  SELECT o.id,
         COALESCE(o.rider_search_started_at, o.merchant_accepted_at, o.created_at) AS started_at,
         c.timeout_minutes,
         COALESCE(o.rider_search_started_at, o.merchant_accepted_at, o.created_at)
           + make_interval(mins => c.timeout_minutes) AS deadline
  FROM public.orders o
  CROSS JOIN timeout_config c
  WHERE o.status='searching_rider' AND o.rider_id IS NULL
),
overdue AS (
  SELECT id, started_at, deadline
  FROM searching
  WHERE deadline IS NULL OR now() >= deadline
),
stats AS (
  SELECT
    (SELECT timeout_minutes FROM timeout_config) AS configured_timeout_minutes,
    (SELECT count(*) FROM searching)::integer AS searching_orders,
    (SELECT count(*) FROM overdue)::integer AS overdue_searching_orders,
    (SELECT count(*) FROM public.qg_rider_order_offers x
      JOIN overdue o ON o.id=x.order_id)::integer AS late_active_offers,
    (SELECT count(*) FROM public.qg_rider_offer_history x
      JOIN overdue o ON o.id=x.order_id)::bigint AS overdue_historical_offers,
    (SELECT count(*) FROM public.qg_rider_offer_history x
      JOIN overdue o ON o.id=x.order_id
      WHERE x.offered_at >= now()-interval '1 hour')::integer AS overdue_offers_last_hour,
    (SELECT count(*) FROM public.notifications n
      JOIN overdue o ON o.id=n.reference_id
      WHERE n.created_at >= now()-interval '1 hour')::integer AS overdue_notifications_last_hour,
    (SELECT count(*) FROM public.qg_rider_offer_history x
      JOIN overdue o ON o.id=x.order_id
      WHERE x.attempt > 120)::integer AS overdue_offer_attempts_above_120
)
SELECT *,
  CASE
    WHEN overdue_searching_orders>0 OR late_active_offers>0
      OR overdue_offers_last_hour>0 OR overdue_notifications_last_hour>0
      OR overdue_offer_attempts_above_120>0
    THEN 'NO_GO_DISPATCH_P0'
    ELSE 'PASS_DISPATCH_OVERTIME'
  END AS release_gate
FROM stats;
