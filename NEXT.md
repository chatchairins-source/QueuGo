# QueueGo Next Actions

Current snapshot: 2026-10-21 · Authoritative main: `6050a51ab7cb06001fb4163b5ff88c890fb4950d`

## Active chunk

**POS inventory manager guard — source fix in progress; Production activation remains blocked.**

- PR #568 cash movement source was merged after exact-head QueueGo RC `39622237860` completed all 12 test/browser steps successfully. Merge commit: `c79640e0c896cc81961e8ba2dc69d336a9f519b0`; main now contains the audited cash-in/out UI and RPC source.
- PR #572 merged to `main` at `6050a51ab7cb06001fb4163b5ff88c890fb4950d` after exact-source RC `39636799778` passed all 12 steps on `7d2ffec2417884dce70805f646dccaa3f388059e`. Merge publishes source only; no Production migration was applied.
- Follow-up review found shop-scoped `products_shop_update` allows direct row updates, while the inventory feature's tracking flag was not manager-guarded. Successor migration `20261021233200_pos_inventory_tracking_manager_guard.sql` enforces existing server-side `pos_allowed('manage_staff')` for enabling tracking on insert and toggling it. Focused PGlite test passes 40 checks; full `npm test` EXIT 0.
- Read-only Production schema verification: migration history ends at `20261021133330`; `pos_register_cash_events` has RLS enabled, `anon SELECT=false`, `authenticated SELECT=true`, `authenticated INSERT=false`; it has no `event_type`/`idempotency_key` columns and `pos_record_cash_movement(...)` is absent. The merged cash feature is not live. The four PR #572 inventory migrations are also unapplied.
- Production Supabase advisors were read. Security lints include intentionally private tables with RLS/no policies and public/anon SECURITY DEFINER API warnings; review each finding against the intended endpoint policy before activation. Performance lints include unindexed foreign keys. No advisor-based change or database mutation is authorized by these findings alone.
- PR #572 source adds shop-scoped SKU/barcode, server-authoritative options/prices, opt-in generic inventory reservation across POS/QR/Delivery, manager-only idempotent stock adjustments and private audit history. Grocery Master and Market stock owners remain separate.

## Then, in dependency order

1. Finish full suite and exact-source RC for the manager-guard successor; merge only after expected-head review and green CI. No Production apply without the authenticated cashier acceptance gate.
2. Continue actual-schema review for all POS inventory migrations, ACL/RLS, trigger order and forward-repair plan; keep Production activation blocked until acceptance.
3. Complete authenticated cashier flows: shift open/float → sale → cash movement/refund → close/count/variance/history; then barcode/options/bill and inventory reservation/adjustment/cancel/refund across POS, QR and Delivery. Use authorized test accounts and avoid fake Production data.
4. After inventory release dependencies are satisfied, implement supplier directory + purchase order receiving through the same generic inventory adjustment/audit authority; do not duplicate Grocery Master or Market ledgers. Then cost history/COGS, item exchanges, hardware and offline recovery by dependency.
5. Continue the highest-priority unblocked North Star row from `FEATURE_GAP_MATRIX.md`, always checking current main and newest successor first. QueueGo and POS remain **NOT DONE** until role journeys and applicable release evidence pass.

## Resume protocol

After each bounded chunk, update this file and `CHECKLIST.md` with exact SHA, local/hosted tests, deployment/readback evidence, blockers, and one precise next action. A new session must begin from `WORKFLOW.md`, `CHECKLIST.md`, current main and the newest successor.
