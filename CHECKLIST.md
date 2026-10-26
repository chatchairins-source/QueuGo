Warning: truncated output (original token count: 50981)
Total output lines: 1286

## 2026-10-26 Customer Profile destinations and Favorites — IN PROGRESS

- Web blueprint: `main=7e036a0735cc49905e9b0a2434492470551a0c9d`. Native owner branch `work-native-web-parity-20261026` was fetched at `5e589cecc4f11a4dbf27d8c595aa9952ad630e28`, still the newest branch head and a descendant of the prior Native successor.
- Parent exact-head Native Pilot `40268085994` on `5e589cecc4f11a4dbf27d8c595aa9952ad630e28`: **SUCCESS** — full regression, tracking tests, all three APKs, 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle.
- Root cause/evidence: Web Profile exposes address, orders, notifications, Favorites, promotions and support. Native Profile previously exposed support, deletion and logout only. Favorite toggles already use `qg_customer_favorites`, but no Native Favorites route/list existed.
- Bounded source change: add Native Profile links matching Web labels; add a Favorites list backed by existing authenticated favorites/shop/product reads; keep unavailable favorites visible and removable; Android Back from Favorites returns to Profile. No backend, production data, or second navigation authority was added.
- Focused checks on the working tree: `node tests/native-android-source.cjs` PASS (323 checks); `node tests/native-customer-web-home-parity.cjs` PASS (55 checks); `git diff --check` PASS.
- First full `npm test` attempt stopped at the evidence bundler because its clean-checkout precondition rejected the working tree; no product assertion failed. Rerun on clean source checkpoint `1ca5c7475a573fed5e0119384cf820970aa92b77`: **PASS**, including map asset provision, complete regression, Grocery, release evidence bundler and map field-evidence gate.
- **NEXT:** publish a normal descendant of `5e589ce...`; run Native Pilot on the exact remote commit and record APK/emulator evidence. Notification settings/privacy and Customer route-specific Back/process-recreation P1 remain open.

## 2026-10-26 Customer Guest protected-action parity — DONE (source/test/CI)

- Web blueprint: fetched `main=7e036a0735cc49905e9b0a2434492470551a0c9d`; newest Native owner branch `work-native-web-parity-20261026` was verified at `297ab952c3d94090c9026f6f696404a7a03f9182`, a descendant of the branch's prior Native checkpoint.
- Root cause: Web persists a post-login destination only for checkout. Guest Ride/Orders/Laundry/Market submit must return Home. The exact-head regression initially failed on two stale assertions that still required Android-only cart/checkout callbacks; runtime behavior was already Web-aligned.
- Bounded change: source assertions now require the shared `customerGuestPostLoginDestination` policy; no runtime or backend change was made in this test-fix checkpoint.
- Focused: `node tests/native-android-source.cjs` PASS (321 checks); `node tests/native-customer-web-home-parity.cjs` PASS (55 checks); `git diff --check` PASS.
- Full local `npm test`: PASS on clean SHA `17c65d51999ba00ed7e84e02b1e896f0c48fb8db`, with the same source tree as the Pilot head.
- Exact-head Native Pilot `40266778666` on `297ab952c3d94090c9026f6f696404a7a03f9182`: **SUCCESS** — full regression, tracking tests, all three APKs, 16 KB packaging, WebView rejection, and emulator launch/map/voice lifecycle. Artifacts: APKs `12626459492`; emulator evidence `12627496112`.
- Visual status: public Web Home/Food/Search were inspected at GitHub Pages; this Pilot captures Rider lifecycle, not Customer screenshots. Customer paired Android screenshots and physical-device parity remain open.
- **NEXT:** close Customer Profile/Favorites/notification-settings P1, then fix route-specific system Back/process-recreation parity. See `docs/native-customer-web-parity-matrix.md`; do not advance to Merchant until Customer P0/P1 are closed.

## 2026-10-26 Guest protected-action parity — local full regression PASS

- Web blueprint: fetched `main=7e036a0735cc49905e9b0a2434492470551a0c9d`; Native owner branch remains `work-native-web-parity-20261026` and is a descendant of the fetched remote head.
- Root cause/evidence: Native Pilot `40265752567` exposed a stale Market login test; after that contract was corrected, exact-head run `40265972696` exposed a second stale assertion that expected a literal checkout callback instead of the shared Web-aligned destination policy. The runtime continues to preserve checkout as the sole post-login destination.
- Change: remote descendant `ba5138d3b8bef147adaa313099eb1f7673b63f58` updates that second source assertion. No runtime or backend behavior changed in this checkpoint.
- Focused tests on merged exact local HEAD `702f4c90b4d656b13565a91d20ccd89d057206e5`: `node tests/native-android-source.cjs` PASS (321 checks), `node tests/native-customer-web-home-parity.cjs` PASS (55 checks), `git diff --check` PASS.
- Full local `npm test` on clean HEAD `17c65d51999ba00ed7e84e02b1e896f0c48fb8db`: PASS, including 12 pinned map assets, full repository regression, Grocery suite, release evidence bundler and map field-evidence gate.
- **NEXT:** run Native Pilot on the exact pushed successor. If green, mark Guest protected-action policy DONE and resume the Customer page-by-page Web ↔ Android inventory and evidence-backed P0/P1 selection. APK/device evidence is pending that run.

## 2026-10-26 Guest protected-action parity — second exact-head stale assertion FIXED (test-only)

- Failing exact HEAD: `6b337be6c234f1dcb15b2c93bd4c0c31b080d1f2`; Native Pilot run `40265972696`.
- First real failure: full regression advanced past the Market stale contract, then `tests/native-android-source.cjs:224` still searched for literal `onCheckout = { onLogin("checkout") }`.
- Evidence: runtime still preserves checkout through `customerGuestPostLoginDestination("checkout")`; the dedicated Web-parity guard remained green at `55 checks / 0 failures`.
- Bounded fix: test-only assertion now verifies checkout persistence through the shared Guest destination authority. Runtime and backend remain unchanged.
- Resolution: exact-head Native Pilot `40266778666` on `297ab952c3d94090c9026f6f696404a7a03f9182` passed full regression, APK, packaging, WebView, and emulator lifecycle gates.

## 2026-10-26 Guest protected-action parity — exact-head first failure FIXED (test-only)

- Failing exact HEAD: `3fb69567e4ff5994d3f79d285dfdb014d4112de8`; Native Pilot run `40265752567`.
- First real failure: full `npm test` reached the new Web-parity guard successfully (`55 checks / 0 failures`) and then failed in `tests/native-android-source.cjs:172`, whose older assertion still required `onLogin("market-cart")` and claimed Guest Market checkout must resume the cart after login.
- Root cause: stale regression contract. Web main `marketPlaceOrder()` sends an unauthenticated user to login without persisting a Market destination, and Web login preserves only `checkout`; therefore the old test encoded Android-only behavior.
- Bounded fix: test-only. The source regression now requires Guest Market submit to use `customerGuestPostLoginDestination("market-submit")`, rejects literal `onLogin("market-cart")`, and preserves public Market browse/cart. Runtime implementation from `3fb69567e4ff5994d3f79d285dfdb014d4112de8` is unchanged.
- Resolution: after both stale assertions were aligned with current Web policy, exact-head Native Pilot `40266778666` passed; runtime was unchanged.

## 2026-10-26 Native Customer Web parity — authenticated Laundry global chrome DONE (source/test)

- Implementation HEAD `3becc3b0adef21041773e021c08e84543ccb0f91` restores the Web shell contract for authenticated Laundry without changing Laundry business logic.
- Root cause/evidence: Web `V.laundry` renders inside the normal Customer layout, so the QueueGo global top bar remains visible and the compact bottom navigation remains visible with Home selected. Native previously hid both whenever `screen == "laundry"`.
- Change: removed only the two Native Laundry chrome hide conditions. Laundry remains mapped to Home selection and default compact navigation density; service/order logic is unchanged.
- Test/CI: Native Pilot run `40264624269` **SUCCESS** — full regression, source integrity, Kotlin unit suites, all three APK builds, 16 KB packaging, WebView rejection, artifact upload and emulator launch/map/voice lifecycle all passed.
- Status: **DONE (source/test)**. Physical-device visual certification is not claimed.

## 2026-10-26 Native Customer Web parity — Guest protected-action login policy IN PROGRESS

- Parent exact HEAD `3becc3b0adef21041773e021c08e84543ccb0f91` is green as above.
- Root cause/evidence: Web main `7e036a0735cc49905e9b0a2434492470551a0c9d` persists a post-login destination only for `checkout`. Guest Ride/Orders/Laundry and any other protected route authenticate then land on Home. Web Market browsing/cart are public, but `marketPlaceOrder()` sends a Guest to login without persisting `market-cart`, so successful login also lands on Home. Native still preserved `market-cart` on Market submit and silently ignored protected banner `#ride` / `#express` routes.
- Change: add one `customerGuestPostLoginDestination` authority. Checkout alone returns `checkout`; every other protected Guest action returns `home`. Ride/Laundry/Orders/shop actions/Market submit and protected banner routes use it. Public Guest browsing and authenticated business flows are unchanged.
- Regression guard: parity blueprint + `tests/native-customer-web-home-parity.cjs` now reject Android-only Ride/Market resume, require Ride/Express banner protection and preserve checkout as the sole resumed destination.
- Production/backend: unchanged.
- **NEXT:** run exact-head Native Pilot. Fix only the first real failure. If green, checkpoint this P1 flow parity, then continue Customer Guest top-bar visual parity (Home 16/10; non-Home 12/6; brand tracking -0.4sp; plain red login link).

## 2026-10-26 Native Customer Web parity — Guest Ride login destination DONE (source/test)

- Stream: Android/Web functional parity, Customer only. Active branch `work-native-web-parity-20261026`; Web authority remains pinned to `main=7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Root cause/evidence: current Web protects Guest `ride` and, unlike checkout, does not persist a post-login Ride destination; successful login returns Home. Native Guest preserved `ride`, creating an Android-only post-login flow. Web and Native already agree that Guest orders/laundry return Home and checkout alone resumes checkout.
- Change: Native Guest Ride now calls `onLogin("home")`. Authenticated Ride routing/screen implementation is untouched. The pinned parity blueprint now records Guest protected-route login destinations and the deterministic regression rejects restoring the Android-only Ride resume.
- Implementation HEAD: `2376155ae0806302e62bbb53db59900f85efaca3`.
- Test/CI: Native Pilot run `40263250008` **SUCCESS** — full QueueGo regression including route-parity guard, source integrity, Kotlin/tracking tests, all three APK builds, Android 16 KB packaging, WebView rejection, artifact upload, and emulator launch/map/voice lifecycle all passed.
- Independent local full `npm test` on source HEAD `2376155ae0806302e62bbb53db59900f85efaca3`: PASS, including 12 pinned map assets, repository regression, Grocery suite, and map field-evidence checks. This is supplemental to the exact-head Native Pilot evidence above.
- Status: **DONE (source/test)**. Physical-device parity is not claimed.
## 2026-10-26 Native Customer Web parity — authenticated Laundry global chrome — DONE (source/test)

- Stream: Customer Android ↔ Web parity only. Web blueprint `main=7e036a0735cc49905e9b0a2434492470551a0c9d`; current Native branch includes the Guest Ride checkpoint above.
- Root cause/evidence: the deterministic parity gate requires authenticated Laundry to retain the Web shell's QueueGo top bar and compact bottom navigation with Home selected. Native previously hid both controls on `screen == "laundry"`.
- Change at `3becc3b0adef21041773e021c08e84543ccb0f91`: restored both existing shell controls, preserved Laundry content/behavior, and expanded the parity blueprint and assertions from 42 to 48 checks.
- Focused parity at `3becc3b0adef21041773e021c08e84543ccb0f91`: `node tests/native-customer-web-home-parity.cjs` PASS, 48 checks / 0 failures.
- Full local `npm test` at docs-only checkpoint `4beafbd843a3f0fb57c4014653166b919f6f9db8` (same runtime tree as `3becc3b0adef21041773e021c08e84543ccb0f91`): PASS, including the 12 pinned map assets, complete repository regression, Grocery checks and map field-evidence checks.
- Exact-head Native Pilot run `40264624269` for `3becc3b0adef21041773e021c08e84543ccb0f91`: **SUCCESS** — full QueueGo regression, source integrity, Kotlin/tracking tests, Customer/Merchant/Rider APK builds, 16 KB packaging, WebView rejection, emulator launch/map/voice lifecycle. APK artifact `12625303845`; Android launch evidence artifact `12626645648`.
- Status: **DONE (source/test)** for authenticated Laundry shell chrome. This is not full Customer screenshot/visual parity or physical-device parity certification.
- **NEXT:** complete the page-by-page Customer Web (`main=7e036a0735cc49905e9b0a2434492470551a0c9d`) ↔ Android inventory and deterministic parity matrix; capture/review critical unblocked screens, mark credential-blocked authenticated screens explicitly, then select the first evidence-backed P0/P1 gap.

## 2026-10-26 Native Customer Web parity — nearby shop authority DONE (source/test)

- Stream: Android/Web visual + functional parity, Customer only. Active branch `work-native-web-parity-20261026`; Web blueprint stays pinned to `main=7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Root cause/evidence: Web Home “ร้านใกล้คุณ” admits only `food` and `cafe`. Native previously approximated this with an exclusion filter, which could admit Laundry/other future service categories and diverge from Web.
- Change: Native Customer Home now uses the explicit Web allowlist `setOf("food", "cafe")`; distance sorting and shop-card presentation are unchanged. The deterministic Web-parity regression now rejects an exclusion-based approximation.
- Implementation HEAD: `71af23c9505dd77b5d57636098dc37491e11cbff`.
- Test/CI: Native Pilot run `40261856409` **SUCCESS** — full QueueGo regression, Customer Web parity guard, source integrity, Kotlin/tracking tests, all three APK builds, Android 16 KB packaging, WebView rejection, artifact upload, and emulator launch/map/voice lifecycle all passed.
- Status: **DONE (source/test)**. Physical-device pixel parity is not claimed.
- NEXT: continue Customer only. Fix Guest Ride post-login destination parity: current Web protects `ride` and returns to Home after login, while Native Guest currently preserves `ride`. Change only Guest login destination for Ride to Home; keep Ride implementation/routing intact for authenticated entry points. Add deterministic route-parity assertions, then run exact-head Native Pilot.

## 2026-10-26 Native Customer Web parity — Bottom navigation geometry DONE (source/test)

- Stream: Android/Web visual parity, Customer only. Active branch `work-native-web-parity-20261026`; Web blueprint remains pinned to `main=7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Root cause/evidence: Native bottom navigation imposed Android-only row geometry instead of the Web Home/default density contract.
- Change: Native bottom navigation now preserves Web labels/order and matches Web-equivalent outer padding, item padding, icon box, 20 dp icon and 10/9 sp labels without an Android-only fixed row height or explicit label line-height.
- Implementation HEAD: `bd03119ea967f886aef1e3dd48a17dd1232d0f0d`.
- Test/CI: Native Pilot run `40260504415` **SUCCESS** — full QueueGo regression, Customer Web Home parity gate, source integrity, Kotlin/unit suites, all three APK builds, Android 16 KB packaging, WebView rejection, artifact upload, and emulator launch/map/voice lifecycle all passed.
- Status: **DONE (source/test)**. Physical-device pixel parity is not claimed.
- NEXT: continue Customer only. Match Web Home “ร้านใกล้คุณ” authority exactly: Web includes only `food` and `cafe`; Native must not admit Laundry/Grocery/Market/Shopping or other service categories merely by exclusion. Add a deterministic parity guard, then run exact-head Native Pilot.

## 2026-10-26 Native Customer Web parity — Home category geometry DONE (source/test)

- Stream: Android/Web visual parity, Customer only. Active branch `work-native-web-parity-20261026`; Web blueprint remains pinned to `main=7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Root cause/evidence: after category authority/order parity, Native category tiles were wider than the Web Home contract and allowed labels to wrap. Current Web uses 64 px tile width, 13 px gap, 54 px circle, 25 px icon and 10 px one-line label.
- Change: Native category tile width is now 64 dp; category labels are single-line/non-wrapping. Existing 54 dp circle, 25 dp icon, 13 dp gap, 10 sp label and all routes are preserved.
- Implementation HEAD: `693aa1a6ccc3c5abb2155bb3198c53195e75320e`.
- Test/CI: Native Pilot run `40259408078` **SUCCESS** — full QueueGo regression, Customer Web Home parity gate, source integrity, Kotlin/unit suites, all three APK builds, Android 16 KB packaging, WebView rejection, artifact upload, and emulator launch/map/voice lifecycle all passed.
- Status: **DONE (source/test)**. Physical-device pixel parity is not claimed.
- NEXT: continue Customer only. Match bottom navigation geometry to Web: preserve exact labels/order, use Web-equivalent outer padding (home 6 dp / other 4 dp), item padding (3 dp / 2 dp), icon box (24 dp / 22 dp), icon 20 dp, label 10 sp / 9 sp, and remove Android-only fixed row height / oversized line-height. Then run focused parity + exact-head Native Pilot.

## 2026-10-26 Native Customer Web parity — Home category authority DONE (source/test)

- Stream: Android/Web visual parity. Active branch `work-native-web-parity-20261026`; Web blueprint is current `main=7e036a0735cc49905e9b0a2434492470551a0c9d`. Native successor descends from tested `65918d8ca7dbe720127e1c915a0483c3f209cb01`; no legacy branch, WebView, Longdo or duplicate implementation was introduced.
- Root cause/evidence: current Web Customer Home exposes exactly 7 service tiles in order — อาหาร / เครื่องดื่ม / ร้านขายของชำ / ฝากซัก / ตลาดสด / บริการเรียกรถ / ช้อปปิ้ง — while Native exposed an Android-only eighth ส่งด่วน tile and different ordering. Express remains a real capability but is not a Web Home tile.
- Change: Customer Native Home now renders the same 7 routes/order from an explicit Web-parity list. Express routing and `CustomerExpressScreen` remain intact off Home. A pinned parity blueprint records exact Web source SHA instead of treating the Native branch's older bundled `index.html` as authority. The stale Express regression was reconciled to the same contract.
- Exact-head implementation: `96e81a0911a90c461bf991c59e042f594e60d654`.
- Test/CI: Native Pilot run `40258319078` **SUCCESS** — full QueueGo regression, Customer Web Home parity, Express capability regression, source integrity, Kotlin/unit suites, all three APK builds, Android 16 KB packaging, WebView rejection, artifact upload, and emulator launch/map/voice lifecycle all passed.
- Status: **DONE (source/test)** for Customer Home category authority/order. This does not claim physical-device visual certification.
- NEXT: continue Customer only. Match category-tile geometry to Web Home blueprint: 64 px/dp tile width and one-line non-wrapping labels while preserving 54 dp circle, 25 dp icon, 13 dp gap, 10 sp label and all routes. Then rerun exact-head Native Pilot before moving to the next Customer visual gap.

## 2026-10-25 Native Customer checkout QueueGo Places parity — DONE (source/test)

- Implementation HEAD `4e7028be0cfc734828a8762628bb860840176093` extends the reconciled shared Places/Search successor without touching Rider compass/navigation.
- Customer checkout now uses the same shared `QgPlaceSearchPanel` / `QueueGoNativeApi.searchPlaces` authority as the other location pickers. Search stays outside the map canvas; selecting a result updates coordinates/address atomically, recenters the existing QueueGo map, and preserves reverse-geocode behavior for later map movement.
- Exact-head Native Pilot run `40183176581`: **SUCCESS** — full regression/source guards, Kotlin unit suites, all three APK builds, Android 16 KB packaging, WebView rejection, artifact upload and emulator launch/map/voice lifecycle all passed.
- This is source/test evidence only. No physical-device navigation, real GPS/road drive, signed Play release or Production data mutation is claimed.
- **NEXT:** run the docs-only checkpoint HEAD, then mark PR #724 ready. Keep global Customer shop search on its current operational shop source until QueueGo Places shop ingestion is complete; do not regress it to the currently sparse Places POI set.

## 2026-10-25 Native shared QueueGo Places Search + Reverse — IN PROGRESS

- Successor remains a strict descendant of Rider compass PR #718 head `4d56aa3ba7b1efcee6073820c36fa31902212aee`; Rider compass/navigation implementation files remain untouched.
- Production Thailand service boundary revision 2 and Web Places/Search source/attribution were already merged to `main` at `2941dff7fdf5585963b09123695dc20afcd37da4`; main RC, Chat Retention and Pages all passed.
- Shared Native reverse authority: `QueueGoNativeApi.reversePlaceAddress` uses public `qg_search_places`, bounded to Thailand coordinates, 100 km lookup / 50 km accepted administrative-label distance. Customer, Merchant and Rider use the same reverse client; direct Customer/Rider Longdo reverse calls and obsolete Longdo formatter tests were removed.
- Exact-head Native Pilot run `40178222093` for `8853c17f30319fc93b202c77bde99b8644ba05a8` passed full regression, source integrity, all Kotlin unit suites, three APK builds, Android 16 KB packaging, WebView rejection, artifact upload and emulator launch/map/voice lifecycle.
- Merchant parity is now included: GPS/map movement resolves the shop address through QueueGo Places with debounce; typed merchant addresses are preserved; selecting a search result recenters the existing QueueGo map instead of adding an overlay.
- Shared Native text search is now implemented with `QueueGoNativeApi.searchPlaces` + one `QgPlaceSearchPanel`. Search is nationwide by default, debounced 300 ms, capped to 8 displayed results, and parses only valid Thailand coordinates from the authoritative RPC.
- Customer delivery-location picker, Merchant shop setup and Rider My Destination all place the shared search panel outside the map canvas. Selecting a result uses one shared label formatter, moves the current pin and recenters the existing QueueGo map; no duplicate map/search backend was introduced.
- Source regressions require all three roles to converge on shared QueueGo search/reverse authority and keep the search UI outside `QgLocationPickerMap`.
- Exact-head Native Pilot run `40179473743` on `48aa131b5b2998387394af4e735c5bf041ff7f46` passed full regression/source gates and failed first at shared Kotlin compile because `QueueGoPlaceSearch.kt` explicitly imported Compose `weight`, resolving to an internal API in this Compose version.
- Fix `36f807f55f2c1b0fc011d4dafbd3b5d4e9abf5e6` removes only that invalid import; `Modifier.weight(1f)` remains correctly scoped inside `Row`.
- **NEXT:** run Native Pilot on the exact successor HEAD, fix only the first failure if any, then mark PR #722 ready. Physical-device navigation remains a separate release gate and must not be claimed from emulator evidence.

## 2026-10-25 Authenticated Android visual review — DONE

- Branch: `work-native-rider-auth-review-20261024`; verified exact HEAD: `0a07a9352159295ff0f0c6a4d182c85703d329be`.
- Exact-head Actions run `40148163225`: **SUCCESS**. Both debug APKs built; Android Emulator booted; Rider authentication and all expected 11 screenshots passed; Merchant authentication and all expected 5 read-only screenshots passed; both artifacts uploaded.
- Rider metadata: `review_complete=true`, 11/11 screenshots, no missing screens. Merchant metadata: `review_complete=true`, 5/5 screenshots, no missing screens. Screenshots were inspected after download; no order/dispatch mutation was used.
- Transient UIAutomator empty-root failure is handled by five bounded retries. Support dialogs close by accessible button or Android Back.
- Physical device evidence is not claimed. Customer app capture remains blocked until an authorized Customer test account is provided.
- Status: DONE for Rider + Merchant authenticated emulator screenshot scope.
- **NEXT:** continue only if an authorized Customer test account becomes available or the owner requests another workstream.

# 2026-10-24 Rider Android production schema mismatch — IN PROGRESS

- Active local successor: `fix/native-customer-map-picker-20261024` (descends from PR #710 head `8a9089d4`; the Rider and POS source fixes are local and unpublished).
- Evidence: owner photo shows Rider map with `column orders.express_distance_km does not exist`. Supabase production read-only schema query confirms none of the Express columns selected by Rider exist. Production PostgREST logs at `2026-10-24T16:29:49Z` show the same Android `SM-X135G` request failing with SQLSTATE `42703`.
- Root cause: Native Rider already routes `GET orders` through `QueueGoOrderReadCompatibility`, but its optional-column allowlist omitted Express fields. The intended compatibility retry therefore rethrew this missing-column error and blocked the full Rider snapshot. The Express migration is not present in production migration history; it is explicitly source-only/staged, so it is not applied here.
- Fix commit: `c288e3d9` adds the 11 selected Express fields to the optional read allowlist and covers repeated missing-column removal while preserving owner/status filters.
- Evidence: `node tests/native-android-source.cjs` PASS (311 checks); `git diff --check` PASS. No Android SDK, Gradle wrapper or `adb` is available in this workspace, so no APK or emulator screenshot was produced. Production was only inspected, not changed.
- Status: source fix is in progress; APK/runtime verification is blocked pending an exact-head Native Pilot build. Prior APK files predate this fix and must not be reused as evidence.
- NEXT: run the shared Kotlin compatibility test and exact-head Native Pilot, inspect generated Rider screenshot/map state, and publish a new test APK only from that exact commit. Also continue the separate Merchant POS timeout/layout issue from the owner screenshots.

## 2026-10-24 Native Merchant POS compared with Web — IN PROGRESS

- Fix commit: `4e812a6e` makes Native POS fetch independent shop-scoped reads concurrently, matching the Web flow, and turns raw timeout text into a Thai retry message.
- Evidence: owner screenshot shows raw `timeout` on the Native POS page. Native `MerchantPosScreen` displayed `failure.message` verbatim. Web `merchant/pos-table-v2.js` runs independent table/product/order/staff reads with `Promise.all`; Native `MerchantPosApi.snapshot()` did these reads sequentially. Production logs for the shown Android device (2026-10-24 16:24:30–16:27:00 UTC) show POS snapshot endpoints returning HTTP 200, origin times 0.19–2.30 s. The data and timeout appearing together means this log does not support a persistent POS backend failure.
- Follow-up fix in progress: successful POS snapshot refresh clears a prior raw `timeout` or mapped read error while preserving success messages and offline-draft status. This addresses stale error text after a later successful read.
- TEST: `node tests/native-android-source.cjs` PASS (311 checks); `git diff --check` PASS. No Android SDK, Gradle wrapper or `adb` is available here, so the Kotlin unit test/build and screenshot capture have not run.
- Status: source fix only; no APK generated. The existing APKs predate these fixes. Kotlin unit test, exact-head APK build and screenshot are still unverified in this workspace.
- NEXT: run Merchant Kotlin unit tests and exact-head Native Pilot; inspect actual POS screenshot on the generated build, then provide that build. Preserve web POS flow, which already loads these independent records concurrently.

## 2026-10-24 Customer Android map picker failure — IN PROGRESS

- Evidence from owner-provided Android photo and 7-second screen recording: the Ride location picker displayed a blank map surface with an indefinite loading spinner and no coordinates. The root cause is confirmed in the tested APK build path: `QG_MAP_SERVICE_BASE_URL` was empty in Native Pilot, so `queueGoMapVectorSourceUri()` returned null and `QgLocationPickerMap` could never signal ready. The Customer picker also used a fixed 150dp map height, too short on the shown device.
- Fix commit `8df77697`: Native Pilot now uses the existing deployed QueueGo Map endpoint `https://queuego-map-buriram.onrender.com` when no repo override is set; production release workflow still requires an explicit validated endpoint. Customer map height now scales to 34% of screen height (240–400dp) and a 12-second load timeout exposes retry feedback instead of an endless spinner.
- TEST: `node tests/native-android-source.cjs` PASS, 309 checks / 0 failures; `git diff --check` PASS. Android Gradle build is not available in this local environment; exact-head Native Pilot CI is required for APK build/runtime verification.
- **NEXT:** publish this descendant of PR #710 with fast-forward only, run exact-head Native Pilot, confirm the Customer map source endpoint appears in the packaged manifest/runtime evidence, then provide the new three-APK test ZIP. Existing public map coverage is Buriram; nationwide map coverage remains a separate workstream.

## 2026-10-23 Current owner scope: map only; recovery cancelled

- Latest owner instruction: ไม่ต้องกู้แล้วปรับแค่เรื่องแผนที่. Do not continue whole-file/module timestamp recovery. Native recovery PR #658 is closed without merge. Initial recovery snapshots are historical references only.
- Web #657 had already merged at `2baf1a512eac574d3ca3095c581cc8f7589aaf43` before cancellation. The map-only successor removes its unintended UI/presentation changes by preserving the pre-recovery current UI from `59051ddfb89a03b7d34e3b5b94f80c62919f9e6a` in a new forward commit; no reset/revert.
- Evidence-backed map issue: Web qgNavigationCameraShouldRun excluded online Riders without a job, so live GPS updates could leave the map unfollowed and the puck near an edge. Online now retains the same perspective/heading follow as active navigation. Web speed zoom now interpolates continuously between the existing speed tiers; auto-pan recovery, 25m snap, sole QueueGo route service and current proof/cash/auth flow remain.
- Native source stays on current successor 2c7a63de, with only three map files changed: enable perspective when online without a job; align camera road-snap radius with puck/pipeline 25m; compile the already hosted QueueGo route URL when CI supplies no nonblank override. Current Rider app UI, HUD, order/API/proof modules and map engine algorithms are preserved; no historical module restoration.
- Focused validation: standalone navigation module contracts PASS; Native source 307 PASS. Full local regression and exact-head Native Pilot build/emulator evidence are NEXT. No installed APK or physical-road PASS claimed.
- NEXT: exact-head Native Pilot on the map-only successor; merge only after successful validation.

# QueueGo Active Work Checklist

Last governance checkpoint: `WORKFLOW.md` commit `9439569c15f72568b3398a1072f6d2285c8e7ce7`

This checklist is the repository continuation ledger. Read `WORKFLOW.md` first.

## Active Rider benchmark successor — 2026-10-23
- Status: IN PROGRESS
- PR: #625 `work-native-rider-grab-benchmark-20261022`
- Parent PR: #620 `work-rider-auto-start-swipe-complete-20261022 @ f64ad6b2c85e04a2c6016c452753d264c4b6cee3`
- Implementation checkpoint before this ledger update: `6e0deb5408dbdadcdb6e35035baf892be0214c4d`
- Completed in source:
  - QueueGo MapLibre + `/map-api` remain the sole Rider map/route authority; no Longdo/Google/public-routing fallback was added.
  - Web + Native off-route detection uses >30 m sustained for 3 seconds; route display/bearing snap is bounded to 25 m.
  - Native + Web expose current/next maneuver guidance including U-turn behavior; Native uses 150 ms HUD crossfade.
  - Navigation camera includes low-speed bearing guard, turn-proximity zoom, speed-aware rotation and 8-second pan-to-follow recovery.
  - Merchant receipt proof is durable/compressed to <=500 KB, restored after interruption, and food pickup requires explicit received-food confirmation.
  - Web delivery proof routes to full-screen cash collection and 100% slide confirmation before completion.
  - Completion summary uses actual Rider payout and data-backed rating; no fabricated rating.
  - Customer order note is shown only near reliable customer dropoff guidance.
  - Active-job Rider location heartbeat is separated from dispatch lease: 3 s normally and 1 s within 200 m of the current QueueGo stop.
  - Rider accept, arrival, pickup and completion transition audit paths preserve idempotent server mutations and action-specific GPS/server timestamps.
  - Scoped proof recovery RPC keeps proof tables server-only while allowing ambiguous mutation recovery for the assigned Rider.
  - CI Rider migration/test path filters now trigger Native Pilot and Map regression gates.
  - Native stale 3-sample reroute test was aligned to the 3-second policy; shared 8-second auto-follow constant is public across modules.
  - Missed/passed maneuver handling reroutes immediately on reliable moving evidence, including the 8–30 m post-turn window, while generic drift keeps the sustained 30 m / 3 s gate.
  - QueueGo route responses expose only provenance-backed explicit OSM `maxspeed` intervals; Native parsing rejects inferred road-class values, and the Native HUD now renders a legal-speed sign only on a reliable matching interval.
  - Map CI release-asset transport has a deterministic fallback: rebuild the published Planetiler 0.10.2 / Buriram-bounds recipe from the pinned Thailand PBF and still require the published PMTiles SHA-256.
- Current verification:
  - Full JS/SQL regression reached PASS before the map asset transport step on prior exact heads.
  - Native full QueueGo regression reached PASS on `f02bd4e3`; first Native failures there were the stale reroute unit test and module visibility, both corrected afterward.
  - Exact-head Map Engine run `39883581231` and Native Pilot run `39883577181` both PASSED on `3c25e3036e57b36614a47a536072a49a76a7ea1a`, unlocking the explicit-speed-limit HUD chunk. The new HUD successor must pass the same exact-head gates again before this newer source is marked DONE.
- Release blockers after source gate:
  - Physical Android device ANR/OOM/battery certification.
  - Real Buriram GPS/road navigation acceptance.
- NEXT: run Map + Native exact-head gates on the new Native speed-limit HUD successor and fix only the first failure. In parallel, finish PR #629 Web explicit-only speed-limit RC; the sign must stay hidden whenever `osm:maxspeed` is absent. Physical road/GPS/ANR/OOM/battery acceptance remains a release gate.

## Dependency order

### 0. Work continuity / anti-repeat process
- Status: DONE
- Branch: `main`
- HEAD/commit: `9439569c15f72568b3398a1072f6d2285c8e7ce7`
- Evidence: `WORKFLOW.md` exists on main and defines startup, checkpoint, successor, release and interruption rules.
- NEXT: keep this checklist updated after every bounded chunk.

### 1. QueueGo Map / Buriram routing core / unified route authority
- Source/test status: DONE
- Release status: BLOCKED
- Active integration PR: #417
- Branch: `work-queuego-map-route-authority-unified-20261017`
- HEAD: `de5c206fb0798115ef1bff8052ddc22358a2d33f`
- Evidence:
  - QueueGo RC Tests run 39043592024: SUCCESS
  - QueueGo Map Engine Buriram run 39043592049: SUCCESS
- Release blockers:
  - Production/public hosting not approved
  - physical Buriram road/GPS certification not complete
  - Android full offline routing runtime not complete
- NEXT: do not claim launch-ready; continue unblocked dependent source work while these remain blocked.
- Superseded/ancestor examples: PR #379 and earlier Local Roads PRs are not restart points when #417 contains their successor integration.

### 2. Rider Web navigation / QueueGo sole route authority
- Source/test status: DONE
- Release status: BLOCKED
- Active PR: #413
- Branch: `work-web-rider-queuego-route-authority-20261017`
- Previous failing HEAD: `ece9fd795e5e965db9c1c3fbf4cafb27cad4f591`
- Current HEAD: `08e5af506468e08e5d815bc1d8bfc6e5e2b69611`
- Current CI: QueueGo RC run 39049849304: SUCCESS on `08e5af506468e08e5d815bc1d8bfc6e5e2b69611`; previous run 39043362341 failed on stale policy assertion
- Fix commits: `8a933b998fb3e4d6441ea99557ae8ba279272ead`, `08e5af506468e08e5d815bc1d8bfc6e5e2b69611`
- Root cause: stale regression assertion still requires Android navigation to force the Google Maps app package, conflicting with the newer QueueGo in-app navigation authority policy.
- Evidence: job 117427127897 fails with `Android navigation must force the Google Maps app package`.
- Release blocker: authorized QueueGo Map runtime plus physical road/GPS certification inherited from Map release gate.
- NEXT: no more source rework unless a newer successor/regression appears; do not reintroduce automatic Google Maps navigation.

### 3. Rider Android navigation / QueueGo route authority
- Status: IN PROGRESS
- Active PR: #414
- Branch: `work-native-rider-queuego-route-authority-20261017`
- Current HEAD: `e8446b540566d012d83acf8520578bdbd4f16ce0`
- Recovery checkpoint lineage:
  - `297f9e4a918b8bdab86cc4abebb38d3176ab1842` restore current Grocery stylesheet
  - `735cdf215ffe0334839e31f00172116080dcc950` + `7826b019e89847d8cdeae17b3faa63fdd7a3d3cb` forward-port Passenger Ride production backend delta while retaining the newer env-only quote handler
  - `7d0aa2bdeee087685cc350996b70b88dc9abe8f7` restore current Grocery normalize helper
  - `992d117cb2ec88da0ad4e04cf90262596154a9c7` restore current Admin Grocery catalog asset
  - `55ea1afd4bad60c48e5524762039fdedbdff667b` restore current Customer Grocery catalog asset
  - `59a32dd7cfba0b540280ae085e1f5d0a0a79e5c8` add pinned Map asset generation before regression
  - `4bd4825d01f724882403e9a3514569d48b78bacb` lock Playwright runtime for Map validation
  - `d335a75d4a9e4825bda5c31cc61e6884802d3d49` restore current Merchant Grocery catalog asset; the immediately preceding successor also restored Merchant Grocery image preview
  - `9905f53f7f75cd2237f17271e842946c3406ff5e` restore credit-only Merchant GP retirement on current successor
- Established CI root causes and evidence:
  - Earlier source-integrity failures were caused by long-lived branch integration drift plus missing generated Map asset lifecycle setup. Unified #417 generates pinned/checksummed `map-assets` in `pretest`; #414 originally did not.
  - After `59a32dd...` and Playwright lock, source-integrity advanced to 94 scripts / 80 local references / 0 failures.
  - Exact-head run 39050648816 on `d335a75...` then failed at `Retired GP slip reporting must have no post-commit path`.
  - #414/current main had reintroduced legacy Merchant GP slip mutation/view paths after the credit-retirement successor; credit #332/#333 has zero `report_shop_gp`, `qgmSubmitGpSlip`, `settlements?on_conflict`, `qgmOpenGpSlip`…30981 tokens truncated…job `117670009359`: 100/0 PASS on PostgreSQL17, 3/4/5 Orders, forced independent row-lock overlap, single targeted offer winner, replay/stale rejection, preserved locked Rider payouts, exact satang conservation and disconnect-before-commit rollback.
- Map display job `117670009346`: actual Customer denied/allowed and Merchant denied/allowed scopes PASS; Rider phone-denied/phone-allowed/large-allowed mount/background/recreation all PASS with exact source metadata and no Production/account/order writes. Debug PMTiles + injected emulator GPS evidence is not physical positioning or release HTTPS approval; visible tiles still require owner visual acceptance.
- Map Engine job `117670006986`: actual graph/roads/directional restriction gates, external-request-blocked Chromium, offline package/rollback, no-network container, loopback staging and release-gate report PASS. The existing six source-open restrictions remain quarantined (four malformed/incomplete, one member conflict, one direction conflict); `all_source_rules_gate=false` is deliberately still OPEN. They are not six hidden code-test failures. See `docs/map-engine-release-gate.md`: relations 2148441, 17320674, 20613951, 20613952, 6982243, 1538817; all 12 affected member ways are excluded until independently verified.
- STATUS: SOURCE READY / CI DONE for verified source above. PHYSICAL BLOCKED / SIGNING BLOCKED / PRODUCTION BLOCKED; ACTIVATION BLOCKED. Not RELEASE READY or RELEASE DONE. Live cap remains unchanged/default `route_bundle.max_orders=2`; no Production change or live 3–5 activation occurred.
- OWNER ACTION REQUIRED (additional map-source gate): authorized map/data reviewer with actual sign/direction/junction evidence must inspect those six OSM relations, document lawful from/via/to members and one-way directions, and approve independently evidenced source correction + rebuilt package + quarantine/release-gate rerun. Never invent connectors, swap roles or lift quarantine based on CI alone. Use the physical/hosting acceptance accounts/devices/actions/evidence table above for the other gates.
- NEXT: inspect the documentation-only successor's exact Native + Map Engine runs selected automatically by the checkpoint paths. If green, no additional implementation is pending in this requested source/CI scope; proceed only to the explicit owner acceptance/signing/provider/public-HTTPS/source-data/Play/Production gates above. If red, read its first actual failing job log before changing anything. On resume fetch newest successor before using this recorded SHA.

## 2026-10-18 owner video: Web camera and one-proof pickup/delivery
- Stream: Rider Web road-test camera + pickup/delivery proof, owner authorized website correction/deployment. Source branch `work-native-route-bundle-web-camera-20261018`, strict parent `73c458c53c78681d5336f54ff953e6bc82168d8c`; backup `backup/web-camera-before-20261018-1938`. No older PR restarted.
- Evidence: owner Oct17 20:15:49 recording shows camera flipping/backtracking and puck leaving its anchor; raw GPS telemetry was not supplied, so the clip alone cannot certify the individual bad fix. Executable regressions prove missing heading/null became numeric north (270° camera changed to316.8°) and Rider heading null became0. Current public main also lacks the newer overlap/direction/continuity guards already present in this successor.
- Fix: optional numeric GPS/camera values preserve unknown rather than zero; missing heading retains reliable motion/last course. Ignore stale/duplicate geolocation callbacks; synchronize marker duration with camera or true GPS cadence. Existing single camera ownership and opposite-route/overlap guards retained. Rider asset version `20261018-12`; public build tag `rider-20261018-camera-proof1`.
- Proof root evidence: actual DOM test on parent fails because cancelling retake erases the selected image. Reopening the same workflow also recreates the page; a second cash/collection page and slider remained in Web, unlike current Native.
- Fix: one pickup photo + one direct pickup action; one delivery photo + one direct completion action. Money stays visible on that same page. Keep the selected File/preview on camera cancel, back/reopen and failed submit; reuse uploaded proof path on retry; guard double tap and session/order scope; release photo memory on success/logout. No new proof/wallet/payment/backend system. Native existing one-photo/one-action/no-slider contracts PASS and are reused unchanged.
- TEST: Web camera65/0; Rider map119/0; GPS stream9/0; actual one-proof DOM34/0; source scripts98/local refs84; Rider UI103/0, runtime83/0, critical client64/0 and Native delivery flow PASS. Full clean-checkout regression and exact successor CI REQUIRED before source completion.
- STATUS: IN PROGRESS. NEXT: full regression, publish strict successor/check CI; deploy only tested bounded Web deltas to latest main. Do not merge staged backend/cap/feature changes for a website repair. Public GitHub Pages still has existing route transport; private QueueGo map-api hosting remains a distinct release gate, so do not replace the deployed route call with an unavailable /map-api endpoint. Preserve all backend state/rollout gates and cap2.
- BLOCKER: road/OEM Safari/physical camera acceptance still requires real-device evidence after website update. Browser binary download is unavailable in this runtime; actual production-script DOM/state regressions run locally and CI/runtime evidence remains required. No Production database mutation or sample order was made.

## 2026-10-18 bounded remaining-route HUD correction
- Camera route reuse must not freeze the distance/ETA card. Store existing authoritative route distance/time with its cache; read the accepted route match on each fresh GPS fix and render remaining road distance plus explicitly approximate ETA. Unknown, opposite, off-route or inaccurate fixes do not fabricate progress. Geometry, route authority, quote/pricing/payout and business states unchanged.
- Tests: production helper14/0, GPS9/0, proof DOM34/0, camera65/0 and existing Rider route/source suites PASS. Build rider-20261018-camera-proof2; one-photo fix retained. Full clean-tree regression REQUIRED before this runtime checkpoint is published.
- STATUS: IN PROGRESS until exact CI/public bytes verify. NEXT: publish tested bounded progress delta and verify build2 on the public Rider URL; physical road/camera acceptance remains separate.


## 2026-10-18 owner video 20:19: bold navigation route
- Stream: Rider Web/Android route visibility. Branch: work-native-route-bundle-web-camera-20261018; verified parent HEAD `c41453e9a5303d7eaac435deca3bb3ec92ec86f3`. Source parent exact Native3414/run39129868715 and Map156/run39129868717 SUCCESS; live parent RC1871/run39129856790 and Pages1434/run39129856588 SUCCESS. Local backup backup/route-stroke-before-20261018 preserves source parent.
- Evidence: owner real iPhone Safari video20:19 shows a thin red stroke on broad pale roads; existing Web uses6px with no casing, Native active8/future6. This bounded visual revision uses Web12px red/16px white casing and Android active12/future8 with4 wider white casing, same authoritative geometry and cleanup owner. Build rider-20261018-route-bold1. No route engine/camera/GPS/proof/backend/pricing/cap change.
- TEST: focused source Rider121/0, live Rider96/0, camera65/0, proof DOM34/0, progress14/0, source integrity PASS; Native road-display/navigation/marker/source contracts PASS. Runtime confirms shared casing/fill geometry and both clean up during reroute/dispose. Full clean-tree regression and exact successor CI REQUIRED.
- STATUS: IN PROGRESS. NEXT: run relevant/full checks, publish bounded Web for owner review and run exact successor Native/Map gates. Physical turn/GPS/camera and visual acceptance remain BLOCKED; see existing owner account/device/action/evidence table. Route cap remains2.


## 2026-10-18 route disappearance / puck alignment / direction arrows
- Stream: Rider Web and Android; branch work-native-route-bundle-web-camera-20261018; verified parent HEAD `58acbd5be2536ab7495390a0bc9692224c184f6c`, source Native3415/run39131732532 and Map157/run39131732523 SUCCESS, live RC1872/run39131720597 and Pages1435/run39131720132 SUCCESS. Backup backup/route-arrow-before-20261018.
- Real owner20:43 recording shows no active route initially, then a distance badge without visible red line. Actual runtime reproduction on parent fails: adding casing starts source loading; isStyleLoaded=false defers red fill to an already-fired load event. Fix existing line helper with owned style.load replay and immediate idempotent application; removal cancels pending replay. Direction chevrons use a local canvas glyph, no remote glyph/font dependency.
- Presentation trims only the displayed route to the interpolated puck; full authoritative road geometry/ETA/reroute remains unchanged. Web confidence-bounded road alignment requires known accuracy<=35m, match<=12m and no opposite heading; camera/puck use the same presentation point while raw GPS stays untouched. Native reuses existing signal/hybrid snap authority and trim helper; active route head moves with existing puck animator, white direction chevrons share route cleanup. No second engine/dispatcher/GPS service or backend/cap/proof/payment change. Build rider-20261018-route-arrow1 / map asset20261018-13.
- TEST: parent red-fill runtime FAIL reproduced. New actual renderer runtime22/0, camera65/0, Rider source121/live96, GPS9/0, progress14/0, one-photo DOM34/0 and Native source/navigation/road contracts PASS; full clean regression and exact successor CI REQUIRED.
- STATUS: IN PROGRESS. NEXT: full regression, publish tested Web first, verify public HTML/JS and exact source CI. BLOCKER: physical acceptance remains required. OWNER ACTION REQUIRED: approved Rider on real iPhone Safari/Android opens route-arrow1 during approved active route; observer records device/OS/build/time/order, route line at animated puck, direction through turns, parallel-road/off-route recovery and background/foreground. Do not fabricate a route without a destination or activate cap3-5.


## 2026-10-18 shared Rider heading follow
- Stream Rider Web/Android camera. Parent HEAD `12e7c79cf68478e8b4d409ed41a92e042199db5b`; source branch work-native-route-bundle-web-camera-20261018 / live main. Previous route visibility/alignment/arrows checkpoint passed full local regression; its exact CI may be superseded by this requested camera successor.
- Owner requests camera follow Rider arrow. Existing Web camera uses road look-ahead yaw while puck smooths GPS separately. Use one cached smoothed heading per accepted fix for camera and puck; marker rotation interpolates the same shortest angle/easing. Gesture/overview ownership, unknown-heading and opposite-road guards preserved. Native FOLLOW explicitly prefers existing filtered signal/puck course while retaining fallback/quality gates and existing camera policy; added actual Kotlin policy test. Raw GPS/route authority unchanged. Build route-arrow2 / map asset20261018-14.
- TEST: renderer runtime23/0, camera65/0, GPS9/0, Rider source121/live96, proof34/0 and Native navigation/road/source contracts PASS; new Kotlin shared-course case requires CI. Full clean regression/exact HEAD Native/Map/RC/Pages REQUIRED. STATUS IN PROGRESS. NEXT publish tested successor and verify public bytes plus exact CI; physical road/heading/parallel-road/foreground acceptance remains BLOCKED using owner action table above.


## 2026-10-18 completed/multipart route overlay cleanup
- Stream Rider Web renderer; branch work-native-route-bundle-web-camera-20261018, parent HEAD f71b54c4b95634e7decac5c0e5abd88d8592fde8. Parent full local regression PASS; public arrow2 HTML/JS byte-match verified; RC1874/run39134784093 and Pages1437/run39134784706 SUCCESS. Native3417/run39134801387 and Map159/run39134801473 were still pending at review.
- Actual helper replay at the route endpoint reproduces a stale red/casing tail: zero remaining lines skipped the update loop without deleting owned layers. Multipart render copies could also leave obsolete parts. Reconcile exact owned IDs on each valid render, delete exhausted parts, retain only the matched remainder and subsequent authoritative parts. Only first part is anchored to the animated puck; later parts stay untouched. No route/heading/financial/GPS authority change.
- TEST: endpoint before-fix assertion FAIL (layer remains true); renderer now29/0 incl exhausted cleanup, no jitter regrowth and multipart retention. Camera/Rider/GPS/proof + full regression and exact successor CI REQUIRED. STATUS IN PROGRESS. Build route-arrow3 / asset20261018-15. NEXT publish tested bounded renderer repair and follow exact current CI; physical acceptance still BLOCKED.


## 2026-10-19 real Buriram places / source checkpoint
- Stream: business POIs/search; branch work-native-route-bundle-places-20261019; baseline HEAD531c1ac9632a836fa753fa77fd13cc3be93544ce, mainf836f05da7a3aa5465f9648e7d83997e32fe406b fetched. No newer open successor exists. Parent Native3418/run39135860022 + Map160/run39135860008 and live RC1876/Pages1438 SUCCESS. Backup backup/before-buriram-places-20261019.
- Existing normalize places table/geocode/nearby is reused; extend named OSM nodes with named area ways instead of creating another catalog backend. Preserve road topology/restriction code and existing immutable database build. Export the same places table for read-only Web/Android use.
- Real reference-complete PBF recovered from parent CI artifact12140330989, checksum426e2d638ba73005a681b0ddb30ffa2638ce9fc392b329cef89f04e70fbe442d verified. Snapshot2026-10-15T20:21:00Z. Export3079 records (1284 business/service categories), nodes+areas, source IDs/URLs/aliases/known contact/hours/address, ODbL attribution. Coverage is the existing padded Buriram operating envelope incl border areas; not exhaustive or physical validation.
- TEST: actual normalization/integrity/foreign keys PASS; real catalog identity/bounds/provenance/no-ordering QA PASS. STATUS IN PROGRESS. CI exact successor REQUIRED after integration. No Supabase/Production order/merchant insertion, invented photo/rating/open status, merchant link or live feature/cap change.
- NEXT: use this same static snapshot in existing Web search/nearby/place display and native shared picker search; test/deploy Web first, exact-head Native/Map/RC gates. Physical location/contact/entrance verification remains BLOCKED: owner/merchant must review exact source IDs and real shop entrance/device evidence before treating records as verified/orderable.


## 2026-10-19 Web real place search / display checkpoint
- Stream business places Web; branch work-native-route-bundle-places-20261019; parent29f135a3d65d7463aa3db94753dbf3107c0f7350 (PR437), strict successor436.
- Existing QueueGoMapWeb search now reads the same ODbL snapshot first; map-view geocode/nearby reuse it when local, with existing external/provider or configured-engine fallback retained. Generic POI dots, collision-limited named labels, safe text-only detail/known contact/hours/source and explicit coordinate selection; no merchant/order inference. Customer picker uses existing draft callback; other shared pickers gain local search control. Rider navigation ownership/route/proof untouched.
- Existing engine places response exposes only known source fields/precision and explicit unverified/non-orderable flags. Existing Map Engine workflow adds data/test selectors and real PBF rebuild/export comparison; no duplicate workflow/backend/database.
- TEST: real catalog24643/0; actual local query13/0, actual DOM stale-result/select/cleanup7/0, camera65/Rider121/source integrity PASS. Real database re-export checksum matches shipped snapshot, rebuild comparison5/0 PASS. Complete clean-tree regression and exact successor CI REQUIRED.
- STATUS IN PROGRESS. NEXT publish tested bounded Web projection for owner review; integrate same asset into shared Android place search; require exact final Native/Map/RC/Pages. Physical contact/entrance freshness and signed/public-map runtime gates remain BLOCKED.

- Full-regression first actual failure: location-lifecycle older-helper fixture lacks attachPlaces, causing TypeError at picker startup. Treat catalog enhancement as optional when an older/cached helper is present; function-level optional call preserves picker availability. Fix the root compatibility call rather than weakening lifecycle checks. Exact failing suite must pass, then full regression restarts.

## 2026-10-19 Owner: manual-only Android usage guide and credit visibility audit
- Stream: Rider Android settings / credit deployment audit; branch work-native-route-bundle-places-20261019; parent HEAD 9a8e8c42a638d7f744a291e988a6df06ad45f947.
- STATUS: IN PROGRESS. Removed startup learning dialog and obsolete seen-store; existing profile settings alone opens the guide. Current QueueGo navigation replaces obsolete Google Maps instructions. No receive-work readiness or safety gates removed.
- Production read-only audit: no credit-named functions/tables in pg_proc/pg_tables; queuego-credit Edge Function exists but ledger RPCs are absent. Main has no current credit/Admin promo UI; staged source has the shared Merchant/Rider credit card and Admin grant. This is NOT live topup/promo readiness.
- TEST/CI: pending focused/full/exact-head gates on this checkpoint. BLOCKER: credit prerequisite migrations, isolated gate, provider signed settlement and controlled Production release. NEXT: verify guide change, expose existing credit navigation safely after source tests; do not fabricate balances, activate paid intake or grant credits.

## 2026-10-19 Rider calm Home / Settings checkpoint
- Branch: work-native-route-bundle-clean-home-20261019; baseline d367af6c1c43bdc9642315a278bdfffea414fa3e (PR437 newest fetched source successor), backup backup/rider-before-clean-home-20261019. Current commit is this checkpoint; use git rev-parse HEAD for its exact SHA.
- Web: single Ready card/switch, no top duplicate or large Online buttons/stats; acknowledged/serialized existing profile metadata write with session fencing and failure retention. Existing polling/reconnect reconciles authoritative profile. Red existing Rider puck retained. Existing account/profile rows enlarged, calmer group surfaces; availability Settings row returns to Home.
- Android: same one Ready switch to existing changeOnline/setOnline; Home Auto Accept and destination moved to Profile Settings using original state/store/dialog. Removed idle statistics/recommendation/demand/destination overlays and redundant top availability. Profile vehicle/document cards behind an explicit section; larger rows/text and simple groups. Existing active-work transition restriction preserved.
- Both: owner now requests calm idle map, so temporary no-job FOLLOW/3D experiment disabled. Job route/proof/cash/offer/session/dispatch guards preserved; no database/dispatch/schema changes.
- Validation checkpoint: Home DOM/mutation25, Rider runtime83/map121, Native screen384, delivery and P0-P4 source contracts PASS. Full regression running; exact-head Native compilation/emulator CI PENDING (local SDK/Gradle absent). Browser screenshot attempt BLOCKED: Chromium download returned truncated archive; no visual result claimed.
- NOT DONE: rendered Web review/preview, Android compile/emulator/visual and authenticated ON/OFF/reload/reconnect/process/dispatch evidence still required. Current source has no verified reusable Web service/Auto Accept/destination settings parity yet; do not invent a new preference backend. Realtime channel delivery remains unverified (existing Web reconciliation is polling/reconnect).
- NEXT: inspect first full-suite failure, commit corrections; push exact candidate for Native CI, review screenshots on small/large/font-scaled devices, publish bounded Web-only preview preserving current main and routing transport. Production cutover, physical GPS/authenticated accounts, signed AAB/Closed Testing stay BLOCKED until actual gates pass.

## 2026-10-19 Ready switch follow-up
- Published PR438 source3789cc00ac6d25ae2ea59a413096216c440de189/tree2607b0c81ba2a93ef834e88b65c56ffebe80d567. Native3422/run39195324274 and Map167/run39195324104 running; five independent SQL race gates PASS, Android build/runtime pending.
- Delayed profile read cannot overwrite a later acknowledged switch write: request-order fence and actual overlapping read/write regression added; Home DOM27 PASS. Build marker rider-20261019-ready1. No new business state.
- Local full regression exposed test timezone mismatch (machine Oct18/New York vs business Oct19/Bangkok); existing dispatch suite passes108/0 under TZ=Asia/Bangkok. No business SQL changed. Require clean full run exit0 in business timezone.
- Main Web-only projection prepared preserving current transport/places; not deployed. Visual/authenticated availability/dispatch, Web configuration parity, Android screenshots/build/restoration still PENDING. No DONE/release claim.


## 2026-10-19 Web-first Ready card publication
- Public Web main e37dc61cd37ec6f24460d69f12dd17b8d2ba72e3/tree daef900932cff884e3310d5b4a8cef9876d09288 publishes the bounded UI/profile/poll-fence projection, preserving current legacy route transport and real places. Full clean Web TZ=Asia/Bangkok npm pre/test/post EXIT0; projected DOM27, runtime83 and map96 PASS. Pages/RC deployment verification still required before claiming the live page updated.
- Full clean source TZ=Asia/Bangkok npm pre/test/post EXIT0 on local eff80250 tree (same follow-up code, before this documentation receipt). Published parent3789 Native3422 has full regression/JVM unit, navigation/MapLibre and five SQL race gates PASS; APK and display/launch runtime still running. Exact final successor CI still required.
- Web review URL: https://chatchairins-source.github.io/QueuGo/rider/ (build rider-20261019-ready1 after Pages). Current Settings source is improved but Web feature preference parity/authenticated acceptance/screenshot gates remain pending; this is not DONE.

## 2026-10-19 Rider Settings Web destination parity
- Stream: existing My Destination settings parity; branch work-native-route-bundle-rider-settings-20261019, parent9e16277f594fdab737d1ae2e6e7040b62844e012, backup backup/rider-before-settings-parity-20261019. No newer open successor at start.
- Existing Native My Destination state/store/dialog remains unchanged. Web Profile Settings now opens a clean map/label/preference editor using the SAME public qg_rider_destination_preference and qg_rider_set_destination_preference RPCs and existing QueueGoMapWeb create/place search/GPS helpers. No new preference table/API/availability state or route engine.
- Production read-only pg_proc audit confirms both destination RPCs and expected signatures are installed. qg_rider_dispatch_preference is absent; no continuous-dispatch/Auto Accept readiness activation is claimed or deployed. No real account preference/order write during QA.
- Existing saved preference loads from backend on every open; only matching acknowledged data yields success. Lost-write response reconciles with the same read RPC. Single dialog/write, account/session fencing, blank/out-of-range backend rejection, map/GPS cleanup and keyboard focus handling retain valid current state.
- Focused TEST: actual Settings DOM/RPC36 PASS, Home DOM27, Rider runtime83/UI103 and source integrity99/87 PASS. Full exact-tree npm pre/test/post required next. Native parent9e16277f exact run39195851635 has full regression/JVM/three APK/16KB/no-WebView and five SQL race gates PASS; emulator launch/display final steps still running at this checkpoint.
- Web review: previous maine37dc61c RC39195747917 + Pages39195747447 SUCCESS, actual HTTP200/build rider-20261019-ready1 confirmed. New Settings editor not yet deployed. Browser inspection reached the real sign-in page without an authenticated session; no credentials bypass, synthetic logged-in page or authenticated screenshot claimed.
- NEXT: full source/Web projection tests; checkpoint/publish source descendant, deploy bounded Web editor after passing tests, inspect exact-head CI. Authenticated availability/destination save/reopen, physical GPS/font-scale/Android visual and signing/Play/Production backend gates remain BLOCKED; owner must use an approved real Rider session and actual phone for those acceptance steps.

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
