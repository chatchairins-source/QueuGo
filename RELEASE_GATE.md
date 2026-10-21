# QueueGo Release Gate

Snapshot date: 2026-10-21 · Main: `643a115129b0bf1d6b05de261baa0aa959310a1b`

## Overall status

**NOT READY for a whole-platform production launch or Play Closed Testing claim.** Current main has working source and automated regressions, but the required real-user journey, physical-device/hardware evidence, Android signing and production cutover receipts are not all present. A green source test or CI run does not pass those gates.

## POS cash in/out slice

- Source branch: `work-pos-complete-next-20261021`
- Exact HEAD: `0f9af0cbc91af0bf7d964c9c52665ceb4e19cb9a`
- PR: #568, open at review; successor based on current main `6669dc108137f8119c86d4de9709dacba7a301d7`.
- Previous HEAD `119c68eb1172967e3542d66a15b02de30b33253f` passed QueueGo RC run `39617058579`. Updated exact-head RC run `39619648922` completed **SUCCESS**.
- Local verification in the current session: focused POS tests pass (`36` cash-shift SQL/contract checks, `73` POS runtime checks, `18` item-sales report checks); full `npm test` completed successfully on updated exact source tree.
- The update serializes identical actor/shop/request keys before receipt lookup and handles a competing unique-key receipt only when payload matches. Local PGlite validates migration semantics and retry contract; it is not a real simultaneous-connection/Production race certification.
- Production migration history (read-only Supabase check): installed through `20261021133330_pos_owner_item_sales_report`; `20261021211709_pos_cash_register_movements` is **not installed**.
- Release status: **BLOCKED before activation**. Do not deploy UI/migration as active behavior or report cash movement live until the repository's migration/release process is satisfied and an authorized real cashier acceptance is recorded.

## POS generic inventory successor

- Local source adds opt-in stock tracking for generic merchant products, reservations for shared `order_items`, sellable-stock UI, zero-stock POS prevention, pre-completion cancellation return, no automatic restock on completed refunds, open-order toggle protection and a single authority boundary that excludes Grocery Master products.
- Official Shopify guidance distinguishes available inventory from committed inventory reserved by unfulfilled orders. QueueGo's UI labels the editable count as sellable stock, and its private reservation table records committed quantities. [Shopify: Understanding inventory states](https://help.shopify.com/en/manual/inventory-and-locations/fundamentals/inventory-states)
- Verification: `tests/pos-generic-inventory.cjs` 22 PASS; POS option/stock interaction 27 PASS; POS barcode 31 + runtime 11 PASS; POS shifts 36 PASS; Merchant product reliability 72 PASS; complete `npm test` exit 0. PGlite is isolated and does not certify hosted concurrent connections, live RLS, Production or a physical register.
- Migration `20261021233000_pos_generic_inventory_reservations.sql` is staged, not deployed. GitHub push is blocked by missing write authentication. No hosted CI, merge, production apply/readback or real cashier sale was completed. Release gate remains BLOCKED.
- Next POS work: publish and verify exact source, then real authorized cross-channel concurrency/cancel/refund acceptance. Follow with stock adjustment history and purchase receiving/supplier traceability.

## POS SKU/barcode scan successor

- Branch: `queuego-pos-sku-scan-pr`, latest source commit `338ebc8b9fe580e870b6d5577c89c6329fe3a999`, directly based on #568 branch HEAD `a1592df31c41cbb6bbfdf13e166d977c9bcf901c` (which contains current main).
- Source adds shop-unique SKU/barcode registration, guarded manager RPCs, shop-scoped lookup and queued additions through existing bill RPCs. Option selection is validated server-side against saved product variants; option price and display snapshot are persisted and request replay is guarded. The SQL test invokes the existing Grocery Master stock trigger to confirm that the insert decrements tracked stock once and option price update/replay do not double-decrement.
- Local tests: POS runtime 73, cash shifts 36, item-sales report 18, product-code SQL/security 31, scan-to-bill UI 11, option pricing/security/replay 27; Security Definer review and complete `npm test` PASS on the local source commit tree.
- Migrations `20261021211800_pos_product_code_registry` and `20261021223000_pos_menu_option_pricing` are staged and not installed in Production. No live write was made. Publishing is blocked by unavailable GitHub write credentials (`git push` cannot authenticate). Exact-head hosted CI, migration review/readback, authenticated cashier acceptance and physical scanner validation remain open. The next POS inventory gap is generic non-master product stock tracking across POS and Delivery; Grocery Master already has its own reservation authority and must remain on that single path.
- Release status: **BLOCKED before activation**; do not claim deployed or real-user certified.

## Whole-platform gates

| Gate | Status | Evidence required |
|---|---|---|
| Current code and ancestry | PASS for this snapshot only | Fetch main, every relevant open PR and newest successor again before the next change |
| Full web automated regression | PASS for POS cash-movement HEAD `0f9af0c` in this session | Re-run for every later code change and capture exact SHA |
| Exact-head hosted CI | PASS for PR #568 HEAD above | New exact-head CI after any further commit; required browser/device workflows must be green, not skipped |
| Migration and schema alignment | BLOCKED for the staged cash-movement migration | Review migration order, ACL/RLS, rollback/forward-repair plan and production schema compatibility; controlled apply and readback only after gate review |
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
