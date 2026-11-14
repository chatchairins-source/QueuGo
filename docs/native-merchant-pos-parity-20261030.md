# Native Merchant POS parity review — 2026-10-30

Scope: CLOSE NATIVE MERCHANT POS FULL PARITY. Implementation base: published Native successor `e54714eccdd8ee10a468ba0fa48afd7f9f6d7fa2`, existing draft PR #735. Web Production source oracle: main `7e036a0735cc49905e9b0a2434492470551a0c9d`. This is a source/backend review, not physical acceptance or a release certificate.

## Verified fixes in this continuation

- `c962ea7c67c47c3cb4ce5ecc2290b40177dfd815`: removed duplicate receipt-tax test declaration and moved PASS output after all assertions. Native POS CI push 41000094234 and PR 41000098909 succeeded. Local full npm test exited 0.
- `cf62cfae0fd9072fa1b4af5bbf71f20307b68079`: guarded optional tax columns in both bill reads. Read-only Production schema audit confirmed those columns and tax settings are absent. Native POS CI push 41000460407 and PR 41000466095 succeeded. Android Pilot runs on this intermediate head were cancelled; do not report a build pass from them.
- `e54714eccdd8ee10a468ba0fa48afd7f9f6d7fa2`: cashier cash movement uses the same durable pending store as the existing shift-history entry point, restores exact keys after reopening, and separates financial acknowledgement from subsequent refresh. Local full npm test exited 0; hosted tests/build require their own exact-head result.
- Current bounded chunk: preserve offline draft payload and per-unit IDs after any uncertain response, including 4xx after a prior committed-but-lost response. Real Production RPC definition reviewed read-only: it hashes the whole payload, scopes receipt to shop/actor/request, locks it, and rejects changed payloads. The new actual Web runtime test reproduced 401 clearing before the fix and now verifies an identical retry, one server bill, and blocked draft deletion/editing until confirmation. No Production bill or payment was created for testing.

## Scope matrix

All rows remain IN PROGRESS until their stated test/build and acceptance gates pass. Existing implementation is evidence of presence, not proof of physical correctness.

| Requested area | Current implementation/evidence | Remaining gate |
| --- | --- | --- |
| POS Category | Product category read, category chips plus search, shop-switch reset, offline cache category | Exact-head build; authenticated phone/tablet comparison |
| Product Catalog | Existing products/stock/options; barcode/SKU RPC; store-scoped catalog | Live authorized catalog/option/stock acceptance |
| Cart / Quantity | Existing pending edit store; atomic RPCs, option selection and grouped lines | Timeout/reopen, quantity changes and stock-conflict acceptance |
| Pricing | Server bill subtotal/total; existing POS price/options RPCs | Test price changes and rounding on real non-production cashier flow |
| VAT / Tax | Tax foundation/UI/receipt code exists on successor; Production schema and save RPC absent | BLOCKED for live VAT: reviewed additive migration, SQL lifecycle tests, Web checkout/receipt/report parity, immutable snapshot protection, offline reconciliation; no rollout performed |
| Discounts | Existing protected pos_apply_discount RPC; native dialog | Discount limit/permission and resulting receipt/ledger acceptance |
| Payment / Cash / Change | Existing shift gate; short-cash UI guard; server PAID+CLOSED/method/tender validation and read-only reconciliation | Non-production cashier E2E with lost RPC and failed refresh |
| Checkout | Existing authoritative pos_take_payment; no automatic second payment | Verify one settlement, closed bill and stock/ledger effects |
| Receipt | Confirmed paid bill only, stored snapshot data, existing HTTPS Print Bridge | Physical printer/bridge and manual/automatic duplicate behavior |
| Sales History | POS-only bills; existing dashboard/item-sales RPCs; hourly rows and escaped CSV | Verify reports against authorized server rows and refunds/voids |
| POS Offline | Actor/shop/device cache and drafts; online server submission required | Does not accept payment or issue paid receipts offline; full offline sale capability is NOT established |
| Sync / Idempotency | Existing atomic offline submit RPC plus durable IDs; uncertain response fixes in this continuation | Android process-kill/auth expiry/concurrency E2E; immutable retry until authoritative outcome |
| Navigation / Back | Parent app BackHandler; existing modal dismiss and POS home action | Phone/tablet back, configuration change and mid-request recovery |
| Tablet / Mobile | Responsive product grid 2/3/4 columns; responsive tables; bounded receipt | Samsung tablet and compact phone screenshots/tap acceptance |
| Web Production Parity | Existing main assets used as oracle; RPC authority preserved | Latest branch Web fix is source only, not a Production deployment; authenticated comparison still required |

## Release blockers and NEXT

1. Exact-head Native POS CI and Native Android Pilot (JVM tests, three APK builds, 16 KB, WebView rejection and emulator lifecycle). Never borrow green status or APKs from a parent commit.
2. VAT is not Production ready. Do not apply the pending tax migrations merely to unblock a client read, or advertise the foundation as live tax support.
3. Non-production authenticated cashier acceptance: open shift → product/options/quantity → discount → tender/change → exactly one PAID/CLOSED bill → receipt → report; timeout, authentication expiry and process-kill recovery.
4. Offline currently means durable drafts; offline paid checkout is not implemented/certified. Do not fabricate offline settlement.
5. Physical printer, tablet/mobile visuals, signing and release certification remain separate gates. No APK requested or delivered in this continuation.

Only after Merchant POS blockers are bounded should Customer/Rider/Admin release blockers resume. No new map, credit, POS or refund engine was introduced.
