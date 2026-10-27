Warning: truncated output (original token count: 82589)
Total output lines: 1935

## 2026-10-27 Checkout Places call-site compile fix — source correction; exact-head CI NEXT

- Native Pilot run `40472996941` passed Full QueueGo regression and source/asset checks, then failed Kotlin compilation at `QueueGoCustomerApp.kt:1006,1212`. Exact compiler errors: Location Picker was missing required `searchPlaces`; Checkout still passed the removed `searchPlaces` parameter.
- Cause was the prior edit removing the first matching callback in the app file, which belonged to Location Picker. Restored Places callback to Location Picker, removed it from Checkout, and strengthened source guard to assert each call site separately. Checkout map/GPS/location/note remain unchanged.
- **NEXT:** inspect exact-head Kotlin compile and tracking JUnit result, then APK, 16 KB and emulator lifecycle; no APK or visual-completion claim yet.

## 2026-10-27 Native source regression follow-up — cache-aware guard; exact-head CI NEXT

- The rerun on the checkout parity checkpoint fixed the early-reference error, then Full QueueGo regression exposed a stale source assertion at `tests/native-android-source.cjs:740`. It still required unconditional shop/banner downloads even though the current Native Customer refresh deliberately reuses a fresh Home cache and skips those static requests.
- Updated the guard to require cache freshness gating for shops/banners/service banners, preserve parallel order refresh, and retain Guest Home's parallel initial read path. This keeps the newer download-reduction behavior while aligning source contracts to it. Checkout UI change remains unchanged.
- **NEXT:** inspect the new exact-head Native Pilot regression result; then continue compile, APK, packaging, and emulator checks. No APK or visual acceptance claim until those gates finish.

## 2026-10-27 Checkout parity guard correction — regression source fix; exact-head CI NEXT

- Parent Native source included `e0fcd4009d7b1e02ae642a33d97c82ade42d5e34`, which removed the extra Checkout Places field while retaining Places in the dedicated Customer Location Picker.
- Exact Native Pilot push `40472155179` reached Full QueueGo regression and failed at `tests/native-android-source.cjs:193`: the new assertion referenced `customerLocationPicker` before its later declaration. Earlier regression contracts passed; no APK or Kotlin source failure was reached.
- Corrected the guard to read `CustomerLocationPicker.kt` inline at the assertion site. This preserves the check that Checkout has no Places panel and the dedicated picker keeps it. No product logic change in this correction.
- **NEXT:** inspect exact-head Native Pilot Full QueueGo regression first; if green, continue Kotlin/JVM, APK, packaging and emulator gates. Customer authenticated screenshot and physical-device status remain open.

## 2026-10-27 Customer Checkout Web parity — source checkpoint; exact-head CI NEXT

- Parent Native successor HEAD: `e0fcd4009d7b1e02ae642a33d97c82ade42d5e34`; PR #730 remains open/draft; Production Web remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Source audit confirmed Web Checkout has the QueueGo map, GPS action, delivery address and note; the picker attaches Places with `searchControl:false`. Removed the extra Places search panel from Native Checkout so the visible control order matches Web. The dedicated Native location picker retains Places search; map pin/GPS, reverse-geocoded address, note, fee, and server submit/recovery remain.
- Updated `tests/native-android-source.cjs` to guard this UI contract and its retained Location Picker search. Updated Customer parity matrix and PROJECT-MEMORY. No Web/backend/schema/business-authority changes.
- Source commit `e0fcd4009d7b1e02ae642a33d97c82ade42d5e34` has PR Native Pilot `40472086491`, push Native Pilot `40472081080`, and PR Map Engine `40472086542` queued; Rider authenticated review readiness is PASS, not screenshot acceptance.
- **NEXT:** inspect exact-head Pilot and Map Engine first confirmed failure only. Continue Customer Shop/menu/cart and checkout/tracking source and visual comparisons; Customer authenticated credentials and physical-device checks remain open.

## 2026-10-27 Preserve Native exact-head gate runs — source checkpoint; exact-head CI NEXT

- Parent Native successor HEAD: `c1fa9f5a63f880446bda5c2c0a146a05fb615aaa`; PR #730 remains open/draft; Production `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Confirmed why exact-head Native Pilot and Map Engine runs repeatedly ended cancelled while the successor advanced: both workflow concurrency groups used `cancel-in-progress: true`. Changed both to preserve the active run and leave the newest pending run queued. Added a source guard for both workflow policies.
- CI scheduling only; no product behavior change. Cancelled runs did not establish a source failure. No Production data/schema mutation.
- Customer authenticated visual review remains blocked by missing Actions credentials; emulator CI does not certify physical-device or visual parity.
- **NEXT:** inspect exact-head Native Pilot and Map Engine on this source commit. Fix only the first confirmed failure, then continue the Customer P1 parity matrix.

## 2026-10-27 Customer Market GPS refresh deduplication — source checkpoint; exact-head CI NEXT

- Parent Native successor HEAD: `c32b37d333556f2dce7a50f60d4233cbf0d71c7d`; PR #730 remains open/draft; Production `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Confirmed root cause in `QueueGoMarketScreen.kt`: the main market-data `LaunchedEffect` depended on latitude/longitude while loading the complete markets, shops, and catalog sets. GPS updates therefore cancelled/restarted all three remote reads despite the local cache being hydrated.
- Removed location coordinates from that refresh key. Cache-first display remains; account changes and explicit/lifecycle refresh still reload. Active trip, pricing, availability and checkout remain server-backed. Added a source guard for the request trigger.
- Exact-parent Native Pilot and Map Engine runs were cancelled during emulator lifecycle capture as the successor advanced; Rider authenticated review succeeded. Run both release gates on this source commit. No Production schema/data mutation.
- Customer authenticated screenshot review remains blocked by missing Actions credentials; emulator CI does not certify physical-device or visual parity.
- **NEXT:** inspect exact-head Native Pilot first. Fix only its first confirmed failure; if it passes, continue auditing remaining repeated, non-authoritative reference downloads.

## 2026-10-27 Merchant local-first reference categories — exact-head CI PASS

- Current source HEAD `9e9068429c60ce08b37bc9daa126cc38cbe875e5` on `work-native-web-parity-20261026`. Exact PR Native Pilot `40464819987`, push Native Pilot `40464814881`, and Map Engine `40464819951` all completed SUCCESS. PR #730 remains open/draft; Production main is `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- The Customer and Merchant Grocery category references and Merchant Market catalog categories now load from a bounded local cache before server refresh. Merchant Market categories refresh once per visible screen lifecycle instead of on every search/page request. Current product pages, prices, availability and add-to-shop/order actions remain server-backed.
- Exact Native evidence includes full regression, Kotlin unit tests, all three APKs, 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle. Exact Map Engine runtime PASS. These are CI emulator/source gates, not physical-device or visual-parity certification.
- Local `node tests/native-android-source.cjs`: 379 checks PASS; `git diff --check` PASS. Gradle is unavailable in this workspace. No Production schema/data mutation.
- Customer authenticated screenshot review remains blocked by the recorded missing GitHub Actions credentials; Rider/Merchant emulator screenshots do not certify Customer visual parity.
- **NEXT:** audit the remaining screens for repeated, non-authoritative reference downloads. Cache only display/reference data; keep live order, inventory decision, price, financial and dispatch authority server-backed. Continue the broader authenticated Customer parity work where credentials permit.

## 2026-10-27 Merchant Grocery category local-first cache — source checkpoint; exact-head CI NEXT

- Parent exact Native successor HEAD: `dcfcc43273548874ba48ee266b067c981486ba8a`. Before this change, Native Pilot run `40461138799` and Map Engine run `40461134860` both completed SUCCESS on exact parent HEAD. PR #730 remains open/draft; Production `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Merchant Grocery master-catalog category names are now read from and written to the existing owner-scoped durable content file, with the shop ID checked both ways and a 200-row/100-character bound. Cached categories appear before server refresh. Fresh server categories replace them; catalog item pages remain online/server-backed and no inventory, price, finance, order, or dispatch authority is cached.
- Customer's per-shop Grocery category cache from the preceding checkpoint remains in the same successor. Local `node tests/native-android-source.cjs`: 378 checks PASS; `git diff --check` PASS. Gradle is unavailable locally; Kotlin, APK and emulator gates must come from hosted exact-head Actions.
- No Production schema/data mutation.
- **NEXT:** inspect exact-head Native Pilot and Map Engine for the new branch tip. Fix only the first confirmed failing step. If green, continue auditing the remaining bounded reference-data downloads; keep operational state server-authoritative.

## 2026-10-27 Customer Grocery category local-first cache — exact-head CI in progress

- Current Native successor: `work-native-web-parity-20261026`; source checkpoint `19b1392998e82e41ec3c3c201b9a334ff936b08f`, parent `3585fa937cc2c06112046bf577041a299f88fe01`. PR #730 remains open/draft; `main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Confirmed the exact parent Native Pilot run `40458248182` and Map Engine run `40458248211` completed SUCCESS. The branch tip had advanced beyond the older checklist checkpoint before this continuation.
- Customer grocery category names are now stored in the existing bounded durable content cache, scoped per shop. A cached category list is rendered before fresh categories/products return; fresh nonempty server categories replace and refresh it. This is display/reference data only; current products, price, availability and checkout remain server-authoritative. Guest and signed-in flows use the same behavior.
- Added a coroutine regression for immediate cached-category display and fresh replacement, plus a source guard. Local `node tests/native-android-source.cjs`: 377 checks PASS; `git diff --check` PASS. Gradle is not installed in this workspace, so Kotlin/APK/emulator evidence is delegated to exact-head Actions.
- No Production schema/data mutation.
- **NEXT:** inspect only the first confirmed failure, if any, on exact-head Native Pilot `40461039903` and Map Engine `40461039883`; both are running on the source checkpoint. If green, continue the next safe local-first reference-content gap and keep live order/financial/dispatch state uncached.

## 2026-10-27 Android local-first cache + silent data refresh — source checkpoint; exact Pilot NEXT

- Current Native successor: `work-native-web-parity-20261026`; checkpoint HEAD `26a9c5ab6042dab897aeaaeeccd388a649d2455b`.
- Owner rule restored as an Android-wide UX contract: data refresh must be visually silent. Existing/cached content stays on screen; data-loading spinners/progress chrome are removed. Action progress such as login/save/upload/checkout remains explicit.
- Shared remote images now use durable app storage under `noBackupFilesDir` instead of expiring from Android cache every 24 hours; the bounded disk LRU remains. Existing legacy cached images migrate forward when read.
- Customer already had durable Home/shop/Market content cache; Merchant now has durable local-first shop/menu cache. Rider retains its durable home snapshot. Live orders, finance and dispatch remain server-authoritative and are not converted into stale durable decision state.
- Customer Laundry had a confirmed duplicate-download path: choosing a hub called `api.services()`, which fetched the full laundry catalog again although the first catalog already includes `servicesByHub`. Added durable laundry catalog cache, load-cache-first/background-refresh, and direct service reuse from the current catalog. Hub selection no longer redownloads the catalog.
- Silent-refresh regression coverage now includes Customer primary/Market/Laundry/Shopping/Promotions/tracking, Merchant primary/Catalog/POS/Revenue/Support/Stock/Media/Modules/Tickets/Laundry/Hours/Setup Map, Rider Earnings/Messages/Support/Safety, and shared role-shell loading chrome.
- Exact-head Rider authenticated review is green at the latest observed checkpoint; exact Native Pilot/Map CI for the final successor is still pending. No Production data/schema mutation was made.
- **NEXT:** inspect exact-head Native Pilot first confirmed failure only. If green, continue durable caching for remaining safe static/reference content without persisting live order/financial/dispatch authority. Keep Customer authenticated visual review and physical-device/GPS/release gates separate.

## 2026-10-27 Customer Notifications page-entry parity — source/test/CI PASS; authenticated Rider/Merchant recapture NEXT

- Source commit `869da7a219d74dea05d6b598f02021fdd8b142e1` is the latest fetched descendant on `work-native-web-parity-20261026`; `origin/main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`. Exact-head PR Native Pilot `40450263531`, push Native Pilot `40450259446`, and Map Engine `40450263616` all completed SUCCESS. Native gates include regression, Kotlin/JVM, three APK builds, 16 KB packaging, WebView rejection and Android emulator launch/map/voice lifecycle.
- Rider artifact `12708029912` captured 11/11 expected screens and Merchant artifact `12708139641` captured 5/5 on exact review HEAD `92bf10a2`; actual contact sheets and representative Rider/Merchant screenshots were inspected. Metadata states `physical_device:false`, no Production order/dispatch mutations and `visual_parity_certified:false`. Customer authenticated review remains blocked by missing `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`.
- Web Production `renderNotifications()` fetches the authenticated user's notifications, PATCHes owned unread rows to read on page entry and always renders “อ่านทั้งหมด”. Native reuses the existing authenticated own-user `notifications?user_id=eq.<user>&is_read=eq.false` PATCH; it now triggers that action when unread IDs enter the Notifications screen and keeps the manual control visible even with no unread rows. It does not add a table, endpoint, RPC or authority.
- Regression evidence: clean-checkout `npm ci && npm test` completed through `posttest`; `node tests/native-android-source.cjs` passes 361 checks; `git diff --check` passes. No Production data was changed.
- Readiness-only authenticated review `40450259433` on `869da7a` succeeded with capture not requested. **NEXT:** add the repository-required `[authenticated-review]` marker on the current successor, wait for authenticated Rider/Merchant capture, inspect actual screenshots before making any UI changes, then continue the next unblocked Customer parity row. Customer visual review is still blocked by missing `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`; readiness-only is not screenshot acceptance.

## 2026-10-27 Rider/Merchant authenticated capture PASS; Customer Notifications source audit COMPLETE

- Published descendant `92bf10a2cfdc2a9c585c76989d32b7dce528523b` is parented on the green harness-fix commit `a7ceb1adf6de262e825cfab29657dfaeb6e4e96d`. Exact-head PR Native Pilot `40446858417`, push Native Pilot `40446853549`, and Map Engine `40446858313` completed SUCCESS. Authenticated read-only run `40446853492` completed SUCCESS after the transient SDK archive retry was needed on the preceding attempt.
- Actual exact-head artifacts: Rider `12708029912` captured all 11/11 expected screens, Merchant `12708139641` captured all 5/5; metadata source SHA matches `92bf10a2`, `missing_screens` is empty, `physical_device:false`, Production order/dispatch mutations false, and `visual_parity_certified:false`. I inspected actual contact sheets plus Rider `03-profile-laundry.png`, `08-learning-center.png`, and Merchant `01-home.png`: Laundry remains server-backed OFF, extra-tools accordion screens now capture, and no new Rider/Merchant mismatch was found. This is emulator review evidence, not physical-device or parity certification.
- **NEXT:** the source-confirmed Customer Notifications parity gap is implemented in the newer checkpoint above. Continue with its exact-head CI result, then audit the next unblocked Customer P1 row. Customer authenticated Profile/Notifications visual review remains blocked by missing review credentials.

## 2026-10-27 Customer Profile contact parity — source/test/Pilot PASS; authenticated recapture NEXT

- Recovered from repository source of truth: `origin/main` is `7e036a0735cc49905e9b0a2434492470551a0c9d`; PR #730 remains open/draft with newest `work-native-web-parity-20261026` HEAD `6de3a93eb92a304ea9eb574aea17247339ac9ccd`. No newer successor branch tip was found after fetch. Exact-head PR Pilot `40439224823`, push Pilot `40439220389`, and Map Engine `40439224755` all completed SUCCESS on `6de3a93e`.
- Rechecked Web Production Profile: it renders `phone || email` below the customer's name. Supabase `public.users` has `phone` and no `email`; Native now reads that existing own-user phone field and uses authenticated Supabase Auth email only as fallback. No schema, RPC, backend authority or Production mutation was added.
- `NativeUser` retains Customer phone/email in the existing encrypted role session; validate refreshes phone from the user row and retains Auth email. Synthetic `@auth.queuetech.local` values are suppressed. Profile renders the optional contact line using Web priority. Added focused contact precedence/blank/synthetic-address JVM cases and source guards. Source commit `de32099c8c540b6fb22dfd1632ef2871048529fc` is parented directly on the latest successor.
- Local `node tests/native-android-source.cjs` passes 358 checks; `git diff --check` passes. Exact `de32099c` PR Native Pilot `40441166424`, push Native Pilot `40441160644`, and Map Engine `40441166419` completed SUCCESS. This includes regression/source, Kotlin/JVM, all three APKs, 16 KB, WebView rejection and emulator launch/map/voice lifecycle. Emulator evidence is not physical-device acceptance.
- Customer authenticated screenshot acceptance remains BLOCKED by missing GitHub Actions secrets `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`. The readiness-only reviews are not screenshot evidence. Do not claim physical-device PASS.
- Readiness-only review run `40441160801` on `de32099c` succeeded but capture was skipped because no marker was present. Marker `08a4a407` then requested authenticated Rider/Merchant capture; see the newer harness-failure checkpoint above.
- **NEXT:** the Rider capture harness fix above supersedes this checkpoint.

## 2026-10-27 Customer Profile cleanup; latest Pilot and Rider/Merchant capture PASS

- Recovered on newest published descendant `9f6d54f81416cee63fc4b44987e42cc8fe39e446` (PR #730 remains open/draft; no later successor was present after fetch). `origin/main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`; no newer Native descendant or main update was found.
- Exact-head PR Native Pilot `40433753021` and push Native Pilot `40433747432` completed SUCCESS on `9f6d54f8`; full regression/source, Kotlin/JVM, three APKs, 16 KB, WebView rejection and emulator laun…72589 tokens truncated…ion save/reopen, physical GPS/font-scale/Android visual and signing/Play/Production backend gates remain BLOCKED; owner must use an approved real Rider session and actual phone for those acceptance steps.

## 2026-10-19 actual Android idle-recenter runtime failure
- Latest source PR439187ac19d/tree536479b9 preserves Home PR4389e16277f. Settings source/Web full npm pre/test/post EXIT0; Web editor published as main65638570b670b3949457d2fd8317bf4f65d8c0a5/treee282a6827e2d07e0eb529b91c07ed1a5e8d69c12, awaiting Pages/RC/live verification.
- Actual parent9e Native run39195851635 map-display job117878415155 FAILED. First real failure: RiderMapRuntimeTest assertSameCamera line102, initial SDK zoom16 versus resumed17 at line47. Artifact12161594769 retains actual frames/cameras. JVM/three APK/16KB/five SQL races and isolated navigation/MapLibre gates passed; this is NOT a green containing Native run.
- Root path: idle recenter used a transient 180ms follow-state pulse. It could be overwritten/expire before the map style/GPS was ready, leaving the fallback camera on first load. Existing renderer now receives the durable UI recenter command identity in its existing render state; lastState retains it before SDK/style readiness and consumes it only after scheduling a real camera update with a valid fix. No idle continuous follow, new map engine or business state; policy and strict continuity assertions unchanged.
- Backup backup/rider-before-idle-recenter-20261019 at actual published187ac19d. Focused navigation/source273/runtime-budget contracts PASS. Actual Android compilation and the unchanged first-load/resume/recreate instrumentation MUST pass on this successor before claiming repaired. Full local regression next; do not merely rerun the known-failing parent or weaken zoom assertions.


## 2026-10-20 Native Merchant Market Master Catalog parity
- Status: IN PROGRESS; branch work-native-market-catalog-parity-20261020; parent PR439 HEAD38baf74c78a5881a225df5264a9fd28be485bf9b. Parent Native39197043125 and Map39197043133 are SUCCESS. Web-only main contains no native-android tree, so it is not a Native merge source.
- Evidence: Native had market stock management but its catalog action routed to the generic category editor, with no qg_market_master_categories/qg_market_catalog_page/qg_market_catalog_add callers. The live Web catalog already exposes128 populated catalog previews.
- Change: existing Products action now opens a Native Market catalog for market/meat/fish/vegetable/fruit; Grocery flow retained. Reads the SAME deployed public guarded RPCs,50/51 rows, search/categories, added-state and clean catalog_preview_image. Add form supports1/2/3kg plus custom decimal weight, price/cost/stock/minimum and existing GP display.
- Recovery: app-private no-backup atomic intent file is persisted/fsynced off Main before the RPC. Lost response/recreation replays identical UUID/body; matching server product UUID is required before clearing. Store isolates actor/shop, rejects changed pending bodies and malformed input. Backend remains authoritative for inventory, idempotency and eligibility; no new mutation/table/dispatcher.
- Tests: existing Grocery source24 and three-app source273 PASS. New JVM behavior tests cover50/51, malformed rows, fractional2.25kg, invalid prices/weights, durable UUID reuse, mismatched retries, actor/shop isolation and corrupt files. Actual Kotlin compilation/JVM/full Native CI PENDING; local Android SDK/Gradle absent.
- NEXT: full local regression from committed tree; exact-head Native Pilot compilation/unit/APK/16KB/emulator and Map gates; repair first failure. Physical approved/pending-owner save/reopen/stock/font-scale acceptance and signed Play release remain separate gates.


## 2026-10-20 Native Market first exact-head runtime failure
- Exact PR491 HEADa5c0bf8a7986d5510c33e5b93b5a5d0794d83188 / Native39395017610: full regression, Kotlin/JVM tests, all three APK builds,16KB and fresh-session phone/small/large launch matrix passed. Containing run FAILED during Customer system-kill recovery; no full Native PASS claim.
- First failure: capture-rider-launch.py line792 expected no Customer PID after am-kill + run-as SIGKILL; artifact12252380039 reports mode=run-as-sigkill and PID4838. Prior PID values and kill return codes were not saved, so evidence cannot yet distinguish a failed signal from a new process starting.
- Diagnostic successor records original PIDs, each kill return code/stdout/stderr, poll observations and failure logcat/process/services snapshots. The original death assertion is unchanged; no gate is bypassed. NEXT: run exact successor and diagnose from recorded evidence before changing runtime or acceptance criteria.
- Additional catalog context fence: before catalog-page/add/recovery calls, compare existing qg_market_owner_shop with the selected Native shop. This rejects stale archived/replaced shop context. Live unique shop_profiles_one_current_per_owner index is confirmed; no new backend or RLS changes. A JVM test covers matching, replaced and absent shop IDs.
- Full local regression on first source tree EXIT0; focused Grocery24, three-app source273 remain PASS after context guard. Exact successor JVM/runtime evidence is required next.


## 2026-10-20 Credit / Ride rule-guard reconciliation
- Status: IN PROGRESS; branch work-native-credit-rule-guard-reconcile-20261020; newest Native integrationbb9f41e6ccc99e9acd0e56744db050f4bf53182c contains merged PR491. Its exact code64a8a2b6 passed Native39396673821 including JVM,3 APKs,16KB and real Android lifecycle/system-kill recovery. Integration recheck39398702208/39398702254 running.
- Root cause: isolated13-migration credit rollout followed by the actual later Ride17 guard definition rejects credit feature keys; that definition also dropped the earlier continuous-dispatch numeric keys/bounds. Empty13-chain replay alone did not cover later migration replacement.
- Change: CLI-generated forward migration20261020091222 restores the union of existing credit/dispatch and current Ride tariff validation. No activation, balances, business rows, grants or old migration edits. Production NOT APPLIED.
- Test: actual SQL13-chain plus later guard and forward reconciliation PASS; checks valid/invalid credit flags and dispatch values, preserved tariff bounds/ACL/security mode, repeated apply and unchanged real platform rules. Full local suite requires a clean committed checkout because release-evidence tests deliberately reject dirty sources; initial uncommitted run stopped at that guard.
- NEXT: commit, full clean-checkout regression and exact hosted CI. Reconcile the complete deployed migration history before any financial/service rollout; this isolated fix is not Production cutover approval.


## 2026-10-20 Map HTTP runtime resource bounds
- Status: DONE (bounded source/local socket test); hosted exact-head Map CI and public release remain separate.
- Base: newest fetched Native map successor `3ffd43f1` on `work-native-route-bundle-carriageway-lock-20261020`; no Web/native tree overlay.
- Root cause: `ThreadingHTTPServer` created an unbounded daemon thread per accepted connection, and accepted sockets had no read timeout. A client stalled before finishing headers/body could retain runtime threads indefinitely even though parsed JSON bodies were capped.
- Change: existing shared service now caps concurrent accepted workers at16, expires socket inactivity after15s, and returns503/Retry-After when admission is full. Every worker exit releases its slot. No route algorithm, dataset, GPS display, booking, finance or Production change.
- Validation: `python tests/map-http-resource-bounds.py` PASS9/0 with actual loopback stalled-header/stalled-body/overload/recovery/size-limit requests; `map-engine-trace-safety.py` PASS12; Python compile PASS. Socket tests do not certify graph accuracy or physical road behavior. Existing Map workflow executes the new test.
- BLOCKED (hosting): approved hostname/VM access/TLS owner/package store are still required by `docs/map-staging-deployment.md`. Public Web main currently still calls Longdo RouteService; switching to QueueGo `/map-api/route` requires a reachable approved runtime, not just merging a client button.
- NEXT: exact successor hosted Map CI; run existing `stage_container.py` against approved host/package, terminate TLS with request/rate limits, verify `/map-api/health` and real Buriram route, then forward only the reviewed shared transport to newest Web main and configure Native endpoint. Physical GPS/U-turn/parallel-road certification remains BLOCKED.


## 2026-10-20 six-lane integrated successor
- Canonical branch: `work-native-release-six-lane-integrated-20261020`. Base includes carriageway PR #510 plus merged Map runtime PR #512 (`fc146950d44afa67a34020cdfaa2a7e4a1b2f1b4`). This successor replaces the temporary parallel #513/#514 heads for continued integration; no reset/revert/force/cherry-pick was used.
- Lane 1 Rider Native Navigation: forward-ported only the tested bounded display-hold delta after heading/carriageway rejection plus current manifest/source guards.
- Lane 2 QueueGo Map: PR #512 exact Map Engine run 39428225752 / job 118646115172 SUCCESS and merged; runtime files are unchanged in this successor.
- Lane 3 Admin: latest main audit found no new full-table hydration or DB polling regression; Admin sync is 60s-bounded and suppressed while Realtime is subscribed, Support refresh is visible-page/newest-cursor only. No speculative Admin rewrite added.
- Lane 4 Credit/Top-up: restored the existing shared QueueGoCredit surface on Merchant Revenue and Rider Earnings Web; Native Merchant/Rider shared credit cards already existed and remain unchanged. Added source regression so later UI rewrites cannot orphan those mounts. No provider/schema/balance/Production activation.
- Lane 5 Grocery/Market: PR #491 exact green implementation is contained; compare to this successor shows no later Market/Grocery implementation rewrite. Preserve existing RLS, approved-image and idempotent-add contracts.
- Lane 6 Passenger Ride/Express/Dispatch: inherited release-unified implementation remains contained; after PR #497 only navigation/UI files changed, so existing exact green race evidence remains the baseline while this integrated Native full regression runs.
- STATUS: SOURCE INTEGRATION ACTIVE. Exact-head Native Pilot required. Physical GPS/road, authenticated real-account flows, provider settlement, signing/AAB, Play Closed Testing, public map HTTPS and Production cutover remain BLOCKED until real evidence/approval.
- NEXT: inspect exact-head Native Pilot. If red, fix only the first actual failure on this branch; if green, use this branch as the sole successor for subsequent six-lane work and close superseded temporary PRs after preserving their evidence.


## 2026-10-21 PromptPay Web publication / Native client successor
- Web source222538752a1cf273aee3e47e03bea45306c5ea8a PR528 merged ascee5a58d7505b1e810d717687c060a6d02beb74f. Full local Web npm test EXIT0, exact RC39523018771 SUCCESS including mobile WebKit; Pages39523292975 SUCCESS. Actual served queuego-credit.js and admin/credit.js equal tested files.
- Backend: queuego-credit v2 + signed queuego-credit-webhook v1 deployed. Existing database ledger retained. Rider grant migration20261021003910 applied; authenticated active-Admin/live-session guards, durable request IDs and audit. Isolated Rider ledger16 plus Web Rider form4, Merchant38, Edge82, Web37 checks passed; no real bank settlement/grant-to-owner was claimed.
- Readiness: environment_ready=true, live_key_configured=false, webhook_signing_configured=false, live_intake_enabled=false. Both existing credit feature flags remain false. BLOCKED: OMISE_SECRET_KEY (LIVE), OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED, provider LIVE PromptPay and webhook signing configuration. No secrets fabricated, no charges or money seeded.
- Native scope: strict descendant of current credit021caf67 (PR526), preserving its passed Native run39520235790. Sync shared Merchant/Rider client only: server pending UUID restoration, provider recheck through original queuego-credit endpoint, authoritative summary, explicit terminal statuses, same one-balance display. Public Web remains on main; no old Web snapshot published from Native branch.
- Evidence: native source282 and Edge82 PASS. NEXT: exact Native full regression/three-app build; signed bank webhook and actual Owner payment/persistence acceptance remain blocked by provider settings above. Do not claim LIVE DONE.


### Rider Grab-benchmark navigation / pickup / payment completion (2026-10-22)
- Status: IN PROGRESS
- Predecessor PR: #623
- Active branch: `work-native-rider-grab-benchmark-20261022`
- Current checkpoint HEAD: `3f9a4d55baf43bd1123866f7d1b8556da37805ae`
- Successor base: PR #620 / `f64ad6b2c85e04a2c6016c452753d264c4b6cee3`
- Completed source checkpoints:
  - off-route requires >30 m sustained for 3 seconds on Native and Web;
  - display road snap capped at 25 m; beyond that use real Rider position/bearing;
  - Android current + following maneuver HUD, U-turn guidance and 150 ms crossfade;
  - Android/Web merchant receipt proof is durable and explicit “ได้รับอาหารแล้วใช่ไหม?” confirmation gates pickup;
  - Web proof compression target <=500 KB with IndexedDB recovery;
  - Android/Web full-swipe completion requires 100%, haptic feedback and 200 ms bounce-back when incomplete;
  - completion summary uses authoritative Rider payout and real rating summary only;
  - Android and Web pan now resume navigation follow after 8 seconds idle; return animation is <=500 ms.
- Map Engine off-route policy now also uses >30 m sustained for 3 seconds and rejects navigation decisions above 35 m GPS accuracy.
- Source tests added/updated:
  - `NavigationGuidanceTest.kt`
  - `NavigationViewModeTest.kt`
  - `RiderNavigationHudTest.kt`
  - `tests/native-rider-delivery-success-parity.cjs`
  - `tests/queuego-map-web-camera.cjs`
- Evidence status: source assertions committed; this `work-native-*` successor exists specifically to run exact-head Native Pilot CI.
- Release blockers: physical Android road/GPS test, ANR/OOM/battery evidence, Production/public runtime gate remain mandatory before launch-ready claim.
- NEXT: finish Web current/next maneuver HUD using QueueGo route `legs[].steps`, then run exact-head source/CI gates; fix only the first failure before any further release claim.


## Rider Android authenticated visual review — 2026-10-24

- Branch: `work-native-rider-auth-review-20261024`, based on the latest Native Rider/route-bundle checkpoint.
- Added a dedicated secret-gated Android Emulator workflow and capture script for authenticated Rider Home map, Messages/Notifications, Earnings for today/7/30 days, Profile, Learning Center, Safety Center, Support, and Diagnostics.
- The capture script uses a verified pinned real Buriram PMTiles archive in debug-only app storage; it does not tap online/accept/start/complete controls or save credentials/screens UI hierarchy.
- Added the same debug-only map archive to Native Pilot capture so its map lifecycle is exercised instead of recorded as blocked.
- Screenshot evidence status: **not captured yet**. The repository Actions secrets `QG_RIDER_REVIEW_PHONE` and `QG_RIDER_REVIEW_PASSWORD` must exist before the authenticated job can run. The current GitHub connector cannot create Actions secrets.
- NEXT: configure those two repository secrets, then rerun the Rider authenticated review workflow and inspect the four screenshot artifacts. Until then, login and all post-login screens remain unverified.
## 2026-10-27 Native local-first cache checkpoint — exact-head Pilot PASS

- Current successor branch `work-native-web-parity-20261026`, source HEAD `cd94ffcf23c232d5ffaa1053180ae6ff9aa6a9fc`; PR #730 remains open/draft. The branch tip is unchanged from the inspected exact HEAD and descends from the current PR base and the earlier Native rebuild successor.
- Exact-head Native Pilot run `40473824690` (#4486) completed SUCCESS: Full QueueGo regression, tracking coordinate/terminal-state Kotlin tests, Customer/Merchant/Rider APK builds, Android 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle all passed. Exact-head Map Engine run `40473824733` (#535) also completed SUCCESS.
- Local `node tests/native-android-source.cjs`: 398 checks PASS. Existing local-first coverage persists safe Customer/Merchant content, shared remote images and bounded reference caches; routine data refresh stays visually silent. Orders, prices, stock decisions, balances and dispatch remain server-authoritative. No Production writes.
- This is CI/emulator and source evidence; it does not certify authenticated Customer screenshots, physical Android devices, road/GPS behavior or release readiness. Customer authenticated screenshots remain blocked by the recorded missing review credentials.
- **NEXT:** audit remaining Customer/Merchant/Rider remote reads for repeated downloads of non-authoritative display/reference data. Record each exact callsite and trigger; cache only a confirmed repeatable gap, and keep live order/financial/inventory/dispatch decisions server-authoritative. Run exact-head Native Pilot after each source change.


## 2026-10-27 Customer Favorites Web parity — source checkpoint; exact-head Pilot NEXT

- Current successor branch parent: `5680a9d4d8d490061efc18df89fce43a042fff8c` (PR #730). Source audit of Production Web `origin/main` confirmed Favorites renders the item name, product price when applicable, and a filled heart removal control; it does not render product/shop thumbnails or shop-category metadata.
- Native Favorites had added thumbnails, shop-category text, and a text `นำออก` button. Updated the screen to match the Web row content and red heart action, keeping existing authenticated favorite toggle, stale local cache, unavailable-item removal, and shop navigation behavior.
- Added a scoped source contract for the Web-aligned row. Local `node tests/native-android-source.cjs`: 399 checks PASS; `git diff --check` PASS. No business authority or Production data changed.
- Exact parent Native Pilot run `40475975171` and Map Engine run `40475975073` are being inspected; parent Pilot has reached emulator lifecycle without a confirmed failure. Do not treat parent CI as validation of this new source change.
- **NEXT:** commit this bounded source chunk, inspect terminal results for the parent exact-head Native Pilot/Map Engine, then publish this successor and run exact-head Native Pilot for the new commit. Continue the Customer Shop/menu/cart parity matrix after the source gate.

## 2026-10-27 Customer order-tracking contact parity — source checkpoint; exact-head CI NEXT
- Compared Production Web `customer-features.js` tracking action with Native `CustomerOrderTrackingScreen.kt`: Web's `linkPhone` omits the shop contact action when the phone value contains no digit or `+`; Native previously rendered the action whenever an order context contained a shop, including missing/blank phone values.
- Native now applies the same usable-phone guard while preserving its existing in-app voice call action. Added a source regression contract for this boundary; `node tests/native-android-source.cjs` passes 400 checks and `git diff --check` passes.
- Parent Favorites exact-head Native Pilot `40478110875` and Map Engine `40478110860` were still running at checkpoint time; Native was in Full QueueGo regression and Map Engine in Dependencies and regression. No failure observed yet.
- No Production data, pricing, dispatch, or backend authority changed.
- **NEXT:** inspect parent exact-head runs, publish this bounded source successor and run exact-head Native Pilot + Map Engine. Then continue the remaining Customer Shop/menu/cart parity matrix; do not claim visual/device acceptance without its evidence.
