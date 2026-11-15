-- Read-only, aggregate-only payment integrity triage. Never returns order IDs or customer data.
-- Multiple payment attempts may be legitimate; these counts are investigation signals only.
-- Success labels are heuristic and must be reconciled with the actual payment state machine.
WITH per_reference AS (
  SELECT btrim(transaction_reference) AS reference_key, count(*) AS attempts
  FROM public.payments
  WHERE transaction_reference IS NOT NULL AND btrim(transaction_reference) <> ''
  GROUP BY btrim(transaction_reference)
), per_order AS (
  SELECT p.order_id,
         count(*) AS attempts,
         count(*) FILTER (
           WHERE lower(btrim(p.status)) IN ('paid','succeeded','success','completed','captured')
         ) AS successful_attempts
  FROM public.payments AS p
  WHERE p.order_id IS NOT NULL
  GROUP BY p.order_id
)
SELECT
  (SELECT count(*) FROM public.payments) AS payment_rows,
  (SELECT count(*) FROM public.payments WHERE transaction_reference IS NOT NULL AND btrim(transaction_reference) <> '') AS payments_with_nonblank_transaction_reference,
  (SELECT count(*) FROM public.payments WHERE transaction_reference IS NULL OR btrim(transaction_reference) = '') AS payments_without_transaction_reference,
  (SELECT count(*) FROM public.payments WHERE order_id IS NULL) AS payments_without_order_id,
  (SELECT count(*) FROM public.payments WHERE status IS NULL OR btrim(status) = '') AS payments_without_status,
  (SELECT count(*) FROM public.payments WHERE amount IS NULL OR amount <= 0) AS payments_with_nonpositive_amount,
  (SELECT count(*) FROM public.payments WHERE payment_method IS NULL OR btrim(payment_method) = '') AS payments_without_method,
  (SELECT count(*) FROM per_order WHERE attempts > 1) AS orders_with_multiple_payment_rows,
  (SELECT count(*) FROM per_reference WHERE attempts > 1) AS duplicate_nonblank_transaction_references,
  (SELECT count(*) FROM per_order WHERE successful_attempts > 1) AS orders_with_multiple_successful_payments,
  (SELECT count(*)
     FROM public.payments AS p
     LEFT JOIN public.orders AS o ON o.id = p.order_id
    WHERE p.order_id IS NOT NULL AND o.id IS NULL) AS payment_orphans;
