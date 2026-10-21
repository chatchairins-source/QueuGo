# QueueGo Next Actions

Current snapshot: 2026-10-21 · Authoritative main: `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`

## Active chunk

**POS Market inventory owner isolation — exact-source RC in progress; Production activation remains blocked.**

- PR #568 cash movement source was merged after exact-head QueueGo RC `39622237860` completed all 12 test/browser steps successfully. Merge commit: `c79640e0c896cc81961e8ba2dc69d336a9f519b0`; main now contains the audited cash-in/out UI and RPC source.
- PR #572 merged to `main` at `6050a51ab7cb06001fb4163b5ff88c890fb4950d` after exact-source RC `39636799778` passed all 12 steps on `7d2ffec2417884dce70805f646dccaa3f388059e`. Merge publishes source only; no Production migration was applied.
- PR #573 manager guard merged to `main` at `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`; exact-source RC `39638917216` passed all 12 steps on PR head `7d2bc0d0c14cce0f3a64eb8d71e068d92b260ff0`. Source tree guard requires existing server-side `pos_allowed('manage_staff')` to enable tracking on insert or toggle it. Focused PGlite 40 checks and full local `npm test` pass.
- Read-only Production trigger/function review found `market_item_stock_guard()` already decrements `market_products.stock_quantity`, while generic inventory only excluded Grocery Master rows. A Market-linked product with generic tracking enabled could decrement both ledgers. Successor migration `20261021233300_pos_market_inventory_owner_guard.sql` rejects overlapping Market/generic ownership, prevents concurrent map/toggle conflicts, and skips mapped Market products after product-row lock. Focused inventory test passes 45 checks; migration-order simulation applies 330 → 331 → 332 → 333 and verifies the RLS-enabled/no-policy Market table under authenticated role. `npm test` EXIT 0; adjustment UI test 9 PASS; `git diff --check` PASS.
- RLS review exposed that the toggle guard also needs SECURITY DEFINER to see the Market mapping because Production enables RLS on `market_products` without an authenticated SELECT policy. It retains fixed `search_path` and still calls the existing permission function under caller JWT context. PR #574 head `e5f27b2c295fa69b7c0639ef33e96572a2146d6b` has matching local tree `dfc3c7fa4dc1d05c52e6c39e96c0660408d25f80`; exact QueueGo RC `39641504472` is in progress. Prior RC `39640731144` passed but is superseded by these changes.
- Read-only Production schema verification: migration history ends at `20261021133330`; `pos_register_cash_events` has RLS enabled, `anon SELECT=false`, `authenticated SELECT=true`, `authenticated INSERT=false`; it has no `event_type`/`idempotency_key` columns and `pos_record_cash_movement(...)` is absent. The merged cash feature is not live. The four PR #572 inventory migrations and #573/#574 guard migrations are also unapplied.
- Production Supabase advisors were read. Security lints include intentionally private tables with RLS/no policies and public/anon SECURITY DEFINER API warnings; review each finding against the intended endpoint policy before activation. Performance lints include unindexed foreign keys. No advisor-based change or database mutation is authorized by these findings alone.
- PR #572 source adds shop-scoped SKU/barcode, server-authoritative options/prices, opt-in generic inventory reservation across POS/QR/Delivery, manager-only idempotent stock adjustments and private audit history. Grocery Master and Market stock owners remain separate.

## Then, in dependency order

1. Complete exact-head QueueGo RC `39641504472`; recheck latest main, all relevant open PRs/review comments and successor head before considering merge of PR #574. Six total POS inventory migrations remain unapplied. Do not apply Production schema without authenticated cashier acceptance.
2. Continue staged schema compatibility review and preserve Market/Grocery stock authorities; keep Production activation blocked until authenticated sale/cancel/refund and manager-adjustment journeys pass with an authorized register/device, then controlled schema release, deployment/readback and regression.
3. Complete authenticated cashier flows: shift open/float → sale → cash movement/refund → close/count/variance/history; then barcode/options/bill and inventory reservation/adjustment/cancel/refund across POS, QR and Delivery. Use authorized test accounts and avoid fake Production data.
4. After inventory release dependencies are satisfied, implement supplier directory + purchase order receiving through the same generic inventory adjustment/audit authority; do not duplicate Grocery Master or Market ledgers. Then cost history/COGS, item exchanges, hardware and offline recovery by dependency.
5. Continue the highest-priority unblocked North Star row from `FEATURE_GAP_MATRIX.md`, always checking current main and newest successor first. QueueGo and POS remain **NOT DONE** until role journeys and applicable release evidence pass.

## Resume protocol

After each bounded chunk, update this file and `CHECKLIST.md` with exact SHA, local/hosted tests, deployment/readback evidence, blockers, and one precise next action. A new session must begin from `WORKFLOW.md`, `CHECKLIST.md`, current main and the newest successor.
