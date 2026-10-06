-- QueueGo: fully retire delivery PIN generation after switching Rider proof to photos only.
BEGIN;

DROP TRIGGER IF EXISTS qg_issue_pin_on_assignment ON public.orders;
DROP TRIGGER IF EXISTS trg_qg_market_sync_delivery_pin ON public.orders;

REVOKE EXECUTE ON FUNCTION public.qg_customer_delivery_pin(uuid) FROM authenticated;
REVOKE EXECUTE ON FUNCTION public.qg_customer_delivery_pin(uuid) FROM PUBLIC;

-- Keep historical rows/table for audit compatibility, but stop generating or exposing new PINs.
COMMIT;
