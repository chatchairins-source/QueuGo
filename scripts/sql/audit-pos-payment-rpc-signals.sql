-- Read-only POS payment/refund authorization and concurrency review.
-- Signals are triage only: they do not certify correct authorization or idempotency.
-- Never return function bodies, payment records, or customer data.
WITH payment_functions AS (
  SELECT p.oid, p.proname, lower(pg_get_functiondef(p.oid)) AS definition,
         p.prosecdef, p.proconfig
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public'
    AND p.proname IN ('pos_take_payment','pos_refund_bill')
)
SELECT proname,
       prosecdef AS security_definer,
       has_function_privilege('anon',oid,'EXECUTE') AS anon_can_execute,
       has_function_privilege('authenticated',oid,'EXECUTE') AS authenticated_can_execute,
       coalesce(array_to_string(proconfig,','),'') LIKE '%search_path=%' AS has_fixed_search_path,
       position('for update' IN definition)>0 AS row_lock_signal,
       position('auth.uid()' IN definition)>0 AS auth_uid_signal,
       position('idempot' IN definition)>0 AS explicit_idempotency_signal,
       position('payment_status' IN definition)>0 AS payment_status_signal
FROM payment_functions
ORDER BY proname;
