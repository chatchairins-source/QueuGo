# QueueGo Release Gate

Snapshot date: 2026-10-22 · Main: `cc036ee001bf23425d055977f8330e3753749743`n: `a4f12dd39b751e7e05a2d37b54e47863d477f391`

## Overall status

**NOT READY for a whole-platform production launch or Play Closed Testing claim.** Current main has working source and automated regressions, but the required real-user journey, physical-device/hardware evidence, Android signing and production cutover receipts are not all present. A green source test or CI run does not pass those gates.

## POS cash movement and inventory

- PR #568 merged after exact-head QueueGo RC [39622237860](https://github.com/chatchairins-source/QueuGo/actions/runs/39622237860) completed full `npm test`, Merchant WebKit, Admin/Merchant mobile, Map Lab/search and Grocery mobile/tablet checks. Merge commit: `c79640e0c896cc81961e8ba2dc69d336a9f519b0`.
- Production was inspected read-only after merge. Migration history ends at `20261021133330`; `pos_register_shifts` and `pos_register_cash_events` exist; cash-events RLS is enabled; anon SELECT and authenticated INSERT are denied; authenticated SELECT is allowed. Cash-events still has only `id,shop_id,shift_id,order_id,actor_id,amount,reason,created_at`; `event_type`, `idempotency_key` and `pos_record_cash_movement(text,numeric,text,uuid)` are absent. Therefore cash movement remains **NOT LIVE** despite its source merge.
- The PR #568 migration source and PGlite tests cover actor/shop/request serialization, exact-payload idempotency, cash direction, reason, permissions, till calculation and retry after close. This does not certify real simultaneous Production connections or cashier hardware.
- PR #572 merged to main as `6050a51ab7cb06001fb4163b5ff88c890fb4950d` after exact-source RC [39636799778](https://github.com/chatchairins-source/QueuGo/actions/runs/39636799778) passed all 12 steps on `7d2ffec`. It adds four source migrations: SKU registry, authoritative options/pricing, generic reservation, and adjustment audit/history. Generic products use one reservation authority across POS/QR/Delivery; Grocery Master and Market ledgers are excluded. Production schema/security and real cashier/scanner/multi-channel acceptance remain BLOCKED; no inventory migration or live stock write has occurred.
- Follow-up permission review: Production `products_shop_update` permits authenticated shop users to update their shop's product rows directly. PR #573 merged as `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`; migration `20261021233200_pos_inventory_tracking_manager_guard.sql` enforces owner/`manage_staff` permission for enabling tracking on insert or changing the flag. PGlite authorization tests and full local suite pass; exact-source RC `39638917216` passed all 12 steps. No Production DDL applied.
- Market ledger compatibility review: PR #574 isolates Market/Grocery ledgers; numeric-schema follow-up PR #576 merged as `a4f12dd39b751e7e05a2d37b54e47863d477f391`. Exact PR RC `39647073978` and post-merge main RC `39647725266` passed; Pages deploy `39647725023` succeeded. PGlite now models Production `order_items.quantity numeric`, rejects fractional generic stock, and verifies fractional Market ledger updates without generic reservations. Production still has no `products.inventory_stock_tracking`; migration history ends at `20261021133330`. No Production DDL/data mutation. Cashier/device acceptance, ordered migration apply/readback and real-flow regression remain BLOCKED.
- Supabase security/performance advisors were read on 2026-10-21. Findings include private RLS/no-policy tables and public API SECURITY DEFINER warnings requiring endpoint-by-endpoint intent review, plus unindexed foreign keys. These global findings were not treated as POS-specific failures or silently changed.

## Whole-platform gates

| Gate | Status | Evidence required |
|---|---|---|
| Current code and ancestry | PASS for this snapshot only | Fetch main, every relevant open PR and newest successor again before the next change |
| Full web automated regression | PASS for PR #568 and #572 exact RCs | Run on each new source successor |
| Exact-head hosted CI | PASS for #568 `39622237860`, #572 `39636799778`, #573 `39638917216`, #574 `39643279380`, #576 `39647073978` | Post-merge main RC `39647725266` PASS; Pages deploy `39647725023` SUCCESS; Production migration/user acceptance gates remain open |
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


## Inventory quantity schema compatibility follow-up
- Read-only Production inspection confirms `order_items.quantity` is `numeric`, `market_products.stock_quantity` is `numeric`, and the Market-to-product relationship is the composite FK `(product_id,shop_id) → products(id,shop_id)`. Existing Market/Grocery item stock triggers are SECURITY DEFINER.
- Prior PGlite inventory fixture used integer quantity. PR #576 changed it to numeric, added fractional Market and generic rejection cases, and merged at `a4f12dd39b751e7e05a2d37b54e47863d477f391`. Exact-source RC `39647073978` and post-merge RC `39647725266` passed; Pages deploy `39647725023` succeeded. No Production change.


## POS migration preflight — 2026-10-21
- PR #577 exact RC `39648478732` passed and merged at `e70961d5b70fa7f84519fbebf473659603e35132`. Main RC `39649126036` had a first-attempt Admin WebKit title assertion failure at `tests/admin-boot-browser.cjs:69`; rerun of the failed job passed the full workflow. Pages deploy `39649125931` succeeded on the same SHA. Keep the first failure recorded as intermittent test evidence.
- Read-only Production migration history ends at `20261021133330`. POS inventory migrations `20261021211800`–`20261021233300` and cash movement migration `20261021211709` remain unapplied. Existing Grocery/Market order-item stock triggers remain installed; generic inventory triggers/tables are absent. No Production DDL or data mutation.
- Release order remains blocked on authorized cashier/device acceptance: apply ordered stack only after acceptance; run Market overlap preflight after tracking-column migration 330; read back ACL/RLS/triggers; then sale/cancel/refund/adjustment regression. No DONE claim.


## Post-merge receipt — 2026-10-22
- PR #578 merged at `cc036ee001bf23425d055977f8330e3753749743`. Exact PR RC `39650817495` passed all 12 required stages. The PR stabilizes the existing Admin WebKit route assertion by waiting for the rendered heading, with no runtime UI change.
- Main push RC `39694314231` passed all stages on the merge SHA, including npm test, Admin WebKit boot/layout, Merchant mobile WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet rendering. Pages deployment `39694313643` succeeded on the same SHA. Main ref verified at `cc036ee001bf23425d055977f8330e3753749743`.
- PR #578's change is test stabilization and documentation; Pages success is recorded as deployment evidence, not a claim of end-user UI behavior change. The prior main RC failure on #577 remains recorded as a separate earlier attempt.
- Production database remains unmodified. POS feature migrations remain unapplied; continue authorized cashier/device acceptance and controlled migration gates. QueueGo/POS are **NOT DONE**.
