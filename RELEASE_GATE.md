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
- Published as dependent PR #572, head `3850595ced53e2717ac0620ee241789b4544740a`, based on open #568 head `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`. Its source tree matches local tested commit `1eafe37ed1eb19ec6d017a0ace9219cfe0b85659` (all 15 Git blob SHAs verified). PR is ready for review and mergeable.
- Hosted RC has not run: `.github/workflows/rc-tests.yml` triggers for PRs targeting `main`, while #572 is stacked on #568. Retarget after #568 merges. Local `npm test` EXIT0; `npm run test:merchant:browser` is blocked because Playwright WebKit is not installed. No Production apply/readback or real cashier sale was completed. Release gate remains BLOCKED.
- Next POS work: publish and verify exact source, then real authorized cross-channel concurrency/cancel/refund acceptance. Follow with stock adjustment history and purchase receiving/supplier traceability.

## POS SKU, modifiers and inventory successor

- PR #572, ready for review and mergeable, head `3850595ced53e2717ac0620ee241789b4544740a`, stacked on the open #568 head `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`.
- Local tested source commit `1eafe37ed1eb19ec6d017a0ace9219cfe0b85659`; all 15 changed remote blob SHAs match the exact local source tree. Full `npm test` EXIT 0; focused tests: generic inventory 22, option pricing 27, SKU SQL/security 31, scanner runtime 11, POS cash shifts 36, Merchant product reliability 72.
- Local Merchant browser verification is blocked because this workspace lacks Playwright WebKit. Hosted RC has not run: `.github/workflows/rc-tests.yml` only triggers PRs targeting `main`; retarget #572 after #568 merges to run exact-head RC.
- Three migrations are staged and not in Production: `20261021211800_pos_product_code_registry`, `20261021223000_pos_menu_option_pricing`, `20261021233000_pos_generic_inventory_reservations`. No live sale, Production apply/readback, merge or deployment is claimed. Release gate remains **BLOCKED** until CI, authorized migration/readback and cashier acceptance pass.

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
