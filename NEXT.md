# QueueGo Next Actions

Current snapshot: 2026-10-21 · Authoritative main checked: `643a115129b0bf1d6b05de261baa0aa959310a1b`

## Active chunk

**POS generic inventory reservations and adjustment history — source implemented; full regression passed locally, hosted RC and production release gates open.**

- Tested source commit `e40dcd17480cd6068d2eda4ef9d04bdc67f6771f` is PR #572's exact head at RC; it is based on open PR #568 at `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`. Current main snapshot is `643a115129b0bf1d6b05de261baa0aa959310a1b`. Source tree matches locally tested commit `1eafe37ed1eb19ec6d017a0ace9219cfe0b85659`; commits after it only update status documents.
- New migration `20261021233000_pos_generic_inventory_reservations.sql` adds opt-in generic `inventory_stock_tracking`, a private reservation table and order-item/cancellation triggers. It reserves stock for tracked non-Grocery-Master products through shared `order_items`, rejects overselling, handles line-quantity changes and returns reservations on pre-completion cancel and keeps completed/refunded units consumed until an explicit return workflow exists. Tracking cannot be toggled while an order containing the product remains open. Existing Grocery Master triggers remain the sole stock authority for linked products.
- Merchant product editor exposes the tracking toggle and names the quantity as sellable stock. POS shows remaining units and disables tracked products at zero. The server reservation remains authoritative across POS, QR and Delivery writes.
- New migration `20261021233100_pos_inventory_adjustments.sql` adds immutable audit entries, manager-only idempotent adjustment/history RPCs, opening balances, order reservation/release and cancellation history. It guards direct stock edits; tracked product edits omit stale stock and use the dedicated adjustment flow. Merchant UI records reason/note, supports retry with the same request key, cursor-loads history, and escapes audit text.
- `tests/pos-generic-inventory.cjs`: 38 PGlite checks PASS for reservations, manual adjustment, idempotency, opening balance, order/cancel history, oversell rollback, role denial and private ACL. `tests/pos-inventory-adjustments-runtime.cjs`: 9 UI retry/cache/history checks PASS; Merchant product reliability 79 PASS; full `npm test` EXIT 0. Hosted exact-source RC [39633575108](https://github.com/chatchairins-source/QueuGo/actions/runs/39633575108) passed on PR source SHA `3ac73c2c1aa9221c392e5cfcaca5afd82653dd15`: full regression, WebKit installation, Admin/Merchant mobile, Map Lab/search and Grocery mobile/tablet rendering. No live RLS or cash-register acceptance is claimed.
- Shopify's official inventory model distinguishes available, committed and on-hand quantities; QueueGo follows the available/committed boundary for this slice while keeping its current product stock field as sellable units. [Shopify: Understanding inventory states](https://help.shopify.com/en/manual/inventory-and-locations/fundamentals/inventory-states)
- PR #572 is the ready dependent source review against open PR #568. Hosted RC used a matching `work-main-*` push branch because `.github/workflows/rc-tests.yml` only starts PR runs targeting `main`. The source SHA `3ac73c2c1aa9221c392e5cfcaca5afd82653dd15` passed full exact-source RC. PR #568 remains open, so #572 stays stacked and unmerged. Four migrations are staged, none is in Production. Merge, deployment, Production schema readback, real account/scanner use, and authenticated cashier acceptance remain open. No deploy or real sale is claimed.

## Then, in dependency order

1. Review the green exact-source RC; resolve PR #568 without discarding its current head. Then rebase/retarget PR #572 onto merged main and run RC again for the resulting commit.
2. Review/apply the four staged migrations only through the authorized release process; verify Production schema, trigger order, RPC ACL and RLS read-only. Then complete authorized cashier tests for concurrent boundary sale, line edit, adjustment, cancellation and refund across POS, QR and Delivery.
3. Continue POS inventory by dependency: purchase receiving and supplier records, then COGS/cost history, exchanges, printer/drawer/multi-terminal and offline recovery. Avoid duplicate ledgers for Grocery Master or Market's own inventory authorities.
4. Continue other platform gaps by dependency without resetting or duplicating implementations. Overall QueueGo and POS remain NOT DONE until all release gates and real user journeys are evidenced.

## Resume protocol

After each bounded chunk, update this file and `CHECKLIST.md` with status, exact SHA, tests/CI, deployment evidence and one precise next action. A new session must begin from `WORKFLOW.md` then `CHECKLIST.md` and fetch current GitHub state; this snapshot is not a substitute for a fresh ancestry check.
