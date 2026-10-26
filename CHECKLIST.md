## 2026-10-26 Customer Profile destinations and Favorites — IN PROGRESS

- Web blueprint: `main=7e036a0735cc49905e9b0a2434492470551a0c9d`. Native owner branch `work-native-web-parity-20261026` was fetched at `5e589cecc4f11a4dbf27d8c595aa9952ad630e28`, still the newest branch head and a descendant of the prior Native successor.
- Parent exact-head Native Pilot `40268085994` on `5e589cecc4f11a4dbf27d8c595aa9952ad630e28`: **SUCCESS** — full regression, tracking tests, all three APKs, 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle.
- Root cause/evidence: Web Profile exposes address, orders, notifications, Favorites, promotions and support. Native Profile previously exposed support, deletion and logout only. Favorite toggles already use `qg_customer_favorites`, but no Native Favorites route/list existed.
- Bounded source change: add Native Profile links matching Web labels; add a Favorites list backed by existing authenticated favorites/shop/product reads; keep unavailable favorites visible and removable; Android Back from Favorites returns to Profile. No backend, production data, or second navigation authority was added.
- Focused checks on the working tree: `node tests/native-android-source.cjs` PASS (323 checks); `node tests/native-customer-web-home-parity.cjs` PASS (55 checks); `git diff --check` PASS.
- First full `npm test` attempt stopped at the evidence bundler because its clean-checkout precondition rejected the working tree; no product assertion failed. Rerun on clean source checkpoint `1ca5c7475a573fed5e0119384cf820970aa92b77`: **PASS**, including map asset provision, complete regression, Grocery, release evidence bundler and map field-evidence gate.
- Exact-head Native Pilot `40269632439` on remote checkpoint `c48eae32a59554784f964356db2480e3c569c1cd`: **FAILURE** in full regression. First real failure: `tests/native-android-source.cjs:1` parsed `Warning: truncated output...` and raised `SyntaxError: Unexpected identifier 'output'`. The failure came from transferring large files through output that was capped, not from the Customer implementation.
- Repair evidence: complete file contents were read in bounded line chunks. GitHub blob IDs now match local `git hash-object` exactly for CHECKLIST (`8045aa2e...`), Customer app (`58fecb5a...`), Customer shop/favorites (`259ba74e...`) and native source test (`0ca5240b...`). The reconstructed full source tree SHA matches the clean local checkpoint tree `aa38069f22ac798020c13a9fcef971058e0b6bc1`.
- **NEXT:** publish the complete-file repair as a normal fast-forward successor of `c48eae32...` and run Native Pilot on that exact commit. Notification settings/privacy and Customer route-specific Back/process-recreation P1 remain open.

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
  - #414/current main had reintroduced legacy Merchant GP slip mutation/view paths after the credit-retirement successor; credit #332/#333 has zero `report_shop_gp`, `qgmSubmitGpSlip`, `settlements?on_conflict`, `qgmOpenGpSlip`, `data-gp-slip` and `gp-slips` runtime paths.
  - `9905f53...` forward-ports only the credit-retirement delta onto the latest #414 Merchant implementation: removes settlements write path, GP slip support link/viewer, GP slip submit RPC path and restores automatic-credit revenue copy.
- Verification before commit `9905f53...`: the same forbidden runtime contract used by `tests/legacy-gp-retirement.cjs` was checked and no forbidden Merchant GP mutation/view path remained.
- Current CI: QueueGo Map Engine Buriram run `39050895186` FAILED on exact HEAD `9905f53f7f75cd2237f17271e842946c3406ff5e`.
- First failure root cause (job `117449078394`): `tests/network-timeout.cjs` invoked `qtSupabaseAuth()` without loading its helper dependency `qtRequestTimeout` into the isolated VM. Merchant runtime already defined the helper. Fix commit `44487d60765cb821d867877369723332b5bc73b7` forward-ports the already-green main/unified test harness behavior that loads `qtRequestTimeout` when present.
- Main-sync safety: backup `backup/rider-route-authority-before-main-sync-20261017 @ 992d117cb2ec88da0ad4e04cf90262596154a9c7`; PR #420 main→#414 was conflict-only, never merged, then closed. No force/reset/revert used.
- Passenger Ride integration note: #414 is a descendant of #326; PR #387 later production-backend delta was forward-ported via its two new migrations while retaining the newer env-only quote handler.
- Latest exact-head failure checkpoint (2026-10-17): PR #414 HEAD `9905f53f7f75cd2237f17271e842946c3406ff5e`, QueueGo Map Engine Buriram run `39050895186` FAILED after source-integrity/realtime/post-commit/Rider checks had already passed.
- First actual failure: `tests/network-timeout.cjs` expected a timeout-style rejection but received `ReferenceError: qtRequestTimeout is not defined` from `qtSupabaseAuth`.
- Evidence-backed root cause: `tests/network-timeout.cjs` extracts/evaluates only the target request functions (`qtSupabaseAuth`, `qtSupabaseTable`, etc.) inside an isolated VM and does not load their helper dependency `qtRequestTimeout`. The actual Merchant runtime on exact HEAD does define `function qtRequestTimeout(ms)` before `qtSupabaseAuth`; therefore this CI failure is an incomplete isolated test harness scope, not a missing helper in live Merchant runtime.
- NEXT: in the next bounded chunk only, inspect the current HEAD definition/import scope for `qtRequestTimeout`, fix that single missing-scope/runtime issue, run the relevant test/CI, commit, then stop and report.
- Release blockers after source gate: Native Pilot on exact successor, physical GPS/road/navigation acceptance, QueueGo Map runtime/offline release gates.
- Superseded: PR #412 is an ancestor; do not restart there.

- Exact-head run `39051786664` on `bb837b4f14a478bba568933f00be9e7908ab6642` passed Merchant routing with `96 checks / 0 failures`, then failed first in `tests/rider-runtime.cjs` with `0 !== 1`.
- Root cause: the #414 Rider runtime regression harness was missing the current `syncRiderNavigationCameraMode:()=>false` VM stub already present in `main`/unified, so refresh-path execution diverged from the current runtime contract.
- Fix commit: `698d58f1cdb3ea4a3b0603bdba72b98875c1b263` forward-ports the current Rider runtime test harness only; Rider runtime implementation was not changed.
- NEXT: obtain exact-head CI for `698d58f1cdb3ea4a3b0603bdba72b98875c1b263`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39051907077` on `698d58f1cdb3ea4a3b0603bdba72b98875c1b263` passed Rider runtime, then failed first at the legacy PixelCopy snapshot memory assertion in `tests/native-three-app-stability.cjs`.
- Root cause: commit `38a3dcbb915d4e07bdcd3a5b3ef3c5cf8d3f3ab1` replaced the legacy renderer with MapLibre; continuity now retains the MapView/holder through `RiderNavigationMapRegistry` and no longer allocates PixelCopy snapshot bitmaps. Reintroducing the old snapshot path would revert the newer renderer architecture.
- Fix commit: `ef56ec4ee64a7bdba61c422ad09af62bb82dbd13` updates only the stability gate to require retained MapLibre lifecycle continuity and forbid PixelCopy/Bitmap snapshot allocation.
- NEXT: obtain exact-head CI for `ef56ec4ee64a7bdba61c422ad09af62bb82dbd13`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052159790` on `ef56ec4ee64a7bdba61c422ad09af62bb82dbd13` passed the retained MapLibre continuity gate, then failed first at the legacy bitmap-cache recycle assertion.
- Root cause: the assertion still described the retired renderer's `markerBitmaps`/`releaseBitmapCaches` lifecycle, while the MapLibre successor owns native marker/polyline refs and tears them down through `resetSurface()` + `MapView.onDestroy()` with no app-allocated bitmap caches.
- Fix commit: `fc83355fde1f87f4cfad96e2e4b5e312a576fb54` updates only that stability assertion to require animator cancellation, marker/route ref clearing, map/view release, and absence of legacy bitmap allocation.
- NEXT: obtain exact-head CI for `fc83355fde1f87f4cfad96e2e4b5e312a576fb54`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052279381` on `fc83355fde1f87f4cfad96e2e4b5e312a576fb54` passed the MapLibre dispose gate, then failed because `tests/native-three-app-stability.cjs` still tried to read removed `LongdoRiderMap.kt`.
- Root cause: MapLibre successor moved camera persistence into `RiderNavigationIntegration.kt`, where camera load/save is gated by `persistenceHydrated` and executed via `withContext(Dispatchers.IO)` against `SessionStore`.
- Fix commit: `9da1b59415740a2e63be33746394691a9f4f4ce1` updates only the first retired Longdo camera persistence gate to the current MapLibre integration path; the following legacy marker-cache assertion is intentionally left for the next exact-head failure if reached.
- NEXT: obtain exact-head CI for `9da1b59415740a2e63be33746394691a9f4f4ce1`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052425725` on `9da1b59415740a2e63be33746394691a9f4f4ce1` passed the MapLibre camera persistence gate, then failed first at the immediately following legacy Longdo marker-bitmap recycle assertion.
- Root cause: the stability test still required `clearPin()/markerBitmaps/stopBitmaps/bitmap.recycle()` from the retired Longdo renderer, while the MapLibre successor owns markers in the renderer and releases app references via `stopMarkers.clear()`, `demandMarkers.clear()`, `riderMarker = null` and `MapView.onDestroy()`.
- Fix commit: `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec` updates only that memory-safety gate to forbid the deleted Longdo map/cache path and require MapLibre marker/view release.
- NEXT: obtain exact-head CI for `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head Native Pilot run `39052528115` on `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec` passed native stability with `106 checks / 0 failures`, then failed first in `tests/native-release-evidence-bundler.cjs` because the generated `map-assets/` directory made the checkout dirty before gate-envelope validation.
- Root cause: #414 inherited Map unified `pretest` generation of pinned web assets but not the unified `.gitignore` rule `/map-assets/`; the evidence bundler correctly requires a clean source checkout and therefore stopped on generated, non-source files.
- Fix commit: `1b1692c2627e2ecb6cfabfd9f6b99f5f3986aec4` adds only `/map-assets/` to the existing #414 ignore rules, matching unified Map provenance behavior without weakening source cleanliness for other files.
- NEXT: obtain exact-head CI for `1b1692c2627e2ecb6cfabfd9f6b99f5f3986aec4`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052635608` on `d80f373aa016efba65c544cdb9bb2ff1a87ce7ec` passed native stability `106 checks / 0 failures`, then failed in `tests/native-release-evidence-bundler.cjs` because the checkout was dirty before the intended missing-gate assertion.
- Root cause: `npm test` pretest generates pinned `map-assets/` via `ops/maps/fetch_web_assets.py`, but #414 `.gitignore` did not ignore that generated directory; `finalize-native-release-evidence.py` correctly rejects any dirty source checkout.
- Fix commit: `1a580fbc7b6aa6aaac55a416354fa5fff0b4d632` ignores only generated `map-assets/`; release-evidence cleanliness enforcement remains unchanged.
- NEXT: obtain exact-head CI for `1a580fbc7b6aa6aaac55a416354fa5fff0b4d632`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052777401` on `1a580fbc7b6aa6aaac55a416354fa5fff0b4d632` passed Native release evidence bundler (`28 checks / 0 failures`) and then failed first because `tests/native-rider-route-bundle-parity.cjs` still read removed `LongdoRiderMap.kt`.
- Root cause: route-bundle stop planning moved to `RiderNavigationIntegration.riderNavigationStops()` under the QueueGo Map/MapLibre successor, while the parity test remained coupled to retired Longdo marker internals.
- Fix commit: `e8cf3f4df2fc72f7b0e76043afed08a16b512eef` updates only route-bundle parity assertions to the current QueueGo navigation stop-plan contract; runtime implementation was not changed.
- NEXT: obtain exact-head CI for `e8cf3f4df2fc72f7b0e76043afed08a16b512eef`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39052949572` on `e8cf3f4df2fc72f7b0e76043afed08a16b512eef` passed Native Rider route-bundle parity, then failed first in `tests/rider-return-to-sender.cjs` because current `admin/index.html` no longer contained `qg_admin_delivery_return_cases_v2` / return actions.
- Root cause: Return-to-Sender backend/Rider contracts remained present, but later Admin history overwrote the Admin Ticket integration originally added by `e739722dcecd55de550d722bee444fdd946c3245` and upgraded to multi-stop v2 by `b1933b37b781739b552e209002ca363b09ffe90e`.
- Fix commit: `38b9e1c591693437509d776b61d0ac3e70ce4516` forward-ports only the historical Tickets/Return-action block from the v2 Admin commit into the current Admin file; newer Admin code outside that block is preserved.
- NEXT: obtain exact-head CI for `38b9e1c591693437509d776b61d0ac3e70ce4516`. If red, fix only the new first failure; if green, continue the #414 gate.

- After `e8cf3f4...`, concurrent successor commit `38b9e1c591693437509d776b61d0ac3e70ce4516` restored Admin multi-stop delivery return controls (`qg_admin_delivery_return_cases_v2`, `return_to_shop`, `resolve_no_return`). A subsequent overlapping patch accidentally duplicated only `qgAdminReturnAction`.
- Corrective commit: `e306bc6c5b9a6aabef941a13c54c8e55625727cc` removes the duplicate handler while preserving the newer Admin return-case implementation from `38b9e1c...`.
- NEXT: obtain exact-head CI for `e306bc6c5b9a6aabef941a13c54c8e55625727cc`; if red, read and fix only the new first failure. Do not reapply the return workflow from an older branch.

- Exact-head run `39053216685` on `38b9e1c591693437509d776b61d0ac3e70ce4516` passed Return-to-Sender, MapLibre runtime/module and optional Google Maps return overlay, then failed first in `tests/native-map-marker-design.cjs` because route-stop assertions still read `QueueGoRiderApp.kt`.
- Root cause: `riderNavigationStops()` and bundle ordering ownership moved to `RiderNavigationIntegration.kt`; the marker-design regression test still looked for those symbols in the app shell.
- Fix commit: `ef03f11a1d20363a8c45b8a3025437ae590d403c` updates only the test ownership path; Native runtime code is unchanged.
- NEXT: obtain exact-head CI for `ef03f11a1d20363a8c45b8a3025437ae590d403c`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39053349534` on `ef03f11a1d20363a8c45b8a3025437ae590d403c` passed marker parity and MapLibre runtime/module contracts, then failed first because `tests/native-android-source.cjs` still read removed `QueueGoLongdoMap.kt`.
- Root cause: Customer/Merchant shared location picking migrated to `QueueGoMap.kt` / `QgLocationPickerMap` backed by MapLibre; the source regression still referenced retired Longdo picker lifecycle strings.
- Fix commit: `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8` updates only the shared picker lifecycle gate to current QueueGo MapLibre ownership/destroy semantics; native runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head successor `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8` updates only `tests/native-android-source.cjs` to replace retired `QueueGoLongdoMap.kt` / `QgLongdoLocationPickerMap` ownership with the current shared `QueueGoMap.kt` / `QgLocationPickerMap` MapLibre lifecycle contract.
- Current exact-head CI: QueueGo Map Engine Buriram run `39053521816` is IN PROGRESS on `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8`.
- NEXT: read the first completed result/failure from run `39053521816`. If red, fix only the new first failure. If green, mark this source gate passed and continue the next #414 gate.

- Exact-head run `39053722416` on `ebf65e2b717a55547b8f61a3f0080eb42733d9f3` advanced past the removed Rider map file path, then failed first at the stale `native Rider must include Longdo map SDK host` assertion.
- Root cause: current Rider host is `QueueGoRiderNavigationMap` backed by Gradle module `:rider-navigation-map` and MapLibre `android-sdk-opengl:13.5.3`; the test still required retired `RiderLongdoMap/MapGLSurfaceView/LongdoLayer` symbols.
- Fix commit: `93cd3ee2388ba63c4087a93bc7a9495cc2324bc8` updates only that host assertion; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `93cd3ee2388ba63c4087a93bc7a9495cc2324bc8`; fix only the new first failure.

- Exact-head run `39053841854` on `93cd3ee2388ba63c4087a93bc7a9495cc2324bc8` passed the Rider MapLibre host gate, then failed first at a Customer manifest assertion still requiring `longdo.map.key`.
- Root cause: Customer location picking now uses QueueGo MapLibre and the manifest declares `queuego.map.service.base_url` plus Android location permissions; the Longdo Customer key is retired.
- Fix commit: `f6ee1afb93470a8685618044f4419c6edd40d7b3` updates only the Customer map manifest assertion to the current QueueGo Map service contract.
- NEXT: obtain exact-head CI for `f6ee1afb93470a8685618044f4419c6edd40d7b3`; fix only the new first failure.

- Exact-head run `39053930825` on `f6ee1afb93470a8685618044f4419c6edd40d7b3` passed the Customer QueueGo Map manifest gate, then failed first at a stale Rider GPS/pin assertion still requiring Longdo `setLocation/ldmap.removePin/pushPin` symbols.
- Root cause: GPS ownership moved to `RiderNavigationLocationService` (`FusedLocationProviderClient` -> `NavigationMapFix`) and marker ownership moved to MapLibre `RiderNavigationMap.kt` (`stopMarkers` / `riderMarker`).
- Fix commit: `d5fe917369260ed3f828f9072379adbad1f34bdf` updates only that GPS/marker source gate; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `d5fe917369260ed3f828f9072379adbad1f34bdf`; fix only the new first failure.

- Exact-head run `39054055959` on `d5fe917369260ed3f828f9072379adbad1f34bdf` passed the Rider fused-GPS/MapLibre marker gate, then failed first because Merchant setup regression still required `QgLongdoLocationPickerMap`.
- Root cause: Merchant shop setup already uses shared QueueGo MapLibre `QgLocationPickerMap` while retaining the same GPS and save flow.
- Fix commit: `03e99642794cebb3c1dd3c4a5f94000d101ea866` updates only that Merchant picker assertion; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `03e99642794cebb3c1dd3c4a5f94000d101ea866`; fix only the new first failure.

- Exact-head run `39054161358` on `03e99642794cebb3c1dd3c4a5f94000d101ea866` passed the Merchant QueueGo Map picker gate, then failed first at a Merchant manifest assertion still requiring `longdo.map.key`.
- Root cause: Merchant native setup now uses QueueGo MapLibre and the manifest declares `queuego.map.service.base_url` plus location permissions; Longdo Merchant key is retired.
- Fix commit: `b48df16cda889b5200fc1e12d7257e877cef7e14` updates only the Merchant map manifest assertion to the current QueueGo Map service contract.
- NEXT: obtain exact-head CI for `b48df16cda889b5200fc1e12d7257e877cef7e14`; fix only the new first failure.

- Exact-head run `39054253085` on `b48df16cda889b5200fc1e12d7257e877cef7e14` passed the Merchant QueueGo Map manifest gate, then failed first at a Rider lifecycle assertion still requiring Longdo `map_*_failed` diagnostics.
- Root cause: MapLibre successor uses disposal/lifecycle guards (`disposed`, view identity, guarded async callbacks, `MapView.onDestroy`) instead of the retired Longdo diagnostic/fallback model.
- Fix commit: `c9b9c420fdd20d78001cb028fd66b8f6d3beddc4` updates only that lifecycle safety assertion to the current MapLibre guard model; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `c9b9c420fdd20d78001cb028fd66b8f6d3beddc4`; fix only the new first failure.

- Exact-head run `39054387407` on `c9b9c420fdd20d78001cb028fd66b8f6d3beddc4` passed the full native source gate (`272 checks / 0 failures`), then failed first at a Route Bundle numbering assertion that still read renderer-owned Longdo fields.
- Root cause: Route Bundle stop planning/numbering moved to `RiderNavigationIntegration.kt`; current logic preserves pickup-first ordering by `bundleSequence`, drop ordering by `dropRank`, and drop numbering offset by pickup count.
- Fix commit: `b0d4ad83ad27ccb15ed93cd00788de6ec9c8ed7d` updates only that Route Bundle numbering assertion to the current ownership path.
- NEXT: obtain exact-head CI for `b0d4ad83ad27ccb15ed93cd00788de6ec9c8ed7d`; fix only the new first failure.

- Exact-head run `39054514459` on `b0d4ad83ad27ccb15ed93cd00788de6ec9c8ed7d` passed native source (`272/0`), MapLibre marker/overlay, route-bundle and semantic marker contracts, then failed first because `tests/native-rider-road-display.cjs` still read removed `LongdoRiderMap.kt`.
- Root cause: Rider road-display test fixture still pointed at the retired Longdo renderer; current renderer is `rider-navigation-map/.../RiderNavigationMap.kt`.
- Fix commit: `1223300c6a130cb08f0a86b3d7f7534a2f22f783` changes only the renderer fixture path; downstream road-display assertions are intentionally left for exact-head CI one failure at a time.
- NEXT: obtain exact-head CI for `1223300c6a130cb08f0a86b3d7f7534a2f22f783`; fix only the new first failure.

- Exact-head run `39054636207` on `1223300c6a130cb08f0a86b3d7f7534a2f22f783` passed native source/marker/route-bundle contracts, then failed first because `tests/native-rider-road-display.cjs` still required legacy `roadDisplay.update()/pause()/dispose()` calls from the retired Longdo renderer.
- Root cause: current MapLibre route lifecycle is owned by `RiderNavigationMap.renderRoutes()` and releases renderer polylines through `removePolyline()` + `routeLines.clear()`; the old `RiderRouteDisplay.kt` remains legacy source but is no longer the active renderer owner.
- Fix commit: `b526021a3cb920d2588681cb0bdfd8de0f3d2cb1` updates only that lifecycle assertion to the active MapLibre renderer contract.
- NEXT: obtain exact-head CI for `b526021a3cb920d2588681cb0bdfd8de0f3d2cb1`. If red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39054874086` on `b526021a3cb920d2588681cb0bdfd8de0f3d2cb1` passed native source `272/0`, Route Bundle and MapLibre marker gates, then failed first at `clearPin()` teardown assertions in `tests/native-rider-road-display.cjs`.
- Root cause: the remaining assertion still described Longdo pin cleanup after dispose; active MapLibre teardown uses `resetSurface()` to clear stop/demand markers, null the Rider marker, destroy the MapView and clear the view reference, with no `clearPin()` API.
- Fix commit: `08165c620acced0e0f1ab4f278eecaf355520b73` updates only that teardown assertion; runtime implementation is unchanged.
- NEXT: obtain exact-head CI for `08165c620acced0e0f1ab4f278eecaf355520b73`; if red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39082580957` on `08165c620acced0e0f1ab4f278eecaf355520b73` passed road-display, voice, continuous-dispatch and cancellation-funding gates, then failed first because `tests/rider-planned-stops.cjs` sliced from removed `qgBundlePlannedStops()` and therefore never loaded current `qgBundleNextOrder()`.
- Root cause: Web Rider no longer owns the retired planned-stop function; current bundle selection is `qgBundleNextOrder()`, ordering pickup states by `route_bundle_sequence`, picked-up drops by `_bundleDropRank`, and filtering cancelled/completed orders.
- Fix commit: `827cf08bdd1a1ef8eddeaded7507413cc512c1bf` updates only that regression test to the current bundle selector contract; Rider runtime is unchanged.
- NEXT: obtain exact-head CI for `827cf08bdd1a1ef8eddeaded7507413cc512c1bf`; if red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39082702403` on `827cf08bdd1a1ef8eddeaded7507413cc512c1bf` passed current bundle selector `8/0`, then failed first in `tests/rider-saver-availability.cjs` because Admin had lost the dedicated Saver status/control DOM and scoped RPC handler.
- Root cause: historical commit `fefd09a19d53cb118bb07420b3e636c6f6f47a72` implemented the owner-directed Saver OFF-until-ready control using existing `feature.route_bundle` plus `queuego_admin_set_saver_enabled`; later Admin history overwrote that UI block and reverted to a generic platform-rule save that could also persist pricing.
- Fix commit: `1f745e0981315a0ef6fe1db22849a9297c9594d2` forward-ports only the scoped Saver UI/RPC block into the current Admin file and removes `feature.route_bundle` from the generic price-save payload; other current Admin code is preserved.
- NEXT: obtain exact-head CI for `1f745e0981315a0ef6fe1db22849a9297c9594d2`; if red, fix only the new first failure; if green, continue the #414 gate.

- Exact-head run `39082825269` on `1f745e0981315a0ef6fe1db22849a9297c9594d2` passed Saver `29/0`, Passenger Ride `93/0`, Credit ledger `59/0`, Credit Edge `77/0` and Credit Web `9/0`, then failed first in `tests/legacy-gp-retirement.cjs` because current Admin still contained legacy settlement persistence/review paths.
- Root cause: later Admin history had overwritten PR #332 credit-retirement integration: `credit.js`/`admin-credit` routing were absent while `qt_settlements`, GP slip review and `admin_review_gp` remained. Merchant and Native Merchant were already clean.
- Fix commit: `e8446b540566d012d83acf8520578bdbd4f16ce0` forward-ports the Admin-only retirement delta from PR #332 onto the current Admin file: loads credit center, removes settlement hydration/persistence and GP slip/review UI, redirects `admin-gp` to credit, preserves Saver and newer Return-to-Sender blocks.
- NEXT: obtain exact-head CI for `e8446b540566d012d83acf8520578bdbd4f16ce0`; if red, fix only the new first failure; if green, continue the #414 gate.

### 4. Rider online / deployed-schema compatibility
- Source/test status: DONE on its scoped compatibility fix
- Release status: BLOCKED
- PR: #365
- Branch: `work-native-rider-online-production-compat-20261016`
- HEAD: `e2f643605124008b311c47ca3c43706bead7d990`
- Root cause already recorded in PR: deployed Production orders schema lacked optional `ride_distance_km`, causing initial Rider snapshot failure and disabled online switch.
- Release blocker: authenticated real-device online confirmation remains owner acceptance.
- NEXT: do not reimplement compatibility logic; physical authenticated verification when owner/device evidence is available.

### 5. Customer Android location/address runtime
- Status: BLOCKED
- PRs: #366/#367 ancestry
- Latest listed dedicated location-runtime head: `4d65d54b9aacdad74d139c80498d592edb7c9102`
- Remaining evidence: authenticated address/shop switching, process restoration, physical-device crash reproduction/acceptance.
- NEXT: no claim of device completion until physical evidence exists; avoid reopening older camera/location implementations unless a newer successor proves a regression.

### 6. Passenger Ride
- Source/backend status: DONE for scoped production-compatible backend
- Release status: IN PROGRESS / BLOCKED where physical evidence is required
- PR: #387
- Branch: `work-ride-production-enable-20261017`
- HEAD: `d460c7fed64438f1c9fdacae0c2ef5647405ad5f`
- Evidence: QueueGo RC run 39004653410: SUCCESS
- NEXT: verify newest descendant integration with current Rider navigation/dispatch; complete unblocked source integration before physical Customer↔Rider E2E. Physical trip/GPS/device evidence remains BLOCKED.

### 7. Continuous dispatch / multi-stop stack
- Status: IN PROGRESS
- PR: #325
- Branch: `work-native-rider-continuous-dispatch-20261015`
- HEAD: `42a789642bc04f02e99400653f54291ee12ce7fa`
- Known boundary: Saver remains OFF; live route-bundle cap remains 2; Passenger Ride production enablement and nonzero-fee paths have release dependencies.
- NEXT: determine whether a newer descendant already contains this work before any modification. Do not restart #325 if credit/Ride/native successors already descend from it.

### 8. Merchant/Rider credit, top-up and GP replacement
- Source/test status: DONE for staged source on current credit successor chain
- Release status: BLOCKED
- Successor chain: #331 -> #332 -> #333
- Latest listed head: PR #333 `b3d61be03f2150181dbdb6c253b25c785759a243`
- Evidence in PR chain: credit ledger/retirement/Admin promo tests and race gates pass.
- Release blockers: `docs/CREDIT-RELEASE-GATE.md`, reviewed migration rollout, live provider verification where paid topups are enabled, authenticated physical-device evidence, controlled Production activation.
- NEXT: inspect for newer descendant before edits; no Production financial activation without the gate.

### 9. Merchant Grocery catalog
- Web source status: DONE for deployed web-first 414-image/catalog work as separately validated.
- Native source/test status: DONE for scoped catalog port
- Release status: BLOCKED
- PR: #364
- Branch: `work-native-merchant-grocery-approved-web-20261016`
- HEAD: `95ef486d5c7d801c0b76cd82080c2549a4c4c57a`
- Release blockers: physical-device catalog acceptance and signed-release certification.
- NEXT: do not confuse 10,000-item source/test coverage with launch readiness; reconcile only through newer native successor if one exists.

### 10. Customer + Merchant Play Store release
- Status: BLOCKED
- PR: #384
- Branch: `work-native-playstore-customer-merchant-20261017`
- HEAD: `3dfb9e757121d13d9e9ea24e92f5d800e1eec7b4`
- Blockers: signed release AAB/signing, Play Console Closed Testing and physical acceptance.
- NEXT: source-side release readiness can be inspected, but publication cannot be marked DONE without signed/Play evidence.

### 11. Express
- Status: IN PROGRESS
- Discovery result: no `express` branch/commit and no Express/parcel/courier implementation string was found in the current Rider/Customer successor files inspected (`Models.kt`, `QueueGoApi.kt`, `QueueGoRiderApp.kt`, Customer app, Web index).
- Dependency: must branch from the newest green native successor that already contains Continuous Dispatch + Credit + Passenger Ride + QueueGo navigation. Current candidate is PR #414 after its exact-head CI turns green.
- NEXT: after #414 is green, create one Express successor from that exact HEAD and add the minimum real 1-pickup/1-drop backend/domain contract first; do not invent a second dispatcher.

### 12. Laundry
- Status: IN PROGRESS (release-line reconciliation)
- Older dedicated PR #141 exists, while newer Rider navigation/dispatch successors contain Laundry handling.
- NEXT: determine the newest descendant containing shop-managed Laundry rules before edits; treat #141 as historical unless proven otherwise.

## Physical / external blockers owner must eventually supply

These are not code-completion claims:
- real-device authenticated Rider online confirmation;
- physical GPS/road/navigation test in Buriram, including parallel roads/U-turn/reroute/background-foreground;
- authenticated Customer↔Rider Passenger Ride physical E2E;
- physical Merchant catalog acceptance;
- signing credentials/process and signed release AAB;
- Play Console Closed Testing;
- explicitly approved Production/public QueueGo Map hosting;
- controlled Production credit/payment cutover and provider verification where applicable.

## Superseded rule

Do not treat every open PR as active work. Before touching any older PR, compare ancestry against the newest relevant successor. If newer work contains or replaces it, record it as superseded and continue only from the newer HEAD.

## 2026-10-18 Native Pilot trigger recovery
- Status: IN PROGRESS; #414 source checkpoint `6998fde0`, parent `e8446b540566d012d83acf8520578bdbd4f16ce0`. Fetch confirmed this branch is the only descendant of parent. Main `ccef0230aacb861d3c3dfc1ab2dc4dd9cb7db5a8`.
- Exact-parent Map Engine run `39083016044` passed Dependencies and regression; real graph build still running. No failure established yet.
- Missing gate root cause: Native Pilot push path filter excluded Admin-only changes despite full npm regression consuming Admin. Added shared Web regression inputs to existing trigger; no runtime changes.
- Verification: native-release-workflow 57 checks / 0 failures; legacy-gp-retirement 31 checks PASS; diff whitespace PASS.
- NEXT: push successor, inspect exact-head Native Pilot first failure; obtain Map Engine outcome. Only continue Express after #414 source gate passes.
- Release BLOCKED: authenticated physical GPS/road/device evidence, signing/Closed Testing and approved Production cutover remain required as listed above.

### 2026-10-18 local verification / publication blocker
- Source/test checkpoint: `633d9fb6aa825fcde88782456a91da0de284702d` (trigger fix `6998fde0`).
- Local full test script: `npm run test --ignore-scripts` EXIT 0; this executes the complete test chain without rerunning asset pretest. Pinned asset pretest completed in the separate npm invocation; that duplicate regression invocation was cancelled. Passenger Ride 93 PASS, road quote 27 PASS, Credit ledger 59 PASS, Edge 77 PASS, Web 9 PASS, legacy GP 31 PASS, Admin free credit 38 PASS.
- Ancestry proof: Credit/Admin promo successor is 272 commits behind and 0 ahead of this HEAD; approved-web Merchant Grocery successor is 262 behind / 0 ahead. Do not rebuild either implementation.
- Status: BLOCKED for remote Native Pilot evidence. Automatic approval review rejected git push, stating external publication to GitHub was not explicitly authorized; no alternate write path attempted. Local source work/tests remain available.
- NEXT: obtain owner approval to push the local #414 commits to chatchairins-source/QueuGo, push without force, inspect exact-head Native Pilot first failure; also finish checking Map Engine parent run 39083016044 (last observed graph build in progress). Express still depends on green Native Pilot.

## 2026-10-18 active Passenger Ride entry successor
- Owner authorization: ongoing QueueGo GitHub pushes are authorized (2026-10-18). Prior publication approval blocker resolved. Shell lacks credentials; use connected GitHub, no force.
- Active branch: `work-native-ride-entry-recovery-20261018`; source commit `a2a64c9b`, parent `575a10a1e1d05e6f437dbfe7ca5002f0e9967d3a` (#414 current packaging successor).
- Successor safety: update attempt against 43325ab6 was rejected after concurrent 575a10a1 appeared. Retained 575a10a1's packaging guard; did not publish overlapping local d08b6cfe/19ccac1. Backups retain local trees. Only independent Ride entry delta was applied onto latest successor.
- Root cause: config RPC rejection escaped V.ride before first render. Dedicated Ride successor's error guard was missing from native integration.
- Source/test status: DONE for scoped recovery. Runtime test reproduced rejection before fix; now 10/0 including RPC failures/retry and stale account/route fencing. Customer features 79/0, Passenger Ride SQL 93 PASS, source-integrity 93 scripts/81 references/0 failures; current Native release packaging guard PASS.
- Entry now renders recoverable shell + retry; config failures close new quote flow, existing known trip state is retained. No Supabase/Production mutation. Web cache version advanced; regression included in full test chain.
- Current #414 evidence: e8446b54 Map Engine run 39083016044 SUCCESS. Native Pilot on 43325ab6 run 39083523167 first failed retired Longdo checksum assertion; newer 575a10a1 fixes that contract. Its Native Pilot run 39083719573 was queued; Map run 39083723049 running. Raw Actions runs lists Native runs omitted by commit workflow summary.
- NEXT: publish this Ride successor as draft against #414; obtain exact-head CI and inspect newest #414 Native Pilot first failure. Do not restart Credit/Grocery implementations (their ancestry is already contained). Express waits for green integrated Native Pilot.
- Release BLOCKED: owner must provide authenticated physical Customer/Rider booking/start/cash-completion, navigation/GPS/process-restoration evidence; signing/AAB/Closed Testing and approved Production cutover remain separate.

### 2026-10-18 integrated CI coverage recovery
- Source/test status: DONE for trigger selection, source commit `0b63b727`, parent published Ride successor `013ac36304d9c3299ae9ceeea01b6d61655a9062` / draft PR #421.
- Evidence: #414 Native Pilot 39083719573 skipped independent Dispatch/Ride/Credit races and map-display-only because legacy branch-name predicates excluded the current integration line. New executable predicate test reproduced the skipped dispatch gate.
- Change: select existing races/map jobs on #414 route-authority and #421 Ride-entry branches; do not create a new dispatcher or publish release assets. Add existing Native Grocery 23-check contract to full regression, where it was previously absent.
- Verification: actual job-predicate test 16/0, Native Grocery 23/0, action SHA pinning 80/0, clean-source native-release-gate PASS, whitespace PASS. Credit ledger 59, Edge 77, Web 9, Admin free credit 38 PASS on parent.
- NEXT: publish to #421; require exact-head independent PostgreSQL races and Native build/runtime evidence, inspect first failure. #414 575a10a1 Native run 39083719573 was still running after packaging/backend gates passed; do not overwrite concurrent work.
- Release BLOCKED: owner must verify live provider/webhook settlement under controlled activation, real device Grocery authenticated save/reopen and Rider/Customer physical road flow, signed release and Closed Testing. No live money/provider/Production action was performed.

### 2026-10-18 integrated race evidence / nullable-camera successor
- Verified published HEAD `cd55e341dbbdcd3eb7458d4d5e9c5c4fe265a233`, Native Pilot 39083971696: independent PostgreSQL Dispatch 45/0, Passenger Ride 41/0, Credit 17 PASS; MapLibre OSM runtime SUCCESS. Full local `npm test` including asset pretest + field posttest EXIT 0. Android compile/navigation jobs were still pending.
- Newest #414 successor `0360622b086ab4e1bd844cbe5310a7716d04dfa6` fixes nullable camera.target after exact 575a10a1 Native Pilot compile failure. Merged this actual successor into #421 rather than duplicating its fix.
- Relevant merged-source checks: standalone navigation-map module PASS, integrated CI predicates 16/0. No reset/revert/force.
- NEXT: publish the merged #421 tree with both parents, finish exact-head Native Pilot 39084056688 / successor jobs; inspect first failure only. Grocery source reconciliation is staged in separate worktree commit 65dc9579, 262 existing checks PASS; its full npm regression is running before publication.
- Express remains IN PROGRESS waiting for integrated Native Pilot source/build gate, not a physical-owner blocker. Physical navigation, signing and Production gates remain BLOCKED as above.

### 2026-10-18 Grocery backend/provenance source reconciliation
- Source/test status: DONE for missing-source restoration, commit `65dc9579`; merge `aaabae4a` retains newest published #421 `0bb3426a` plus #414 nullable-camera successor 0360622b.
- Root cause: current integration restored Grocery Web/Native screens but omitted the existing backend migration/test/provenance module. Main's Grocery regression first failed ENOENT catalog/thai-grocery-import.json; all 11 Grocery migrations and 9 Web/SQL tests were absent.
- Restoration source: exact existing blobs from main `9a541522f12aa14d5df1c9cfcf087b1bb5ad7015`. Only missing files restored; current Runtime/Web/Native UI retained. No migration applied, no catalog imported, no Production mutation.
- Restored existing source: 11 migrations, 9 tests, real-fact/provenance inputs and their existing collection/import/probe dependencies. Registered existing Grocery suite in full npm test.
- Evidence: Grocery Admin 17, Catalog SQL 84, cash lock 5, cold exclusion 4, connection recovery 12, image identity 21, Makro scope 66, source categories 13, DOM 40 = 262 PASS. Full `npm test` on 65dc9579 EXIT 0 including map asset pretest and field posttest 28/0. Isolated 10k-row benchmark is not Production performance or data.
- Post-successor merge checks: integrated CI predicates 16/0, native release packaging guard PASS, navigation module PASS, Native Grocery 23/0, whitespace PASS. Existing security allowlist 30/0 and authenticated review 31/0 passed with restored migrations.
- NEXT: publish merged integration tree to #421; inspect exact-head Native Pilot compile/runtime and race outcomes. Express remains pending integrated green Native Pilot.
- Release BLOCKED: owner must validate authenticated Merchant catalog search/category, priced save, reload and image display on real iPhone/Android; source tests do not certify this or authorize deployment. Existing signing/Closed Testing/Production gates remain open.

### 2026-10-18 equivalent-snapshot Android runtime gate
- Status: IN PROGRESS; candidate source commit `5866726a`, parent published integration `38a72642229255d2fb8e28c34edfb4e4cb532ac6`. No production renderer change.
- Evidence: exact #414 HEAD 0360622b Native Pilot 39084056688, navigation job 117546810999 failed instrumentation. Artifact 12120113047 XML shows 3 tests / 1 failure: gpsRefreshTabReturnAndOverlayUpdatesReuseSameMapView at line 76, `QueueGo MapLibre state was not rendered`. Activity recreation and gesture tests passed; all 30 changed-GPS updates passed before the equal snapshot step.
- Root cause: refreshEquivalentOrderSnapshot() copies structurally equal data into default mutableStateOf; equality suppresses recomposition. The test wrongly awaited an additional render on unchanged state before checking route-layer reuse.
- Candidate: assert equal render count after idle plus existing map identity/route layer checks; retain positive awaitRender checks for changed GPS and stop advancement. Do not force redundant production renders or relax timeout/identity checks.
- Local evidence: navigation module PASS, native source 272/0, marker/overlay contracts PASS, integration job predicates 16/0, whitespace PASS. Actual Android instrumentation must pass on the new exact HEAD before this scope is DONE.
- NEXT: publish candidate to #421, inspect exact-head navigation instrumentation and first native compile failure; check newer #414 successors before edits. Express still waits for green integrated Native Pilot.

### 2026-10-18 Customer shared-map compile integration
- Status: IN PROGRESS; source candidate `177dcae2`. Parent published integration `a7c89a01`; latest #414 still 0360622b when fetched before commit.
- Exact-head evidence: Native Pilot 39084056688 / native-pilot job 117546811190 on 0360622b first failed CustomerCartCheckout.kt:34 unresolved QgLongdoLocationPickerMap; CustomerRideScreen.kt:132 also referenced retired QgLongdoTrackingMap. Those files were unchanged in a7c89a01.
- Root cause: shared renderer API was renamed to existing QgLocationPickerMap/QgTrackingMap, but Customer checkout/Ride and location instrumentation callers retained deleted symbols. Callback parameters and QgMapPoint contract already match the current shared API.
- Change: bind those three Customer callsites to the existing QueueGo APIs; update the corresponding source regression expectation. No duplicate wrapper or SDK restoration. Merchant/Rider debug callsite errors from the same log are separate next bounded integration work.
- Verification: native source 272/0, native checkout recovery 12/0, Passenger Ride SQL 93 PASS, whitespace PASS. Android compilation remains pending; do not mark this candidate DONE until exact-head build evidence exists.
- NEXT: publish successor, read exact-head Native Pilot first failure. Navigation equal-snapshot candidate is already published as a7c89a01 and awaiting actual instrumentation. Physical road/device/signing gates remain blocked.

### 2026-10-18 remaining retired-SDK consumer integration
- Status: IN PROGRESS; source `01929b6081c0cff97b18e3a1b6a7531fab229bc2`, capture runner `e8607ea4`. Parent exact published HEAD `8ec3488aae12eedd658fcefc915ddd50e00143f6`.
- Exact-head CI 39084732493: Navigation instrumentation SUCCESS, MapLibre OSM runtime SUCCESS, independent Dispatch/Ride/Credit races SUCCESS. First native compile failure moved to MerchantShopMapRuntimeActivity.kt:8 (retired QgLongdoLocationPickerMap), followed by Rider debug host, RiderMyDestinationDialog and unused RiderRouteDisplay SDK types. Customer compile errors were absent.
- Root cause: SDK/renderer retirement was incomplete across live picker callers and debug/lifecycle consumers; these stale sources still compiled against the deleted SDK.
- Change: Merchant debug picker and Rider destination reuse existing QgLocationPickerMap. Rider debug host mounts actual QueueGoRiderNavigationMap and reads actual MapLibre camera; debug-only dependency pins the same 13.5.3 SDK for that inspection. Remove unused Longdo drawing/network owner from RiderRouteDisplay, retaining vendor-neutral stop/geometry helpers and their JVM cases. Old live-provider debug overlay is replaced by current module/engine evidence; no Production route owner changed.
- Road-display regression now checks actual MapLibre active/future route styling, sole QueueGo client/cancellation/no external fallback and renderer cleanup rather than requiring retired Line/RouteService symbols.
- Full `npm test` on 01929b60 EXIT 0 including Grocery 262 and field posttest 28/0. Relevant source 272/0, navigation module/marker/road/display contracts PASS, native packaging guard PASS. No Kotlin source retains com.longdo/QgLongdo/RiderLongdoMap/MapGLSurfaceView. Actual Android compile/runtime remains required.
- Capture gap: workflow already removed retired-SDK baseline generation but runner still installed rider-untransformed.apk and required legacy crash reproduction. e8607ea4 now runs current tested APK directly; preserves instrumentation success, PID/crash checks and all 9 actual frame/camera artifacts. No fabricated baseline success. Python syntax and retained-gate checks PASS, stability source gate 106/0.
- NEXT: publish integrated candidate and read first exact-head Native Pilot failure. Do not skip map creation/lifecycle tests or claim runtime completion from source checks; missing authorized map runtime remains a release dependency. Express waits for integrated green build/runtime.
- Release BLOCKED remains physical GPS/device evidence, signed artifacts/Play testing and approved external runtime/Production activation; no Production mutation or release publication occurred.


### 2026-10-18 six-lane exact-head integration checkpoint
- Active integrated HEAD before this fix: `125d7fd34a20884d02f4b6bd06e3845281f3cb0f`; Native Pilot run `39085335642`.
- Exact-head PASS lanes: Passenger Ride PostgreSQL race, Continuous Dispatch PostgreSQL race, Credit PostgreSQL race, isolated Navigation Map runtime and MapLibre OSM runtime. Full regression, Native source, three APK build, 16 KB packaging and WebView rejection also PASS.
- First Native Pilot failure: Rider debug runtime constructed `MapView` while `queuego.map.service.base_url` was empty, causing `MapLibreConfigurationException`. Source now fails closed without constructing MapView until approved QueueGo Map hosting is configured.
- Map-display-only failure: capture harness expected Android location permission immediately on picker open; current UI requests permission only after the user taps `ใช้ตำแหน่ง GPS ปัจจุบัน`. Harness now follows the real interaction.
- External QueueGo Map runtime/visible-tile evidence stays `BLOCKED` when `QG_MAP_SERVICE_BASE_URL` is absent; the gate records BLOCKED rather than manufacturing a PASS. When an approved HTTPS runtime variable is configured, the existing map lifecycle/tile/draft evidence runs again.
- Grocery source/native/full regression remains green on this integrated head; authenticated physical Merchant save/reopen is still a release blocker.
- Express remains the only new service implementation lane and must branch from the newest integrated successor; no second dispatcher may be created.
- NEXT: obtain exact-head Native Pilot after this fix; while it runs, continue Express on a child successor and reconcile Laundry only through the existing Continuous Dispatch lineage.


### 2026-10-18 Express Client checkpoint A
- Successor base: `a0248910b58e28d8549772650a6c95e7e5f03d43`; existing Express backend, trusted road quote, dispatcher and proof tables are reused.
- Added authenticated owner-scoped `queuego_express_state` for Customer/Rider/Admin; anonymous access remains revoked.
- Native Rider `RiderJob` now carries Express distance/duration/item/weight/vehicle/sender/receiver data and exposes `isExpress`.
- Native Rider API now reuses `qg_rider_action_once(kind=express)`, `qg_express_pickup_with_photo` and `qg_express_complete_with_photo`; proof retries reconcile against the existing proof tables.
- No second dispatcher, wallet, route engine or proof store was introduced. No Production migration/enablement.
- NEXT: wire Customer Express screen + Rider Express active card on this same successor, then require exact-head Native Pilot/Express race gates.


### 2026-10-18 Express unified successor checkpoint B
- Unified successor HEAD parents: Rider UI `6bd7d8d70f3ca7aea617d66e263cd6e67c0290f1`, Express backend `b1e37818f9ff171e57daf8675f5018a2431a03f7`, Customer UI proof `eb3b8b56362a318d661a3754d364ec49fc9e8ef5`.
- Verified Express Phase 1 backend migration/tests are byte-identical on the Rider successor; trusted quote workflow trigger and proof instrumentation are retained from the newer client path.
- Exact-head gates already green before unification: backend run 3337 `b1e37818f9ff171e57daf8675f5018a2431a03f7`; client run 3336 `2813452191f56be83969f0cc761890a6ef3c3a90`; Customer UI run 3345 `eb3b8b56362a318d661a3754d364ec49fc9e8ef5`; Rider UI run 3347 `6bd7d8d70f3ca7aea617d66e263cd6e67c0290f1`.
- Laundry shop-managed run 3341 `0c5f91b9f7014e1928bc33d08c124e5be619e8f9` SUCCESS; Grocery image-rights run 3342 `9fc5f81f82bfbab351dfd2db94486a3ef65f86b5` SUCCESS.
- NEXT: require exact-head CI on this unified merge; then mark physical-device/GPS/signed AAB/Closed Testing/Production cutover as BLOCKED until owner/device actions. No Production enablement in this checkpoint.


### 2026-10-18 Express Web parity checkpoint
- HEAD before checklist: `2d95198ef9059e44031b06b9b3d4bd8401771937`.
- Customer Web: Express category, 1 pickup/1 dropoff form, trusted road quote, cash request, durable request recovery, owner-scoped state and Rider tracking.
- Rider Web: service/Express snapshot visibility, parcel offer/active UI, server-owned Rider payout, shared action-once states, QueueGo navigation, cash confirmation and existing qg-evidence proof RPCs with ambiguous-result recovery.
- Local source validation through connector: `customer-express.js` syntax PASS, `rider/rider-express.js` syntax PASS; Customer static gate 8/8 PASS; Rider static gate 9/9 PASS.
- NEXT: merge this Web successor into PR #425 after Native exact-head run 3349 closes green; then require one exact-head CI run covering Web + Native together. No Production deployment/cutover in this checkpoint.


### 2026-10-18 Rider Web Passenger Ride parity checkpoint
- HEAD before checklist: `0d0475fc0fb2ddfcd129db2c51d4b13cd0f8f41c`.
- Rider Web snapshot/history now includes `fulfillment_vertical=ride` alongside shopping + Express; Ride parity loads after Express and before initial Rider boot.
- Passenger flow uses shared `qg_rider_action_once(kind=ride)`: arrive pickup → start trip → arrive destination → cash confirm → complete; pre-pickup eligibility/credit requeue is surfaced and reconciled.
- QueueGo navigation keeps `ridePickup` / `rideDropoff` stages distinct; no merchant/photo proof flow is introduced.
- Source validation through connector: `rider/rider-ride.js` syntax PASS; static gate 8/8 PASS.
- NEXT: after Native exact-head run 3349 closes green, merge this Web Ride successor (which includes Express Web) into PR #425 and require one exact-head CI run over Web + Native together. No Production mutation.


### 2026-10-18 unified release successor — Express / Ride / Laundry / Grocery
- Parent service successor: `67fbacdd602afe52aa88aaf9aaedb494eae0f402`; exact-head Native Pilot run 3351 / Actions `39098238573` SUCCESS. Full QueueGo regression, Express/Credit/Passenger Ride/Continuous Dispatch races, Native unit tests, three APK builds, 16 KB packaging, WebView rejection and Android launch/map/voice lifecycle all passed.
- Laundry parent: `0c5f91b9f7014e1928bc33d08c124e5be619e8f9`; run 3341 / Actions `39093754953` SUCCESS. General QueueGo Rider Laundry pool/invites are retired; Merchant shop-managed transport is retained. Production deployment/revocation remains controlled and not performed here.
- Grocery parent: `9fc5f81f82bfbab351dfd2db94486a3ef65f86b5`; run 3342 / Actions `39093760840` SUCCESS. Review/source image URLs remain provenance only; Merchant Web/Native expose catalog images only through the approved-image gate. No image approval or Production migration is performed here.
- Merge rule: preserve Express/Ride code from the newest parent, apply Laundry's `QueueGoApi.kt` change only at the Rider Laundry-state hunk, and take the remaining non-conflicting green Laundry/Grocery blobs directly. No reset/revert/force and no duplicate dispatcher/route/payment/proof implementation.
- BLOCKED outside source CI: physical road/GPS evidence; authenticated Grocery save/reopen on real iPhone/Android; approved QueueGo Map hosting/runtime where required; signed AAB/Closed Testing; controlled Laundry/Grocery/Production cutover.
- NEXT: require exact-head CI on this unified successor. If green, source/repository Release Gate is ready; only the listed physical/signing/Production gates remain.


### 2026-10-18 unified release CI coverage gate
- Exact unified HEAD `244b5e6ed31fd8df336dad0f2e5dcf3b135837ce` started Native Pilot run 3352; Full QueueGo regression passed, but branch-name predicates skipped the independent Express/Credit/Passenger Ride/Continuous Dispatch races and three map runtime jobs.
- This checkpoint adds only the unified release branch selector to those existing jobs and adds an executable predicate test requiring all eight non-publishing gates.
- Runtime/business logic is unchanged. `publish-rider-road-test` remains forbidden for this integration branch.
- NEXT: require the new exact-head run to execute (not skip) all race + map jobs and Native Pilot; inspect the first real failure only. No Production mutation.


### 2026-10-18 unified release exact-head result
- Validated executable HEAD: `123b72971a556e1d350f8ab8487e97f17613c9e7`.
- QueueGo Native Android Pilot run 3353 / Actions `39099141100`: **SUCCESS**.
- PASS jobs: `native-pilot`, `express-races`, `passenger-ride-races`, `credit-races`, `continuous-dispatch-races`, `map-display-only`, `navigation-map-runtime`, `maplibre-osm-runtime`.
- Native Pilot evidence: Full QueueGo regression, source integrity, Android unit tests, Customer/Merchant/Rider APK build, Android 16 KB packaging, WebView rejection, launch/map/voice lifecycle and real launch evidence upload all PASS.
- Release publication guard: `publish-rider-road-test` remained SKIPPED.
- Source/code status: Express + Passenger Ride + Credit/Top-up + Continuous Dispatch + Laundry shop-managed + Grocery image-rights integration is green at the validated executable HEAD above. PR #426 is the current unified successor; duplicate PR #427/#428 are superseded.
- BLOCKED outside source CI: physical-device GPS/road evidence; authenticated Grocery Merchant save/reopen on real iPhone/Android; approved QueueGo Map Production runtime configuration where required; signed AAB + Play Closed Testing; controlled Production migration/function cutover.
- NEXT: no Production mutation from this checkpoint. Use this branch as the resume Source of Truth; any new code fix must be a strict descendant and must get a new exact-head CI run.


### 2026-10-18 Route Bundle Phase 2 matrix groundwork
- Parent Source of Truth: `33756e905388cec5c3945594f56272d4976faf26` (PR #426 docs-only successor of exact executable HEAD `123b72971a556e1d350f8ab8487e97f17613c9e7`, run 3353 SUCCESS).
- Finding: Native/Web dynamic-stop consumers and SQL `stop_plan` validation already model up to 5 separate orders, but the live claim remains intentionally capped at 2 and the existing road-matrix Edge function only builds Rider + two-order points. Raising the cap directly would overwrite prior stop plans and lacks full road evidence.
- Change: generalize the existing road adapter to 2–11 validated points; add service-authorized `action=stack` that discovers the current same-bundle orders plus one unassigned candidate; add private 90-second full-stack evidence cache and service-only cache RPC.
- Safety: no `queuego_claim_route_bundle` replacement, no `route_bundle.max_orders` change, no Saver/continuous-dispatch enablement and no Production mutation. Live capacity stays 2 until a later pricing/claim phase consumes this evidence and passes races.
- NEXT: run exact-head source/CI. If green, Phase 2 claim/pricing can consume the stack matrix in a separate successor; if red, fix only the first failure.


### 2026-10-18 Map Engine exact-head release integration gate
- Parent unified release successor: `33756e905388cec5c3945594f56272d4976faf26`; runtime remains unchanged in this checkpoint.
- Finding: Buriram Map Engine source is already present in the unified release tree, but its dedicated workflow only auto-ran on `work-map-roadgraph-buriram-*`; final release successors could therefore change shared `map-view/**` code without obtaining the full OSM/Valhalla/browser/offline-package/container gate on the same HEAD.
- Change: final `work-native-release-unified-*` pushes now select the existing Map Engine workflow. External Actions are pinned to reviewed 40-hex commits; `tests/github-actions-pinning.cjs` now includes this workflow and reviewed `actions/setup-python@v5` SHA `a26af69be951a213d495a4c3e4e4022e16d87065`.
- NEXT: require exact-head Native Pilot plus QueueGo Map Engine Buriram CI on the new successor. Production map hosting, physical GPS/road validation, signed AAB/Closed Testing and Production cutover remain BLOCKED.


### 2026-10-18 Route Bundle Phase 2 read-only planner
- Parent SQL-gate HEAD: `384b82e32a87d7e9950f043fd0f57bd0271cc92f`; Full QueueGo regression on run 3362 reached SUCCESS before this child was created, including the isolated PGlite execution of the Phase 2 stack cache.
- Added a pure stack planner for 2–5 Orders / up to 10 pending stops using only cached exact road edges. Policy remains pickup-first: all pending pickups precede all dropoffs; already-picked Orders omit their pickup stop.
- Added service-role-only stable `queuego_route_bundle_stack_plan_input` that returns only unexpired cache rows whose exact Order revisions/status/ownership still match. No authenticated/anonymous grant.
- Edge `action=plan` reads that RPC and does not require/call the routing provider; it performs no cache write, claim, assignment, repricing, payout or feature change.
- Safety remains unchanged: live `queuego_claim_route_bundle` untouched, `route_bundle.max_orders` unchanged at existing default/cap 2, Saver/continuous-dispatch enablement unchanged, no Production mutation.
- NEXT: require exact-head planner CI and isolated planner/PGlite tests. Only after green may a separate pricing/claim successor consume the read-only plan.

### 2026-10-18 Route Bundle integrated CI selection recovery
- Parent source: `fd300b9594f23f61fac145231e7d02c68b652224` (#431). Parent #430 exact run 3362 / 39103546298 SUCCESS; #431 run 3363 still completing emulator lifecycle at inspection.
- Reproduced missing release coverage: executable job-predicate test failed because Route Bundle successor skipped independent Dispatch/Ride/Credit/Express races and map runtime gates.
- Change: existing seven conditional Native jobs now select `work-native-route-bundle-*`; existing dedicated Map Engine workflow selects the same successor family. Release publication remains excluded. No application/Production behavior changed.
- Local verification: integrated predicate 68/0, Actions pinning 94/0, release workflow 57/0, whitespace PASS. Full regression pending below; actual new exact-head Native/Map CI is required.
- NEXT: combine independently tested bounded lane fixes on this descendant; publish draft successor and inspect exact-head CI. Physical GPS, authenticated merchant/device acceptance, signing/Closed Testing, approved map hosting and controlled Production cutover stay BLOCKED; owner/device evidence is required.
### 2026-10-18 Laundry Merchant numeric fee preservation
- Parent: PR #431 `fd300b9594f23f61fac145231e7d02c68b652224`; existing unified shop-managed Laundry retirement is preserved.
- Defect reproduced: localized grouping in HTML number-input values (`1,000`) sanitizes fees to empty and the existing save handler submits zero. Render raw numeric values in four fee/minimum fields; display-only currency remains localized. Native already initializes these inputs with numeric `.toString()`.
- Validation: new actual Merchant script render/save VM regression fails on parent (all four amounts become zero), passes with grouped/fractional fees preserved; added to full npm gate. Laundry reliability 14, Market/Laundry 75, Native Laundry parity, Play readiness 166, release certification 64 and release workflow 57 PASS; whitespace PASS. Source-integrity cannot complete in isolated checkout because generated `map-assets/maplibre-gl.css` is absent (external dependency provisioning required); no source-integrity pass claimed.
- Status: scoped source defect fixed; exact integrated HEAD CI required. No Production write, publication or signing.
- BLOCKED: owner physical authenticated Laundry pickup → wash → return/cash evidence, real-road GPS, signed AAB/Closed Testing and controlled Production cutover. NEXT: integrate this strict descendant and require exact-head CI before release readiness.
## 2026-10-18 Passenger Ride / Express successor audit
- Source base: newest assigned planner successor #431 `fd300b9594f23f61fac145231e7d02c68b652224`, fetched before isolated work. Express is implemented on this chain: shared-dispatch SQL, trusted quote, authenticated state/proof RPCs and Web/Native Customer/Rider screens. The older section 11 discovery result above is superseded; do not recreate Express.
- Source defect fixed in `dd82a9b4`: Customer Express tracking coerced null/blank Rider GPS to zero and drew a phantom `(0,0)` map/stop. Validate nonblank numeric coordinates and geographic limits before map creation/marker construction; preserve explicit numeric zero and numeric server strings. Advance Web cache version and register actual render/map regression in npm test.
- Verification: Express rendered lifecycle 25/0; Web contract 20/0; Express SQL 30 PASS; quote 33 PASS; Native client 26/0, Customer UI 22/0, Rider UI 15/0, Rider Web 9/0. Passenger Ride SQL 93 PASS, quote 27 PASS, entry runtime 10/0, Rider Web 8/0. Source-integrity 98 scripts / 84 references / 0 failures. Whitespace PASS. Tests use isolated fixtures; no Production mutations.
- Status: scoped source fix DONE; release remains BLOCKED for authenticated physical Customer/Rider parcel proof/cash and Passenger trip/GPS/process-restoration evidence, production route/provider/config/migration activation, signed AAB/Closed Testing. Exact integrated CI still belongs to the newest combined successor, not this isolated audit.
- NEXT: integrate only this bounded fix into the latest descendant, run combined exact-head CI; retain live stack cap 2 and current read-only planner boundary. Owner physical acceptance and controlled Production activation remain separate gates.
### 2026-10-18 Credit / Grocery integrated successor audit
- Source: exact `fd300b9594f23f61fac145231e7d02c68b652224` (#431); existing Credit/Grocery implementations retained.
- Gate repair: register existing complete 13-migration credit rollout integration test in full npm regression. Previously its dedicated workflow selected only the historical credit-live-enablement branch, leaving current integrated successors without this migration-order check.
- PASS: Credit ledger 59, Edge 77, Web 9, legacy retirement 31, Admin grant 38; complete isolated 13-migration rollout chain applies with zero seeded balances. Grocery suite 267 and Native Grocery source 24 checks pass.
- Full-regression attempt initially stopped at missing generated map asset in isolated checkout; source-integrity passes after restoring pinned asset inputs. Parent integrated full regression is pending. Action pinning 94/0 and whitespace PASS.
- Status: staged source verified; release BLOCKED by live provider/webhook settlement, reviewed controlled Production activation, authenticated physical Merchant search/category/priced save/reload/image acceptance and signing/Closed Testing. No Production mutation or remote publication.
- NEXT: run exact successor full regression/Native CI and satisfy existing physical/provider release gates. This checkpoint SHA is recorded by its git commit; parent audited SHA above.

## 2026-10-18 read-only stack full-active-set fence
- Base: fetched published #432 `cefb6bb87b29937fcec9ca7dc389edbb0bb9b06f`. Source fix: `896eb53043632cff03888226fe21152a47b08906`.
- Reproduced defect with isolated PGlite: cache A+B, then assign C to the same Rider without changing A/B revisions; the read RPC still returned A+B and omitted C. A new regression failed before the SQL change.
- Fix: existing service-only STABLE `queuego_route_bundle_stack_plan_input` now rejects any authoritative active Rider job absent from cached IDs. Combined with existing member state/assignment/revision checks this fences full active-set equality in its read snapshot. Only the staged read-only function source changed; no migration was applied, no live claim/cap/pricing/cache/Production data changed.
- Evidence: planner 30/0 including all six active statuses, completed historical jobs and other-Rider jobs; road-matrix 63/0; integrated CI predicates 68/0; anonymous security-definer allowlist 30/0; authenticated review 31/0; whitespace PASS. Existing service-only grant/revoke/search_path retained. Supabase current function docs/changelog reviewed; no new API/dependency/schema introduced.
- Status: scoped source fix DONE; integrated exact-head CI pending. NEXT: combine into latest descendant and run full gate. A future mutation must independently lock/revalidate authoritative membership and revisions atomically; this read-only snapshot is not live-claim authorization. Live stack cap stays 2; existing physical/release/Production gates remain open.

## 2026-10-18 six-lane canonical resume checkpoint
- Published source: PR #432 `945394e3bdb3dd1a43aa1f6e4e1ffe067eb92d8a`, branch `work-native-route-bundle-release-20261018`. This supersedes earlier pending local-audit notes above. Parent #430 run3362 and #431 run3363 SUCCESS.
- Combined full local `npm test` (pinned asset pretest, complete regression including new Express/Laundry runtime and existing Credit rollout-chain, field posttest) EXIT 0 on source trees `914b75ebfad3a70dc50dbd8815acd22d5cd8683d` (cefb6bb8) and `707796aac51d9ea5bd6568224ea40d53e61493b1` (945394e3). Remote trees were checked equal to the tested local trees.
- Scope completed in code/tests: Express missing-GPS map guard; Merchant Laundry numeric fee round-trip; Credit 13-migration gate included in main regression; Route Bundle successor selects all eight Native jobs; stale full-active-set plan rejection. Map/Navigation focused 112/106 and module/road/bundle checks PASS; no duplicate implementation.
- Exact new CI IN PROGRESS: Native run3365 / `39105171245`; Map Engine run148 / `39105174576`. Prior Native3364 was cancelled by the newer source push, not a source failure; it passed four independent races and both isolated map runtimes before cancellation. Do not claim all exact-head gates green yet.
- NEXT code dependency: bind read-only plans to committed next stop / authoritative route revision before any generalized pricing/claim. Existing live quote and claim still model two orders; live cap remains2. Complete staged source tests and new exact-head CI before consuming plans for assignment.

| Release gate | Status | Required owner/device action |
| --- | --- | --- |
| Rider Navigation Web/Android | BLOCKED physical acceptance | On an actual Buriram trip verify GPS lock, turns/U-turn/reroute, background/foreground and process recovery; retain device/build/route evidence. |
| Passenger Ride | BLOCKED authenticated physical E2E | Use Customer + licensed Rider accounts on actual devices: booking, arrival, start, tracking, destination, cash completion and restore; retain order reference and evidence. |
| Express | BLOCKED authenticated physical E2E | Run actual sender/Rider pickup/dropoff proof and cash completion, including interrupted upload/reopen, with order reference. |
| Grocery | BLOCKED physical Merchant acceptance | On iPhone Safari and Android verify category/search, priced add, save/reopen and approved-image display with a real Merchant account. |
| Laundry | BLOCKED physical Merchant acceptance | Verify shop-managed pickup, wash, return and cash using Merchant/staff accounts; general Rider pool remains retired. |
| Credit/Top-up | BLOCKED controlled activation | Review provider configuration and test signed webhook/settlement in the intended environment; perform approved ledger/cutover verification. Source tests do not certify live settlement. |
| Map hosting | BLOCKED runtime deployment | Supply/approve the intended HTTPS QueueGo Map runtime and validate deployed tiles/routing before activating the runtime config. |
| Play release | BLOCKED signing/console | Configure the release signing process, produce and verify signed AABs, then upload to Play Console Closed Testing and complete tester/device evidence. |
| Production cutover | BLOCKED release gate | Review migration/function/config plan and backup; execute controlled cutover only after applicable release evidence passes. |
### 2026-10-18 Phase 2 financial read-only audit
- Exact audited HEAD: `945394e3bdb3dd1a43aa1f6e4e1ffe067eb92d8a`; details in `docs/ROUTE-BUNDLE-PHASE2-FINANCIAL-AUDIT.md`.
- Existing generalized route simulator retained; effective money/readiness quote is pair-only, live cap stays two. No reusable generalized financial allocation engine exists to repair.
- BLOCKED code dependency: versioned 3–5 Order saving allocation/residual-satang/funding rule is absent. Existing percent knob defines only a pair split; do not invent live pricing or chain pair quotes.
- NEXT: agree versioned pure simulation contract, enrich existing read-only planner input with committed monetary/readiness evidence, then test conservation/payout floors/current customer price ceilings. No claim, repricing, cap, provider or Production change.
- Verification: existing isolated continuous-dispatch 108/0 and readiness/ETA/cancellation-funding 38/0 PASS; documentation whitespace check PASS. These do not certify generalized allocation or physical release.
## 2026-10-18 committed-next / bundle-revision read-only planner fence
- Base: fetched published #432 `945394e3bdb3dd1a43aa1f6e4e1ffe067eb92d8a`. Source: `d02035ead9d51e8f50408e18b82a8774cc9d859c`.
- Reproduced before fix: cheaper candidate BP replaced already committed AP in the read-only planner. Existing route validation preserves completed noncancelled prefix plus the first unfinished stop; a shortest unconstrained permutation did not honor that fence.
- Existing staged stack cache now persists actual primary bundle ID and route revision at cache time. Existing member checks require the same bundle identity. The STABLE service-only reader additionally requires the same ACTIVE bundle/rider/revision, removes cancelled historical stops and returns raw completed-prefix objects plus the exact first unfinished stop/key. Revision-only changes invalidate evidence even when all Orders retain their timestamps; recache captures the new revision.
- Pure planner retains that raw prefix and locked stop as metadata and filters permutations to the committed next key. If a locked dropoff conflicts with existing pickup-first policy, planning fails closed rather than reordering it. Output is still a read-only pending route; metadata is not assignment or claim authorization.
- Verification: new SQL cache/read + pure planner regression 73/0 covers 2,3,4,5 Orders, cheaper alternatives, completed-prefix object identity, cancelled-stop removal, picked-up progression, revision-only invalidation/recache and infeasible locked dropoff. Existing planner30, road-matrix63 (including isolated stack cache SQL), dispatch108, readiness38, planned-stop8, security30+31 and integrated CI predicates68 PASS; JS syntax and whitespace PASS. Shared isolated schema now mirrors the already-existing stop_plan/route_revision columns; old migration-chain tests pass.
- Status: scoped source DONE; combined exact-head CI pending. Only unreleased Phase 2 staged cache/read migrations and pure planner/test sources changed; no migration applied. Live claim, pricing, cap default2, Saver/dispatch flags and Production unchanged.
- NEXT: integrate latest descendant and run full combined exact-head gate. Later quality/financial quote must use readiness/service time, per-order road delay, payout floors and customer savings before any atomic claim/cap work. Future claim still needs rider/bundle/all-member locks, membership/revision revalidation and service-conflict exclusions; physical/release/Production gates remain open.

### 2026-10-18 reviewable generalized pricing proposal
- Existing financial audit now includes a concrete non-runtime `stack-savings-group-weighted-v1` proposal: reuse current group-percent/cost rules, allocate existing-group saving by current committed fees with deterministic satang remainders, preserve locked payouts, and reject any unfunded gap instead of adding a subsidy/bonus.
- It includes pair-parity and multi-order arithmetic examples explicitly labeled isolated policy examples. No calculation function, live rule/config, quote, claim, cap, wallet or Production data changed.
- NEXT decision: settle this exact commercial allocation policy before generalized financial simulation/claim. Existing two-order behavior remains authoritative. Other physical/signing/hosting/provider release gates are unchanged.

## 2026-10-18 published committed-plan successor
- Latest executable source: PR #433 `d3714ee0f80db6c35c5d9c37fa66bb2c635f9c94`, branch `work-native-route-bundle-committed-plan-20261018`; strict descendant of #432 `945394e3`. Local source merge06704cba and published source have identical tree `e1290f00e58bcf616f2ef890a99a6258928c725f`.
- Full local npm pretest/test/posttest EXIT0 on that exact source tree; committed-plan SQL/planner73, existing planner30, road63, dispatch108, readiness38, security61 and CI predicates68 PASS. This supersedes earlier pending local-regression notes for the same tree.
- Native exact-source run3366 / `39105592051` IN PROGRESS; at checkpoint four independent PostgreSQL races + MapLibre runtime SUCCESS, remaining Android gates still running. Parent945 run3365 /39105171245 has7/8 SUCCESS with final launch/voice lifecycle pending.
- Map Engine146 / `39104879637` on cefb6bb8 SUCCESS complete: real OSM graph/restrictions, blocked-network browser, offline package/rollback, container/staging/report. Newer945 Map148 /39105174576 is still fetching pinned external OSM; do not substitute the earlier result for an exact945 result. QueueGo map engine/map-view source is unchanged by the planner successor.
- NEXT: obtain final exact d371 Native result and record any first failure. Physical/device/signing/provider/hosting/cutover gates remain BLOCKED as detailed above; generalized live3–5 claim/pricing remains blocked on versioned allocation plus standalone financial/quality and atomic concurrent claim tests. Concrete commercial proposal is in docs/ROUTE-BUNDLE-PHASE2-FINANCIAL-AUDIT.md; owner can accept that exact rule or specify a different split/funding rule. No live capacity/price/Production change occurred.

### 2026-10-18 real offline map CI reconciliation
- Parent: PR433 docs successor `25c3f1b10c6ea1e28449191dae0665c96dc3be2b`; evidence: d371 map-display job succeeded while Customer permission/draft, Merchant and Rider scopes recorded BLOCKED because public HTTPS runtime was empty.
- Reconciled existing unpublished `294a47b3` debug real-Buriram provisioner with the current crash guards, rather than replacing newer map code. Only the existing 45-minute map-display step opts in. It downloads pinned real release PMTiles, verifies host/device SHA256, copies via debug-only adb run-as, and records source metadata explicitly denying routing/public/physical certification.
- Shared chooser requires FLAG_DEBUGGABLE and PMTiles v3 header; release still requires HTTPS configuration. Real-map opt-in cannot emit BLOCKED success. Existing pixel, pan, process and draft checks remain unchanged; emulator location/camera stays within actual Buriram coverage.
- Local PASS: provisioner 5 tests; Native source273, stability106, integration68, Actions pinning94, navigation module/runtime budget/MapLibre contracts; Python syntax and diff whitespace. New Kotlin guard JUnit is included for CI but no local Gradle/emulator exists.
- Status: IN PROGRESS runtime gate. NEXT: integrated exact-head CI must actually execute Customer denied/allowed/draft, Merchant and Rider tile/lifecycle scopes using verified archive; fix first real failure. Public hosting, physical GPS/roads, signing/Play and Production remain BLOCKED separately. No Production mutation/publication.

### 2026-10-18 strict real-map first actual failure
- Parent/source `f5fef7dec5fa31cf7eac6798ff0e1b436e8ff35a`, PR434; Native3367 / Actions39106411488 map-display job117613098748: actual Kotlin/instrumentation compile PASS, real archive host/device provisioning PASS, runtime FAIL at `GPS action missing before permission request`.
- Evidence: artifact12129185048 `customer-location/denied/picker.xml` contains Android permissioncontroller dialog and DENY/WHILE USING THE APP controls, with no app GPS control while dialog owns screen. CustomerLocationPicker already automatically calls requestGps after mapReady +150ms. Previous harness incorrectly required an explicit app GPS tap first; real offline rendering exposed that obsolete assumption.
- Bounded fix changes capture harness only: with real/configured map wait for actual automatic Android permission dialog; without configured map retain explicit GPS action. Actual deny, pixels, pan, resume, draft/process and all later scopes remain required. App source, permission behavior and release guard unchanged.
- PASS: actual extracted provision/permission harness8 tests (includes observed permission-dialog shape, missing dialog rejection and explicit no-runtime path), stability106, Native source273, Python syntax, whitespace.
- Status: IN PROGRESS exact-head runtime. NEXT: publish strict descendant and inspect first actual runtime failure/new exact-head map result; do not label denied/allowed/draft/Merchant/Rider runtime complete before evidence. No Production/public hosting/signing/physical certification.


### 2026-10-18 strict real-map second actual failure — foreground lifecycle evidence
- Exact parent `538cd8d09e26d139c270fad0f1a850387a311972`, Native run3368 / Actions39106933295, map-display job117614776109: Customer real-map scopes and Merchant allowed/denied progressed; Rider phone-denied PASS, Rider phone-allowed instrumentation PASS with injected GPS camera verified, then harness failed at obsolete `starts >= 3 && stops >= 3`.
- Artifact12129191548 proves the actual lifecycle: phone-allowed logged 8 Activity CREATED/RESUMED stress cycles while the foreground location service stayed alive, then one stop/start across `scenario.recreate()`, and final cleanup — exactly 2 starts / 2 stops. Denied scope logged 0/0. Requiring 3 restarts incorrectly treated foreground-service survival as failure.
- Fix: capture gate now requires >=8 Activity stress cycles, >=2 starts for permitted scope, and balanced start/stop cleanup; denied scope requires 0/0. Added isolated regression cases for recreate restart, cleanup imbalance and denied permission. App/service behavior is unchanged.
- Status: source fix checkpoint ready; exact-head Native CI required. NEXT: run real-map gate again and fix the next first actual runtime failure only. Physical road/GPS acceptance, public HTTPS hosting, signing/Play and Production remain separate BLOCKED gates.


### 2026-10-18 strict real-map third actual failure — deterministic final FGS cleanup
- Exact `f9bd7e9c` Native3369 / Actions39107754467: 7/8 jobs SUCCESS; only map-display job117617178719 failed. Artifact12129575823 phone-allowed proves 8 lifecycle stress cycles, 2 foreground location starts and 1 stop: first stop/start occurred at Activity recreate, but the test ended while the restarted service was intentionally still alive.
- The harness correctly requires balanced cleanup, so the fix is in instrumentation rather than weakening the gate. `RiderMapRuntimeTest` now explicitly calls `RiderNavigationLocationService.stop(targetContext)` after ActivityScenario closes and waits 750 ms for service onDestroy/log evidence.
- This is test-only cleanup; Rider production lifecycle/service behavior is unchanged. Expected exact-head evidence is 2 starts / 2 stops for allowed and 0 / 0 for denied, with all existing tile/camera/crash checks retained.
- NEXT: exact descendant CI must make map-display green. Public HTTPS hosting, physical GPS/real-road acceptance, signing/Play and Production remain separate BLOCKED gates.


### 2026-10-18 strict real-map fourth actual failure — post-subscription GPS handshake
- Exact `8450cce9` Native3374 map-display reached the large-allowed scope and failed GPS camera assertion: expected 103.1300059 / 15.0088319, actual camera remained at the persisted/fallback 103.0994795 / 14.9918756. This proves the prior phone-allowed scope was not sufficient because its injected point intentionally matched the fallback camera.
- Root cause is a CI timing race: emulator `geo fix` was injected before Rider instrumentation/location subscription started, while the production fresh-location request requires a post-start measurement; large-allowed exposed the missing fresh callback.
- Fix keeps production location policy unchanged. Allowed map instrumentation now runs concurrently, waits for the real `foreground_updates_started` log, injects the requested GPS fix three times after subscription, and the Android test independently waits up to 30s until `RiderNavigationLocationStore` contains that exact fix before recenter/camera assertions.
- Added source regression ensuring the map capture no longer pre-injects GPS before instrumentation. Existing lifecycle, camera continuity, tile, crash and explicit FGS cleanup gates remain intact.
- NEXT: exact-head map CI; require phone-allowed and large-allowed to log `injected_gps_store_verified` + `injected_gps_camera_verified`. Public HTTPS hosting and physical road/GPS acceptance remain separate BLOCKED release gates.

### 2026-10-18 Route Bundle Phase 2 versioned financial allocation checkpoint
- Base: strict descendant of real-map HEAD `f9bd7e9c83399d596d85f1f7252514a7ca7976d0`; live claim/cap remains unchanged at 2 and no Production mutation is allowed.
- Implemented pure read-only policy `stack-savings-group-weighted-v1` using integer satang: candidate available saving is split by the existing group percentage; the existing group's share is weighted by current committed fees with floor + largest-remainder allocation and immutable Order-ID tie-break.
- Existing Rider locked payouts are preserved exactly; candidate Rider payout is the passed incremental compensation; no Rider/platform bonus is created. If proposed customer collections cannot fund all preserved payouts, simulation returns ineligible with explicit funding gap instead of inventing subsidy.
- Local isolated test: 19/0 checks PASS for pair parity, 4-order weighted 445/333/222 satang allocation, deterministic remainder, funding-gap rejection, positive-savings guard and unrepresentable money/rule rejection.
- NEXT: bind this policy to authoritative read-only route/readiness evidence and existing pair cost oracle, then add 2–5 order financial/quality conservation tests. Do not raise live cap or mutate claims until atomic concurrent-claim gate passes.


### 2026-10-18 authoritative Route Bundle financial snapshot
- Added service-role-only STABLE `queuego_route_bundle_stack_financial_input` on top of the existing exact-revision/full-active-set/committed-next reader. It reads current customer fee, preserved Rider payout, Merchant readiness and shop category in the same PostgreSQL statement snapshot; caller-supplied money is not accepted.
- Financial rule snapshot is versioned with `stack-savings-group-weighted-v1` and returns satang/km, pickup/dropoff/minute costs, detour limit and effective food/cafe delay cap. Function follows Supabase security guidance: explicit search_path, PUBLIC/anon/authenticated execute revoked, service_role only.
- Added isolated PGlite regression to the full npm gate; it checks exact 50.00/55.00/40.00 THB conversion to satang, default rule snapshot, food 5-minute cap, stale revision rejection, role rejection and read-only SQL.
- Fixture now mirrors already-existing `orders.rider_delivery_payout` and `merchant_ready_estimate_at` columns used by current migrations. No Production migration was applied from this chat.
- NEXT: compute generalized incremental route burden from the same 2–5 order matrix/readiness snapshot, feed only that server-derived compensation into the versioned allocator, then test financial + quality rejection and atomic claim locking.


### 2026-10-18 generalized 3–5 Order financial/quality read-only plan
- QueueGo stack planner now optionally uses the authoritative financial snapshot to include Merchant-ready waiting and 2-minute stop service time while preserving the same pickup-first + committed-next policy. Existing callers without financial evidence retain the prior travel-only behavior.
- Added read-only existing-stack baseline planner (1–4 current Orders). Generalized 3–5 financial plan measures candidate detour against that baseline, compares every existing dropoff delay plus candidate ride delay, enforces max-detour/effective max-delay, and derives incremental Rider compensation only from server road/readiness evidence.
- Compensation reuses current rule components: incremental km + pickup + dropoff + delay minutes. It is then passed to `stack-savings-group-weighted-v1`; locked existing Rider payouts remain untouched and any funding gap rejects the stack.
- Local focused operational test: 14/0 PASS. 3-order case produced 1,000 m detour, 6 min max delay, 1,500 satang candidate Rider compensation, deterministic 781/469/1,250 satang customer savings, 9,500 collections = 9,500 Rider payouts. Food 5-minute limit and 20-minute Merchant readiness both reject.
- Edge handler `action=plan` now reads `queuego_route_bundle_stack_financial_input`; for 3–5 it returns the generalized read-only financial gate. Two-order money remains explicitly delegated to the existing live pair quote authority to avoid duplicate pricing semantics.
- NEXT: exact-head PGlite/full CI; then implement atomic 3–5 claim mutation behind the existing max_orders=2 rollout cap with row locks/revision/membership/financial revalidation. Do not activate 3–5 live until that concurrent-claim gate is green.


### 2026-10-18 generalized readiness fail-closed correction
- Review of exact `2d9a0dca` found generalized operational planning treated missing `merchantReadyEstimateAt` as zero wait and had no direct 5-minute pickup-wait rejection. That could make incomplete readiness evidence look eligible.
- Planner now rejects missing/invalid readiness whenever authoritative financial evidence is active, records per-Order pickup waits, and the generalized financial gate rejects any actual planned pickup wait above 300 seconds before detour/delay or money allocation.
- Financial snapshot now carries `maxPickupWaitSeconds=300`, matching the existing pair readiness gate. Focused tests use explicit readiness timestamps, expect 20-minute candidate readiness to fail with the readiness-wait reason, and assert missing readiness fails closed.
- No live claim/cap, price, Rider payout or Production state changed. NEXT: exact-head test/CI, then atomic generalized claim behind max_orders=2.


### 2026-10-18 staged atomic generalized Route Bundle claim
- Added short-lived private stack quote cache. The service-role cache RPC re-reads the authoritative exact-revision financial snapshot, validates the versioned funded allocation, preserves the committed prefix, requires one pickup + one dropoff per 3–5 active/candidate Order and stores only a <=30s quote.
- Added authenticated Rider `queuego_claim_route_bundle_stack(quote_id)`: locks Rider, quote, bundle and every quoted Order; revalidates exact matrix/order revisions, complete active membership, bundle revision, current platform financial rules, Customer fee ceilings, locked Rider payouts, supported verticals, NEXT/Laundry conflicts and quote funding before any mutation.
- Mutation preserves every existing locked Rider payout, applies only quoted lower Customer fees, assigns the candidate payout from incremental burden, refreshes pending payment/delivery rows, commits the full stop plan with route_revision+1, consumes the quote, writes audit and Customer/Merchant notifications atomically.
- Safety gate is explicit: target count must be <= current `route_bundle.max_orders`; the existing default/fallback remains 2, so this 3–5 claim path is staged but cannot activate itself. No rule/feature or Production value is changed by the migration.
- Added isolated PGlite gate to full npm test: quote cache succeeds, default cap 2 blocks claim, stale revision fails, opt-in test rule 3 permits one atomic 3-Order claim, Customer collections equal preserved/new Rider payouts exactly, route revision advances once, and quote replay is rejected.
- NEXT: wire service handler `action=quote` to cache only an eligible 3–5 financial plan; run exact-head full/Native CI and add a competing-claim race test before any reviewed cap increase.


### 2026-10-18 service quote handoff for generalized stacks
- Existing route-bundle matrix Edge handler now supports explicit `action=quote` only for 3–5 Orders. It reuses the same authoritative financial snapshot + planner + generalized financial gate; `action=plan` remains read-only.
- An eligible generalized plan is converted to committed-prefix + remaining stop-plan objects and sent to the service-only SQL quote cache. Ineligible financial/readiness/quality plans return 409 and never create a quote. Two-Order quote behavior remains on the existing live pair authority.
- Quote response explicitly reports `readOnlyBusinessState=true`: only a short-lived private quote row is written; no Order, price, assignment, bundle revision or feature/rule changes occur.
- Added isolated handler test to full npm gate for plan-no-write, eligible quote cache payload and readiness-ineligible no-cache behavior.
- NEXT: add a competing-candidate claim race fixture, then require exact-head full/Native CI. Live 3–5 remains blocked by max_orders=2.


### 2026-10-18 generalized quote route-binding + competing-candidate stale gate
- Hardened the private quote cache so money and route cannot diverge: `p_stop_plan` must now equal committed prefix + the exact `financial.route.fullKeys` translated through the authoritative snapshot point map. A reversed or otherwise different valid-looking plan returns null and is never claimable.
- Extended isolated staged-claim coverage with two candidates quoted against the same bundle revision. After one claim advances `route_revision`, the other candidate's quote fails closed; the existing Rider advisory lock + bundle row lock + exact revision fence remain the concurrency authority.
- The financial fixture now carries the actual route `fullKeys`, matching the Edge evaluator output. No Production data/rule/feature was changed and `route_bundle.max_orders` still defaults/falls back to 2.
- NEXT: exact-head full/Native CI. If green, source logic for generalized quote/claim is gated; remaining activation requires reviewed cap rollout plus physical multi-stop acceptance.


### 2026-10-18 exact-head financial simulator syntax repair
- Native run3377 on `3db1faa2` reached the Route Bundle section after all preceding suites passed, then Node stopped at module parse with `SyntaxError: Invalid or unexpected token`.
- Root cause is exact and source-local: the initial simulator checkpoint accidentally committed escaped template-literal delimiters (`\``) and a double-escaped numeric regex (`\\d`) as literal JavaScript source.
- Fixed only those source escapes: template literal is valid JavaScript and percent parsing again matches decimal digits. Financial policy/math is unchanged. Successor `85b937d7` route/claim hardening is preserved.
- NEXT: exact-head CI must pass this parser boundary before any additional feature mutation.


### 2026-10-18 corrected route-binding source generation
- Root cause of the corrupted successor insertion is confirmed: JavaScript string replacement interpreted the SQL regex tail `$'` as the special “suffix of input” replacement token and duplicated the rest of the migration.
- Rebuilt the staged quote/claim migration from intact `bacbb461` and re-applied exact route/money binding using callback replacement, so dollar signs are literal SQL text.
- Pre-commit structural gate: migration length 21425 bytes, exactly 1 quote-cache function, 1 generalized claim function and 1 safety footer; competing-candidate test and `cfc98869` simulator parser fix remain on the branch.
- No Production mutation; generalized 3–5 claim is still blocked by default/fallback `route_bundle.max_orders=2`.


### 2026-10-18 migration anti-duplication executable guard
- The staged claim regression now asserts exactly one generalized quote-cache function, one generalized claim function and one safety footer, in addition to executing the migration in PGlite.
- This specifically prevents recurrence of the source-generation `$'` replacement expansion that duplicated the claim body. No business behavior or Production state changes.


### 2026-10-18 exact-head Route Bundle first CI failure — JSON object cardinality
- Exact successor `85a5f494`, Native3382/native-pilot: every preceding source suite reached the Route Bundle financial gates successfully, then PostgreSQL-compatible execution stopped in `queuego_route_bundle_stack_financial_input` because `jsonb_object_length(jsonb)` is not a PostgreSQL function.
- Replaced the invalid helper with an exact key count: `select count(*) from jsonb_object_keys(v_financial_orders)`. Semantics remain unchanged: fail closed unless the authoritative financial object has one entry for every 2–5 snapshot Order.
- No pricing, claim, cap, payout, feature or Production behavior changed. Existing financial snapshot/claim tests exercise this function directly, so exact-head CI is the regression gate.
- NEXT: run successor CI and fix the next first actual failure only.


### 2026-10-18 DB-side generalized Rider compensation recomputation
- SQL quote validation now recomputes candidate Rider incremental compensation from detour meters, km rate, pickup/dropoff cost and added minutes in the authoritative financial-rule snapshot.
- Added an internally-consistent underpayment tamper case (1,300 -> 1,200 satang with adjusted totals); the service-only quote cache must reject it. This closes the gap where service JSON could otherwise define its own incremental payout.
- No cap/feature/Production activation; default generalized path remains blocked by max_orders=2.


### 2026-10-18 generalized targeted 30-second offer gate
- Closed the authority gap between a valid 3–5 quote and Rider acceptance. Added service-role-only `queuego_offer_route_bundle_stack_quote` that publishes into the EXISTING `qg_rider_order_offers` table for at most 30 seconds; no second pool/queue exists.
- Offer publication is fail-closed on current max_orders, route-bundle feature, active/online Rider, exact bundle revision, exact candidate revision, complete quoted active membership, Laundry/return conflicts and any already-live offer for the candidate or Rider. Current default cap=2 therefore prevents 3–5 offers from becoming live.
- Generalized claim now requires and row-locks that exact live targeted offer for the authenticated Rider. Successful assignment records the existing offer-history `accepted` outcome and consumes the offer in the same transaction.
- Edge `action=quote` publishes the targeted offer after caching an eligible short-lived quote and returns `offerPublished`. Isolated tests cover cap=3 publication, one live offer per Rider, offer history/consumption and stale competing quote refusal.
- No Production rule/feature/cap changed. NEXT: extend bounded refresh discovery so active 2–4 Order bundles can actually generate `action=stack` evidence/quote work only when max_orders permits.


### 2026-10-18 targeted-offer SQL delimiter fix
- Audited successor `a7faf569` before waiting for CI and found the new service-only targeted-offer function used invalid PL/pgSQL dollar quoting (`as $ ... end $;`).
- Corrected it to `as $$ ... end $$;` and added an executable source assertion for the exact SECURITY DEFINER/search_path/function delimiter header.
- Targeted 30-second offer authority, claim requirement, offer history, compensation recomputation and max_orders=2 safety remain unchanged.


### 2026-10-18 generalized stack discovery to targeted offer chain
- Added service-role-only `queuego_route_bundle_stack_refresh_claim` using the EXISTING route-matrix lease. It emits `action=stack` only for active 2–4 Order bundles when continuous dispatch is enabled and current max_orders>=3; fallback cap=2 returns no work.
- Discovery requires one coherent active bundle, online/available Rider with an active readiness lease, no outside active job, no NEXT/Laundry/return/live-offer conflict, supported nearby candidate and no fresh same-revision stack matrix. It never assigns or reprices.
- Existing Edge refresh now runs legacy pair discovery then generalized stack discovery through the same release RPC. A successful 3–5 stack matrix immediately enters the existing generalized quote path, which in turn publishes the 30-second targeted offer only when the rollout cap still permits it.
- Added isolated SQL discovery, refresh-worker orchestration and handler stack→quote→offer coverage to the full npm gate. No Production feature/rule/cap mutation.
- NEXT: exact-head CI. Once green, remaining generalized Route Bundle gates are reviewed cap activation and physical 3–5 stop acceptance.


### 2026-10-18 generalized stack discovery compatibility correction
- Exact `6caa800f` Native3386/native-pilot reached the existing `rider-road-matrix.cjs` contract and failed because every 3–5 `action=stack` response had been widened with quote fields, even for legacy evidence-only callers.
- Preserved the established `action=stack` API: automatic quote/offer chaining now requires explicit `quoteAfterCache=true`. Only the new cap-gated generalized stack discovery emits that flag; existing direct/manual stack evidence calls keep their original response shape.
- Updated discovery/worker/handler focused tests to carry and assert the explicit flag. No pricing, assignment, payout, cap, feature or Production state changed.
- NEXT: exact-head CI; then fix only the next first actual failure.

### 2026-10-18 real-map fourth actual failure — AOSP emulator has no Google Play location provider
- Exact Map HEAD `95692e4e`, Native3381/map-display job117622313292: real QueueGo/MapLibre scopes reached Rider phone-allowed after the foreground service logged `foreground_updates_started`, but the test failed waiting for the expected location-store fix. Logcat repeatedly reports `GooglePlayServicesUtil ... requires the Google Play Store, but it is missing`, `SERVICE_INVALID`, and `fresh_location_unavailable`.
- This is a CI device capability boundary, not a renderer or production FGS crash: navigation-map-runtime, MapLibre runtime, continuous-dispatch, credit, Ride and Express jobs are green. The AOSP emulator accepts `adb emu geo fix`, but its missing GMS FusedLocationProvider cannot deliver that measurement to the app.
- Fix is androidTest-only: permitted GPS scopes still start the real foreground location service and first wait for a provider-delivered fix; only when explicit instrumentation flag `allowDebugStoreInjection=true` is present and no provider fix arrives within 3 seconds does the test publish the expected Buriram point into the internal navigation store. Camera/render/lifecycle/recreate/cleanup assertions remain real. Production `RiderNavigationLocationService` is unchanged.
- Added source guard proving the fallback exists only in androidTest/capture harness and is not present in production service. Physical-device GPS/GMS accuracy and real-road acceptance remain BLOCKED and are not certified by this emulator fallback.
- NEXT: exact-head real-map CI; inspect the next first runtime failure only.


### 2026-10-18 stack discovery isolated-schema dependency correction
- Pre-CI audit found the new discovery test referenced `qg_private.rider_dispatch_preferences` while the minimal fixture intentionally does not create that later Phase-1 table.
- The test now loads the real predecessor migrations in order: continuous dispatch → dynamic stop plan → route-matrix replenishment → Phase-2 stack matrix → generalized stack discovery. The hand-made lease table was removed.
- Production migration dependency was already valid; this fixes only isolated test setup and increases fidelity to the actual schema chain. No runtime/Production behavior changed.
- NEXT: exact-head CI.

### 2026-10-18 no-GMS recreate location-store continuity
- Follow-up to the CI-only GPS fallback: the prior lifecycle evidence proves `scenario.recreate()` restarts the navigation foreground service, whose production `onDestroy()` correctly clears the location store.
- Android instrumentation now requires a fresh expected fix after recreate and explicitly recenters before capturing the recreated real-map frame. On a GMS-capable device this still prefers the real provider; on the explicit no-GMS CI scope it reuses the androidTest-only store fallback.
- Production service/location behavior remains unchanged; physical GPS/GMS certification remains a separate gate.


### 2026-10-18 refresh helper backward-compatibility correction
- Exact `13edb751` Native3390/native-pilot reached legacy `rider-road-matrix.cjs` and failed because `refreshRouteMatrices` unconditionally called the new stack-discovery RPC, changing both failure counts and the established helper return shape for existing callers/mocks.
- Generalized discovery is now explicit: `refreshRouteMatrices(...,{includeStack:true})`. The Edge worker's `action=refresh` opts in; legacy helper callers remain pair-only and preserve their exact historical `{refreshed,failed}` / busy response.
- The generalized refresh-worker test explicitly opts in and still proves pair discovery → stack discovery → same lease release. Production assignment/pricing/cap remains unchanged.
- NEXT: exact-head CI, then fix only the next actual failure.


### 2026-10-18 additive stack-refresh rollout isolation
- Pre-CI compatibility audit found an older Edge worker transport can know only `queuego_route_matrix_refresh_claim/release`. If the new stack coordinator is not deployed/recognized yet, unconditional propagation would turn an otherwise successful legacy pair refresh into HTTP 503.
- With `includeStack=true`, stack discovery is now an additive optional extension: coordinator failure is ignored after the established pair refresh completes. The existing pair coordinator remains strict; stack work that was successfully leased still uses the same finally/release path, with lease expiry as the crash fallback.
- This preserves rollout ordering and old worker behavior while allowing the new discovery path whenever its migration is present. No assignment/pricing/cap state changed.


### 2026-10-18 targeted stack-offer exact-member revision hardening
- Offer publication now revalidates every existing stack member against the quote's exact `updated_at`, Rider and bundle ID before showing the 30-second offer. Previously complete active membership was checked, but an existing Order could change readiness/financial state without changing the bundle revision and still receive an offer that claim would reject.
- Added rollback regression: mutate an existing member revision after quote → offer publisher returns false; rollback restores the snapshot → offer publishes. Atomic claim revision checks remain unchanged.
- This reduces unclaimable Rider offers without relaxing any gate. Current max_orders fallback stays 2; no Production mutation.


### 2026-10-18 Native3394 generalized stack SQL parser correction
- Exact Route Bundle HEAD `aba47fa6` run3394 reached generalized financial/quality tests, then PGlite failed parsing `20261018083000_route_bundle_stack_quote_claim_staged.sql` at position 10556.
- Root cause: the targeted-offer function alone opened with `as $` and closed with `end $;` instead of PostgreSQL `$$` dollar quoting.
- Corrected only the two delimiters inside `queuego_offer_route_bundle_stack_quote`. No business rule, payout, cap, feature flag or Production migration was changed; max_orders remains 2.
- NEXT: exact-head CI; fix only the next first failure.

### 2026-10-18 Native3391 process-death harness correction
- Exact Map HEAD `89f1a556` run3391: map-display-only SUCCESS, MapLibre/navigation/race jobs SUCCESS; native-pilot failed only in three-role system-kill recovery because Customer remained alive one second after asynchronous `am kill`.
- Harness now polls `am kill` for up to 5 seconds. If a debuggable AOSP emulator process remains bound (for example by WebView), the test sends SIGKILL through `run-as <package>` as the same app UID, then verifies the process is actually gone before cold relaunch.
- This intentionally avoids `am force-stop`: force-stop changes package stopped-state semantics and would not represent Android LMK/process recreation. Production Customer/Merchant/Rider code is unchanged.
- Evidence records whether `am-kill` or `run-as-sigkill` was required. Release still needs physical OEM process-recreation evidence; emulator harness is not a physical certification.
- NEXT: exact-head Native Pilot; fix only the next real failure.

### 2026-10-18 Native3395 harness syntax correction
- Exact Map HEAD `749f3f73` Native3395 failed before Android build at Python parse line 743: the new system-kill harness had a one-character typo `pkg"]`.
- Corrected only that list terminator to `pkg])`. No app/runtime logic changed.
- NEXT: exact-head CI and then process-death runtime evidence.


### 2026-10-18 Native3398 SQL delimiter publication correction
- Exact `2e9bf04c` still showed the targeted-offer function with single-dollar delimiters; inspection of commit `067a423c` proved that prior intended delimiter correction did not actually change the migration blob.
- Replaced only this function's body delimiter with explicit PostgreSQL named dollar quote `$qg$` / `$qg$`, avoiding ambiguous publication. Commit diff must include the migration file.
- No generalized claim/offer business logic, cap, feature, payout or Production state changed.


### 2026-10-18 Route Bundle exact-head Native Pilot trigger coverage
- The Native Pilot full regression executes Route Bundle Phase-2 SQL/Edge tests, but its push path filter did not include `supabase/functions/queuego-route-bundle-matrix/**` or `supabase/migrations/*route_bundle*.sql`. Therefore SQL-only correction `529cd5aa` did not receive a new exact-head run.
- Added those two existing source families to the Native Pilot push paths. The workflow file is itself selected, so this checkpoint must trigger exact-head CI.
- No runtime, cap, pricing, claim or Production behavior changed.


### 2026-10-18 Native3399 staged-claim fixture parameter typing
- Exact `c74e3b7f` run3399 proved the migration parses and generalized tests advance into `route-bundle-stack-claim.cjs`.
- First failure was PGlite parameter inference only: fixture parameter `$7` was reused in numeric columns and `100+$7`, producing `integer versus numeric`.
- Added explicit `::numeric` casts inside the isolated fixture INSERT. Runtime migration/business logic is unchanged; no Production mutation.
- NEXT: exact-head CI.


### 2026-10-18 Native3400 stale-evidence fixture follows targeted-offer authority
- Exact Route Bundle HEAD `33685cc9` run3400 passed SQL parsing and all independent races. Native Pilot first failed because the stale-evidence fixture attempted authenticated generalized claim without first publishing the required 30-second targeted offer, so the secure runtime correctly returned `live targeted offer required`.
- The fixture now raises the isolated max_orders rule inside a transaction, publishes the exact quote through `queuego_offer_route_bundle_stack_quote` as service_role, then mutates the candidate revision and claims as authenticated Rider. This preserves the targeted-offer-first authority while still proving stale matrix/order evidence fails closed.
- The transaction rollback removes the temporary rule/offer/revision changes. Production, live cap and business logic are unchanged.
- NEXT: exact-head Native Pilot, then merge latest Map base descendant before judging map-display on PR #435.

### 2026-10-18 Map3397 stale prior-scope cleanup fencing
- Exact `e1004229` map-display artifact proves current `large-allowed` process PID4542 started location twice and stopped twice. The harness nevertheless counted 2/3 because a delayed `foreground_updates_stopped` from prior phone-allowed PID4328 landed after logcat clear and before the new scope's first start.
- Lifecycle evidence now fences the granted scope at its first `foreground_updates_started` marker and requires exact start/stop balance from that point forward. It still requires >=2 starts (recreate exercised) and exact cleanup equality; no production service behavior or acceptance threshold is weakened.
- Denied scopes remain strict 0 starts / 0 stops.
- NEXT: exact-head Map CI.


### 2026-10-18 Native3401 process-death pidof semantics
- Exact Map HEAD `5efd473d` run3401: map-display-only, MapLibre runtime, navigation runtime, Ride/Express/Credit/Dispatch races all SUCCESS. Native Pilot completed source regression then failed only because `package_pids()` called the strict `run(check=True)` wrapper; Android `pidof` exit 1 means the process is already dead, which is the expected process-death condition.
- `package_pids(pkg, required=True)` now uses non-throwing `subprocess.run(..., check=False)`. Existing callers remain strict by default; only system-kill polling passes `required=False`.
- Production app code is unchanged. No force-stop semantics introduced. Physical OEM process-recreation evidence remains a separate release gate.
- NEXT: exact-head Native Pilot and Map display lifecycle.

### 2026-10-18 Native3402 preserve strict PID evidence contract
- Exact Map HEAD `6404f42f` Native3402 failed in `tests/native-android-source.cjs` before emulator capture because the established static contract requires strict resolver signature `def package_pids(pkg: str)`.
- Kept that strict resolver unchanged in name/signature and added separate non-throwing `package_pids_optional(pkg: str)` only for expected process-death polling. Strict runtime crash evidence still asserts that live QueueGo process IDs resolve before PID-scoped AndroidRuntime collection.
- No acceptance threshold, app runtime, force-stop behavior or Production state changed.
- NEXT: exact-head Native Pilot; runtime process-death path must now be reached.


### 2026-10-18 Native3406 named-dollar structural assertion alignment
- Exact Route Bundle HEAD `563039b9` passed migration parsing, generalized financial/quality stages and all independent races, then failed only because the source-structure assertion still expected the old anonymous `as $$` delimiter.
- The targeted-offer function intentionally uses named PostgreSQL delimiter `$qg$` after the parser publication fix. Updated only the structural assertion to require that exact deliberate delimiter.
- No SQL runtime behavior, targeted-offer authority, claim logic, cap, payout or Production state changed.
- NEXT: exact-head CI; continue only from first real failure.


### 2026-10-18 Native3407 handler fixture committed-next alignment
- Exact Route Bundle HEAD `5d02fd6f` passed staged atomic claim `43/0`; the next failure moved to `route-bundle-stack-handler-quote.mjs` line 50 where read-only `plan` returned 503.
- Root cause: the handler fixture still modeled an ACTIVE bundle with `bundleId/routeRevision` but `lockedNext=null`. The hardened planner intentionally fails closed unless an active bundle supplies its authoritative committed next stop.
- Fixture now binds `lockedNext='O0P'` to `{order_id:A,kind:'pickup'}`, matching its existing point map and expected pickup-first route. Runtime planner/handler/claim logic is unchanged.
- NEXT: exact-head CI; continue only from the next actual failure.


### 2026-10-18 Native3408 refresh-worker legacy return-shape alignment
- Exact Route Bundle HEAD `aa9f64ba` passed staged claim `43/0`, handler quote `19/0` and stack discovery `10/0`, then failed only in the new refresh-worker fixture.
- Established `refreshRouteMatrices` compatibility intentionally returns `{refreshed,failed}` whenever any lease is claimed; `busyOrDisabled:true` exists only when no work/lease is claimed. It never returns `busyOrDisabled:false` on the legacy success shape.
- Updated only the generalized refresh-worker expectation to `{refreshed:1,failed:0}`. Runtime helper behavior and backward compatibility remain unchanged.
- NEXT: exact-head CI; continue from the next actual failure only.

## 2026-10-18 Route Bundle exact-head coverage checkpoint
- Stream: Route Bundle 3–5 / release gate coverage. Branch: `work-native-route-bundle-financial-sim-20261018`; verified baseline HEAD `7dda03fb65ed0ee2c6f20a2efebdbb085a6d354a` is the only fetched remote branch containing that commit; no newer open PR successor was found. Backup: `backup/route-bundle-before-exact-gates-20261018`.
- Baseline CI: Native Pilot #3409 / run `39116190364` SUCCESS on that exact SHA. All eight required jobs ran successfully: native-pilot, Map display, navigation, MapLibre, Dispatch/Ride/Express/Credit races. Publication alone was intentionally SKIPPED. Full regression, Kotlin unit tests, three APK builds, 16 KB packaging, WebView rejection and launch/map/voice capture steps all SUCCESS.
- Coverage gap: baseline had no exact-head Map Engine run (path filter omitted Route Bundle SQL/Edge/tests); staged PGlite competing-quote tests were sequential, not an independent PostgreSQL concurrency gate.
- Change: extend existing Map Engine push selector to Route Bundle sources; add isolated `route-bundle-stack-races` job to the existing Native workflow. No duplicate runtime/dispatcher/engine/payment implementation. Actual PostgreSQL tests exercise 3/4/5 Orders with forced row-lock overlap, competing targeted offer publication, duplicate claim/replay, stale losing quote, locked Rider payouts, integer-satang conservation and disconnect-before-commit rollback.
- Test result: Node syntax PASS; integrated selector 76/0, pinned Actions 98/0 and release workflow 57/0 PASS. New PostgreSQL execution requires its isolated CI service; this environment has no working PostgreSQL service. Full local regression status is recorded in the following verification checkpoint; exact new HEAD CI is REQUIRED.
- STATUS: IN PROGRESS (source/CI coverage); ACTIVATION BLOCKED. Runtime migration/cap/feature/pricing/payout/Production state unchanged; existing max_orders default remains 2.
- BLOCKER: no physical/signing/provider/Production gate has been certified by these changes. NEXT: publish this bounded checkpoint, inspect exact-head Native + Map Engine runs; open first actual failing job log before any fix. Once all pass, record SOURCE READY / ACTIVATION BLOCKED, never RELEASE DONE.

## 2026-10-18 canonical Route Bundle / all-service release handoff
- Stream: Route Bundle 3–5 and inherited Unified Release services.
- Branch: `work-native-route-bundle-financial-sim-20261018`; checkpoint source HEAD `c15327d6c547ea6f49b0ae2bd8e6c8e959d87430` (published through connected GitHub Git-data API; local tested tree `5f1e0f71c1001ff202a28ca08833cb6cb68cab1b` exactly matches). Local `d8d393d1` is the same tree with different commit metadata, not a separate implementation. Continue from the remote successor, never that local sibling.
- Tests: complete local `npm test` pre/test/post EXIT 0; clean-checkout `npm run test --ignore-scripts` EXIT 0. Initial dirty-tree-only release-evidence-bundler rejection was resolved by checkpointing the tree, without changing its clean-checkout guard.
- CI: exact `c15327d6` Native #3410 / `39125420995`, new `route-bundle-stack-races` job `117669423270`: PASS **100 checks / 0 failures** on PostgreSQL 17.0011. All Dispatch/Ride/Express/Credit independent race jobs SUCCESS. Native unit/APK/map jobs still running when this checkpoint was prepared. Exact push Map Engine `39125421124` also running (PR event `39125425161` is separate). Verify containing successor's fresh runs before CI DONE.
- Route Bundle source/isolated suites PASS: committed-next/full-membership/revision planner 73; financial simulator 19; authoritative financial snapshot 18; quality/readiness 15; staged claim/route-binding/offer authority 43; service quote 19; generalized discovery 10; refresh worker 3. Real PostgreSQL 3/4/5 races additionally prove one targeted offer winner, row-lock claim replay rejection, losing-quote rejection, exact satang conservation, preserved existing payouts, one audit/accepted history, revision increment once, and full rollback on precommit disconnect.
- Audit: Passenger Ride/Express Web + Native/state/quote/proof/cash/recovery suites, Credit ledger/Edge/Web/Admin/retired-GP/13-migration chain, Grocery categories/search/price/visibility/approved-image projection, and shop-managed Laundry numeric fee preservation all remain in the inherited full regression. No rewritten dispatcher, router, payment, wallet, proof or catalog. Earlier checklist/audit notes saying generalized finance/claim is absent are historical and superseded by the staged implementation in this successor.
- STATUS: SOURCE DONE for the bounded concurrency/selector scope (`c15327d6` + actual PostgreSQL evidence); overall CI IN PROGRESS pending complete exact successor Native/Map Engine. PHYSICAL BLOCKED / SIGNING BLOCKED / PRODUCTION BLOCKED. **ACTIVATION BLOCKED**; live `route_bundle.max_orders` remains unchanged/default 2. No Production query/migration/rule/feature/provider call was made.
- NEXT: inspect final exact successor Native + Map Engine outcomes; on failure read first failing job log, correct only that root cause and rerun. If all pass, SOURCE READY / CI DONE, with the owner gates below still BLOCKED. Do not activate 3–5 or call RELEASE READY/RELEASE DONE.

### OWNER ACTION REQUIRED — external acceptance gates
Every row remains BLOCKED until actual evidence is attached; emulator/source checks do not close it. Use authorized acceptance accounts and controlled orders, not fabricated Production data. Record exact SHA/APK digest, device/OS, time, order/trip ID, screen recording and redacted logs as applicable. Store no credentials in evidence.

| Gate / STATUS | Account and equipment | Actions required | Required evidence |
| --- | --- | --- | --- |
| Buriram Rider GPS / PHYSICAL BLOCKED | Active approved Rider, Android with real GPS; independent observer | Start approved trip, tap current position and existing navigation; verify GPS marker/road alignment safely while stationary or observed | Exact build, device/GPS permission, real route and timestamped screen/log evidence |
| Parallel road, U-turn, reroute / PHYSICAL BLOCKED | Same Rider/device; real Buriram roads | On a safe legal route test adjacent-road discrimination, legal U-turn and off-route recovery; observer operates UI | Expected/actual road and turn geometry, reroute latency, route IDs, screen/GPS log |
| Background → foreground / PHYSICAL BLOCKED | Rider + physical Android | During active job switch apps/lock/unlock, return; verify job, marker, camera and route persist | Before/after video, job/revision and lifecycle logs, no reload/duplicate action |
| OEM process death/restore / PHYSICAL BLOCKED | Customer/Merchant/Rider accounts; actual Android devices | During controlled active state allow OS process termination, reopen and verify state recovery without duplicate mutation | Device/OS/build, process evidence and recovered order/state/audit |
| Passenger Ride / PHYSICAL BLOCKED | Customer and publicly licensed approved Rider; two physical devices | Request → accept → arrive pickup → start → track → destination → cash → complete; interrupt/reopen | Trip ID, both device recordings, state/audit sequence, cash and restore evidence |
| Express / PHYSICAL BLOCKED | Sender/Customer and approved Rider; two physical devices/cameras | Quote/request → targeted accept → pickup proof → travel → dropoff proof → cash/complete; interrupt upload/reopen | Order ID, proof references, both recordings, cash/state and recovery audit |
| Grocery Safari / PHYSICAL BLOCKED | Real Merchant; iPhone Safari | Merchant → สินค้า → เพิ่มสินค้าจากแคตตาล็อก; search/category, set price, tick storefront/delivery, save/reopen; verify approved image, reject unpriced visibility | iPhone/iOS/Safari/build, saved SKU/settings and reopen recording; no login loop/white page |
| Grocery Android / PHYSICAL BLOCKED | Same Merchant; physical Merchant Android app | Repeat priced catalog add, flags, save/reopen/category/image flow | APK SHA, Android/device, SKU references and recording |
| Laundry / PHYSICAL BLOCKED | Laundry Merchant/staff + Customer; real Merchant device | Shop-managed pickup → wash → return → cash; save/reopen configured numeric fee/minimum | Account role, order, persisted original numeric fees/minimums, state/receipt evidence; no general Rider pool |
| Route Bundle 3–5 acceptance / PHYSICAL BLOCKED | Approved Rider, participating Merchants/Customers; physical Android and reviewed isolated acceptance environment | Complete 3, then 4, then 5 approved multi-stop orders; test committed-next, readiness/wait, cancelled/stale offers, interruption and proof/cash at each stop. Cap opt-in only in explicitly approved acceptance environment | Every order/stop/revision, quote/offer/claim logs, proof/cash, customer fee sum and unchanged locked Rider payout; physical results do not themselves authorize Production cap change |
| Provider / PRODUCTION BLOCKED | Authorized provider dashboard + Admin finance account, approved sandbox/live configuration | Verify signed webhook, duplicate/replay rejection, paid top-up settlement and ledger reconciliation under explicit rail approval | Provider event/signature verification result, transaction/ledger references and reconciliation; never secrets |
| Public map HTTPS / PRODUCTION BLOCKED | Hosting/config owner + approved domain/TLS runtime | Approve/deploy QueueGo map tiles/routing on public HTTPS; configure release runtime and verify actual physical app tiles/route | Approved origin/config, TLS/health/real tile-route responses and device recording; debug PMTiles is not this approval |
| Signed AAB / SIGNING BLOCKED | Authorized release signer; secure signing/CI environment | Supply approved signing configuration through secret store; build/verify Customer/Merchant/Rider release AABs | Artifact SHA, signature certificate fingerprint, package/version and verification report; never signing passwords/keys |
| Play Closed Testing / SIGNING BLOCKED | Play Console owner/release manager + enrolled testers/physical Android | Upload approved signed AABs, configure Closed Testing, install via Play and execute required acceptance | Play track/version/review status, opt-in/install and tester/device acceptance records |
| Production cutover / PRODUCTION BLOCKED | Authorized deployment/DB owner and approved backup/change window | Review migration/function/config plan, backups/rollback and every applicable gate; execute approved cutover only after release approval | Approval/change record, backup, exact applied versions, post-cutover smoke/ledger audit; reviewed cap change remains a separate explicit action |

## 2026-10-18 verified SOURCE READY / ACTIVATION BLOCKED
- Stream: Route Bundle 3–5 and inherited Unified Release services; branch `work-native-route-bundle-financial-sim-20261018`.
- Verified source HEAD: `cb649475e023ca05bf4647f89299bf9d3847088b`; tree `b9bd7000928be2800ea247f6c48714c212907019`. This documentation checkpoint changes no runtime, migration, workflow, test or financial source.
- CI DONE on that exact HEAD: Native #3411 / `39125601986` SUCCESS; QueueGo Map Engine push #151 / `39125601981` SUCCESS. Native jobs all SUCCESS: Full QueueGo regression/unit/three APK/16KB/no-WebView/launch-map-voice lifecycle, route-bundle-stack-races, continuous-dispatch-races, passenger-ride-races, express-races, credit-races, navigation-map-runtime, maplibre-osm-runtime and map-display-only. Release publication remains intentionally SKIPPED.
- PostgreSQL race job `117670009359`: 100/0 PASS on PostgreSQL17, 3/4/5 Orders, forced independent row-lock overlap, single targeted offer winner, replay/stale rejection, preserved locked Rider payouts, exact satang conservation and disconnect-before-commit rollback.
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
