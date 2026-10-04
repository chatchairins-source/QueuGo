# QueueGo production readiness — current engineering batch

Date: 2026-10-04 (Asia/Bangkok). Source base: `main` at `6b3a07e4176c874e32f30fb51c9b7afe635445fc`. Only `main` was checked out. No old branch was merged or used as a code source. Recovery uses Git history.

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

## Verification evidence

- `npm test`: 52 JS blocks parsed, 17 static local file references checked, 18 isolated critical-client checks, 20 completion SQL checks, 21 claim SQL checks, 21 transport interruption checks. The SQL fixture tests wrong PIN, wrong rider, invalid GPS/evidence, missing advance, rollback, no merchant receipt, multi-shop completion and replay.
- Initial cleanup-only DOM regression: 40 route/boot cases matched the original source with no extra errors.
- Final Chromium regression: 144 route/boot cases at 390, 768 and 1280px, zero additional JavaScript errors and zero unplanned screen differences. Customer guest/navigation behavior intentionally differs from baseline. A later identical-image CSS-variable deduplication changes no pixels. Fixtures intercept all external operations; no production order or test user is created. Public browser smoke could not run because Chromium network navigation returned `ERR_EMPTY_RESPONSE`; live console/authenticated network behavior is not certified.
- Workflow YAML and embedded Python parse checks passed. Native role packaging now includes referenced QR/laundry files. The permission bridge that bypassed Capacitor and requested permissions on startup was deleted; Capacitor 8's actual `BridgeWebChromeClient` handles geolocation on use and file selection. Release signing is attached to `buildTypes.release`.
- Customer embedded banner/image payloads match original `main` exactly. Anonymous production reads for shop profiles, available products and shop open states returned HTTP 200. All four deleted POS/QR generation URLs and the three previously deleted root Market assets return HTTP 404. No authenticated transaction was performed by these checks.
- Actual query indexes were inspected: customer/time, shop/status/time, rider/status/time, order-item/order and delivery/order indexes already exist. No speculative index or RLS change was applied.
- No source-integrity test, SQL fixture, or browser fixture is loaded by application HTML or native packaging. Tests are development-only.

## Source transfer changes

Sizes are source bytes and zlib-compressed bytes, not device INP measurements.

| Entry | Before bytes | After bytes | Removed bytes | Compressed before → after |
|---|---:|---:|---:|---:|
| `index.html` | 172,341 | 175,032 | -2,691 | 113,646 → 114,500 |
| `merchant/index.html` | 471,336 | 328,891 | 142,445 | 229,879 → 141,966 |
| `admin/index.html` | 515,014 | 234,214 | 280,800 | 125,581 → 61,847 |
| `rider/index.html` | 257,099 | 239,125 | 17,974 | 124,196 → 120,050 |

## Remote operations and release blockers

- No remote branch has been deleted in this checkpoint. Git transport has no push credential; the connected GitHub API can update `main` but exposes no branch-delete operation. Browser fallback was authorized. The observed GitHub browser is signed out; secure sign-in timed out and no successful login was verified. No backup/archive/new branch was created.
- GitHub Pages deployment run `37141760482` succeeded on `main` at `e5348ad956787781605879269b6d4e182d9cd497`. All 16 checked live HTML/JS/CSS entry/dependency files match the published source byte-for-byte, including Customer, Merchant, Rider, Admin, Market, POS, printer, laundry Rider integration and table-order. Customer live SHA-256 is `aeb1ff570cdfdf1e3ef1783320dadca605f086c6c048c3eab532221baeaa9c80`. This proves served-source parity; it does not certify authenticated flows or access to Pages settings (the settings API is unsupported).
- Real E2E/negative E2E/concurrency needs an isolated test deployment/database and four-role test identities; it must not run against real production orders. The Supabase branch listing contains only production `main`; no billable staging branch was created.
- Full Android lifecycle, physical printer/QR, Samsung tablet/iPhone Safari, field INP, fresh install and user acceptance tests remain.
- Do not release or call this batch closed-beta ready. No store submission or old artifact deployment was performed.

The 16 remote branches still requiring deletion are:

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


## Follow-up cleanup and claim hardening

Source commit: `6c3425de551ad7e21560cb791160244d512eaa01`. Claim serialization applied to the existing Supabase project as migration `20261004000119` (`rider_claim_profile_serialization`). Post-deployment introspection verifies both RPCs lock the Rider profile before the active-job check. No production order was claimed or altered for testing. Pages run `37163579710` succeeded on `main` at this source commit; all 16 checked live entry/dependency files matched that published code byte-for-byte.

- Deleted the disabled Admin online-ready `/api/sync/events` outbox/API prototype, session-only Rider presence writers, Rider heartbeat/exit/storage listeners, stale presence cleanup timer, unused online diagnostics/notification wrapper and their dead CSS. The current Admin Rider panel still reads `users` and `rider_profiles` from Supabase; its current monitoring refresh remains.
- Deleted Admin's unreachable Merchant promotion renderer/form/bid board/demo payment and unused category helper. The Admin promotion stop action now has only its Admin caller/renderer.
- Deleted the reachable `ADMIN-DEMO-*` promotion activation/payment fabrication and its button. Approval/rejection/end states remain. Promotion activation requires a real server-verified paid path before reintroduction; no replacement payment or pricing model was added. This is an explicit remaining feature blocker.
- Added 15-second cancellation to the existing Merchant/Admin/Rider Auth, table and core RPC request implementations. Abort/timeout while reading a response body is propagated, preventing a cancelled mutation from being reported as successful `null`. No automatic mutation replay or second request layer was added. Full lifecycle/recovery verification is still required.
- `QueueGo-Rider-Claim-Serialization-Migration.sql` preserves existing ordinary/Market claim definitions and locks the caller's Rider profile before checking existing active work. Both paths take the same profile row lock; existing order/group locks still arbitrate competing Riders. No price, fee, GP or state semantics changed. This closes the identified same-Rider/different-order precheck race by serialization; simultaneous-connection certification is still pending.
- Added 21 isolated claim checks for roles, online eligibility, competing ownership, notifications, current-job exclusivity, Market capacity, atomic group assignment and rollback. Local multi-process PostgreSQL could not start because the execution environment forbids switching to a non-root OS user; PGlite cannot certify simultaneous connections. No production race/load test was performed.
- Repeated Chromium regression across 144 cases: no additional JavaScript errors or unplanned non-Customer screen changes. Customer differences are the previously approved guest/navigation changes. The subsequent demo-activation removal affects only approved promotion rows; source tests pass and the missing production payment path is reported above.
- Deleted `android-build/RELEASE_BUILD.txt` after removing its only caller, the push-triggered Android release build. Both native workflows are now main-only, manually dispatched builds so core cleanup commits do not automatically build native packages before the release gates pass. No backup branch or fallback renderer was created.

## References

- [Feature matrix](FEATURE_MATRIX.md)
- [Supabase leaked-password protection](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)
- [Google Play current target requirements](https://support.google.com/googleplay/android-developer/answer/11926878)

## POS / KDS and printer cleanup — 2026-10-04

- The deployed POS delivery kitchen RPC still used `pending → accepted → preparing`, unlike Merchant's rider-first `pending → searching_rider → rider_assigned → preparing → ready`. Replaced its separate transition body with the same internal transition used by Merchant; entry RPCs retain independent owner/staff and shop checks. No pricing, GP, payment model or Supabase project changed. The internal helper is SECURITY INVOKER with EXECUTE revoked from PUBLIC, anon and authenticated.
- POS KDS now fetches and displays searching/assigned states and enables preparing/ready only when the rider assignment exists. The delivery query uses the same delivery-channel boundary as the server.
- POS reloads now share one in-flight read, apply a complete snapshot, ignore stale reads after exit/account change, and request one follow-up refresh when an event arrives during a load or write. Subscription attachment is also single-flight and scoped to the current shop, actor and view. Foreground refresh uses the document visibility event.
- Deleted Printer's second orders subscription, repeated Supabase/CDN setup, shop lookup cache, hash listener, delayed auto-print timers, polling mount loop and unused global open/print aliases. POS's actual subscription forwards order events and mounts/removes printer controls with the POS lifecycle. The hidden button and display override were deleted.
- Replaced the printer global busy early-return, which discarded concurrent jobs, with a serial queue. Automatic kitchen printing identifies the current item batch and prints only that batch; receipts retain all items. Leaving/switching shops invalidates queued and in-flight reads. Print Bridge requests have a 15-second timeout. No automatic retry of an ambiguous physical print was added.
- POS owner snapshots feed the existing new-order alert monitor; no additional order query or sound handler was added. Removed unused alert timer/busy variables.
- `npm test`: 130 isolated behavioral checks (80 previous + 25 delivery kitchen + 25 POS/printer lifecycle), plus 52 parsed JavaScript blocks and 17 local references. These are synthetic SQL/browser fixtures and do not certify deployed RLS, actual socket recovery, concurrent database connections or hardware.
- Chromium quick regression: 48 cases, no additional JavaScript errors or non-Customer content changes. The eight Customer differences remain the previously intended guest navigation changes against the original source baseline.

Full release gates above remain FAIL where live/device proof is missing. No remote branch was deleted in this batch; the GitHub browser remains signed out and the connector does not expose branch deletion.
