# QueueGo Release Gate

Snapshot date: 2026-10-21 · Main: `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`

## Overall status

**NOT READY for a whole-platform production launch or Play Closed Testing claim.** Current main has working source and automated regressions, but the required real-user journey, physical-device/hardware evidence, Android signing and production cutover receipts are not all present. A green source test or CI run does not pass those gates.

## POS cash movement and inventory

- PR #568 merged after exact-head QueueGo RC [39622237860](https://github.com/chatchairins-source/QueuGo/actions/runs/39622237860) completed full `npm test`, Merchant WebKit, Admin/Merchant mobile, Map Lab/search and Grocery mobile/tablet checks. Merge commit: `c79640e0c896cc81961e8ba2dc69d336a9f519b0`.
- Production was inspected read-only after merge. Migration history ends at `20261021133330`; `pos_register_shifts` and `pos_register_cash_events` exist; cash-events RLS is enabled; anon SELECT and authenticated INSERT are denied; authenticated SELECT is allowed. Cash-events still has only `id,shop_id,shift_id,order_id,actor_id,amount,reason,created_at`; `event_type`, `idempotency_key` and `pos_record_cash_movement(text,numeric,text,uuid)` are absent. Therefore cash movement remains **NOT LIVE** despite its source merge.
- The PR #568 migration source and PGlite tests cover actor/shop/request serialization, exact-payload idempotency, cash direction, reason, permissions, till calculation and retry after close. This does not certify real simultaneous Production connections or cashier hardware.
- PR #572 merged to main as `6050a51ab7cb06001fb4163b5ff88c890fb4950d` after exact-source RC [39636799778](https://github.com/chatchairins-source/QueuGo/actions/runs/39636799778) passed all 12 steps on `7d2ffec`. It adds four source migrations: SKU registry, authoritative options/pricing, generic reservation, and adjustment audit/history. Generic products use one reservation authority across POS/QR/Delivery; Grocery Master and Market ledgers are excluded. Production schema/security and real cashier/scanner/multi-channel acceptance remain BLOCKED; no inventory migration or live stock write has occurred.
- Follow-up permission review: Production `products_shop_update` permits authenticated shop users to update their shop's product rows directly. PR #573 merged as `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`; migration `20261021233200_pos_inventory_tracking_manager_guard.sql` enforces owner/`manage_staff` permission for enabling tracking on insert or changing the flag. PGlite authorization tests and full local suite pass; exact-source RC `39638917216` passed all 12 steps. No Production DDL applied.
- Market ledger compatibility review: read-only Production inspection of `market_item_stock_guard()` confirms Market-mapped order items decrement `market_products.stock_quantity` and append `market_stock_movements`. The earlier generic reservation trigger could also decrement `products.stock` if tracking were enabled on the same product, despite intended owner separation. Follow-up migration `20261021233300_pos_market_inventory_owner_guard.sql` rejects overlap in both directions, serializes insert/toggle checks by locking the product row, locks both old and new products in stable order on remap and serializes deletion against tracking activation, and makes generic reservation recheck Market mapping after that lock. Production `market_products` has RLS enabled with no policies, so the toggle guard is SECURITY DEFINER with fixed `search_path` to see the mapping while preserving caller JWT for `pos_allowed`. Timestamp-order PGlite regression with authenticated RLS simulation and mapping move/delete checks passes 47 checks; adjustment UI 9 PASS; full local `npm test` EXIT 0 after the remap/delete serialization fix. The preceding exact-source RC is superseded; final exact-source RC pending. Production lacks `products.inventory_stock_tracking` until prerequisite migration 330 runs, so overlap preflight must follow the feature schema in the controlled ordered release. No Production DDL/data mutation.
- Supabase security/performance advisors were read on 2026-10-21. Findings include private RLS/no-policy tables and public API SECURITY DEFINER warnings requiring endpoint-by-endpoint intent review, plus unindexed foreign keys. These global findings were not treated as POS-specific failures or silently changed.

## Whole-platform gates

| Gate | Status | Evidence required |
|---|---|---|
| Current code and ancestry | PASS for this snapshot only | Fetch main, every relevant open PR and newest successor again before the next change |
| Full web automated regression | PASS for PR #568 and #572 exact RCs | Run on each new source successor |
| Exact-head hosted CI | PASS for #568 `39622237860`, #572 `39636799778`, #573 `39638917216` | PR #574 final exact-source RC pending after remap/delete serialization change |
| Migration and schema alignment | BLOCKED; read-only inspection confirms cash movement columns/RPC and inventory migrations absent from Production | Review merged/successor migration order, ACL/RLS, trigger order, recovery plan and Production compatibility; controlled apply only after authorized cashier/user acceptance |
| Authenticated role journeys | BLOCKED / incomplete | Real Customer→Merchant→Rider completed order; real cashier, owner and staff POS journeys with expected permissions and data scopes |
| Mobile lifecycle and physical hardware | BLOCKED | iOS/Android real device GPS/background/push plus actual POS printer, scanner, cash drawer and multi-terminal checks where used |
| Financial/payment provider | BLOCKED where applicable | Authorized provider credentials, signed webhook, duplicate/retry/settlement proof and owner-approved live test; no secrets in chat |
| Android release | BLOCKED | Current native successor exact CI, signed AAB verification, Data Safety/privacy/listing, Play Console Closed Testing and physical acceptance |
| Performance, accessibility and recovery | BLOCKED / incomplete | Mid-range device metrics, Thai text/font scaling/touch/focus, network loss, process death, restore and duplicate prevention evidence |
| Owner acceptance | BLOCKED for real-world items | Reviewable deployed build and recorded test evidence against `FEATURE_GAP_MATRIX.md` |

## Pass criteria

An individual feature can be marked `DONE (source/test)` only with its exact commit SHA and relevant local/hosted CI evidence. A feature is `DONE (deployed)` only after its approved deployment completes and the served/runtime bytes and behavior are verified. The platform is launch-ready only when all applicable role journeys and release evidence pass; unresolved gates stay `BLOCKED` with the exact required action.

## Production safety

- No test or fabricated records in Production.
- No production migration, provider activation, pricing change, feature-flag activation, public routing cutover or broad data mutation merely because CI is green.
- Preserve cash-first rules, shop-scoped POS records, existing refund ownership, and fail-closed dispatch/route-bundle limits.
- Record before/after schema or setting evidence without exposing secrets or personal data.
