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
- Chromium regression: 144 cases at 390/768/1280px, no additional JavaScript errors, overflow or non-Customer content changes. Customer differences remain the previously intended guest navigation changes against the original source baseline.
- Deleted nine superseded POS tab/table/QR CSS rules, unused flex properties and redundant visibility/opacity/important overrides. Eight additional Chromium checks compared computed layout and appearance at 320/390/768/1280px in screen and print modes; all matched before cleanup.
- Applied Supabase migration `20261004004030` (`unified_delivery_kitchen`). Read-after confirmed both permission-checked entry RPCs call the shared transition, the old accepted transition body is absent, and anon/authenticated cannot execute the internal helper.
- Pages run `37165656335` succeeded from main commit `48f1b0ed4444a123ce2d54adc19cfa3043bdef50`; all four live HTML entries plus updated POS/printer JS/CSS matched the source bytes. The subsequent CSS cleanup is validated separately when its deployment completes.

Full release gates above remain FAIL where live/device proof is missing. No remote branch was deleted in this batch; the GitHub browser remains signed out and the connector does not expose branch deletion.

## Final CSS and duplicate query pass

- Deleted 294 earlier inline CSS declarations proven overwritten under the identical selector, condition and property: Merchant 70, Admin 140, Rider 84. Removed empty rules. No new override styles were added. Chromium compared 144 scenarios against the immediately preceding current source: no text/style changes, extra errors or overflow.
- Removed Admin's extra active-shop users query and metadata profile fallback. Admin already fetches the authoritative users list and shop profiles; read-only inspection confirmed the deployed admin profile SELECT policy exists. Users without a profile still retain their existing user metadata. An isolated empty-profile test verifies the user list is fetched once and the shop metadata remains.
- Removed the Rider nearest-job `fallback` field after repository reference tracing found no consumer; nearest-job selection and server claim authority remain unchanged.
- Printer test receipts now contain real line breaks, and the synchronous verified shop-context check no longer yields between context validation and constructing an order request.
- The new-order monitor was additionally checked with a repeated scoped pending-order snapshot: one sound event, not two. Total isolated behavioral checks: 133.
- Remaining acceptance blockers: no authenticated browser session for deleting 16 remote branches; no safe authenticated full E2E/negative RLS/simultaneous multi-connection run; no printer hardware or device lifecycle/INP proof; promotion payment activation requires a real verified payment path; no release signing artifacts or Play listing/privacy/account deletion acceptance. Permission to act was already given; missing session/test access is the limitation.

## Customer session, QR recovery and chat validation

- Customer login requires an authoritative active Customer row; metadata roles no longer grant entry. A stale refresh cannot restore a logged-out session or overwrite another account. Checkout captures the original actor, access token and cart/pending keys so a late request cannot clear a new account's cart. Auth and JSON body reads are bounded and interrupted bodies remain failures.
- Replaced QR cart handling with persisted session/shop/table ownership and notes. Ambiguous checkout retains the request UUID, items and original coordinates. Reopening or expiry can replay the original request through server authority without discarding the cart; successful confirmation alone clears it. Offline, null results, definitive errors and changing-table scans have explicit recovery.
- Prepared `QueueGo-QR-Chat-Validation-Migration.sql` from the current deployed definitions: QR checkout uses POS availability, matching its catalog, rather than delivery availability. The existing chat restriction now checks the actual Customer/Shop/Technician participant explicitly, while preserving Rider post-completion and Admin rules. Existing RLS ownership policies remain. Applied migration `20261004011501` (`qr_pos_availability_chat_participants`); read-after confirmed POS availability and the explicit participant checks. Pages run `37167429723` succeeded from main `74df70102f19a286882c18d5f763fb85aaed45f8`; all four live entries and QR HTML/JS matched source bytes.
- Location pickers are removed when leaving the view; late GPS callbacks cannot act on destroyed maps. Reverse geocoding has an eight-second deadline and only updates the current pin and unchanged connected address field. Location saves capture actor/token and avoid writing local state after navigation or account changes.
- 232 isolated behavioral checks pass, plus 52 parsed JavaScript blocks and 17 local references. New checks cover session races, QR recovery, POS-only menu availability, geofence/request replay, synthetic chat RLS and disposed-map GPS. These fixtures do not certify real credentials, sockets, simultaneous clients, physical GPS or printers. Full release gates remain unpassed where live/device evidence is absent.

## Customer live order lifecycle

- Customer Orders/Order views now use one actor/view-scoped Supabase connection with INSERT/UPDATE filters on the actual customer ID. SDK loading and attachment are single-flight. The Supabase Auth client does not create a second persisted session source. Leaving the view, changing account, going offline or hiding the document removes the connection; old events and late attachment cannot update a new scope.
- Bursts share one read with one trailing refresh. Connected clients reauthorize once per minute without fetching orders; disconnected clients use one 15-second recovery timer. Reconnection refreshes the authoritative view. No old renderer or duplicate polling path was added. Deployed orders publication was verified read-only; actual authenticated socket/network recovery remains an acceptance blocker.
- Route changes render immediate accessible loading feedback; background order updates keep the current content until its replacement is ready. Removed the separate Orders loading render.
- 263 isolated behavioral checks pass, including 31 new scoped subscription, coalescing and lifecycle checks. Physical-device INP, real reconnect and full authenticated E2E are not certified by these tests.

## Customer cart integrity and notes

- Removed five unused map-grid/map-center CSS rules after tracing HTML, JavaScript, globals and repository references; current Leaflet and map container styles remain.
- Validated persisted cart shape, item quantity/price and shop ownership before rendering. Corrupted storage no longer causes the initial screen to throw. Quantity controls now enforce the existing server limit of 99; unrelated-shop replacement clears the prior shop note after confirmation.
- Added persisted order notes to existing checkout, using the current server `p_note` field and its 500-character limit. Ambiguous retries use the original durable note; pending requests disable note editing. This does not introduce a new order flow, pricing rule or business model.
- Storage events for account/cart/pending changes reload only the current actor's cart; in-flight checkout remains bound to its original request. Added 24 cart corruption, quantity, note replay and account/cart storage checks. Total isolated behavioral checks: 287.
- Pages run `37167760486` succeeded from main `62d2d277d8c6eb3198ecf158ff95a4e1bf7415e7`; all four live entry files matched that source. Supabase security advisors still flag exposed SECURITY DEFINER API groups and leaked-password protection; existing privileged entry points retain explicit actor/role checks. These warnings and live authentication/security acceptance are not claimed resolved.

## Customer menu search

- Replaced the shop-name-only Search renderer with the current shop/category/menu search path. Shop/category matching remains in memory; menu reads start after 250 ms and at least two characters, request at most 30 available delivery products, and remain single-flight with a latest-query refresh. Changing query or leaving the view invalidates stale results. Clear, accessible loading, empty and error feedback are included.
- Anonymous read-only verification returned HTTP 200 and zero matching rows in about 12.1 seconds; this is not a performance PASS. Server EXPLAIN ANALYZE for the same predicate scanned six products and completed in about 10.5 ms under the SQL connection; it does not include anonymous RLS/network costs. No guessed index was added. Actual mobile latency and authenticated catalog tests remain required.
- Added 21 isolated search checks for debounce, bounded query count, category/menu discovery, clear, stale responses and exit. Total isolated behavioral checks: 308; source parsing and references pass. The 48-case Chromium regression showed no added errors, overflow or non-Customer changes; the intended Search content differs from the original baseline.
- Pages run `37168048989` succeeded from main `2546f8c4d131b49a2ddb0639b6c2648938c28397`. The remaining browser branch-cleanup path still shows Sign in; no remote branch deletion is claimed.

## Current checkout quote and Customer support

- Replaced Checkout rendering with one shared frame and current fresh/pending states. Fresh checkout reads current menu availability/prices, retains unavailable items for explicit cart correction, and shows changed-price feedback. The submitted subtotal/delivery fee come from the displayed quote captured at tap time. Removed the second shop-profile lookup that could silently recompute a different fee during submission. Existing server validation remains authoritative.
- Decimal subtotal calculation now follows the server's aggregate rounding, including 20.1 × 3 and 1.005 examples, instead of serializing binary float artifacts. No fee, GP, payment model or order state policy changed.
- Pending checkout can recover with an empty current cart and without catalog/shop/GPS reads. It uses the original persisted request, address, notes and quote. Malformed pending storage is removed, and stale completion does not read or change another actor's pending key.
- Saved and manually entered address details are retained when map initialization/pin changes run. Geocoding fills empty or unchanged automatically generated addresses; it no longer overwrites saved house details.
- Customer Profile and Order now link to the existing Support Ticket workflow. The form is scoped to the actual actor/order, uses durable request IDs, locks ambiguous payloads and first reads its own ticket before replaying. It displays the existing Admin status/response. Existing `qg_create_ticket` active-role/order ownership checks and the deployed own-user/Admin SELECT policy were verified read-only. No real ticket or message was sent. Ticket follow-up/chat and account-deletion/legal acceptance still require completion before public launch.
- Replaced the superseded ordinary checkout definition in `QueueGo-Market-Checkout-Migration.sql` with the current deployed public wrapper/core and matching execution grants. There is one transaction body and its cart validator. Their definition hashes match deployed SQL (`ba8936fc8fe6f35e384e759421758772` core; `ef35d74360d603c5629c5459832adee6` public API). Read-only dependency inspection found only the public wrapper calling the core; anon/authenticated cannot execute it. No database write was needed for this source alignment.
- Removed unreachable Customer shop `approved`/missing-status branches after confirming the deployed non-null status constraint only permits pending/active/suspended/deleted/rejected. Actual active-shop behavior remains.
- Added 19 Support Ticket, 30 checkout quote/address/decimal/replay, and 33 isolated current server wrapper/core checks. Together with six malformed-pending checks, total isolated behavioral checks: 396. These do not certify actual JWT/RLS, triggers, simultaneous connections, hardware or physical devices.
- Pages run `37168399248` succeeded from main `513d0287e9a8b35ac0a92dd7cedb62c8864273de`; all four live entries matched that source. Current batch deployment is verified after its commit. The full release gates remain unpassed where authentic E2E/device/legal/signing evidence is absent.

## Checkout response identity gate

- Checkout now requires the response order ID to equal the original persisted request ID before clearing the request/cart or navigating. A mismatched response retains the original request and cart for safe replay. Repeated taps during an in-flight request give immediate wait feedback while preserving the single-flight guard.
- Six additional isolated checks exercise a mismatched response and subsequent successful replay. Total isolated behavioral checks: 402; authentic JWT/RLS and simultaneous server connections remain unverified.
- Pages run `37170235612` succeeded from main `3ffb3dc6414d7158516da34bbf42c34b025f0b37`; byte comparison confirms all four live entries match. Public Chromium navigation again returned `ERR_EMPTY_RESPONSE`, so live console/network behavior is not certified by the isolated browser fixture.
