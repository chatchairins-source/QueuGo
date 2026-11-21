-- P0 #1268 — read-only deployment-component gate.
-- This deliberately never reports PASS merely because no orders are searching.
-- Presence/enablement is necessary, NOT sufficient: cron scheduling, privileges,
-- real financial settlement, backup/restore and postdeployment negative tests
-- still require independent operator attestation.
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
    ) AS offer_guard_trigger_enabled,
    to_regprocedure('qg_private.cancel_rider_timeout_cash(integer)') IS NOT NULL
      AS cash_timeout_function_present
)
SELECT *,
  CASE
    WHEN offer_guard_function_present
      AND offer_guard_trigger_enabled
      AND cash_timeout_function_present
    THEN 'OPERATOR_ATTESTATION_REQUIRED'
    ELSE 'NO_GO_DISPATCH_P0'
  END AS release_gate
FROM installed;
