# QueueGo Next Actions

Current snapshot: 2026-10-21 · Authoritative main checked: `643a115129b0bf1d6b05de261baa0aa959310a1b`

## Active chunk

**POS inventory reservation for generic products — exact-head hosted RC passed; production release and real cashier acceptance remain blocked.**

- Successor PR #572 head is `7573d543f74e07d188ed704bc2bc8f289feca8db`, based on open PR #568 at `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`. Current main snapshot is `643a115129b0bf1d6b05de261baa0aa959310a1b`. Source tree matches locally tested commit `1eafe37ed1eb19ec6d017a0ace9219cfe0b85659`; the later commits only update release status documents.
- New migration `20261021233000_pos_generic_inventory_reservations.sql` adds opt-in generic `inventory_stock_tracking`, a private reservation table and order-item/cancellation triggers. It reserves stock for tracked non-Grocery-Master products through shared `order_items`, rejects overselling, handles line-quantity changes and returns reservations on pre-completion cancel and keeps completed/refunded units consumed until an explicit return workflow exists. Tracking cannot be toggled while an order containing the product remains open. Existing Grocery Master triggers remain the sole stock authority for linked products.
- Merchant product editor exposes the tracking toggle and names the quantity as sellable stock. POS shows remaining units and disables tracked products at zero. The server reservation remains authoritative across POS, QR and Delivery writes.
- `tests/pos-generic-inventory.cjs`: 22 PGlite checks PASS, including untracked behavior compatibility, reserve/adjust, insufficient stock rollback, open-order toggle guard, cancellation release, completed refund remains consumed, identity freeze, Grocery Master separation and ledger ACL. Hosted RC run [39628421715](https://github.com/chatchairins-source/QueuGo/actions/runs/39628421715) passed on exact RC SHA `7573d543f74e07d188ed704bc2bc8f289feca8db`: full `npm test`, WebKit install, Admin/Merchant mobile boot, Map Lab, Map search and Grocery mobile/tablet rendering. This does not certify Production RLS, real cashier acceptance or deployment.
- Shopify's official inventory model distinguishes available, committed and on-hand quantities; QueueGo follows the available/committed boundary for this slice while keeping its current product stock field as sellable units. [Shopify: Understanding inventory states](https://help.shopify.com/en/manual/inventory-and-locations/fundamentals/inventory-states)
- Published as ready PR #572 against PR #568. Its hosted RC was started through a matching `work-main-*` push branch because `.github/workflows/rc-tests.yml` only starts PR runs targeting `main`. RC passed; the RC branch is evidence-only. PR #568 remains open, so #572 stays stacked and unmerged. No migration is in Production. Merge, deployment, Production schema readback, real account/scanner use, and authenticated cashier acceptance remain open. No deploy or real sale is claimed.

## Then, in dependency order

1. Resolve PR #568's release/merge gate without discarding its current head. Then rebase/retarget PR #572 onto the merged main and rerun exact-head RC for that resulting commit.
2. Review/apply the three staged migrations only through the authorized release process; verify Production schema, trigger order and ACL read-only. Then complete an authorized cashier test with tracking enabled: concurrent boundary sale, line edit, cancellation and refund; verify available stock across POS, QR and Delivery.
3. Continue POS inventory by dependency: stock adjustment audit/history, purchase receiving and supplier records, then COGS/cost history, exchanges, printer/drawer/multi-terminal and offline recovery. Avoid duplicate ledgers for Grocery Master or Market's own inventory authorities.
4. Continue other platform gaps by dependency without resetting or duplicating implementations. Overall QueueGo and POS remain NOT DONE until all release gates and real user journeys are evidenced.

## Resume protocol

After each bounded chunk, update this file and `CHECKLIST.md` with status, exact SHA, tests/CI, deployment evidence and one precise next action. A new session must begin from `WORKFLOW.md` then `CHECKLIST.md` and fetch current GitHub state; this snapshot is not a substitute for a fresh ancestry check.
