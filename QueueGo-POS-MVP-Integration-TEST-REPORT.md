# QueueGo POS MVP integration — test report (2026-09-28)

## Changes made

- Merchant POS kitchen now reads pending/accepted/preparing/ready/assigned QueueGo Delivery orders from the same `orders`/`order_items` database and shows them alongside POS kitchen tickets. Delivery state changes call a shop-scoped, permission-checked RPC with audit and customer notification.
- POS bill creation now uses a persisted per-request UUID and an atomic server RPC. A retry of the same key returns the same Order ID. An uncertain browser request remains visible for explicit retry after page reload.
- Cancelling an unpaid POS bill requires a reason and records it in `pos_events`. The existing paid-bill refund path is separate.
- The POS indicates offline state, blocks new writes when the browser knows it is offline, and refetches on reconnect. POS tabs wrap on narrow displays.
- Existing POS payment, cash change, reports, item edit, product catalog, table and staff flows were retained.

## Verified

| Check | Result |
|---|---|
| JavaScript syntax (`node --check merchant/pos.js`) | Pass |
| HTML parser and asset paths | Pass |
| Migration applied to Supabase | Pass: three new RPCs, two read policies, request-key table with RLS enabled |
| Create bill twice with same key, cancel with reason, inspect audit | Pass in a real authenticated owner context using an existing product; entire SQL transaction intentionally rolled back |
| Cross-shop order visibility and invalid Delivery transition | Pass in an authenticated owner context; test transaction rolled back |
| No test bill/request key persisted | Confirmed request-key test row count is zero after rollback |

## Remaining live checks

- No active Delivery order or separate authenticated kitchen/wa​iter/cashier sessions were available for a genuine multi-device Delivery kitchen run. Test Customer → Merchant POS kitchen → Rider with actual sessions before treating Delivery kitchen as proven end to end.
- No automated iPhone/Tablet browser runtime was available in this workspace; navigation, viewport, sound, and reconnect must be checked on devices.
- The existing POS `pos_edit_bill` edits to an already open bill and the existing `pos_take_payment` payment path were not rewritten here. Their earlier migration checks do not substitute for a fresh full shift test with multiple staff accounts.
- A real transfer/PromptPay in POS is recorded after a cashier confirms receipt; there is no automatic bank verification or payment gateway.
- Ingredient stock, recipes, bill splitting/merging, and full offline order creation are Phase 2.

## Release scope

Source: `merchant/index.html`, `merchant/pos.js`, `merchant/pos.css`, `QueueGo-POS-MVP-Integration-Migration.sql`. The new migration is already applied to the linked Supabase project; do not apply it a second time under the same policy names. No customer, Rider, or Admin file was modified for this POS integration.
