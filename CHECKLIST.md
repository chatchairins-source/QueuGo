Warning: truncated output (original token count: 56718)
Total output lines: 1407

## 2026-10-26 Native three-app parity successor — exact-head Pilot NEXT

- Native branch `work-native-web-parity-20261026` is at `b66b6a3b1b2a83613ed9984a7dc8e900135f0b07`, a verified descendant of fetched remote successor `ed3a29ae` and based on current Web `main=7e036a0735cc49905e9b0a2434492470551a0c9d`. PR #729 map/navigation parity is preserved in the integrated ancestry. No open PR or Actions run exists for this exact HEAD; #710 remains an older open Rider review branch and is not the active successor.
- Exact local full regression: `TZ=Asia/Bangkok npm test` EXIT 0, including Native source 330/0, existing three-app stability, Customer/Merchant/Rider contracts, integration SQL gates and post-test map QA 28/0. No Production writes. The Grocery offline log line is an expected isolated transport path; its suite passed.
- Current code checkpoint includes Customer safe route recovery, Merchant route restoration/dashboard/order filters, and Rider earnings same-period retry. Source and local regression are complete for this checkpoint; hosted Kotlin/JUnit, three APK, 16 KB and emulator evidence are still pending.
- **NEXT:** record this checkpoint, fast-forward push `b66b6a3b` to the existing Native successor branch, inspect exact-head Native Pilot/RC runs, and repair only the first actual failure. While those run, continue Merchant order-detail Web parity and Customer/Rider non-overlapping source inventory. Authenticated visual, physical-device/GPS and Play release gates remain separate.

## 2026-10-26 Merchant Orders Web filter/search parity — DONE (source/full regression); Native Pilot pending

- Blueprint: current Web main 7e036a0735cc49905e9b0a2434492470551a0c9d `renderShopOrdersModern` provides six status filters, order/customer search, and explicit empty/no-result states. `qgmOrderStatus` maps backend statuses to shared user-facing labels.
- Root cause: Native showed every order with no search/filter controls; `merchantStatus` passed through several raw states (`pending_shop`, `shop_accepted`, `delivered`) and used labels different from Web.
- Bounded change: Native adds the Web filter buckets/order, filters the existing authoritative list, adds number/customer search from available `customer_name` RPC projection, result empty state, timestamp/item-count/customer labels, and Web-aligned status wording. No new backend or local fake data.
- Full `TZ=Asia/Bangkok npm test` on exact source HEAD `b66b6a3b1b2a83613ed9984a7dc8e900135f0b07`: EXIT 0; includes Native source 330/0 and the three-app stability and Merchant title/substitution/catalog contracts. Added JUnit coverage for status buckets/search; hosted JUnit/build pending.
- Customer-name results depend on the existing RPC field being present; no broader name lookup was introduced.
- **NEXT:** exact-head Native Pilot on `b66b6a3b1b2a83613ed9984a7dc8e900135f0b07`; then continue Merchant order-detail actions/catalog comparison and Customer/Rider audits while hosted CI runs.

## 2026-10-26 Merchant Home Web status-summary parity — IN PROGRESS

- Blueprint: current Web main 7e036a0735cc49905e9b0a2434492470551a0c9d `renderShopDashboardModern` shows today's total/completed/preparing/cancelled counts and the main menu. Native instead showed revenue/GP cards plus an extra six-order list, and a cover image, so the first screen hierarchy diverged materially.
- Bounded change: Native now derives the four Web status counts from real loaded orders using the device-local calendar day; date label uses Thai Buddhist year; loading shows ellipses instead of false zeroes. Removed the extra dashboard order list and cover block; orders remain reachable from the existing menu and bottom navigation. Existing financial values remain in the Revenue screen.
- Focused: native Android source 329 PASS; three-app stability 106 PASS; Merchant page-title 18 PASS; Merchant Grocery 24 PASS; diff check PASS. Added deterministic Kotlin tests for status grouping, local-day boundary and Thai date label; hosted JUnit is pending.
- **NEXT:** exact-head Native Pilot/JUnit/three APKs/emulator (the CI queue now contains later parity commits); continue Merchant Orders/detail and independent Customer/Rider audits while CI runs. Authenticated visual and physical acceptance remain unverified.

## 2026-10-26 Merchant initial-load recovery — IN PROGRESS

- Web blueprint main 7e036a0 includes explicit boot recovery/retry. Native refreshAll failed into message while shop remained null, leaving first-load failure indistinguishable from no shop in subsequent rendering.
- Bounded change: initial shop-read error owns a retry card; successful authoritative refresh clears it, including legitimate null-shop results. Existing loaded shop remains visible on later refresh errors. Cancellation propagates.
- **NEXT:** focused source checks, clean full regression and exact-head Native Pilot; continue page/state audit during CI. No authenticated or physical acceptance claim.

## 2026-10-26 Customer initial-load error recovery — IN PROGRESS

- Blueprint: Web main 7e036a0 index.html route catch renders a load-error card with ลองอีกครั้ง calling route() after clearing shop cache.
- Root cause: Native initial refresh failure only set message, while Search/Category rendered empty shop lists with no retry entry. A transient network failure looked like no shops and could not be retried from the current page.
- Bounded fix: separate initial-home read error from action messages; show the Web-equivalent error/retry card on Home/Search/Category/Shopping; retry calls existing refresh authority, disabled while loading. Successful load clears error. Existing cached content remains usable.
- **NEXT:** focused checks, full regression and exact-head Native Pilot; continue independent Merchant/Rider audit during CI. Authenticated screenshot/physical acceptance remain unverified.

## 2026-10-26 Rider earnings same-period recovery — IN PROGRESS

- Base: latest integrated parity 3c91d8e; Web blueprint main 7e036a0. Preserves PR729 Rider map successor.
- Root cause: Web period buttons invoke summary/ledger loaders on every click. Native click assigned the same days value; Compose did not restart effects, so retrying the selected period after a read failure did nothing.
- Bounded fix: increment refresh generation on existing period click; key summary, cash ledger and offer performance read effects by it. Cancellation still propagates; no mutation or new UI/backend authority.
- **NEXT:** focused source checks, clean full regression and exact-head Native Pilot; continue Customer/Merchant audits during hosted CI. See docs/native-rider-web-parity-matrix.md.

## 2026-10-26 Merchant route restoration — IN PROGRESS

- Base: newest parity remote 3e7b5aa17995ab9d1ad93d5b635ffe945b3b5d04; Web main 7e036a0735cc49905e9b0a2434492470551a0c9d.
- Root cause: Merchant screen and product return route used remember and disappeared after process recreation; selected order/product/shop editor context is not saveable.
- Bounded change: Compose saved state retains reloadable destinations; detail/editor/catalog routes fall back to Orders/Products/Profile. Existing POS module and backend actions remain authoritative.
- Focused: native source 328 PASS; three-app stability 106 PASS; diff check PASS. Added deterministic JUnit restoration-policy tests. Full regression, exact-head hosted JUnit/three APKs/emulator still required.
- **NEXT:** exact-head Native Pilot for Merchant restoration; continue Customer page audit and Rider non-map inventory during CI. No physical-device or authenticated visual PASS claimed.

## 2026-10-26 Owner authorizes Merchant/Rider overlap — IN PROGRESS

- Owner explicitly permits starting Merchant/Rider while Customer CI runs; this supersedes the prior strict app-order restriction for independent work. Shared capability still has one owner.
- Customer route successor 3e7b5aa: Native Pilot 40283517013 SUCCESS, including full regression/JUnit/three APKs/16 KB/WebView/emulator lifecycle. Full local npm test exit 0. Authenticated route-specific runtime/visual and physical-device evidence remain open.
- Merchant source inventory started in docs/native-merchant-web-parity-matrix.md against main 7e036a0. Proven first gap: Merchant screen uses remember and loses route on recreation.
- Rider already persists activeTab; Actions also shows concurrent puck/FOLLOW-heading work. Do not duplicate map implementation; verify its owner/descendant before integrating.
- **NEXT:** fix Merchant safe route restoration; continue Customer critical-screen audit during CI; inventory Rider non-map surfaces without overlapping active map work.

## 2026-10-26 Customer route restoration — IN PROGRESS

- Base: exact Native `01df476d08105c2a237fd4bcb94c63496e2cdc29`; Web main `7e036a0735cc49905e9b0a2434492470551a0c9d`. Native Pilot `40281441623` SUCCESS: full regression/JUnit, three APKs, 16 KB, WebView rejection and emulator lifecycle. APK artifact `12631549499`; emulator artifact `12632507538`. This closes the prior capture harness CI blocker.
- Root cause: Customer route, return parents, category and shopping mode used `remember`, which loses state on process recreation. Detail selections are also in-memory objects and cannot safely be restored as empty detail pages.
- Change: use the existing Compose saved-state mechanism for reloadable pages and category/mode; a pure restore policy maps order/chat/trip details to Orders, Support to Profile, and Shop/unknown routes to Home. Parent destinations use the same safe policy. Existing cart/location stores and checkout recovery remain the authorities.
- Focused source checks: native source 328 PASS; Customer Web Home parity 55 PASS; diff check PASS. Added deterministic Kotlin restore-policy tests; hosted JUnit/APK/emulator validation still required.
- **NEXT:** full regression and exact-head Native Pilot for this route-restoration successor; inspect runtime evidence before claiming process restoration DONE. Customer remains active; authenticated and physical-device parity remain unverified.

## 2026-10-26 Customer Native Pilot process-death harness fallback — IN PROGRESS

- Workstream: Customer remains active; no Merchant/Rider parity implementation started.
- Web blueprint: latest fetched `main=7e036a0735cc49905e9b0a2434492470551a0c9d`. Native owner branch: `work-native-web-parity-20261026`, newest remote checkpoint before this fix `9cbf45953f80baa3031eb2de4396813a24c4a207`; no open PR targets this branch.
- Exact-head run `40279520146` on `9cbf459`: QueueGo regression, source/JUnit, three APKs, 16 KB packaging and WebView rejection PASS; emulator capture failed at Customer process death. Artifact `12631883677` shows `am kill` left PID `4828`; fallback `run-as ... kill -9` was rejected by emulator SELinux (`avc: denied { sigkill }`, `runas_app` → `untrusted_app`). This is a capture harness permission failure; it does not establish a Customer app process-recreation defect.
- Bounded harness fix: use `adb root` on the CI-only AOSP AVD for SIGKILL fallback, retain PID disappearance and cold-relaunch assertions, and add a focused source contract. No application behavior or Production data changed.
- Source checkpoint: `b814a59499ee852b86cdfc19c000293b469492a1` (remote fast-forward successor; local `ca9565cd` had the same source tree). Focused evidence: `node tests/native-three-app-stability.cjs` PASS (106, 0); Python compile and `git diff --check` PASS. Full `npm test` PASS (exit 0) on the same source tree. Exact Native Pilot with the corrected emulator fallback is still required.
- **NEXT:** run full regression on the clean committed fix, then Native Pilot on its exact remote successor; inspect Customer/Merchant/Rider process-kill evidence. After a green run, resume Customer process-restoration parity.

## 2026-10-26 Customer system Back destination parity — DONE (source/test/Native Pilot); process recreation remains open

- Web blueprint: latest fetched `main=7e036a0735cc49905e9b0a2434492470551a0c9d`; exact tested Native checkpoint `f236094b5535e9d39b37f135f5e24dd17192bed4`, forward from `cdc9b1a8aa6368902302b0a1cab4806cf2d5111e`. No open PR targets `work-native-web-parity-20261026`.
- Root cause/evidence: Web `index.html` and `customer-features.js` use `history.back()` or explicit parent routes on Cart, Checkout, Map picker, Shop, Promotion, Favorites, Order detail, Market Trip, Laundry order, Chat and Support. Native's root `BackHandler` only handled Favorites, Market cart and Checkout specially, then routed every remaining screen to Home.
- Bounded change: add one pure back-target resolver used by the existing Android BackHandler; capture current parent for Cart, Orders, Location picker and Promotions; align Cart, Location and Promotion Back controls with those destinations. Shopping mode still collapses before leaving its page. No second navigation stack or business state was introduced.
- Focused checks: `node tests/native-android-source.cjs` PASS (328); `node tests/native-customer-web-home-parity.cjs` PASS (55); `node tests/native-customer-merchant-push.cjs` PASS; `git diff --check` PASS. Full `npm test` PASS (exit 0) on source commit `68165b524772b349ab54e24ce7bd29a7289a9255`.
- Exact-head Native Pilot `40277886837` on `f236094b5535e9d39b37f135f5e24dd17192bed4`: **SUCCESS** — full QueueGo regression, Customer back JUnit and tracking tests, three APK builds, 16 KB packaging, WebView rejection, emulator launch/map/voice lifecycle and evidence upload. APK artifact `12631286228`; emulator evidence artifact `12631351397`.
- Emulator evidence validates app launch/map/voice lifecycle; it does not exercise every authenticated Back route. Source/JUnit cover target selection. Physical-device parity is not claimed.
- Scope limit: process-recreation restoration is a separate Customer P1 chunk and remains open. Authenticated Profile visual review still needs an authorized Customer review session.
- **NEXT:** make Customer screen restoration survive Android process recreation without reviving detail routes that lack their required server-backed state; add deterministic restore tests and run exact-head Native Pilot. Keep Customer active.

## 2026-10-26 Customer notification preferences and privacy entry — DONE (source/test/Native Pilot); authenticated review remains open

- Web blueprint: latest fetched `main=7e036a0735cc49905e9b0a2434492470551a0c9d`; current Native owner branch is `work-native-web-parity-20261026` at exact tested commit `99398fb06f8eaf16c5e28426dc13c68c3b61cfab`, forward from `846c95ba4b2f91a05a868f9f5c63ea04a0c898c8` and descended from Native checkpoint `260c3f0a84fef014ae30e5c81bc8213ecc46351e`. Open-PR search found no applicable successor.
- Root cause/evidence: Web Profile exposes arrival sound (default OFF), opt-in background push, test notification and privacy policy. Android previously requested permission and subscribed at session startup. Production FCM sends Android a `notification` payload, so Android OS displayed it directly in background, bypassing Native service channel selection and preference checks.
- Bounded change: add Profile controls, Android permission on opt-in, authenticated subscribe/unsubscribe/test, persist per-user sound/push preferences, gate token refresh and received messages, clear old startup registrations when push is off, use separate arrival sound/silent channels, and open the same privacy URL. For Customer Android only, send a high-priority data-only FCM payload with title/body/type intact so Native can enforce preferences. Merchant, Rider, iOS and Web payload paths stay unchanged. No Production data was changed.
- Focused checks: `node tests/native-android-source.cjs` PASS (327); `node tests/native-customer-web-home-parity.cjs` PASS (55); `node tests/native-customer-merchant-push.cjs` PASS; `node tests/background-push.cjs` PASS (113); `node tests/native-rider-offer-push.cjs` PASS; `git diff --check` PASS. Full `npm test` PASS (exit 0).
- Exact-head Native Pilot `40274325691` on `99398fb06f8eaf16c5e28426dc13c68c3b61cfab`: **SUCCESS** — full QueueGo regression, focused push/session/release/voice contracts, tracking tests, all three APK builds, 16 KB packaging, WebView rejection, emulator launch/map/voice lifecycle and evidence upload. APK artifact `12629453604`; emulator evidence artifact `12629847212`.
- Visual/runtime limit: the emulator launch workflow passed, but it did not authenticate a Customer account or exercise the new Profile switches. Authenticated Profile screenshot and permission/delivery interaction review remain blocked by lack of an authorized Customer review session. No physical-device PASS is claimed.
- No Production data was changed. Merchant, Rider, iOS and Web push paths were preserved by source guards.
- **NEXT:** close Customer system Back parity as a separate bounded chunk using Web hash/page back destinations; then address process recreation. Keep Customer active and P1 visual/session review open.

## 2026-10-26 Customer Profile destinations and Favorites — DONE (source/test/CI); notification settings P1 remains

- Web blueprint: `main=7e036a0735cc49905e9b0a2434492470551a0c9d`. Native owner branch `work-native-web-parity-20261026` was fetched at `5e589cecc4f11a4dbf27d8c595aa9952ad630e28`, still the newest branch head and a descendant of the prior Native successor.
- Parent exact-head Native Pilot `40268085994` on `5e589cecc4f11a4dbf27d8c595aa9952ad630e28`: **SUCCESS** — full regression, tracking tests, all three APKs, 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle.
- Root cause/evidence: Web Profile exposes address, orders, notifications, Favorites, promotions and support. Native Profile previously exposed support, deletion and logout only. Favorite toggles already use `qg_customer_favorites`, but no Native Favorites route/list existed.
- Bounded source change: add Native Profile links matching Web labels; add a Favorites list backed by existing authenticated favorites/shop/product reads; keep unavailable favorites visible and removable; Android Back from Favorites returns to Profile. No backend, production data, or second navigation authority was added.
- Focused checks on the working tree: `node tests/native-android-source.cjs` PASS (323 checks); `node tests/native-customer-web-home-parity.cjs` PASS (55 checks); `git diff --check` PASS.
- First full `npm test` attempt stopped at the evidence bundler because its clean-checkout precondition rejected the working tree; no product assertion failed. Rerun on clean source checkpoint `1ca5c7475a573fed5e0119384cf820970aa92b77`: **PASS**, including map asset provision, complete regression, Grocery, release evidence bundler and map field-evidence gate.
- Exact-head Native Pilot `40269632439` on remote checkpoint `c48eae32a59554784f964356db2480e3c569c1cd`: **FAILURE** in full regression. First real failure: `tests/native-android-source.cjs:1` parsed `Warning: truncated output...` and raised `SyntaxError: Unexpected identifier 'output'`. The failure came from transferring large files through output that was capped, not from the Customer implementation.
- Repair evidence: complete file contents were read in bounded line chunks. GitHub blob IDs now match local `git hash-object` exactly for CHECKLIST (`8045aa2e...`), Customer app (`58fecb5a...`), Customer shop/favorites (`259ba74e...`) and native source test (`0ca5240b...`). The reconstructed full source tree SHA matches the clean local checkpoint tree `aa38069f22ac798020c13a9fcef971058e0b6bc1`.
- Repair exact-head Native Pilot `40269901253` on `95652890c6283797d56fdd62d745261568fb69e3`: regression passed; tracking Kotlin compile failed first at `CustomerShopScreen.kt:74` because `internal ShopExtrasApi.reviews()` exposed private-in-file `ShopReviews`. Root cause: helper visibility was widened unnecessarily although Favorites is in the same source file.
- Bounded fix `260c3f0a84fef014ae30e5c81bc8213ecc46351e`: restore `ShopFavorite` and `ShopExtrasApi` to file-private visibility; Favorites UI/query behavior is unchanged. Focused checks pass: `node tests/native-android-source.cjs` (323), `node tests/native-customer-web-home-parity.cjs` (55), `git diff --check`.
- Clean local full `npm test` PASS on source checkpoint `1ca5c7475a573fed5e0119384cf820970aa92b77`, including map asset provision, full regression, Grocery, release evidence bundler and map field-evidence gate. The later visibility-only Kotlin correction was verified by the exact Native Pilot below.
- Exact-head Native Pilot `40270538542` on `260c3f0a84fef014ae30e5c81bc8213ecc46351e`: **SUCCESS** — full QueueGo regression, tracking Kotlin tests, Customer/Merchant/Rider APK builds, 16 KB packaging, WebView rejection and Android emulator launch/map/voice lifecycle. APK artifact `12628191588`; emulator evidence `12629021809`.
- Visual evidence: inspected Web Home at 1365 × 900 and exact-run Android phone Login/Home/Market browse captures at 1080 × 2280. Home hierarchy aligns from top bar through service banner/categories/nearby shops/four-tab nav; this is structural evidence and not a same-viewport pixel comparison. Test shop content is layout-only. Authenticated Profile/Favorites screenshots remain blocked by unavailable authorized Customer review credentials.
- Status: Profile destinations and Favorites source/test/CI are DONE. Customer notification sound/background/test controls and privacy-policy entry are being implemented in the active checkpoint; authenticated Favorites runtime proof, route-specific Back and process recreation also remain open.
- **NEXT:** finish exact-head validation and record this Customer notification/preferences checkpoint; then close route-specific Back/process-recreation parity. Keep Customer as the active phase.

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

## 2026-10-26 Native Rider Web map/navigation parity — DONE (source/test)

- Parallel stream remained isolated from Places/OSM PR #728 and was built from the newest unchanged Native Rider successor PR #710 at `65918d8ca7dbe720127e1c915a0483c3f209cb01`; no older Rider implementation was restored.
- Evidence-backed root cause: Web and Native had separate Rider presentation/navigation policies. Native still used a default MapLibre Rider marker plus Native-only route-match/camera/display-filter/gesture behavior, while Web uses a branded red directional puck, one speed-aware heading, Rider-puck viewport anchoring and different camera/route scoring.
- Tested implementation HEAD `09936c6d9d4bccc59d0228ee0011ae7b9903652c` on draft PR #729. Native Rider now uses the Web-equivalent `#E6002D` red puck with 4px white border and white directional arrow, removes the default `MarkerOptions().title("Rider")` presentation, and feeds puck + FOLLOW camera from the same speed-aware smoothed heading.
- FOLLOW camera now anchors to the same confidence-bounded Rider presentation point rather than a separate Native look-ahead center. Speed zoom, navigation/free-drive pitch, bearing threshold, rotation duration and viewport anchor/bottom-dock padding are aligned to `queuego-map-web.js`.
- Native route presentation matching now uses the Web distance + heading + backtrack score; the Native-only opposite-heading hard rejection and forward-jump penalty were removed from the visible matcher. The separate experimental Native display-confidence pipeline is disabled in the visible path; authoritative raw GPS/business state/routing remain unchanged.
- Gesture parity: one-finger pan can enter FREE_PAN; two-finger pinch/rotate/pitch keeps FOLLOW, matching Web ownership. Existing 8-second FREE_PAN auto-follow is preserved.
- Regression guards were updated so the old default Rider marker cannot be reintroduced. Native Pilot also accepts native fix/PR branches so parity work cannot bypass the standard regression/build/runtime gates.
- Exact implementation Native Pilot run `40282973410`: **SUCCESS**. Full QueueGo regression, source integrity/contracts, Kotlin unit suites including Rider navigation-map tests, Customer/Merchant/Rider APK builds, Android 16 KB packaging, WebView rejection, APK artifact upload, and Android emulator launch/map/voice lifecycle all passed.
- Artifacts: `queuego-native-pilot-apks` id `12632823973`; `queuego-rider-native-launch-evidence` id `12633591041`.
- No Production database/data mutation and no Places/OSM source change.
- **BLOCKED (physical acceptance only):** emulator/source evidence does not certify real-road GPS, OEM sensor heading, turn behavior or physical-device visual parity.
- **NEXT:** keep PR #729 as the Native parity successor; perform physical Rider device/road acceptance when available. For further code-only parity, add a shared golden GPS trace contract comparing Web and Kotlin snapped point / heading / route progress / camera / reroute outputs before changing either navigation authority again.

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
- Fix commit: `c9b9c420fdd20d78001cb028fd66b8f6d3beddc4` upda…16718 tokens truncated…d-next policy. Existing callers without financial evidence retain the prior travel-only behavior.
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
