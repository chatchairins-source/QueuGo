# QueueGo production readiness — current engineering batch

Date: 2026-10-04 (Asia/Bangkok). Audit baseline: `main` at `6b3a07e4176c874e32f30fb51c9b7afe635445fc`. Only `main` was checked out. No old branch was merged or used as a code source. Recovery uses Git history.

**NOT READY — BLOCKERS REMAIN**

A failed gate includes a required test that has not been executed. Isolated browser or SQL tests do not certify real accounts, payments, hardware, RLS, realtime or release packages.

| Full acceptance gate | Status | Evidence / remaining proof |
|---|---|---|
| ZERO LEGACY | FAIL | Proven dead files and superseded renderers deleted; all repository code paths/CSS still require final exhaustive certification; 16 remote branches remain |
| CUSTOMER | FAIL | Checkout/guest/cart/state fixes tested in isolation; complete support, account deletion, notifications, options and live order journey remain |
| MERCHANT | FAIL | Current renderer/router preserved; POS/Market/printer dependencies retained; live order acceptance and alert E2E not certified |
| RIDER | FAIL | Current state and pickup action fixed; actual claim, lifecycle, chat and route E2E not certified |
| ADMIN | FAIL | Old other-role implementations removed; current approval/security/order/support screens preserved in isolated regression; authenticated destructive/negative tests not run |
| POS | FAIL | Current Table POS is the active implementation; isolated view regression passed; multi-device transactions, kitchen, payment and printer hardware not certified |
| QR TABLE | FAIL | Current QR/session/server geofence dependency retained; physical scan/expiry/location and malicious request tests not run |
| MARKET | FAIL | Current Customer banner preserved and shop-first renderer retained; live stock/multi-shop handoff and current checkout semantics require verification |
| SECURITY | FAIL | Server guards and current RPC definitions inspected; authenticated attacks across roles not run; leaked-password protection advisor warning remains |
| RLS | FAIL | All 55 public tables inspected have RLS enabled; role privilege trigger exists; actual cross-user/cross-shop requests not certified |
| REALTIME | FAIL | Current publication and reconnect code traced; stale callback/retry guard added; real socket disconnect/reconnect tests not run |
| PERFORMANCE | FAIL | Source transfer reduced; no measured INP/60fps or mid-range device certification |
| NETWORK RECOVERY | FAIL | Checkout request timeout/replay contract tested; all roles/background/foreground recovery not certified |
| E2E | FAIL | No real authenticated Customer→Merchant→Rider→Completed test was run |
| CONCURRENCY | FAIL | Claim RPC uses server row/group locks, but actual simultaneous multi-connection race tests not run; same-rider cross-order claim serialization fixed in migration; real multi-connection proof still required |
| ANDROID | FAIL | Existing workflow fixes only; no APK/AAB, signing verification or device lifecycle test |
| PLAY STORE READINESS | FAIL | No release artifacts/listing/privacy/Data Safety/account deletion acceptance. Current new-app target requirement is API 36; generated release build must verify it |

## Deleted files

- `QueueGo-Market-TEST-REPORT.md`
- `QueueGo-POS-MVP-Integration-TEST-REPORT.md`
- `QueueGo-POS-TEST-REPORT.md`
- `QueueGo-POS-Tables-TEST-REPORT.md`
- `QueueGo-Table-QR-v1-CHANGELOG.md`
- `QueueGo-Table-QR-v1-TEST-REPORT.md`
- `QueueGo-v23-Rider-Admin-TEST-REPORT.md`
- `android-build/RELEASE_BUILD.txt`
- `android-build/BUILD_SOURCE.txt`
- `android-build/BUILD_TRIGGER_2.txt`
- `android-build/BUILD_TRIGGER_HARDENED.txt`
- `android-build/BUILD_TRIGGER_HARDENED_2.txt`
- `android-build/PERMISSION_BUILD_20261002.txt`
- `android-build/PERMISSION_BUILD_20261002_2.txt`
- `android-build/PERMISSION_BUILD_20261002_3.txt`
- `android-build/PERMISSION_BUILD_20261002_4.txt`
- `laundry/customer-integration.js`
- `laundry/merchant-integration.js`
- `merchant/pos-qr-v1.css`
- `merchant/pos-qr-v1.js`
- `merchant/pos.css`
- `merchant/pos.js`

## Dependency proof and retained modules

- `merchant/pos.js` and `merchant/pos-qr-v1.js` and their CSS had no HTML, dynamic, handler, native-copy or workflow caller before deletion. They contained older bill/QR implementations superseded by the actively loaded `merchant/pos-table-v2.js`.
- `merchant/pos-table-v2.js/.css` implement actual POS, staff, table, kitchen, payment/history and QR actions. Version suffixes are not evidence of disuse.
- `merchant/printer-v1.js/.css` are loaded and provide actual receipt/printer actions; kept.
- `merchant/market.js/.css` and `admin/market.js/.css` are loaded and dispatch actual stock and platform Market operations; kept. Their route/dashboard wrappers were folded into each current router/renderer.
- `table-order.html/.js/.css` remain the QR customer entry and server RPC/session/geofence path.
- `laundry/rider-integration.js` has an actual Rider script reference; retained and packaged in native Rider bundles. The standalone `/laundry/` entry is retained. Customer/Merchant integration files had no production entry, dynamic reference or native build caller; deleted.
- SQL migrations are retained as ordered database history and dependencies. No table, RLS policy, Supabase project or schema was removed based on an old filename.
- Existing session-storage key migrations still serve returning users; they are not retired UI implementations and were retained.
- Realtime outage polling and missing-map recovery are current operational recovery paths, not fallbacks to a retired UI. Responsive CSS for current screens is still active. Historical SQL definitions are migration history.

## Implementations removed or consolidated

- Merchant: old product/editor, promotion package, shop setup/module/settlement implementations, overwritten login, old detail/product fallbacks and copied Admin support/GP code. Current dashboard/order/list/profile/revenue behavior was consolidated into single renderers.
- Merchant: POS route, staff login and navigation join the primary router. Duplicate dashboard/login/route wrapper and hashchange paths were deleted. General order hydration pauses in POS; POS owns its current view subscription.
- Merchant: duplicate minute-level order fetching was removed; the current sync loop and realtime events feed a single new-order monitor. Reconnect callbacks are scoped to the current subscription and use one retry timer.
- Admin: unused Customer, Merchant, technician and Rider UI/handlers, old login/router/order center/GP/delete implementations and old client dispatch timer were deleted. The server rider pool/claim RPCs are the active dispatch authority.
- Admin: current dashboard/governance/GP/Market additions were moved into primary renderers; duplicate privilege guard wrappers were removed while the original guard remains.
- Rider: old direct order PATCH/claim/completion and old earnings renderers were deleted. Current ordinary and Market actions share one dispatch function per action; history cache/chat/support/payment/map dependencies were migrated into primary functions.
- Obsolete markup-specific and identical CSS rules were removed conservatively. A repeated Merchant login photo payload was replaced with a reference to its already-existing CSS variable; the image pixels and appearance are unchanged.
- Root Customer `market.js`, `market.css`, `queuego-market-ai-banner.jpg`, `marketHeroClean` and the old hero text were not restored.

## Core fixes

- Customer order/items are created by existing atomic `queuego_place_cash_order`, not individual browser inserts/deletes. A request ID and snapshot persist before transmission; retry after uncertain outcome reuses the same ID. No compensating DELETE remains.
- Tap lock, bounded request timeout, persistence-before-write, success validation and cart preservation on failure were added. Cart edits pause while an uncertain checkout needs resolution.
- Delivery prices match product delivery prices; checkout shows the existing server fee policy before confirmation. Server price and ownership validation remains authoritative.
- Customer active-order states and labels now include searching/rider-assigned/ready. The existing customer-owned delivery PIN RPC is displayed for delivery handoff.
- Guest discovery is allowed through existing anonymous read policies. Guest cart migrates only when the signed-in cart is empty. Automatic location permission on app launch was removed; explicit pin/GPS controls remain.
- Async Customer views have route generation guards so an older request cannot paint over a newer route. Merchant delayed map mounting is guarded by the original element's connected state.
- Rider active-job queries include the current rider-first states. Pickup only dispatches from server-required `ready`; preparing states keep pickup disabled and navigation points to the shop.

## Financial instruction

`QueueGo-Rider-Completion-Migration.sql` replaces three current completion RPC definitions. It removes merchant acknowledgement as a delivery-completion gate, retains rider assignment/cash advance/PIN/evidence validation, completes cash payment and delivery together, and makes successful completion replay safe. It does not invent a merchant receipt or change GP, fees or rider income.

Applied successfully to the existing Supabase project as migration `20261003174753` (`rider_completion_without_merchant_acknowledgement`) after publishing source commit `e5348ad956787781605879269b6d4e182d9cd497`. Post-deployment introspection confirms all three RPCs have no merchant-receipt gate and retain verified delivery proof checks. No production order was created, completed or modified as a test. Actual authenticated financial E2E remains unverified.

## Current hardened implementations

- Customer uses one scoped order subscription with reconnect recovery, a debounced single-flight menu/category/shop search and one actor-owned persisted cart. Notes, quantity bounds and corrupted state validation are enforced. Explicit GPS/pin controls retain manually entered/saved address details and cancel disposed map callbacks.
- One fresh/pending Checkout frame validates current menu availability/prices, displays the quote submitted to the server and computes decimal subtotals with server-compatible aggregate rounding. Original request ID/items/address/notes/quote persist before sending. Ambiguous recovery works without current cart/catalog/GPS; only a matching order ID clears state. Repeated taps give feedback and retain one request. No direct browser order/item insertion or old renderer remains.
- Existing Support Ticket RPC is linked from Customer Profile/Order, scoped to the actor and selected order. Ticket IDs/payloads persist across ambiguous results; existing own-ticket reads resolve committed requests before replay. No real ticket was sent. Follow-up conversation, account deletion and legal acceptance remain incomplete.
- Merchant/Admin share `role-realtime.js`: one authenticated current-role channel, bounded pinned SDK load, debounced hydration through the existing shared busy/dirty queue, stale callback guards, token refresh and lifecycle cleanup. Both embedded realtime blocks are deleted. Merchant realtime pauses during POS so POS owns that view's channel. Android Merchant packaging copies the shared file and adjusts its relative URL; simulated local script references are complete.
- POS/Printer retain actual active workflows. POS retires failed sockets, reconnects with one scoped timer, updates the existing token and cancels unfinished SDK loading on exit. Printer uses the POS event path, serializes print requests and tracks current kitchen batches. Physical printers and multi-device transactions remain unverified.
- Admin realtime no longer references undeclared subscription state or copied other-role route checks. Its signature includes user approvals/catalog/promotions/settlements/order audit. Approve/Deny, Security Center and Order Management retain their current guarded handlers.
- QR Table persists cart/request/session ownership and replays the original ambiguous request after expiry without inventing a new request/GPS position. Server catalog validation uses `pos_available`, matching the actual POS catalog. Chat participant checks use actual Customer/Shop/Technician ownership while preserving Rider/Admin rules.
- Rider completion removes only the Merchant cash acknowledgement gate; assignment, cash advance, PIN and delivery evidence remain authoritative. Both ordinary/Market claim RPCs serialize on the Rider profile before checking active work. Actual simultaneous connections are not certified.
- Removed 294 overwritten inline CSS declarations, nine superseded POS CSS rules, five unused Customer map CSS rules and unnecessary duplicate query/printer/listener paths. Computed style/layout regression found no unintended changes.

## Database source and deployed changes

- `QueueGo-Market-Checkout-Migration.sql` contains the current deployed ordinary wrapper/core and execution grants, replacing the superseded transaction definition. Hashes match deployed definitions: core `ba8936fc8fe6f35e384e759421758772`; public wrapper `ef35d74360d603c5629c5459832adee6`. Only the wrapper calls the core; anon/authenticated cannot execute the core. This source alignment needed no database mutation.
- Applied migrations: `20261003174753 rider_completion_without_merchant_acknowledgement`, `20261004000119 rider_claim_profile_serialization`, `20261004004030 unified_delivery_kitchen`, `20261004011501 qr_pos_availability_chat_participants`. Server ownership/role/state/price/QR checks remain. No production order, payment, claim or ticket was created or changed for tests.
- Actual query indexes already cover customer/time, shop/status/time, rider/status/time, order-item/order and delivery/order reads. No guessed index was added. Existing SQL migrations remain ordered schema/RPC/RLS history.

## Current verification evidence

- Isolated behavioral checks: **516**, plus source parsing of 50 JS blocks/files and 19 local references. Coverage includes checkout replay/quote/ownership/decimal validation, cart/search/support, transport interruption, rider completion/claim, delivery kitchen, POS/printer, QR/chat, map disposal, Customer live runtime and Admin/Merchant realtime. These tests do not certify real JWT/RLS, triggers, simultaneous server connections or hardware.
- Chromium fixture regression: latest 48 route/boot cases at 390px, no additional JS errors, overflow or non-Customer changes. Earlier 144 cases covered 390/768/1280px; POS computed screen/print layout checks covered 320/390/768/1280px. All external writes are intercepted. Intentional Customer guest/search changes differ from the original baseline.
- Public Chromium navigation returned `ERR_EMPTY_RESPONSE`; live console/authenticated network behavior is not certified. Read-only HTTP checks and source parity are separate evidence.
- Anonymous menu query returned HTTP 200 in approximately 12.1 seconds; SQL EXPLAIN for the six-product predicate completed in approximately 10.5 ms. This does not pass mobile latency/INP/60fps targets and did not justify another index.
- Current Customer banner is preserved. The old root Market assets and deleted POS generations remain absent. No test script is loaded by application HTML or native packaging.
- Native workflow fixes include main-only manual release builds, required QR/laundry/shared dependencies, modern permission handling and release signing configuration. No APK/AAB/device/store verification or submission has been performed.

## Current source sizes

These are source bytes/zlib bytes, not field performance measurements. The new shared runtime is 5696 source bytes and is loaded by Merchant/Admin.

| Entry | Baseline bytes | Current bytes | Compressed baseline → current |
|---|---:|---:|---:|
| `index.html` | 172,341 | 195,852 | 113,646 → 121,008 |
| `merchant/index.html` | 471,336 | 321,403 | 229,879 → 139,945 |
| `admin/index.html` | 515,014 | 225,969 | 125,581 → 59,705 |
| `rider/index.html` | 257,099 | 237,324 | 124,196 → 119,689 |

## Production operations and remaining blockers

- Work is committed only to `main`; no old branch is merged or used as a source. Pages runs `37170704780` and `37170876862` succeeded from `70234cbc495f02446a6b68e4ba3a0aa97cb688cb` and `266ba0d3c49d5f8b9246ae510bb87317ee006fa6`. The four entries and deployed POS JS matched source after completion. Admin run `37171243234` succeeded from `c33b25df333758457a8be666268314a0b81e5ecd`. The shared-runtime batch is checked after its commit. Direct Pages-settings API access remains unavailable; deployment/source parity is verified independently.
- No remote branch deletion is claimed. GitHub connector can update main but exposes no branch-delete capability. The browser still shows Sign in; secure sign-in previously timed out. No backup/archive/new branch was created.
- Real E2E/negative/concurrency tests need an isolated test deployment/database and four-role test identities. Only the production Supabase branch exists; no billable staging branch was created and no load test ran on real orders.
- Physical printer/QR/GPS tests, Android/iPhone/Samsung/tablet lifecycle, measured mobile performance, fresh install, signing artifacts and independent user acceptance remain.
- Customer account deletion, notification/follow-up/options gaps and privacy/retention/Data Safety/legal acceptance remain; no pricing, GP, payment model or legal policy was activated by these changes.

The 16 remote branches still requiring deletion:

- `android-apk-build-20261001`
- `backup/before-customer-restore-20261003-1615`
- `backup/customer-before-checkout-button-20260930`
- `backup/pre-current-ui-all-pages-20261003`
- `backup/pre-location-pixel-rebuild-20261003`
- `backup/pre-rider-first-20260930`
- `backup/pre-shop-restoration-20261003`
- `backup-2026-09-29-pre-fix`
- `backup-2026-09-29-pre-production-hardening`
- `customer-legacy-removal-clean-rebuild`
- `customer-owner-legacy-removal-restart`
- `export-customer-files-20261003`
- `fix-pos-table-qr-2026-09-29`
- `hardening-2026-09-29`
- `pre-apk-hardening-rc`
- `rider-first-order-flow-rc`

**NOT READY — BLOCKERS REMAIN**

## Merchant route ownership and login isolation

- Deleted the separate shop route firewall and its duplicate hash/startup handlers. It rejected the current `market-stock` route despite the primary router supporting it. The primary router remains the route authority.
- Admin rejection now runs before the POS route branch. Login verifies the actual own-user profile alongside staff membership and rejects Admin, suspended and deleted profiles before persisting a Merchant/Staff session. Active staff and ordinary Shop login remain supported; no payment/GP/state policy changed.
- Seventeen isolated route/login checks cover Market navigation, Admin POS rejection, staff/Shop login and inactive accounts. Latest total: 510 isolated behavioral checks; live Auth/RLS remains unverified.

## POS staff snapshot ownership

- Removed the second staff-list query after snapshot hydration and the per-user staff detail query before editing permissions. Both now read the already-loaded current shop's staff snapshot; role changes still use the existing server-authorized RPC and reload the snapshot after confirmation.
- Six isolated checks confirm list rendering without another query, role update/reload, and rejection of a staff ID outside the current snapshot. Total: 516 isolated behavioral checks. Real multi-device/RLS evidence remains required.
