-- P0 #1268 — read-only deployment-component gate.
-- This deliberately never reports PASS merely because no orders are searching.
-- This read-only gate also requires an exact active trusted Cron job.
-- Presence is NOT sufficient: real Cron execution, privileges, financial
-- settlement, restore proof and operator attestation remain independent gates.
WITH installed AS (
  SELECT
    to_regprocedure('qg_private.guard_rider_offer_deadline()') IS NOT NULL
      AS offer_guard_function_present,
    EXISTS (
      SELECT 1 FROM pg_catalog.pg_trigger t
      WHERE t.tgrelid = to_regclass('public.qg_rider_order_offers')
        AND t.tgname = 'qg_rider_offer_deadline_guard'
        AND NOT t.tgisinternal
        AND t.tgenabled IN ('O','A')
        AND t.tgfoid = to_regprocedure('qg_private.guard_rider_offer_deadline()')
        -- PostgreSQL tgtype=7 is precisely BEFORE INSERT FOR EACH ROW:
        -- row(1) | before(2) | insert(4), with no other firing events.
        AND t.tgtype = 7
        -- A WHEN clause can silently make an enabled guard skip every row.
        AND t.tgqual IS NULL
    ) AS offer_guard_trigger_enabled,
    to_regprocedure('qg_private.cancel_rider_timeout_cash(integer)') IS NOT NULL
      AS cash_timeout_function_present
), scheduled AS (
  SELECT
    -- Fail closed for cron disabled/wrong role/interval/command or shadow jobs.
    COUNT(*) FILTER (WHERE command ILIKE '%cancel_rider_timeout_cash%') = 1
      AND COUNT(*) FILTER (WHERE jobname = 'queuego-rider-cash-timeout') = 1
      AND COUNT(*) FILTER (
        WHERE jobname = 'queuego-rider-cash-timeout'
          AND active IS TRUE
          AND username = 'postgres'
          -- A job pointed at another database cannot terminalize these orders.
          AND database = current_database()
          AND schedule = '* * * * *'
          AND command = 'SELECT qg_private.cancel_rider_timeout_cash(30);'
      ) = 1 AS cash_timeout_cron_active
  FROM cron.job
)
SELECT installed.*, scheduled.cash_timeout_cron_active,
  CASE
    WHEN offer_guard_function_present
      AND offer_guard_trigger_enabled
      AND cash_timeout_function_present
      AND scheduled.cash_timeout_cron_active
    THEN 'OPERATOR_ATTESTATION_REQUIRED'
    ELSE 'NO_GO_DISPATCH_P0'
  END AS release_gate
FROM installed CROSS JOIN scheduled;
