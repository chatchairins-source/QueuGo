# QueueGo POS Tables — 2026-09-28

## Actual schema reused

- `public.pos_tables`: `id` UUID, `shop_id` UUID, `label` text, `active` boolean, `created_at`; no seat count column exists.
- `public.orders`: existing `table_id` UUID and `shop_id` UUID. `orders_table_id_fkey` and the unique `pos_one_open_bill_per_table` index already existed.
- Existing `pos_tables_read` RLS policy restricts SELECT to `shop_id = pos_my_shop()`. Table writes use the existing permission-checked `pos_save_table` RPC; there is no direct INSERT/UPDATE RLS policy. Its EXECUTE grant is for `authenticated`, not `anon`.
- New migration `QueueGo-POS-Tables-Migration.sql` adds `orders_table_same_shop_fkey` on `(table_id, shop_id)` to `pos_tables(id, shop_id)`; it was applied and validated. No new table, column, or RLS policy was needed.

## UI and flow

- The โต๊ะ tab now has an Add Table button, an edit/deactivate form, empty state, and statuses sourced from open Supabase orders. Closed bills free tables automatically; inactive tables keep old order references.
- The DINE_IN counter lists active tables and the no-table choice. Occupied tables show their order number and explicitly open the existing bill after confirmation. TAKEAWAY remains table-free.
- Open bills beyond the recent 200 order history are fetched as open POS bills so an older occupied table is not shown as free.
- Counter and kitchen show the dine-in table label. No seat-count field was added because the current table schema does not support one.

## Verification

- `node --check merchant/pos.js` and `git diff --check`: pass.
- Authenticated owner with a real existing product, all within an intentionally rolled-back Supabase transaction: create table → create DINE_IN order with matching `orders.table_id` → reject duplicate open bill on same table → send/cook/ready/serve → cash close → no open bill remains → rename and deactivate table: pass.
- Authenticated owners of two existing shops, in a rolled-back transaction: one cannot SELECT or edit the other's table: pass.
- New composite FK exists and is validated. Test table and bill were rolled back; no test data persisted.

## Device test still needed

- Tap-through on an authenticated iPhone and multi-device Realtime refresh have not been run in this workspace. The database transaction and UI source checks above do not constitute a live Safari run.
