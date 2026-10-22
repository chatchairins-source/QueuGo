# QueueGo Next Actions

Current snapshot: 2026-10-22 · Authoritative main: `7572146400f44bf66c96aca5ae5f84264fe4241b`

## Active chunk

**POS Production continuation — cash shifts and shop capability authorization deployed; complete the remaining schema and real-device release gates.**

- PR #615 exact-head RC `39764778696` passed, including `npm test`, mobile WebKit, map gestures/search and Grocery mobile/tablet rendering. Main is `7572146400f44bf66c96aca5ae5f84264fe4241b`.
- Production recorded Kitchen staff capability migration `20261022105255` and cash movement migration `20261022105630_pos_cash_register_movements`. Readback confirmed the four Kitchen role guards, cash-in/out idempotency RPC and authenticated-only access. No POS staff or cash events exist; no order/payment/refund/drawer data was written.
- Cashier/staff live acceptance and physical register/printer/scanner validation remain open. Product Code/SKU and generic inventory migrations are still absent from Production; inspect schema, ACL/RLS and migration history before release.
- NEXT: preflight and release the existing Product Code/SKU implementation, then proceed through inventory and remaining POS gates. QueueGo/POS remain **NOT DONE**.

- PR #568 cash movement source was merged after exact-head QueueGo RC `39622237860` completed all 12 test/browser steps successfully. Merge commit: `c79640e0c896cc81961e8ba2dc69d336a9f519b0`; main now contains the audited cash-in/out UI and RPC source.
- PR #572 merged to `main` at `6050a51ab7cb06001fb4163b5ff88c890fb4950d` after exact-source RC `39636799778` passed all 12 steps on `7d2ffec2417884dce70805f646dccaa3f388059e`. Merge publishes source only; no Production migration was applied.
- PR #573 manager guard merged to `main` at `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`; exact-source RC `39638917216` passed all 12 steps on PR head `7d2bc0d0c14cce0f3a64eb8d71e068d92b260ff0`. Source tree guard requires existing server-side `pos_allowed('manage_staff')` to enable tracking on insert or toggle it. Focused PGlite 40 checks and full local `npm test` pass.
- Read-only Production trigger/function review found `market_item_stock_guard()` already decrements `market_products.stock_quantity`, while generic inventory only excluded Grocery Master rows. A Market-linked product with generic tracking enabled could decrement both ledgers. Successor migration `20261021233300_pos_market_inventory_owner_guard.sql` rejects overlapping Market/generic ownership, prevents concurrent map/toggle conflicts, and skips mapped Market products after product-row lock. Focused inventory test passes 47 checks; migration-order simulation applies 330 → 331 → 332 → 333 and verifies the RLS-enabled/no-policy Market table under authenticated role. `npm test` EXIT 0; adjustment UI test 9 PASS; `git diff --check` PASS.
- RLS review exposed that the toggle guard also needs SECURITY DEFINER to see the Market mapping because Production enables RLS on `market_products` without an authenticated SELECT policy. It retains fixed `search_path` and still calls the existing permission function under caller JWT context. PR #574 head `3b2c36d43dec42f8d5db92a9df97253d210027f8` has matching local tree `984ba41b28a95808e8032915b77cb481f5d986dd`; the prior exact QueueGo RC is superseded by stronger mapping update/delete serialization; final exact-head RC is pending.
- Read-only Production schema verification: migration history ends at `20261021133330`; `pos_register_cash_events` has RLS enabled, `anon SELECT=false`, `authenticated SELECT=true`, `authenticated INSERT=false`; it has no `event_type`/`idempotency_key` columns and `pos_record_cash_movement(...)` is absent. The merged cash feature is not live. The four PR #572 inventory migrations and #573/#574 guard migrations are also unapplied.
- Production Supabase advisors were read. Security lints include intentionally private tables with RLS/no policies and public/anon SECURITY DEFINER API warnings; review each finding against the intended endpoint policy before activation. Performance lints include unindexed foreign keys. No advisor-based change or database mutation is authorized by these findings alone.
- PR #572 source adds shop-scoped SKU/barcode, server-authoritative options/prices, opt-in generic inventory reservation across POS/QR/Delivery, manager-only idempotent stock adjustments and private audit history. Grocery Master and Market stock owners remain separate.

## Then, in dependency order

1. Continue read-only Production schema compatibility and controlled migration plan review. Six POS feature migrations remain unapplied; Production latest is `20261021133330`, and `products.inventory_stock_tracking` is absent, so Market-overlap preflight cannot run until prerequisite schema migration is installed. No Production apply without authorized cashier/device acceptance.
2. Continue staged schema compatibility review and preserve Market/Grocery stock authorities; keep Production activation blocked until authenticated sale/cancel/refund and manager-adjustment journeys pass with an authorized register/device, then controlled schema release, deployment/readback and regression.
3. Complete authenticated cashier flows: shift open/float → sale → cash movement/refund → close/count/variance/history; then barcode/options/bill and inventory reservation/adjustment/cancel/refund across POS, QR and Delivery. Use authorized test accounts and avoid fake Production data.
4. After inventory release dependencies are satisfied, implement supplier directory + purchase order receiving through the same generic inventory adjustment/audit authority; do not duplicate Grocery Master or Market ledgers. Then cost history/COGS, item exchanges, hardware and offline recovery by dependency.
5. Continue the highest-priority unblocked North Star row from `FEATURE_GAP_MATRIX.md`, always checking current main and newest successor first. QueueGo and POS remain **NOT DONE** until role journeys and applicable release evidence pass.

## Resume protocol

After each bounded chunk, update this file and `CHECKLIST.md` with exact SHA, local/hosted tests, deployment/readback evidence, blockers, and one precise next action. A new session must begin from `WORKFLOW.md`, `CHECKLIST.md`, current main and the newest successor.


## Latest verified checkpoint
- Main: `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1`; PR #574 exact RC `39643279380` PASS; Pages deploy `39643901620` SUCCESS.
- Production remains unmodified. Migration history ends at `20261021133330`; inventory feature migrations are unapplied. Browser runtime readback was unavailable after environment disconnection.


## Completed successor: numeric quantity compatibility
- Read-only Production schema confirms `order_items.quantity numeric`; the PGlite inventory fixture previously modeled integer quantity.
- PR #576 merged to main as `a4f12dd39b751e7e05a2d37b54e47863d477f391`; exact RC `39647073978` passed, with fractional generic rejection and fractional Market ledger coverage. Post-merge RC `39647725266` and Pages deploy `39647725023` also passed.
- Production remains unmodified. Continue read-only migration/schema recovery review and keep cashier/device acceptance open.


## Latest verified checkpoint
- PR #576 merged to main at `a4f12dd39b751e7e05a2d37b54e47863d477f391`; exact QueueGo RC `39647073978` passed all 12 steps. Post-merge main RC `39647725266` passed and GitHub Pages deploy `39647725023` succeeded on the merge SHA.
- Production stays unmodified. Migration history ends at `20261021133330`; `products.inventory_stock_tracking` is absent until the inventory stack is deployed in order. No cashier/device or browser runtime acceptance is claimed.
- NEXT: continue read-only schema/trigger/ACL and controlled migration recovery review; preserve authorized cashier acceptance before Production activation. QueueGo/POS remain NOT DONE.


## POS migration preflight and main RC retry — 2026-10-21
- PR #577 merged at `e70961d5b70fa7f84519fbebf473659603e35132`; exact PR RC `39648478732` PASS. Main RC first attempt `39649126036` failed at Admin WebKit title-size read (`tests/admin-boot-browser.cjs:69`), then exact failed job retry passed all steps. Pages deploy `39649125931` succeeded on the merge SHA.
- Read-only Production catalog: latest migration `20261021133330`; inventory migration stack `20261021211800` through `20261021233300` absent, including reservation/adjustment/manager/Market-owner guards; cash-movement migration `20261021211709` also absent. No DB DDL/data write.
- Installed `order_items` triggers still include Grocery and Market stock owners; generic POS reservation/audit triggers are absent. Market table is RLS enabled and authenticated SELECT grant exists without policies, so migration 333's fixed-search-path definer mapping guard and post-330 overlap preflight are release-critical.
- Next: preserve controlled apply gate; before activation install migrations in order, run overlap preflight after migration 330, verify ACL/RLS/trigger definitions and test sale→cancel/refund/adjustment through authorized cashier and device. QueueGo/POS NOT DONE.


## Post-merge receipt — 2026-10-22
- PR #578 merged at `cc036ee001bf23425d055977f8330e3753749743`. Exact PR RC `39650817495` passed all 12 required stages. The PR stabilizes the existing Admin WebKit route assertion by waiting for the rendered heading, with no runtime UI change.
- Main push RC `39694314231` passed all stages on the merge SHA, including npm test, Admin WebKit boot/layout, Merchant mobile WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet rendering. Pages deployment `39694313643` succeeded on the same SHA. Main ref verified at `cc036ee001bf23425d055977f8330e3753749743`.
- PR #578's change is test stabilization and documentation; Pages success is recorded as deployment evidence, not a claim of end-user UI behavior change. The prior main RC failure on #577 remains recorded as a separate earlier attempt.
- Production database remains unmodified. POS feature migrations remain unapplied; continue authorized cashier/device acceptance and controlled migration gates. QueueGo/POS are **NOT DONE**.

## Next action
Continue the POS migration recovery/compatibility review and verify the authorized cashier/device gate status. Do not apply Production migrations without the required acceptance; after that gate, proceed through ordered activation and readback. Then take the next highest-priority unblocked North Star gap. Keep QueueGo/POS NOT DONE until user and release evidence is complete.


## POS ambiguous bill session-scope guard — 2026-10-22
- Read current main `6acd48ef3c130cc1c6b7f4c0958afcc228920414`, no newer POS successor was open. PR #580 guards the ambiguous bill retry with its captured shop and cashier identity; identity mismatch drops the tab-scoped pending request before any order RPC. Regression covers session change after the request is loaded.
- PR #580 exact RC `39695677982` passed all 12 required stages and merged as `fbdf63707a5eba1b9bf703891330c7b132e6bb54`. Post-merge main RC `39696051611` passed all stages. Pages deployment `39696050699` succeeded on the merge SHA.
- This recovers an ambiguous online create request safely; it is not an offline sale queue. POS offline sale/outbox/conflict/reconciliation remains unimplemented and unaudited. Production database remains unmodified; cash/inventory migrations and authorized cashier/device acceptance gates remain open. QueueGo/POS are **NOT DONE**.

## Next action
Implement the durable offline cart draft slice specified in `docs/POS-OFFLINE-RECOVERY.md`: cache only minimum catalog/cart data, persist drafts scoped to shop + actor + device, label them Not submitted, and require explicit cashier review plus server revalidation before online submission. Add reload/account-switch/duplicate-tab/price-change tests. Do not accept payment, dispatch kitchen, reserve inventory, or report a sale while disconnected. Keep the Production migration and physical cashier/device gates open.


## Latest main successor receipt — 2026-10-22
- PR #581 merged at `c740e9321ec5e3d1fb2d6acafc382180e7166121` after its exact-head QueueGo RC passed. Its immediate push RC `39696877230` was cancelled when main advanced; the descendant main `a71131aa0027b0616260ce6881ef708e99e9e214` added Admin exclusions for POS DINE_IN/TAKEAWAY orders in Admin order reads, count KPI and GP totals.
- Successor main RC `39697139447` passed all required stages, and Pages deployment `39697139101` succeeded on the current main SHA. The earlier cancellation is preserved as evidence; no failure was hidden.
- Current main therefore includes #580's cashier/shop-scoped ambiguous bill retry guard and the Admin POS-order reporting exclusion. Production remains unmodified. POS feature migrations remain unapplied, physical cashier/device acceptance is not claimed, and a durable offline-sale queue remains unimplemented. QueueGo/POS **NOT DONE**.

## Next action
Continue POS controlled migration recovery and authorized device acceptance tracking, then audit the retry/outbox boundary for offline operations without creating any payment or tax policy. Verify Admin's exclusion across orders, dashboard counts and GP after current deployment; keep POS sales reporting and offline queue distinct from platform marketplace metrics.


## POS offline recovery discovery — 2026-10-22
- Current source blocks offline sale writes. Ambiguous online bill creation and cash movement use tab-scoped idempotent retries; #580 added actor/shop protection for bill replay. These are not durable offline sale queues.
- Reviewed current official Square and Shopify POS offline documentation. Both distinguish pending offline work from server-accepted state and expose permission, risk, limits, recovery, or unsupported-operation boundaries. Research and the recommended QueueGo Phase 1 are in `docs/POS-OFFLINE-RECOVERY.md`.
- Recommended next implementation: durable, identity-scoped offline cart drafts that require server price/stock/permission validation and cashier review before online submission. Do not mark as paid, send to kitchen, or reserve stock while disconnected. Actual offline completed sales require owner decisions about payment, multi-terminal inventory conflict, limits, shift/accounting, receipts and customer recovery.
- No Production migration or data change. QueueGo/POS remain **NOT DONE**.

## Next action
Implement the recommended durable offline draft recovery slice from `docs/POS-OFFLINE-RECOVERY.md`, without accepting offline payment or treating the draft as a completed sale. Preserve the cashier/device and controlled Production migration gates.
