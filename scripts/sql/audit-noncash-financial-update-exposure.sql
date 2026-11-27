-- QueueGo Worker 2: non-cash/non-POS financial mutation exposure inventory.
-- READ ONLY. Run against a reviewed isolated clone; never issue UPDATE against Production.
-- A positive grant is NOT proof of an exploit: RLS, BEFORE triggers and RPC context
-- must be exercised with isolated Customer/Merchant/Rider/Admin fixtures.
-- This audit deliberately does not claim runtime authorization has passed.

WITH target_columns(table_name, column_name) AS (
  VALUES ('orders','total_amount'), ('orders','payment_status'),
         ('orders','subtotal'), ('orders','delivery_fee'),
         ('order_items','unit_price'), ('order_items','quantity'),
         ('order_items','order_id')
), roles(role_name) AS (
  VALUES ('anon'), ('authenticated')
)
SELECT r.role_name, c.table_name, c.column_name,
       has_table_privilege(r.role_name, 'public.' || c.table_name, 'UPDATE') AS table_update_grant,
       has_column_privilege(r.role_name, 'public.' || c.table_name, c.column_name, 'UPDATE') AS effective_column_update,
       t.relrowsecurity AS rls_enabled,
       t.relforcerowsecurity AS force_rls
FROM target_columns c
CROSS JOIN roles r
JOIN pg_class t ON t.oid = ('public.' || c.table_name)::regclass
ORDER BY c.table_name, c.column_name, r.role_name;

-- Enumerate every enabled BEFORE UPDATE trigger that could reject a mutation,
-- including schema-qualified private trigger functions; do not assume the
-- named cash/POS guards are the only enforcement layer.
SELECT rel.relname AS table_name, trg.tgname AS trigger_name,
       trg.tgenabled AS trigger_enabled_mode,
       pg_get_triggerdef(trg.oid) AS trigger_definition,
       fn_ns.nspname || '.' || fn.proname AS function_name
FROM pg_trigger trg
JOIN pg_class rel ON rel.oid=trg.tgrelid
JOIN pg_namespace rel_ns ON rel_ns.oid=rel.relnamespace
JOIN pg_proc fn ON fn.oid=trg.tgfoid
JOIN pg_namespace fn_ns ON fn_ns.oid=fn.pronamespace
WHERE rel_ns.nspname='public'
  AND rel.relname IN ('orders','order_items')
  AND NOT trg.tgisinternal
  AND (trg.tgtype & 2) <> 0 -- BEFORE
  AND (trg.tgtype & 16) <> 0 -- UPDATE
ORDER BY rel.relname, trg.tgname;

-- Effective UPDATE policies and their WITH CHECK expressions.
SELECT tablename, policyname, roles, cmd, qual, with_check
FROM pg_policies
WHERE schemaname='public'
  AND tablename IN ('orders','order_items','payments')
  AND cmd IN ('UPDATE','ALL')
ORDER BY tablename, policyname;

-- Catalog-only sentinel: fail if expected guards or RLS are absent.
-- Passing this sentinel DOES NOT prove financial field immutability.
DO $audit$
DECLARE
  target text;
BEGIN
  FOREACH target IN ARRAY ARRAY['orders','order_items','payments'] LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_class c
      WHERE c.oid=('public.' || target)::regclass AND c.relrowsecurity
    ) THEN
      RAISE EXCEPTION 'RLS absent on public.%', target;
    END IF;
  END LOOP;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    WHERE t.tgrelid='public.orders'::regclass
      AND t.tgname='zz_queuego_cash_order_guard'
      AND t.tgenabled IN ('O','A')
  ) THEN
    RAISE EXCEPTION 'Expected orders cash guard absent or disabled';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    WHERE t.tgrelid='public.order_items'::regclass
      AND t.tgname='pos_items_guard'
      AND t.tgenabled IN ('O','A')
  ) THEN
    RAISE EXCEPTION 'Expected order_items POS guard absent or disabled';
  END IF;
END $audit$;

-- REQUIRED NEXT STEP (isolated DB only):
-- With representative non-cash/non-POS orders and actual JWT claims for each
-- role, attempt UPDATE of orders.total_amount, orders.payment_status,
-- order_items.unit_price, and order_items.order_id inside ROLLBACK transactions.
-- Assert unauthorized writes are rejected and authorized RPCs still work.
-- Also test payment replay, concurrency and settlement consistency.
