# QueueGo Release Gate

## Current verified POS checkpoint — 2026-10-23

- Web POS category filters remain deployed and byte-verified from `be33b8318967ad6105222efa8305fec8005a5335` (PR #631). Exact RC `39878988663`, main RC `39879361494`, Pages `39879361545` succeeded. This checkpoint changes documentation only.
- Native PR #633 exact `d98ecdbf423e34b41e4fde3062d7b0d5db6750f0` passed both requested runs `39881729771` and `39881729809`; merged only into Native successor at `0a9c17dd774f59781de0565e9fa313da5ee955b8`.
- Native PR #635 fixes Android cached null-category conversion and tests null/named/literal-null/legacy catalogs on Android itself. Exact source `639bedea25710988c5f446ba6c16d1c0d3072a7d` passed Native Pilot `39882985832`: regression, Kotlin/unit tests, APK/16 KB checks and emulator instrumentation. Log confirms `QUEUEGO_MERCHANT_POS_CATALOG_PASS`. Merge `1f71c351b88d635de810fccb8e82bc447de37390` has the identical tested tree; Native successor documentation head is `382a746ad9176a03c4b7beafdfd1dbc2dad8b9ce`.
- Full clean local source-equivalent regression passed at `4b2c56c0` (map asset-fetch pretest skipped; existing assets reused); source contracts 313 and migration mirror PASS. Hosted CI included full setup. Emulator evidence artifact `12471178946`, SHA-256 `7d794e8f91cdfbdc90e0dea4b5ca7eae913f3f0522360c61808c816d78fbddb8`.
- Existing dirty POS/Native worktrees were preserved. No Production schema/data or authenticated sale changed. BLOCKED (release): authorized owner/cashier/staff journeys, physical phone/tablet/scanner/printer/drawer, and real-register offline/reconnect/retry evidence. Emulator success is not physical-device acceptance. Overall POS remains **NOT DONE**.
- **NEXT:** complete the authorized real cashier/device Release Gate and record evidence on these same successors. Do not restart from an older checkout or merge the divergent Native code into Web main.


Snapshot date: 2026-10-22 · Main: `aca988473d9f5132141dc1df379f0028d3ff64a2`

## Current POS continuation — 2026-10-22

- Main `aca988473d9f5132141dc1df379f0028d3ff64a2`; main RC `39793297082` SUCCESS.
- Production inventory chain is installed at `20261022141101`, `20261022141117`, `20261022144627`, `20261022145405`. Readback confirms Market owner trigger and generic tracking guard; functions use fixed `search_path`, SECURITY DEFINER and no direct EXECUTE grants. 8 Market mappings, 0 generic-stock overlaps; no commerce test data written.
- Full local `npm test` passed on `aca98847`. Supabase advisory counts match pre-migration baseline (24 RLS/no-policy notices, 18 FK-index notices).
- Open gates: authenticated cashier/staff RLS journey, phone/tablet/scanner/printer behavior, offline/reconnect E2E, Android parity and remaining POS Definition of Done. POS is **NOT READY**.

## Overall status

**NOT READY for a whole-platform production launch or Play Closed Testing claim.** Current main has working source and automated regressions, but the required real-user journey, physical-device/hardware evidence, Android signing and production cutover receipts are not all present. A green source test or CI run does not pass those gates.

## Latest main receipts and POS offline draft scope — 2026-10-22

- **Implementation blocker:** Safe draft submission requires a new atomic/idempotent Postgres RPC. The required Supabase skill could not be loaded in the current runtime, so schema/RPC implementation is deferred until the skill is available and reviewed. No migration was written or applied. This is an agent environment limitation, not a Production authorization or user policy decision.

- PR #585 design exact RC `39698519445` passed and merged as `d67f8dd8f79bcc991c447376cdd838988c816884`; main RC `39698920070` and Pages deployment `39698919956` succeeded.
- PR #584 Admin legacy GP/slip removal exact RC `39699366405` passed on `9e9202bf3b688a816eef41cd3fe2eacd0e852562`; merge SHA `aeedb1df2ef4654ee68ed26b25b4f34a01e441de`. Descendant main RC `39699765279` and Pages deployment `39699764713` succeeded. The live GP deduction-from-credit path remains intact.
- PR #584's regression harness serves the before snapshot from one historical commit, including its loader/runtime files. Admin WebKit coverage passed on both exact PR head and merged main.
- Next source slice: durable, identity-scoped offline cart drafts only. No disconnected payment, paid order, kitchen dispatch, stock reservation, final receipt, or sale reporting. Server acceptance and cashier review are required after reconnect.
- Production migrations/data remain unchanged; authorized cashier/device, real-user end-to-end, and controlled migration/readback gates remain open. QueueGo/POS are **NOT READY**.

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


## POS ambiguous bill session-scope guard — 2026-10-22
- Read current main `6acd48ef3c130cc1c6b7f4c0958afcc228920414`, no newer POS successor was open. PR #580 guards the ambiguous bill retry with its captured shop and cashier identity; identity mismatch drops the tab-scoped pending request before any order RPC. Regression covers session change after the request is loaded.
- PR #580 exact RC `39695677982` passed all 12 required stages and merged as `fbdf63707a5eba1b9bf703891330c7b132e6bb54`. Post-merge main RC `39696051611` passed all stages. Pages deployment `39696050699` succeeded on the merge SHA.
- This recovers an ambiguous online create request safely; it is not an offline sale queue. POS offline sale/outbox/conflict/reconciliation remains unimplemented and unaudited. Production database remains unmodified; cash/inventory migrations and authorized cashier/device acceptance gates remain open. QueueGo/POS are **NOT DONE**.


## Latest main successor receipt — 2026-10-22
- PR #581 merged at `c740e9321ec5e3d1fb2d6acafc382180e7166121` after its exact-head QueueGo RC passed. Its immediate push RC `39696877230` was cancelled when main advanced; the descendant main `a71131aa0027b0616260ce6881ef708e99e9e214` added Admin exclusions for POS DINE_IN/TAKEAWAY orders in Admin order reads, count KPI and GP totals.
- Successor main RC `39697139447` passed all required stages, and Pages deployment `39697139101` succeeded on the current main SHA. The earlier cancellation is preserved as evidence; no failure was hidden.
- Current main therefore includes #580's cashier/shop-scoped ambiguous bill retry guard and the Admin POS-order reporting exclusion. Production remains unmodified. POS feature migrations remain unapplied, physical cashier/device acceptance is not claimed, and a durable offline-sale queue remains unimplemented. QueueGo/POS **NOT DONE**.


## POS offline recovery discovery — 2026-10-22
- Current source blocks offline sale writes. Ambiguous online bill creation and cash movement use tab-scoped idempotent retries; #580 added actor/shop protection for bill replay. These are not durable offline sale queues.
- Reviewed current official Square and Shopify POS offline documentation. Both distinguish pending offline work from server-accepted state and expose permission, risk, limits, recovery, or unsupported-operation boundaries. Research and the recommended QueueGo Phase 1 are in `docs/POS-OFFLINE-RECOVERY.md`.
- Recommended next implementation: durable, identity-scoped offline cart drafts that require server price/stock/permission validation and cashier review before online submission. Do not mark as paid, send to kitchen, or reserve stock while disconnected. Actual offline completed sales require owner decisions about payment, multi-terminal inventory conflict, limits, shift/accounting, receipts and customer recovery.
- No Production migration or data change. QueueGo/POS remain **NOT DONE**.


## POS source and release status — 2026-10-22

- Current main at review time: `68fdb52d09bcafc19c602ac5694aecda36b1b29d`. The earlier snapshot at the top of this file predates the POS offline implementation.
- POS schema-fallback fix for the live Safari error shipped in PR #594, merge `04df99bb672e66f68ff7715ad1c31603723c482f`; Pages assets were verified live.
- Web offline cart draft source is PR #595, head `f229752fcf825d7164ab11155409d98ca5fbb2f5`; exact-head Web CI run [39724725075](https://github.com/chatchairins-source/QueuGo/actions/runs/39724725075) passed `npm test`, Merchant mobile WebKit and Grocery mobile/tablet rendering.
- Native Merchant offline cart draft source is PR #596, head `70a91c6935f4ed48e6a2033e3bcbc2ad1a7cd571`, based on Native successor `ff5faaacc289920ca80aa49ed0aa41fac51ca22b`. Exact-head Native CI run [39724962557](https://github.com/chatchairins-source/QueuGo/actions/runs/39724962557) passed full regression, Kotlin unit tests, three APK builds, package checks and Android launch/lifecycle capture. APK and launch-evidence artifacts are attached to the run.
- Production remains unchanged by these PRs. Read-only inspection on 2026-10-22 confirms migration history through `20261022005520`, no `products.inventory_stock_tracking`, and no `pos_add_product_with_options` or `pos_submit_offline_draft` RPC.
- Offline drafts are not completed sales: they do not create a bill, take payment, send kitchen work or reserve stock while disconnected. Both app implementations require online cashier review and server validation before creating a bill.
- **Release status:** offline draft is DONE (source/test) for these exact heads, but NOT LIVE. Keep the feature code out of Production until authorized cashier/device acceptance is recorded, the ordered migrations are applied with ACL/RLS readback, and the authenticated online submit/retry/cancel/refund flow is verified. No Production migration or test record has been created.


## 2026-10-22 latest Kitchen/context deployment
- Web PR598 is LIVE: merge15e7bd07, exact Web CI39740434026/39740439972 and main RC39742257368 succeeded; Pages39742256442 succeeded and served index/POS bytes matched source.
- Food/Cafe retain storefront Kitchen; Grocery/Market have none. POS has no Delivery queries/actions/report GP. Delivery cancellation remains separate and pre-preparation only. Three bounded Production migrations applied with guard/ACL/trigger readback; filenames aligned in PR599.
- PR595 offline successor reconciled with main42e81f57 without restoring Delivery/GP into POS. Its option-pricing/draft-submit migrations remain separate from deployed hotfix. Prior source-only status describes offline work, not Kitchen hotfix. No offline deployment or physical acceptance claimed here.


## 2026-10-22 offline backend rollout update
- Web offline successor0aed635c passed hosted RC39743999528 and full local regression. Pricing/draft tests46 also passed against actual Production pos_edit_bill in isolation.
- Applied only option-pricing and draft-submit migrations20261022080039/20261022080048, with ACL/RLS readback. No real or mock sale created. The earlier unapplied-backend checkpoint is superseded for these two migrations. Web final-head filename alignment and publication still pending at this checkpoint.


## POS Web release receipt — 2026-10-23
- PR #630 table availability, kitchen and payment-waiting labels merged to Web main `5a860ea3e0a26841eca5666a158b034c78b897d9`. Exact PR RC `39877737147`, post-merge RC `39878103940`, and Pages build/deploy `39878103646` succeeded. Cache-busted Production `/merchant/index.html` and `/merchant/pos-table-v2.js` matched source SHA-256 exactly; database/schema unchanged.
- Next Web chunk is category filters using existing product category data; local focused POS runtime 88 PASS and full `npm test` EXIT 0 on source `69a7d35a166acbe5157ea80911acca63c61fd32f`; exact hosted RC/deploy pending. Native parity is required after Web stability.
- This is a bounded UI release only. Authenticated cashier journeys, device/hardware verification, whole POS E2E and full Definition of Done remain open. QueueGo POS is **NOT DONE**.


## POS category parity continuation — 2026-10-23
- Web PR #631 is merged at main `be33b8318967ad6105222efa8305fec8005a5335`; exact PR RC `39878988663`, descendant main RC `39879361494`, Pages deployment `39879361545` succeeded. Cache-busted Production merchant HTML, POS JS and CSS hashes match source.
- Native PR #633 (`ee0bb89c18442e47101ffdca05717e85a360cc8e`) targets only successor `feat/native-merchant-pos-offline-20261022` (`7e058b616e669634a8b58916184f7e551c82b1c3`). Static source checks: 313 PASS; backend migration sync and full local regression pass. Hosted runs `39880496173` and `39880496200` remain in progress; no Kotlin/APK/device acceptance is claimed yet.
- No Production database mutation. Overall POS remains **NOT DONE** pending Native gate, authenticated cashier journeys, device/hardware testing and remaining Release Gate items.
