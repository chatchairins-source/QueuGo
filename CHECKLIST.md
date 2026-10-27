Warning: truncated output (original token count: 76225)
Total output lines: 1802

## 2026-10-27 Authenticated capture + exact Pilot completed; Customer Profile cleanup successor

- Recovered on newest published descendant `9f6d54f81416cee63fc4b44987e42cc8fe39e446` (PR #730 remains open/draft; no later successor was present after fetch). `origin/main` remains `7e036a0735cc49905e9b0a2434492470551a0c9d`; no newer Native descendant or main update was found.
- Exact-head PR Native Pilot `40433753021` and push Native Pilot `40433747432` completed SUCCESS on `9f6d54f8`; full regression/source, Kotlin/JVM, three APKs, 16 KB, WebView rejection and emulator launch/map/voice lifecycle passed. Map Engine `40433753070` completed SUCCESS. These are emulator/runtime gates, not physical-device evidence.
- Authenticated read-only review `40433747486` completed SUCCESS on exact `9f6d54f8`: Rider artifact `12703387135` captured 11/11, Merchant artifact `12703103639` captured 5/5, with no missing screens. Actual contact sheets and `03-profile-laundry.png` were inspected: Laundry mode row is cleanly placed under work preferences and retains its server-backed off state. Review metadata says `physical_device:false`, no Production order/dispatch mutation, `visual_parity_certified:false`.
- Customer authenticated visual review remains BLOCKED by missing GitHub Actions secrets `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`; readiness-only is not visual acceptance. Continue unblocked source work without claiming Customer visual acceptance.
- Current Native source evidence showed Customer Profile's debug-only “ข้อมูลเวอร์ชันทดสอบ” control exposes build metadata but has no Web Production counterpart. Removed only that Profile control; the shared diagnostic component remains available to Merchant/Rider and the isolated Customer Android test. Added a source guard. `node tests/native-android-source.cjs` passes 357 checks; `git diff --check` passes.
- **NEXT:** publish this bounded Customer Profile cleanup successor and run its exact-head Native Pilot; fix only the first confirmed failure. If Pilot is green, request a fresh Rider/Merchant capture using the documented `[authenticated-review]` marker and inspect actual screenshots. Then continue Customer source parity audit (Customer screenshots remain blocked until both named credentials are configured); preserve current Web/Production business authority and do not claim physical-device PASS.

## 2026-10-27 Rider Laundry authority re-check — source checkpoint; exact Pilot NEXT

- Reconciled forward to authenticated-review checkpoint `1be05ed44343ed04e46b169392a8cb00a0f5bced` before continuing. Rider/Merchant authenticated capture `40428480371` on `907cc32c0e0e909db14cd06bc6801aab4a3cbe87` completed with Rider 10/10 and Merchant 5/5; metadata is exact-head, read-only, and `physical_device:false`. Actual screenshots were inspected: map chrome, inbox, all Earnings ranges, Profile hierarchy, Support/Learning/Safety/Diagnostics and Merchant primary surfaces have no new visual mismatch. This does not certify Customer visuals or a physical device.
- Rechecked current `origin/main` (`7e036a0735cc49905e9b0a2434492470551a0c9d`) and read-only Production backend state. Web still reads Rider Laundry preferences/invites and calls `queuego_set_laundry_rider_mode`, `queuego_laundry_rider_invite_action`, active-job/pool/claim/action RPCs. Production currently reports Laundry enabled and these dedicated authenticated RPCs/ACLs present. This is separate from `qg_rider_work_preferences`, whose services remain Food/Grocery/Market/Express/Ride only.
- The earlier checkpoint describing Rider Laundry as retired predates the current Web/main and Production authority readback. Merchant/shop Laundry remains shop-managed through its own RPC. No new RPC/schema, Production mutation, or Laundry key in general Rider work preferences was added. The repository's shop-transport SQL still describes retirement of the Rider RPCs; current Production has not applied that retirement, so do not claim it has.
- Commit `77eebc1b` reconnects Native Rider snapshot to the existing Laundry state RPC, restores the Web Profile mode/invite controls using existing server mutations with refresh-after-write acknowledgement, and reuses existing Home job/pool UI. Parser tests cover feature/mode/invites/active/pool and malformed fail-closed data; the source contract checks dedicated authority separation. Focused Node contract and `git diff --check` pass. This clone has no Gradle wrapper; Kotlin/JVM, APK and emulator gates are pending hosted Pilot.
- Forward-merged Customer successors `7ebdf25e` (density/geometry), `32789851` (source proportions) and `636c0f53` (CSS contract) without replacing their work; the combined Customer Home contract passes 55 checks. Exact Pilot `40430149422` on `32789851` completed regression/source/assets successfully and was cancelled at the tracking/JVM step as the branch advanced; no failure was reported. Exact Pilot `40430483949` on `636c0f53` was in progress when integrated. The old Rider-retirement assertion in `tests/native-android-source.cjs` was the first confirmed local contract failure and has been updated to enforce the current Web/Production authority; full native source contract now passes 357 checks. Current local integration head before this docs refresh is `9d40bfdb` plus commit `07dec5ae`.
- Exact current app-source head `8f17026a0c909691b1f194484ac38aaba6634646` passed PR Native Pilot `40430819774` (full regression/source, Kotlin/JVM, three APKs, 16 KB, WebView rejection and emulator launch/map/voice lifecycle) and Map Engine `40430819796`. Push-triggered review `40430814885` only produced readiness: Rider/Merchant secrets are present, Customer is missing `QG_CUSTOMER_REVIEW_PHONE` and `QG_CUSTOMER_REVIEW_PASSWORD`, and `capture_requested:false`; that is not visual acceptance. No failed CI step.
- Authenticated read-only review `40432384271` on documentation-only successor `61be1798c79a0bc84d99e9d9f5184cf79488b038` completed SUCCESS: Rider artifact `12702138652` reports 10/10, Merchant artifact `12702023702` reports 5/5, with exact source metadata and no missing screens. Actual screenshots were inspected; map/inbox/earnings/primary Rider+Merchant surfaces are clean. The top-of-Profile screenshot does not include the new below-fold Laundry mode control, so that control has not yet received visual review. Both artifacts state `physical_device:false`, no Production order/dispatch mutation, and `visual_parity_certified:false`.
- Added a read-only `profile_laundry` capture after scrolling to the Web-backed control; updated the exact-source contract. Rider Laundry/Customer Home/source contracts pass (357 native source, 55 Customer Home); capture Python syntax and `git diff --check` pass. Exact docs-head Pilot `40432391503` and Map Engine `40432391569` on `61be1798` both completed SUCCESS; the authenticated capture `40432384271` also completed successfully.
- **NEXT:** reconcile any new successor and wait for exact `61be1798` Native Pilot. If green, publish the `930d0acc` capture harness with the repository `[authenticated-review]` marker; run the new exact-head Pilot and inspect the new `profile_laundry` screenshot and Home state before any further UI edit. Customer authenticated visual acceptance remains blocked only by its recorded missing credential pair; continue other unblocked audits afterward.

## 2026-10-27 Rider/Merchant authenticated visual gate — PASS (emulator evidence)

- Final authenticated review run `40428480371` on exact source head `907cc32c0e0e909db14cd06bc6801aab4a3cbe87` completed **SUCCESS** after the Diagnostics QA marker fix. This head is app-source equivalent to the previously green Native source line; the latest change is review-harness-only.
- Rider artifact `12701142972` (digest `sha256:a30fdbb1fdf32a0111c78fdd2ccf38d1a1a1d16f0fa79869fa1d41dc06e73148`) reports `review_complete: true`, 10 expected/10 captured screens, and `missing_screens: []`: Home map, single Messages inbox, Earnings today/7d/30d, Profile, Support, Learning, Safety and Diagnostics.
- Merchant artifact `12701356305` (digest `sha256:b1c79ccc0ed47eca3e2a0aff5befba86af378274fdc2880cc988c99f26078bd1`) reports `review_complete: true`, 5 expected/5 captured screens, and `missing_screens: []`: Home, Orders, Products, Promotions and Account.
- Final Diagnostics screenshot is visually clean and readable. Emulator-only readiness shows 3/5 because Android test image lacks Rider location permission/Google Maps/overlay return; this is environment state, not a UI/layout regression.
- Visually accepted on emulator: Rider map wordmark/right rail/Ready card, single inbox, inline credit, Profile/work-preference hierarchy, Support/Learning/Safety/Diagnostics; Merchant primary 5-screen surface including bottom outlined `เพิ่มเมนู` and single Promotions header.
- Review metadata explicitly records `physical_device: false` and no Production order/dispatch mutation. Do **not** claim physical-device certification from this gate.
- Customer authenticated visual review remains BLOCKED only by the recorded missing Customer review credential pair.
- **NEXT:** preserve this green visual baseline. Continue only concrete successor gaps or release/physical-device gates; do not re-polish accepted Rider/Merchant screens without new evidence.

## 2026-10-27 Authenticated Rider diagnostics QA fix — exact Pilot PASS / recapture requested

- Authenticated visual run `40384466162` on `9c553d43acc2e89087be36e07e110dd61a495313` built Rider/Merchant APKs and booted the emulator. Merchant capture completed all 5 expected screens (artifact `12681395969`). Rider captured Home, Messages, Earnings today/7d/30d, Profile, Support, Learning and Safety before the review harness stopped at Diagnostics (artifact `12679849177`).
- First confirmed review failure was QA-only, not app UI: the harness waited for exact title `สถานะจริงของโทรศัพท์เครื่องนี้`, but the actual Diagnostics title is `ตรวจสอบระบบ Rider`; that phrase only appears inside subtitle copy `ตรวจจากสถานะจริงของโทรศัพท์เครื่องนี้`.
- Fixed only the review expectation at `d675e581444fb45542623ffdf5a0d83803ca6ecd`. Comparison to `9c553d43...` confirms exactly one QA-script line changed and no app source/Kotlin/Android runtime changed.
- Exact `d675e581...` Native Pilot PR run `40426213523` completed SUCCESS and Map Engine PR run `40426213522` completed SUCCESS, covering regression/source, tracking/JVM, all three APKs, Android 16 KB packaging, WebView rejection, and emulator launch/map/voice lifecycle.
- Visual inspection of the partial Rider artifact found no concrete regression in Home map/right rail/Ready card, single Messages inbox, inline Earnings credit, Profile/work preferences, Support, Learning or Safety. Merchant Products/Promotions remain visually clean.
- **NEXT:** rerun the existing authenticated Rider/Merchant capture on this app-equivalent successor and require Rider 10/10 plus Merchant 5/5 screenshots. Inspect Diagnostics and record final visual evidence. Customer authenticated review remains blocked on the recorded missing Customer review credential pair.

## 2026-10-27 Authenticated Rider dialog QA fix — exact Pilot PASS / recapture requested

- Fresh authenticated review `40380709805` on app-source checkpoint `1fd0e69ceecea6a960e988937ccf000f224cc319` built Rider/Merchant APKs and booted the emulator. Merchant capture completed all 5 expected screens; artifact `12679216073`. Rider captured Home, Messages, Earnings today/7d/30d, Profile and Support before the review script failed.
- First confirmed Rider review failure was QA-only, not app UI: support dialog title `ติดต่อฝ่ายดูแล` was matched by substring against the underlying Profile subtitle `ติดต่อฝ่ายดูแล QueueGo`, so the harness falsely concluded the dialog did not close.
- Fixed the harness to use exact text/content-description dialog-title matching at `d84e39a1d1b76f577659e836223507e62f04bddc`; added a source guard at `e37fa9a4f61892cfa34630b7a1845d995e732e48`.
- Exact `e37fa9a4...` Native Pilot push `40382031512` and PR `40382048557` completed SUCCESS, and Map Engine PR `40382047590` completed SUCCESS.
- Visually inspected the partial Rider artifact `12679280989`: Home right rail/wordmark/Ready card, single Messages inbox, inline Earnings credit, Profile hero/work-preferences and Support dialog match the current Web hierarchy. Merchant 5-screen artifact is visually clean; no concrete UI regression found. No Production writes.
- **NEXT:** run the existing authenticated capture again on this source-equivalent successor and require Rider 10/10 plus Merchant 5/5 screenshots. Inspect Learning/Safety/Diagnostics and record final visual evidence. Customer authenticated review remains blocked on the recorded missing Customer review credential pair.

## 2026-10-27 Fresh authenticated Rider/Merchant visual capture — REQUESTED

- Exact current successor `0b04ba1ce2974535f4be92af4b85b49ea4cb6709` passed Native Pilot push `40370717648`, Native Pilot PR `40370726557`, and Map Engine PR `40370726631`.
- Authenticated review `40370717708` was readiness-only: credential-check SUCCESS, capture SKIPPED. It is not visual acceptance.
- This docs-only checkpoint intentionally requests the existing read-only Rider/Merchant emulator capture on the same app/source lineage. No app code, Production data, dispatch, payment, map authority or backend mutation.
- **NEXT:** inspect the generated Rider/Merchant screenshots from this successor. Fix only concrete visual mismatches against deployed Web Production. Customer authenticated visual acceptance remains blocked on its recorded missing Customer review credential pair.

## 2026-10-27 Fresh authenticated visual gate — REQUESTED

- Exact successor `bb747776af826b0c5962e8d54f5765d42fd8dec5` passed Native Pilot push 40360128398, Native Pilot PR 40360140176, and Map Engine PR 40360140190.
- Authenticated review 40360128345 was readiness-only: credential-check passed but `capture` was skipped because the branch push did not request authenticated capture.
- This documentation-only checkpoint requests the existing read-only authenticated Rider/Merchant emulator capture. No app source, Production data, dispatch, payment or map authority changes.
- **NEXT:** inspect the fresh Rider/Merchant artifacts from this exact successor line. Fix only concrete visual mismatches; Customer visual acceptance remains blocked on its recorded missing review credentials.

## 2026-10-26 Rider/Web parity consolidation — exact Pilot PASS; authenticated visual review requested

- Continued on the newest `work-native-web-parity-20261026` successor only; no reset/revert/cherry-pick and no Production database/data mutation.
- Exact Native Pilot run `40355762748` completed **SUCCESS** on app/source head `cd9cb207d8092cd7fa2de579ee96901335fffe6e`: Full QueueGo regression, current Web Rider work-preferences runtime + isolated PGlite migration contracts, source/backend/push/voice/release guards, tracking/JVM tests, all three APK builds, Android 16 KB packaging, WebView rejection, and Android emulator launch/map/voice lifecycle all passed.
- Rider map chrome now matches current Web hierarchy: tiny transparent QueueGo wordmark, 52dp circular speed control, recenter, route overview and compass. Native Profile keeps the Web core section order and collapses Native-only vehicle/learning/safety/voice/navigation/diagnostics tools under `เพิ่มเติม` without removing those capabilities.
- Native Rider work preferences now reuse `qg_rider_work_preferences` as server authority for Auto Accept, bundled-job preference and Food/Grocery/Market/Express/Ride service selection. The device store is only a mirror of the server-acknowledged auto-accept bit for background readiness.
- Web Rider successor was forward-reconciled without replacing the newer map/runtime implementation: `rider/work-preferences.js`, its 52x32 red-switch styling, runtime test and migration source were ported from current Web capability; integrated `rider/index.html` mounts the server preferences and calls `maybeAccept()`.
- General Rider Laundry was intentionally **not** restored. The current backend/test authority is shop-managed Laundry transport, so retired general-Rider Laundry state, pool/claim/action routes, map/home branches, Profile toggle and retired RPC calls were removed from integrated Rider Web and guarded from returning.
- Authenticated capture QA now records the collapsed Profile first, then expands `เครื่องมือ Rider เพิ่มเติม` before read-only Learning/Safety/Support/Diagnostics captures, preserving review coverage.
- **NEXT:** inspect the authenticated Rider/Merchant capture triggered by this checkpoint. Verify Rider Home/map chrome/Ready card, single Messages inbox, inline Earnings credit, server-backed Profile work preferences/core hierarchy, and Merchant Home/Orders/Products/Promotions/Profile. Fix only concrete visual mismatches. Customer authenticated review remains BLOCKED on the recorded missing Customer review credential pair; do not claim Customer visual acceptance.

## 2026-10-26 Authenticated Rider visual parity — inline credit + single inbox — IN PROGRESS (exact Pilot NEXT)

- Fresh authenticated review run `40338610666` on exact checkpoint `2e52a516b8caba51ad9195a6bbe4dea1c67799b4` completed SUCCESS: Rider and Merchant APKs built, emulator booted, Rider capture completed, Merchant capture completed and artifacts were uploaded. Merchant artifact `12659106342`; Rider artifact `12658932086`. This is emulator visual evidence, not physical-device certification.
- Verified visually: Merchant Home now loads the real shop image after the bounded HTTPS retry; Merchant Products/Promotions remain clean. Rider Home now has the Web-aligned right rail (speed → recenter → overview → compass) and compact Ready card.
- Fresh Rider Earnings exposed a remaining architecture mismatch: current Web uses `QueueGoCredit.mountInline()` (label + balance + explicit recheck), while Native still mounted the full ledger/top-up panel. Added native `QueueGoCreditInline` using existing `queuego_credit_summary` only, switched Rider Earnings to it, and guarded Merchant full-panel ownership separately. Source/test head chain: `7ead2afe` → `bf7239f5` → `e0158c6b`.
- Fresh Rider Messages also exposed a current-Web mismatch: Web renders a single message inbox with no `แชต / แจ้งเตือน` segmented tabs. Native now renders the single inbox directly, removes notification-center count from the Messages badge, and updates authenticated capture to 10 expected screens. FCM subscription, notification permission and Profile push/test controls remain intact. Source/test head chain: `c7e675b4` → `4a0d7a87` → `174811ed` → `43a5d0f3`.
- No Production database/data write and no dispatch/order/payment authority change.
- **NEXT:** inspect exact-head Native Pilot on the newest successor and fix only the first confirmed failure. After green source/Kotlin/APK/runtime gates, trigger a fresh authenticated review and visually confirm Rider Earnings inline credit and single Messages inbox. Customer authenticated review remains blocked on the recorded missing secret pair.

## 2026-10-26 Current Web visual parity — exact Pilot PASS / final authenticated capture requested

- Newest successor `4077aa5fd1c2b9a78cc519fcdf0cc70aacdb204f` exact Native Pilot run `40336638038` completed **SUCCESS** on the same head: Full QueueGo regression, source integrity/assets, tracking/unit tests, all three Native APK builds, Android 16 KB packaging, WebView rejection, and Android emulator launch/map/voice/lifecycle all passed.
- Merchant Products/Promotions authenticated evidence from exact UI checkpoint `75b021570d0c24c7276f46e8ea5bc8eab43e2a87` is visually accepted for the two targeted fixes: Products shows the current Web-style bottom outlined `เพิ่มเมนู` action and Promotions uses one shell-owned back/title header without a duplicate QueueGo bar. Shell title now uses `MerchantPageTitleSize`; source title ownership guard is `4077aa5fd1c2b9a78cc519fcdf0cc70aacdb204f`.
- Customer location picker presentation is now Web-aligned at `92edab135d47bd255fdaf4749c6dae88592fad4d`: no raw Lat/Lng debug text, larger rounded map surface, GPS action card, dedicated address section, and red rounded save action. Search remains outside the map canvas; auto-GPS/reverse-geocode/save authority is unchanged.
- Unified credit remains one usable balance. The compact Rider credit visual kept ledger/PromptPay/API authority unchanged; the source guard was corrected to assert the balance invariant independently of Compose styling at `1aa79e723ea0be8bc5aff4171204de9e8e4ec1c7`.
- Rider Home source now follows the current Web right-rail hierarchy speed → recenter → route overview → compass and uses the compact Ready card/credit surface. These Rider visual changes were made after the last full screenshot artifact, so visual acceptance still requires a refreshed authenticated capture.
- **NEXT:** run the existing read-only authenticated Rider/Merchant capture on this app-equivalent successor; inspect Rider Home/right rail/Ready card and Earnings credit plus Merchant Products/Promotions. Fix only concrete visual mismatches. Customer authenticated review remains blocked only by the recorded missing secret pair; do not claim Customer screenshot acceptance.

## 2026-10-26 Native visual parity — Rider rail/credit + exact Pilot failure fix — IN PROGRESS

- Continued only from newest `work-native-web-parity-20261026`; no reset/revert/cherry-pick and no Production mutation. Draft stacked PR #730 remains the active review line.
- Exact Native Pilot run `40334262177` failed on head `75b021570d0c24c7276f46e8ea5bc8eab43e2a87` at the first failure in Full QueueGo regression: the source guard rejected every `Bitmap.createBitmap` in shared tracking maps after Customer Ride introduced intentional labelled stop/aggregate marker images.
- Fixed only that confirmed failure first: tracking-map destroy now clears label-image signatures and last points before releasing MapLibre ownership (`c5a01ad9907a8ec79c44a40cb69a1196e09c6acd`), and the guard now rejects retained Bitmap cache objects rather than legitimate transient marker rendering (`5cab65eefbbb20fec4f468427fddba96869dfcdc`). Later Pilot attempts were superseded/cancelled by newer successor pushes; no exact latest-head PASS is claimed yet.
- Current Web Rider Home uses one right rail ordered speed → recenter → route overview → compass. Work already moved speed to the right and compacted the Ready card (`7bc9af05e334055a2004877f288deca8cd3f4c93`, `e073a6e7ac594dbbeb8bad449fd19bb47763d460`). Native now restores the missing overview control using the existing `overviewSignal`/route overview authority, moves compass down one slot, and keeps no-route feedback explicit. Source `031db07ba45e4a75748973f1a2bcaa412cecbde4`; guard `3f481299db06ffef22fd0c95bf676f3b6609f140`.
- Rider Earnings credit was visibly heavier than Web: Native used a default Material card while current Web `.qg-credit-panel-rider` is compact white with a light border. Native Rider credit styling now matches that hierarchy without touching balance, ledger, PromptPay or API logic. Source `2759602695246c309d960226b2bd91a1e4fac3a6`; source guard `68392313e7741c8cd6223dcacd3e80177c4b7552`.
- **NEXT:** inspect the exact-head Native Pilot for the newest successor and fix only its first confirmed failure. Then inspect the pending authenticated Rider/Merchant capture; verify Products/Promotions/Ready card/right rail/credit visually before the next UI edit. Customer authenticated review remains blocked on the recorded missing secret pair.

## 2026-10-26 Merchant current-Web visual hierarchy — IN PROGRESS (authenticated visual NEXT)

- Continued only from the newest Native successor and compared against current Web Production source on `main` `7e036a0735cc49905e9b0a2434492470551a0c9d`; no reset/revert/cherry-pick and no Production mutation.
- Products: current Web no longer renders the stale search field. Native forward-corrected that temporary patch, preserved `ทั้งหมด / เปิดขาย / ปิดขาย` and Grocery price gating, and moved add actions to the bottom white bordered surface. Food/Cafe now uses the current Web copy/style `เพิ่มเมนู`; Grocery keeps outline `เพิ่มสินค้าเอง` plus red `เพิ่มสินค้าจากแคตตาล็อก`. Source `917270d77ed9dec757de2a91a69591c7d842b130`; current copy tests `f8838bab7a089c2dc2964d3485f1938921cb702f`.
- Promotions: the prior authenticated screenshot showed a global QueueGo top bar plus a second in-content `กลับ / โปรโมชั่น` row. Current Web uses a single back/title header. Native now gives Promotions one top bar with back at left and centered `โปรโมชั่น`, while Home/Orders/Products/Profile retain the normal QueueGo brand + notification bar. Source `2f3b23ea4560e83c1eed7905d5d98d7790c66d7b` + duplicate-header removal `9caf950e1988ca0f9253fba65672966f660a6106`; source policy test `d5f4081908448738f67ab58281fec58d5a5b3690`.
- Draft stacked PR #730 is the active review from `work-native-web-parity-20261026` to direct ancestor `work-native-rider-auth-review-20261024`; latest observed PR is mergeable. No exact-head Native Pilot PASS or refreshed screenshot acceptance is claimed yet.
- **NEXT:** this checkpoint requests the existing authenticated Rider/Merchant capture on the new exact HEAD. Inspect the first confirmed Native Pilot/review failure only; if green, inspect regenerated Merchant Products/Promotions and Rider surfaces before further visual edits. Customer authenticated review remains blocked on its recorded missing secret pair.

## 2026-10-26 Customer Ride stop/aggregate marker labels — IN PROGRESS (source checkpoint; exact Pilot NEXT)

- Base `565dbf9b1129074d8ccb1005b70d784862457452` exact Pilot 40329618714 passed regression/source/assets, Kotlin/JVM including all five supply cases, three APKs, 16 KB and WebView rejection; lifecycle was still running at succession. Its readiness-only review is not screenshot acceptance.
- Confirmed label gap: Web `rideStopMarkerElement` labels endpoints รับ/ส่ง and `rideClusterMarkerElement` shows a motorcycle plus aggregate count. Existing shared tracking renderer rendered only unlabelled circles and ignored roles for these markers.
- Added optional QgMapPoint label, used only by Ride endpoints/aggregate cells. Shared renderer adds native bitmap-backed MapLibre symbol layers for labelled pins/count pills, preserving the existing circle path for unlabelled points and live Rider. No new map engine, glyph endpoint, location inference or raw Rider query.
- Motorcycle outline is extracted once from existing QgIcon geometry and reused by Compose/map bitmaps. Local geometry comparison confirms all eight line segments/two circles exactly unchanged. Bitmap text is trimmed/bounded; marker images are cached by role/color/label and reused by slot. Existing map lifecycle/source/fit bounds authority stays intact.
- Extended Ride JVM cases assert endpoint labels, live-Rider absence of label and exact aggregate count label. Local Kotlin/Android SDK unavailable; new renderer compile/runtime and Customer authenticated visual review remain pending, not certified by source inspection.
- **NEXT:** inspect exact successor Pilot's first confirmed failure; complete all final gates. Reuse already verified Rider/Merchant capture at d65652e for specific fixes, then inspect any needed successor shared-design capture. Customer live visual review remains BLOCKED on recorded secret pair; source audit can continue. Native marker proportions/fonts still require actual Customer visual comparison before full parity certification.

## 2026-10-26 Customer Ride nearby aggregate supply — IN PROGRESS (source checkpoint; exact Pilot NEXT)

- **DONE (checkpoint):** `d65652edc3e70bd43bb0f91edf3320c9f9f0bf3e` full exact Native Pilot [40327540508](https://github.com/chatchairins-source/QueuGo/actions/runs/40327540508) completed SUCCESS: regression/source/assets, Kotlin/JVM, all three APKs, 16 KB, WebView rejection, emulator launch/map/voice/lifecycle. Exact authenticated review [40327540246](https://github.com/chatchairins-source/QueuGo/actions/runs/40327540246) also SUCCESS.
- Verified 11 Rider/5 Merchant capture metadata with that exact HEAD. Rider artifact 12653940162 ZIP SHA256 `ef15dafa9b811eb5631cef24fd35cfc952d3b0638ffdee04da14c2e095b8e1ff` shows actual Ready card/switch on Home. Merchant artifact 12653321038 SHA256 `75663b9ec00437e2e23734a37b5d4470a0d35ff448b177f5cc218f20d025dcd5` confirms loaded Home metrics, no raw status note and no null category. These specific defects are visually verified; all-screen Production parity and physical GPS/device certification remain separate.
- **Confirmed source gap:** Web prebooking Ride calls `queuego_customer_ride_supply` and shows nearby count/approximate cells; Native had no equivalent read/UI. Reused that existing authenticated aggregate RPC only. No preassignment Rider identity/raw-profile query, inferred position, backend migration or Production write.
- Added nearby-count/approximate-cluster parsing and prebooking UI using existing QgTrackingMap. Cluster acceptance requires valid shared Thailand coordinates, approximate flag and count >=2; bounded to existing server's 12 cells. Explicit approximate-position hint matches Web. Cluster count retained for subsequent marker-label visual parity.
- Polling is foreground-only and keyed to account/token/pickup/preview eligibility; it pauses for picker/busy/pending/current-trip states. Same-coordinate attempt timestamp survives effect restarts, preserving >=15-second request spacing; cancellation/ensureActive prevents stale results. Pickup changes reset count/cells.
- Added five JVM cases for throttle boundaries/restarts, one-nearby/no-singleton-marker, aggregate eligibility/coordinates, malformed/empty data and 12-cell bound. Local Kotlin unavailable; hosted compilation/execution NEXT. Source comparison verifies pending request body, quote/request/cancel and checkout/recovery suffix byte-identical to parent.
- **NEXT:** inspect exact successor first Pilot failure only; complete latest-head full Pilot. Then close remaining Ride marker-label/count visual gap using existing map renderer, and continue Customer authenticated review when `QG_CUSTOMER_REVIEW_PHONE`/`QG_CUSTOMER_REVIEW_PASSWORD` exist. Continue other unblocked audits without claiming release/physical acceptance.

## 2026-10-26 Exact authenticated-review framework readiness failure — IN PROGRESS

- Exact source `04842b15fc794dad4dda964f9d2811e3b67feaf9` Pilot 40326293414 passed regression/source/assets, Kotlin/JVM (including Ride endpoint cases), all three APKs, 16 KB and WebView rejection; artifact upload/runtime remained in progress at this checkpoint. No containing-run SUCCESS yet.
- Exact review 40326293619 failed before login: Rider `am start` exited 224, then Merchant `wm size` exited 20 with `Can't find service: window`. APK compilation succeeded. Underlying guest-system loss/restart cause is unproven; this is not evidence of a Rider app-source crash.
- Confirmed harness gap: boot gate accepted one `sys.boot_completed=1` sample without checking Android activity/window/package services. It now requires boot plus all three exact `Service ...: found` responses for three consecutive samples, resetting on absence. Five actual Bash readiness fixtures (boot, each missing service, CRLF/not-found rejection) and workflow YAML parse passed locally.
- Merchant app-equivalent artifact 12651860532 from exact review 40325237658 at `8a70f27` has verified ZIP SHA256 `a9fe6ff4c03635c5862ec2acb9cd21ef7866acb24c367b221e4c7a5e0eb2698e` and matching metadata. Visual inspection confirms loaded Home numbers, no status protocol line in Orders, and no literal null category in Products. Five-screen capture complete; full visual parity/physical certification remains false.
- **NEXT:** rerun exact successor review/Pilot; inspect first actual failure only and obtain Rider Home content-ready screenshot. If system services disappear again, collect framework diagnostics before changing app source. Finish final latest-head emulator lifecycle, then implement/audit Web nearby Ride aggregate supply. Customer authenticated credential and release/physical blockers remain separate.

## 2026-10-26 Rider Home capture content readiness — IN PROGRESS

- The initial 5a68212 artifact captured Home navigation/map before snapshot-owned content appeared. Source confirms tabs exist independently of snapshot; idle Ready card only exists after a nonnull snapshot, and active-job dock has an existing semantic header. The original harness waited only for the tabs.
- The harness now requires visible navigation plus a visible Ready switch, active-job dock header, offer action or inactive-account explanation. Navigation-only/loading/error-only Home fails without being saved as complete. Unsupported content also fails rather than receiving false acceptance. No toggle, claim or order action is performed.
- Eight isolated selector fixtures, capture wiring and Python syntax passed locally: idle, active dock, offer, inactive account, loading, error-only, missing tab and hidden anchor.
- Base Ride endpoint source `e6264171eca0b33a009be6aeddcf24bc8c6484b8` exact Pilot 40326100567 is queued/pending; no compile/pass claim for that chunk yet. Authenticated review 40325237658 at app-equivalent Rider/Merchant source 8a70f27 is still running; review 40326100479 is presence-only and must not be called screenshot acceptance.
- **NEXT:** allow this exact successor Pilot and marker-triggered authenticated review to finish without further source pushes; fix only first confirmed failure, inspect actual Merchant loaded Home/Orders/Products and Rider Home images plus exact metadata. Then continue nearby Ride aggregate-supply parity. Customer authenticated credentials and physical/release gates remain separate.

## 2026-10-26 Customer Ride authoritative map endpoints — IN PROGRESS (source checkpoint; Pilot NEXT)

- Base `8a70f27dd72a6b2912688a807f58aa3b3af4ca20` exact Pilot 40325237596 passed full regression/source/assets and Kotlin/JVM (including Merchant category/shared-note tests); APK build was running at succession. Authenticated capture 40325237658 built actual Rider/Merchant APKs and began emulator capture. These are relevant gates, not full containing-run SUCCESS or visual acceptance.
- Confirmed Web gap: `customer-ride.js` active map uses pickup/delivery/Rider coordinates. The existing `queuego_ride_state` RPC already returns all four endpoint coordinates under its account/ownership authority, but Native PassengerRide discarded them and rendered only a Rider point, hiding the entire map before assignment.
- Added nullable endpoint DTO/parser fields and a pure map-points policy. Existing QgTrackingMap now shows valid real endpoints even before Rider assignment, plus real Rider coordinates when supplied. Same shared Thailand validation rejects missing/partial/invalid pairs. Existing terminal tracking suppression remains; no inferred positions, new map engine, request/cancel/payment or backend changes.
- Added four focused JVM cases for searching endpoints, assigned Rider/endpoints, invalid/partial/missing pairs, and all terminal states. Source review confirms parser-to-renderer wiring; exact hosted compilation/tests are NEXT.
- **NEXT:** inspect exact successor first Pilot failure, then actual Home/Orders/Products artifacts from 40325237658. Add a bounded Rider Home content-readiness gate if the original capture race persists. Next source audit: Web aggregate nearby Ride supply, preserving privacy and existing server RPC authority. Customer authenticated/physical/release gates remain separate.

## 2026-10-26 Merchant order-note presentation — IN PROGRESS (source checkpoint; hosted tests NEXT)

- Artifact 12650270511 at `5a682121d463ce94851a8fc03b591c97667c8550` visibly exposes a marker-only note `__QT_ORDER_STATUS__=completed`. Merchant list directly rendered raw `order.note`; Web stores that protocol line for status compatibility. Rider already had a tested display-only filter for this exact marker.
- Moved that existing policy unchanged into shared `cleanOrderDisplayNote`; Rider delegates to it and Merchant list now uses it. Raw DTO/API notes, status parsing, cancellation and order writes remain intact. Embedded customer text and unrelated note lines remain visible.
- Added shared tests for marker-only/null/blank, mixed instructions and embedded/unrelated marker text; existing Rider tests continue to exercise its delegate. Source review confirms one Merchant display call, unchanged filtering policy and no raw parser mutation. Local Android/Kotlin unavailable; exact hosted regression/JVM/capture required before DONE.
- **NEXT:** inspect exact successor Pilot first confirmed failure, then regenerated Merchant Orders/Products/Home and Rider Home screenshots. Continue Rider Home semantic readiness and remaining Customer Ride map-context audit. Keep Customer credential/physical/release blockers separate.

## 2026-10-26 Merchant JSON-null category — IN PROGRESS (source checkpoint; JVM/Pilot NEXT)

- Authenticated artifact 12650270511 at `5a682121d463ce94851a8fc03b591c97667c8550` shows literal `null` on a product category. Root cause: `merchantProductFromRow` used `optString`, whose JSON-null string bypassed `ifBlank`. Web uses `p.category ?? p.metadata?.category ?? ''`.
- Reused existing nullable JSON helper for category and metadata fallback. Current explicit category and existing blank fallback remain; no stock/pricing/order changes. Parser made internal for four focused JVM cases: JSON-null metadata fallback, both-null, explicit priority, missing/blank.
- Local Kotlin toolchain is unavailable; JVM execution and source regression are pending exact successor hosted Pilot. No runtime fix claim yet.
- **NEXT:** inspect exact successor Pilot first failure only; inspect regenerated authenticated screenshots after source gates. Continue Rider Home readiness and Merchant note-display investigation. Customer authenticated review remains blocked by the two recorded missing secret names.

## 2026-10-26 Authenticated screenshot timing checkpoint — IN PROGRESS

- Exact Native Pilot [40321700728](https://github.com/chatchairins-source/QueuGo/actions/runs/40321700728) at `5a682121d463ce94851a8fc03b591c97667c8550` completed SUCCESS, including regression/JVM, three APKs, 16 KB, WebView rejection and emulator lifecycle.
- Exact authenticated review 40321700647 logged in and captured 11 Rider/5 Merchant screens; metadata source HEAD matched. This is evidence capture, not visual certification or physical acceptance.
- Confirmed capture defect: Merchant Home was saved immediately after its navigation label appeared, while the four metrics still rendered loading ellipses. The harness now waits for all five tabs, all four metric labels and no visible loading ellipsis; timeout fails without saving a loading Home. Six isolated readiness fixtures, wiring and Python syntax passed before commit.
- **BLOCKED (Customer authenticated review):** missing GitHub Actions secret names `QG_CUSTOMER_REVIEW_PHONE`, `QG_CUSTOMER_REVIEW_PASSWORD`; provide these through the existing secret mechanism to unlock. No values requested in logs.
- **NEXT:** inspect exact successor capture/Pilot; inspect Rider Home loading versus dock readiness, then fix Merchant displayed protocol note and JSON-null product category after verifying Web/source behavior. Preserve raw order state authority and continue unblocked work.

## 2026-10-26 Current authenticated Rider/Merchant capture — IN PROGRESS

- Exact readiness [40320797641](https://github.com/chatchairins-source/QueuGo/actions/runs/40320797641) on `00f605f08367d97f0bd3c780bacd6950484149a1` reports Rider/Merchant credential pairs present and Customer missing. Presence is not login or visual acceptance.
- Explicit `[authenticated-review]` owner-branch checkpoints now invoke the existing read-only Rider/Merchant post-login harnesses; ordinary parity pushes remain presence-only. Reused Pilot Firebase preparation before review APK builds.
- Credential entry now suppresses argument-bearing subprocess failures/timeouts. YAML, four isolated readiness/privacy fixtures and ten credential-entry success/error cases passed before commit; the fixture caught and prevented a temporary generation error before any Git write.
- **NEXT:** inspect exact successor authenticated review and Native Pilot, fix only first confirmed failures, inspect actual screenshots/metadata, record exact missing Customer secret names and continue unblocked parity work. Login/presence/guest screens do not certify authenticated visual parity.

## 2026-10-26 Authenticated parity review readiness — IN PROGRESS

- Existing Rider/Merchant authenticated-review workflow only auto-ran on the older review branch. Added the current Native owner branch to its credential-presence check and report per-role Rider/Merchant/Customer readiness plus missing secret names; values never enter logs/artifacts.
- Current parity-branch pushes run presence-only checks. Existing explicit manual/old review-branch capture behavior is retained; this checkpoint does not start account/job actions on the new push trigger.
- Validation before commit: workflow YAML parse and four isolated empty/partial/full role-presence/privacy fixtures passed. App source remains identical to `f5cb1f4273739fea360e671a6b3eb8cedf922cfa`, whose exact Pilot `40318997253` passed regression/JVM/APK/packaging gates and was still in emulator lifecycle at succession.
- **NEXT:** inspect exact successor authenticated-review readiness artifact/job and latest Native Pilot. Use current role readiness to continue authorized read-only review or record the exact missing-credential blocker; finish all Native Pilot gates on the final latest head. Continue unblocked source parity work while external evidence is unavailable.

## 2026-10-26 Customer Ride arrival-state parity — IN PROGRESS (source/test checkpoint; exact Pilot NEXT)

- The existing PassengerRide DTO/parser now retains `arrived_pickup_at` and `arrived_dropoff_at`, making arrival-only server updates change structural state. The renderer uses a pure Web-aligned label policy for arrived pickup/destination within the existing coarse statuses; terminal and unrelated statuses ignore stale arrival fields.
- Added JVM parsing/equality/label/terminal/stale-field cases and a parser-to-renderer wiring contract. Quote, request, cancellation, cash and durable request/quote UUID behavior are preserved.
- **NEXT:** run/poll Native Pilot on this exact successor; fix only its first confirmed failure. Complete regression/JVM, all three APKs, 16 KB, WebView rejection and emulator lifecycle on the final latest descendant, then close the current source P0/P1 audit and record any remaining external/visual gates explicitly.

## 2026-10-26 Customer Laundry authority — DONE (source/regression/Kotlin/JVM)

- Source `48e2d1f17191913321dcc24eebac8300e4c9c34e`; exact Pilot [40317526297](https://github.com/chatchairins-source/QueuGo/actions/runs/40317526297) passed full regression, source integrity and all Android JVM/Kotlin tests, including the new eligible-catalog cases. APK build was running at succession; this is relevant-gate evidence, not a containing-run SUCCESS claim.
- Existing catalog RPC now owns eligible hubs/settings/services. Order, checkout, event and pending-body code remains unchanged.
- **NEXT:** Ride arrival-state successor above and final complete latest-head Native Pilot.

## 2026-10-26 Customer Laundry eligible-catalog parity — IN PROGRESS (source/test checkpoint; Pilot NEXT)

- Native now reads `queuego_laundry_customer_catalog`, exactly as Web `customer-laundry.js`, instead of independently joining raw active/enabled tables that do not enforce accepted-active-Rider eligibility. Settings and services come from the returned eligible snapshot.
- Service selection refreshes that catalog and rejects a hub absent from it; disabled/malformed/empty-service snapshots cannot provide a bookable hub. Added JVM flag/eligibility/fee/service cases and an RPC/no-raw-fallback contract.
- Order/event/checkout implementation from `orders()` onward is byte-identical to the parent. Existing submission, durable pending UUID/body, cash and state authority remain.
- **NEXT:** inspect exact successor Native Pilot and fix only its first confirmed failure. After relevant regression/Kotlin/JVM gates pass, implement the confirmed Ride arrival-state gap below, then complete final latest-head APK/runtime Pilot.

## 2026-10-26 Customer Ride arrival-state parity — P1 gap confirmed

- Web `customer-ride.js` distinguishes `rider_assigned + arrived_pickup_at` (“คนขับถึงจุดรับแล้ว”) and `in_progress + arrived_dropoff_at` (“ถึงจุดหมายแล้ว”). Native `PassengerRide` omits both fields and renders only the coarse status; arrival-only updates cannot change the parsed DTO or label.
- Quote/request/cancel and durable request+quote UUID semantics otherwise match the inspected Web flow.
- **NEXT:** read those timestamps into the existing DTO/parser, use a pure Web-aligned status label policy, and test arrival-only state changes plus stale/terminal timestamps after Laundry's relevant gates pass.

## 2026-10-26 Customer signed-in quantity cap — DONE (source/regression/Kotlin/JVM/APK)

- Source `5f5412016889e0835ef805593a55056b2d668e3e`; exact Pilot [40315822728](https://github.com/chatchairins-source/QueuGo/actions/runs/40315822728) passed full regression, source integrity, JVM/Kotlin, three APKs, 16 KB and WebView rejection. Emulator was still running at succession and may be cancelled by branch concurrency; no full-run SUCCESS is claimed.
- **NEXT:** Laundry authority and Ride arrival-state parity above; final full Pilot on their latest descendant.

## 2026-10-26 Customer signed-in shop quantity cap — IN PROGRESS (bounded source fix; Pilot NEXT)

- Web Production `index.html` refuses another add when a line already has 99 units. Native guest-shop and both cart-plus paths also cap at 99, but signed-in `ShopScreen.onAdd` incremented the quantity without a bound. `QueueGoCartStore` restores at most 99, while checkout sends the in-memory quantity verbatim; SQL `queuego_place_cash_order_core` requires `qty` matching `^[1-9][0-9]?$`. This source mismatch can produce an invalid checkout and inconsistent restored quantity.
- Added the same 99-unit refusal and Thai limit message to the signed-in shop add path only. Existing pending-checkout guards remain in effect. Bounded source review confirms 98→99 is accepted, while 99 or above is refused; full hosted successor validation is pending.
- **NEXT:** run/poll Native Pilot on the exact successor HEAD; fix only its first confirmed failure. Then fix the confirmed Customer Laundry catalog P1 below, using the same existing authority as Web.

## 2026-10-26 Customer Laundry eligible-catalog authority — P1 gap confirmed

- Web `customer-laundry.js` calls `queuego_laundry_customer_catalog`. `QueueGo-Laundry-Customer-Catalog.sql` returns only enabled active Laundry shops with active services and at least one accepted active Laundry Rider; it also returns the authoritative settings/services snapshot.
- Native `CustomerLaundryApi.catalog` instead reads active hubs and enabled pickup/return settings directly, and `services` reads the active service table. `QueueGo-Laundry-v2.sql` hubs RLS allows active hubs, and `QueueGo-Laundry-v1.sql` settings/services RLS allows enabled/active rows without the accepted-Rider condition. Native can therefore expose hubs Web excludes.
- **NEXT:** after the quantity checkpoint's exact Pilot, switch Native catalog/services discovery to the existing customer-catalog RPC, parse nested settings/services, reject a selected hub absent from the fresh eligible snapshot, and add focused JVM/source coverage. Preserve order submission, pending UUID/body recovery and server state/pricing authority.

## 2026-10-26 Customer Grocery category/page parity — DONE (source/test)

- Source `f262329d657409fa29bc4129afcabd674bc91244`; exact Native Pilot [40313768964](https://github.com/chatchairins-source/QueuGo/actions/runs/40313768964) on that HEAD completed SUCCESS: regression/source/contracts, JVM tests including paging/race/cache cases, three APKs, 16 KB packaging, WebView rejection and emulator launch/map/voice lifecycle.
- Follow-up audit confirms Web Customer add/checkout also has no selected menu-option payload, so the absence of that payload in Native is not a Production parity gap.
- **NEXT:** quantity boundary guard and Laundry eligible-catalog parity above, then continue the remaining Customer/Rider source audit. Authenticated visual/physical/release certification remains separate.

## 2026-10-26 Customer Grocery category/page parity — IN PROGRESS (source/test checkpoint; Pilot NEXT)

- Web Production `main=7e036a0735cc49905e9b0a2434492470551a0c9d`, `catalog/customer-grocery.js`, uses `qg_grocery_customer_categories` and `qg_grocery_customer_page` with category, offset, 50 visible rows and the 51st-row sentinel. The existing SQL in `20261012170449_grocery_master_catalog.sql` filters available Delivery products with positive store price.
- Confirmed P1 source gap: Native guest and signed-in shop visits both used one unpaged products-table read for Grocery, with no category or further-page UI. That cannot provide Web's complete paged catalog navigation and sellable-item filtering. No live catalog size/truncation claim is made.
- This atomic checkpoint routes Grocery in both shells through those existing public/authenticated RPCs, adds category selection and load-more/retry, preserves visited-page cache and cart behavior, and scopes every page/category request to the active shop generation. A failed page retries its same offset; the sentinel is not displayed or skipped.
- Added JVM cases for sentinel boundaries, retry offsets, accumulated cache, category races, wrong-shop responses, category-only failures and scoped sellable cache fallback; added the production RPC wiring contract. Hosted Native Pilot is required for compile/JUnit/APK/runtime evidence.
- **NEXT:** run/poll Native Pilot on this exact successor HEAD; fix only its first confirmed failure. If green, record Grocery browse parity DONE (source/test), then continue Customer inventory/availability P1/P0 audit against Web Production.

## 2026-10-26 Merchant POS offline custom options — DONE (source/test)

- Implementation `bbeb3eb9efbcde5c108cb7f514e3020eb679f812`; exact Native Pilot [40311664858](https://github.com/chatchairins-source/QueuGo/actions/runs/40311664858) on that same HEAD completed SUCCESS: full regression, source/contracts, JVM tests, all three APKs, 16 KB packaging, WebView rejection, and emulator launch/map/voice lifecycle.
- Follow-up Merchant audit: `MerchantProductsScreen.kt` and `MerchantProductModel.kt` already edit structured portion/toppings with the same schema and validation as Web `merchant/index.html`. POS reduction uses matching product+description semantics in Web and Native. No further P1/P0 source gap was confirmed in those inspected capabilities.
- **NEXT:** continue the confirmed Customer Grocery browse gap above, then the remaining Customer inventory/availability audit. Physical/release evidence remains separate.

## 2026-10-26 Merchant POS offline custom options — IN PROGRESS (source/test checkpoint; exact-head Pilot NEXT)

- Based on the verified Native successor `49478a5ca477f84b89d7c004d010da3f991739b1`, branch `work-native-web-parity-20261026`; Web Production remains `7e036a0735cc49905e9b0a2434492470551a0c9d`.
- Web `merchant/pos-table-v2.js` stores a separate local line for each product+option combination, snapshots base/option-inclusive price and option labels, validates current required/min/max/unknown/duplicate options before submission, and sends exact saved options with stable per-unit request IDs. Existing `20261022080048_pos_offline_draft_submission.sql` already validates/hashes those items and delegates to `pos_add_product_with_options`; no backend change is needed.
- This atomic source/test checkpoint implements that Native flow: offline selection dialog; canonical option-aware line grouping; durable base-price/options/labels with backward-compatible plain-draft decoding; review of current options and option-inclusive prices; exact saved options in the existing atomic RPC. Price/name changes retain Web's explicit confirmation flow. Offline confirmation says “เพิ่มลงร่าง”; pending drafts retain their payload and request IDs.
- Added focused JVM cases for distinct option-line persistence, retry IDs, old-draft decoding, option-inclusive pricing, equivalent multi-choice normalization, and rejection of missing/duplicate/removed selections. Local Android tooling is unavailable; hosted Native Pilot is the required compile/JUnit/APK/runtime evidence.
- **NEXT:** run/poll Native Pilot on this exact successor HEAD; inspect and fix only its first confirmed failure. If green, record offline custom options DONE (source/test), then continue the Merchant catalog/POS and remaining Customer inventory P1/P0 audit.

## 2026-10-26 Merchant POS online custom options — DONE (source/test)

- Implementation `feef406520de0f912de87e9873aed128b21d508a` with bounded source-contract/compile/test-fixture corrections through `49478a5ca477f84b89d7c004d010da3f991739b1`.
- Exact Native Pilot [40309589021](https://github.com/chatchairins-source/QueuGo/actions/runs/40309589021) on `49478a5ca477f84b89d7c004d010da3f991739b1` completed SUCCESS: full regression, Native source/contracts, all module JVM tests, three APKs, 16 KB packaging, WebView rejection, and emulator launch/map/voice lifecycle.
- **NEXT:** complete offline draft custom-option parity and its exact-head Pilot as recorded above.

## 2026-10-26 Merchant POS options JUnit fixture syntax — bounded repair; exact-head Pilot NEXT

- Exact Native Pilot [40308748160](https://github.com/chatchairins-source/QueuGo/actions/runs/40308748160) on `83f40abb3420a7a3acec06b1d350e44b9f9c490e` cleared regression and all earlier gates, then failed at the first remaining compiler diagnostic: `MerchantPosOptionsTest.kt:17:101 Syntax error: Expecting ','`. The nested `JSONArray().put(...)` fixture had unbalanced closures, causing the remaining option-test diagnostics to cascade.
- Rewrote only that test fixture using explicit `apply` scopes; no production source behavior changed.
- **NEXT:** run/poll Native Pilot on the exact successor HEAD. Fix only its first confirmed failure. If green, proceed with Merchant offline-draft custom-option parity.

## 2026-10-26 Merchant POS option JUnit compile failure — first diagnostic fixed; exact-head Pilot NEXT

- Exact Native Pilot [40308165171](https://github.com/chatchairins-source/QueuGo/actions/runs/40308165171) on `920e19e199c29856d8eb98238a1a0911211390f9` passed full regression and all pre-JUnit gates, including the app Kotlin compile that verifies the Compose import fix.
- The first JUnit compiler diagnostic was `MerchantPosOfflineDraftStoreTest.kt:99:54 Unresolved reference JSONArray` (and the corresponding JSONObject use in the same changed test file); the file lacked `org.json` imports. Added those imports only. Later diagnostics in this same failed compile are deliberately left for the next exact-head run to establish whether they remain after the first confirmed cause is fixed.
- **NEXT:** run/poll Native Pilot on the exact successor HEAD. Fix only its first confirmed failure. If green, continue Merchant offline-draft custom-option parity.

## 2026-10-26 Native POS option dialog compile failure — bounded import fix; exact-head Pilot NEXT

- Exact Native Pilot [40307372943](https://github.com/chatchairins-source/QueuGo/actions/runs/40307372943) on exact HEAD `c9c3a6b21bb446b600a88747d0e60b4f7a920a33` passed Full QueueGo regression, POS idempotency, Customer/Merchant push, session binding, release, voice, source integrity and asset gates. Its first failure was `:merchant:compileDebugKotlin`: `MerchantPosOptions.kt:10:43 Cannot access RowColumnParentData?.weight: it is internal in file`.
- Root cause is the explicit `androidx.compose.foundation.layout.weight` import in the new dialog source; the Compose `weight` extension is scope-provided. Removed only that invalid import.
- **NEXT:** run/poll Native Pilot on the exact successor HEAD. Fix only its first confirmed failure. If green, continue Merchant offline-draft custom-option parity (persist base price, canonical options and option labels; group by product+options; revalidate and submit exact options with stable request IDs).

## 2026-10-26 Merchant POS option source contract — bounded correction; exact-head Pilot NEXT

- Exact Native Pilot [40306967037](https://github.com/chatchairins-source/QueuGo/actions/runs/40306967037) ran on exact HEAD `d648c0c0446281d7f1c236936590f6abfdc7be37`. It stopped at the first Full QueueGo regression check, `tests/native-android-source.cjs:706`: the contract searched for `optionsJson = optionsJson` in `MerchantPosApi.kt`, although that argument is passed from `MerchantPosScreen.kt`. The implementation's API RPC call and screen payload are present; this is a test source-location error, not an app failure.
- Corrected only that assertion in `95037130636aab5522e6e088b7f6dbfda88494a7`. This preserves both independent requirements: API calls the existing option-pricing RPC; screen passes the exact options JSON. Static review confirmed the assertion now checks each in its owning source.
- **NEXT:** run/poll Native Pilot on this checklist successor's exact HEAD. Fix only its first confirmed failure. If all gates pass, implement Merchant offline-draft custom-option parity next.

## 2026-10-26 Merchant POS custom option selection — source committed; exact-head Pilot pending

- Bounded Native implementation committed as `feef406520de0f912de87e9873aed128b21d508a`: product options parse Web structured and legacy variants, a Compose dialog validates required/min/max/duplicate/unknown selections, and the existing `pos_add_product_with_options` RPC receives the selections with a stable UUID and durable pending replay body.
- The app-private offline catalog now retains inventory and option inputs; custom-option products are kept out of offline drafts until the offline option submission flow is implemented, preventing accidental plain-price lines. Added focused JUnit and Native source contracts. No SQL/backend changes.
- **NEXT:** run the hosted Native Pilot on the exact checklist successor; inspect and fix only its first confirmed failure. If clean, implement offline-draft option parity because Web stores and submits exact per-item selections while Native currently blocks those products offline.

## 2026-10-26 Merchant POS custom options parity — P1 source gap confirmed

- Web Production `main=7e036a0735cc49905e9b0a2434492470551a0c9d` loads each product's `variants`, presents structured `queuego.menu-options.v1` groups and legacy string variants, validates required/min/max/duplicate selections, and uses `pos_add_product_with_options` with a stable request UUID. The server validates selected keys and authoritative price deltas, snapshots option labels into the order item, and replays safely (`supabase/migrations/20261021223000_pos_menu_option_pricing.sql`).
- Native Merchant POS product projection and `PosProduct` omit `variants`; tapping a product calls the plain `pos_edit_bill_once` add path. Native offline draft submission also currently sends empty `options`. This prevents required customizations and option-based price calculation in Native, a confirmed P1 catalog/POS gap.
- **NEXT:** implement Native online option parity in one bounded chunk: parse Web option groups/legacy variants, present validated selection UI, persist the exact pending choice/request across interruption, and call the existing authoritative `pos_add_product_with_options` RPC. Add focused JUnit/source contracts, run exact-head Native Pilot, then separately close offline-draft option parity if still open.

## 2026-10-26 Merchant POS tracked stock parity — DONE (source + exact-head Native Pilot)

- Native POS parity implementation `6199e8561c7433a7cf95cdb6cb92ff316bec07ba`; checklist successor HEAD `43f98d6cdd05bccfe7e0b0f76998258abaa0c062` with source tree unchanged after that code commit.
- Exact Native Pilot [40304316009](https://github.com/chatchairins-source/QueuGo/actions/runs/40304316009) on exact HEAD `43f98d6cdd05bccfe7e0b0f76998258abaa0c062` completed SUCCESS: full QueueGo regression, Native source/JUnit gates, all three APK builds, Android 16 KB packaging, WebView rejection, and emulator launch/map/voice lifecycle evidence.
- **NEXT:** continue the Web Production versus Native Merchant catalog/POS audit for any remaining P1/P0 gaps. After that audit, continue Customer inventory P1/P0 parity; document each bounded result and proceed to the next confirmed gap.

## 2026-10-26 Merchant POS tracked stock parity — implementation committed; hosted exact-head Pilot pending

- Web Production `merchant/pos-table-v2.js` loads `stock`, `inventory_stock_tracking`, `grocery_master_id`, and `grocery_stock_tracking`; it keeps products visible and disables only the add action when tracking is enabled and stock is zero or below, while displaying remaining stock.
- Native POS omitted all four inputs and had no sold-out add gate. Bounded source implementation committed as `6199e8561c7433a7cf95cdb6cb92ff316bec07ba`: load/parse stock and both tracking paths, retain Web's missing-column fallback, disable add for tracked zero stock, and show stock status/quantity. Added focused JUnit rules and Native source contract; no checkout or server authority changed.
- Prior exact Pilot `40303613757` on `8ba905f3d11d93e7e3038fa103031d0e8a141fba` passed full regression and all pre-Kotlin contracts, then was cancelled at the Kotlin/JUnit step when another run on parent `5335ecb685bef5139e7afa3af98cf4019aa9dc3b` entered the workflow's branch concurrency group. This supplied no compile diagnostic and no Kotlin/APK evidence. Local Android SDK/Gradle remains unavailable.
- **NEXT:** complete the Native Pilot on the latest checklist successor exact HEAD; read the first confirmed failure if any and fix only that. If successful, continue Merchant catalog/POS and Customer inventory P1/P0 audit from Web Production.

## 2026-10-26 Merchant inventory Pilot compile failure #2 — bounded fix committed; exact-head Pilot NEXT

- Exact Native Pilot [40302510796](https://github.com/chatchairins-source/QueuGo/actions/runs/40302510796) on exact HEAD `79b378f23c554db244140407d3baf380d86530f9`: full QueueGo regression, POS/edit and Customer/Merchant contracts, source integrity, and asset checks passed. Kotlin unit/build step failed first at `MerchantProductsScreen.kt:429:48`: unresolved `launch`; subsequent suspend-call diagnostics at the three adjustment/history callbacks follow from the missing coroutine extension import.
- Bounded fix committed as `5335ecb685bef5139e7afa3af98cf4019aa9dc3b`: add `kotlinx.coroutines.launch` to the existing Merchant coroutine imports. No behavior changed. Local Android SDK/Gradle is unavailable; hosted Native Pilot is the Kotlin/JUnit and APK gate.
- **NEXT:** run Native Pilot on the exact successor after this checkpoint; inspect only the first confirmed failure. If clean, continue the recorded Merchant POS parity chunk: tracked zero-stock products remain visible but cannot be added, with available stock displayed.
  
## 2026-10-26 Merchant inventory tracking — first regression failure isolated

- Exact Native Pilot `40302199708` on `2eb010c99d8d8f5f0132c0904ed82fa2f2a47b7c` stopped at the first full-regression source contract; all preceding regression checks passed. Failure: `tests/native-android-source.cjs:683` required the former exact product projection and rejected the newly required `inventory_stock_tracking,grocery_master_id,grocery_stock_tracking` fields.
- Bounded test correction committed as `46ab406b8c73f20fea4c4126b2f69aee9250479b`: preserve stock/category/variant/channel checks and require the three inventory-tracking fields. No application behavior or test coverage was weakened.
- **NEXT:** rerun Native Pilot on the exact successor after this checklist commit, inspect only its first failure, then continue Merchant POS parity for disabling add on tracked zero-stock products.

## 2026-10-26 Native exact-head Pilot + parity audits — checkpointed

- Exact current Native HEAD before this checklist commit: `843ff11e34cc8d8732e0d41f815398f959e88931`, tree `f8f5939cbf59f3b6fd90bb2f9ca5a2ed0addca5b`. Its bounded Kotlin fix imports shared `QgLine` and replaces the unresolved Merchant `QgBorder` reference.
- Exact-head Native Pilot [40299842063](https://github.com/chatchairins-source/QueuGo/actions/runs/40299842063) on `843ff11` completed SUCCESS: full regression, Native POS/edit and Customer/Merchant contracts, source integrity/assets, Kotlin tests, all three APK builds, 16 KB packaging, WebView rejection, and Android launch/map/voice lifecycle evidence passed.
- Rider active-job audit against Web `main=7e036a0735cc49905e9b0a2434492470551a0c9d`: offer/accept, arrival, pickup/proof, customer arrival/cash, and completion use matching RPC/state transitions; no evidence-backed source gap in the audited flow.
- Merchant catalog/POS audit: Grocery and Market catalogs, POS checkout/staff/kitchen/cash/discount/offline draft flows are present. A P1 Merchant inventory gap is confirmed: Web stores `inventory_stock_tracking`, locks direct stock edits for tracked goods, and supports manager-scoped adjustment plus audit history; Native `MerchantProduct`/editor/load/save omit this flag and adjustment/history API.
- Customer standard product/cart/checkout and Market Trip inventory audit found no P1/P0 gap: Native reads availability/available packs, limits quantities and uses the same authoritative checkout RPCs with durable pending recovery.
- **NEXT:** implement the bounded Merchant generic inventory parity: read/persist `inventory_stock_tracking`, guard tracked-stock edits, and add audited adjustment/history calls through existing `pos_inventory_stock_adjust` and `pos_inventory_adjustment_history`. Add focused Native source/JVM contracts, commit the complete chunk, update this checklist, and run Native Pilot on the exact successor before continuing the next Merchant/Customer P1/P0 audit.

## 2026-10-26 Merchant ticket Pilot compile failure #1 — fixed (focused source check); exact-head rerun NEXT

- Exact hosted Pilot `40297983878` on `0e4b431362db83e0eef7c4be19087fd0e4b4d2d0`: full QueueGo regression and source-integrity/asset gates passed; Kotlin tracking/build stage failed. First compiler diagnostic: `MerchantSupportTicketsScreen.kt:196` cannot smart-cast `ticket.evidencePath` because it is a public API property from another module.
- Bounded fix: capture `ticket.evidencePath` into a local immutable value before rendering the evidence button and pass that local to the signed-evidence API. No ticket behavior or backend changed.
- Focused Native source contract: 336/0; `git diff --check` PASS. Local Android SDK/Gradle unavailable; hosted Kotlin compile remains the relevant verification.
- **Checkpoint:** the smart-cast fix is committed in successor history; subsequent exact-head runs found and fixed `QgBorder` and validated the current tree. This interim NEXT is superseded by the top checkpoint below.

## 2026-10-26 Customer checkout and standard order-state audit — audited; no source gap found

- Audited current Web `main=7e036a0735cc49905e9b0a2434492470551a0c9d` against `CustomerCartCheckout.kt`, `QueueGoCustomerApp.kt`, and `CustomerOrderTrackingScreen.kt` at successor tree `d5da640c3d7d5826c201920d3ff9f1bb05506b6b`.
- Checkout parity evidence: separate cart/checkout screens; latest catalog price and availability reconciliation; QueueGo Places and location picker, GPS, address, note, delivery-fee quote, active-order guard, cash checkout, idempotent pending recovery, and pending cart lock. Native order status labels/stages/time, cancellation eligibility, support/contact, live tracking, totals, and review states match the current Web behavior inspected in `index.html` and `customer-features.js`.
- This audit found no evidence-backed source gap in this bounded flow; no code changed. Paired authenticated screenshots remain open because no authorized Customer review session is available. No physical-device claim.
- **NEXT:** continue the Rider active-job Web/Native transition audit (offer/accept, merchant arrival, pickup/proof, customer arrival/cash, completion) while Native Pilot `40297983878` validates exact successor `0e4b431362db83e0eef7c4be19087fd0e4b4d2d0`; inspect its first failure if any, then proceed to Merchant catalog/POS independent gaps.

## 2026-10-26 Merchant completed-order issue reporting — DONE (source/full regression); exact Pilot pending

- Current Merchant Web `renderShopOrderModern` exposes `แจ้งปัญหาออเดอร์นี้` only when `status === completed`; it opens the QueueGo ticket form with that order selected. Native order detail had no report action, and its profile support route was a separate admin chat.
- Bounded Native flow: add the same completed-only action and an order-bound ticket screen with Web categories, optional order selection, 10–2000 character details, optional JPG/PNG/WebP evidence (5 MB max), create/list/status/admin note and signed evidence viewing. New shared `NativeSupportTicketApi` uses the existing `qg_create_ticket`, `qg_support_tickets`, and `qg-evidence` authority; no schema, refund or GP-slip changes.
- `node tests/native-android-source.cjs` 336/0; clean full `TZ=Asia/Bangkok npm test` EXIT 0 on commit `db9d00c5`, tree `fe6d8fa4`; post-test map QA 28/0. No Production ticket was created. Local Gradle/Android SDK are unavailable.
- Prior exact Native Pilot `40296378388` on `ce2a9cec` failed at first Merchant compile: `CircleShape` was unimported and `QgText` did not exist. Commit `db9d00c5` imports `CircleShape` and uses shared `QgInk`; latest source plus ticket UI needs new exact-head Pilot.
- **NEXT:** resume Customer cart/checkout/order state parity from the pinned Web `main=7e036a0` while exact-head Pilot runs; then inspect Pilot and fix only its first real failure.

## 2026-10-26 Customer Food/Cafe/Grocery category filter parity — DONE (source/full regression); hosted Pilot pending

- Current Web `main=7e036a0735cc49905e9b0a2434492470551a0c9d` filters category pages by exact `public_category` (`food`, `cafe`, `grocery`). `CustomerApi` maps that same field to Native `CustomerShop.category`, but `serviceCategoryRows` also admitted `restaurant`, `drink`, `beverage`, and `convenience`, allowing Native-only rows/counts and changing empty states.
- Bounded fix: apply exact three Web category filters; preserve distance ordering, category banners, other global search labels, and all business actions. Added Kotlin alias exclusion test plus Customer source-parity contract; no API/backend changes.
- Focused Customer parity 55/0, Native source 332/0, and `git diff --check` pass. Full `TZ=Asia/Bangkok npm test` EXIT 0 on exact source commit `ef5799bc`, tree `b0e6a72c`; post-test map QA 28/0. No Production writes.
- Exact Pilot `40295299296` on source checkpoint `966ebb75` failed at first Android compile: `CustomerGuestShell.kt:512` unresolved `clip` (missing Compose import from the Guest-header change). Added `androidx.compose.ui.draw.clip` in commit `991ff3e8`; focused contracts and clean full regression pass on tree `25a0b28f`. Exact successor Pilot `40296040683` on remote checkpoint `d9c66383` is now in progress. Android SDK/Gradle wrapper are unavailable locally.
- **NEXT:** audit Customer shop/product detail and cart against current Web actions/states for another source-backed gap, then continue checkout/order flow. Inspect Pilot `40296040683`; fix only its first real failure.

## 2026-10-26 Customer Guest top-bar Web parity — DONE (source/full regression); exact Pilot in progress

- Web `index.html` gives Guest Home 16/10 dp-equivalent padding, compact pages 12/6, QueueGo letter spacing -0.4, and a plain red `เข้าสู่ระบบ` label. Native Guest header used Home padding on every route, a pink pill, and no wordmark tracking.
- Bounded fix in `CustomerGuestShell.kt`: pass route density, match the Web values and label, retain a generous transparent tap target. No auth or navigation behavior changed.
- Focused parity 55/0 and Native source 332/0 pass. Full `TZ=Asia/Bangkok npm test` EXIT 0 on source commit `39f3bf8a`, tree `db8225f2`; post-test map QA 28/0. No Production writes.
- Remote checkpoint `d9c66383` exact tree includes the missing `clip` import; Native Pilot `40296040683` is in progress. Prior `40295299296` stopped at the Customer Kotlin compile error documented above. Authenticated screens remain session-gated, physical acceptance remains separate.
- Guest top-bar source scope is DONE; **NEXT:** continue Customer shop/product detail and cart Web comparison.

## 2026-10-26 Merchant order-detail Web parity — DONE (source/full regression); exact Pilot running

- Web `renderShopOrderModern` shows the customer name/address, five-step delivery progress, item quantity total, and Rider cash amount only after the pending/cancelled states. Native detail previously lacked the address/progress/quantity summary and always showed expected Rider cash, including pending and cancelled orders.
- Bounded source change: reuse the current authenticated `orders` RLS read to add `delivery_address`; display Web-aligned customer/address, progress stages and item count; hide the Rider cash summary while pending or cancelled. No new endpoint, payment action or order authority. Added deterministic JUnit stage/terminal-state coverage and source contracts.
- Clean full `TZ=Asia/Bangkok npm test` on the combined order-detail and call source tree (`a92d75f2`, exact tree `520f64f7`): EXIT 0; Native source 332/0, order state 70/0, stability 106/0, Merchant title 18/0, substitution 32/0, and post-test map QA 28/0. No Production writes.
- Exact-head Native Pilot run `40294781492` on remote checkpoint `adc08ab8ee8d885298425ec004e628c7ecaa43a3` is running; local/remote tree equality verified at `520f64f7`. Android SDK/Gradle wrapper are unavailable locally, so hosted JUnit/APKs/16 KB/emulator gates remain pending.
- **NEXT:** continue the Customer page/state audit from its pinned Web blueprint while Pilot runs; then inspect exact-head Pilot and fix only its first real failure. Merchant completed-order issue reporting remains a separate open detail gap.

## 2026-10-26 Merchant QueueGo customer call parity — DONE (source/full regression); exact Pilot running

- Web order detail invokes the existing QueueGo voice caller while an order is active. Native already has the shared authenticated voice controller and permission flow, but Merchant detail had no outgoing customer-call action.
- Bounded change: add the Web-labeled call action for nonterminal orders, request `RECORD_AUDIO` only on tap, and reuse the shared controller's existing `startOutgoing(auth, orderId, "customer")`. It disables during another active call and rechecks state after permission returns. No phone-number fallback, new signaling or backend authority.
- Focused Native source 332/0, shared voice UI and call SQL contracts PASS. Full `TZ=Asia/Bangkok npm test` passes on source tree `520f64f7`; exact-hosted Pilot `40294781492` is in progress on that same tree.
- **NEXT:** audit Customer page/state parity using the pinned current Web blueprint. Completed-order issue reporting remains a separate Web parity gap.

## 2026-10-26 Native Merchant order-detail source checkpoint — exact Pilot NEXT

- Current local Native successor commit: `517140355858fb74b2e9593004b5728a22b9ca19`, descendant of the verified local/remote integration checkpoint; last remote source tree was `5aa83efa7d8b2fac897319fb71b4e9e2dfeadae0`. The exact source diff is Merchant order-detail fields/presentation/tests plus the two parity matrices and continuation docs.
- Clean exact-tree regression: `TZ=Asia/Bangkok npm test` EXIT 0 on `51714035`; Native source 331/0, order state 70/0, three-app stability 106/0, Merchant title 18/0, substitution 32/0, and post-test map QA 28/0. No Production writes. Android SDK/Gradle wrapper are not installed locally.
- Exact hosted Native Pilot `40292468422` is still running on prior remote SHA `5aa83efa`; its full regression, POS/push/voice contracts, source integrity and asset checks passed. Its JUnit, APK, 16 KB and emulator gates are not yet complete and do not include this new Merchant detail source chunk.
- **NEXT:** fast-forward the current source tree to the existing remote Native successor using the GitHub connector, verify its commit tree byte-for-byte against local HEAD, and inspect the new exact-head Pilot. Fix only its first real failure. Then continue the P1 Customer audit and remaining Merchant detail call/report actions.

## 2026-10-26 Native three-app parity successor — exact-head Pilot NEXT

- Native branch `work-native-web-parity-20261026` is at `b66b6a3b1b2a83613ed9984a7dc8e900135f0b07`, a verified descendant of fetched remote successor `ed3a29ae` and based on current Web `main=7e036a0735cc49905e9b0a2434492470551a0c9d`. PR #729 map/navigation parity is preserved in the integrated ancestry. No open PR or Actions run exists for this exact HEAD; #710 remains an older open Rider review branch and is not the active successor.
- Exact local full regression: `TZ=Asia/Bangkok npm test` EXIT 0, including Native source 330/0, existing three-app stability, Customer/Merchant/Rider contracts, integration SQL gates and post-test map QA 28/0. No Production writes. The Grocery offline log line is an expected isolated transport path; its suite passed.
- Current code checkpoint includes Customer safe route recovery, Merchant route restoration/dashboard/order filters, and Rider earnings same-period retry. Source and local regression are complete for this checkpoint; hosted Kotlin/JUnit, three APK, 16 KB and emulator evidence are still pending.
- **NEXT:** record this checkpoint, fast-forward push `b66b6a3b` to the existing Native successor branch, inspect exact-head Native Pilot/RC runs, and repair only the first actual failure. While those run, continue Merchant order-detail Web parity and Customer/Rider non-overlapping source inventory. Authenticated visual, physical-device/GPS and Play release gates remain separate.

## 2026-10-26 Merchant Orders Web filter/search parity — DONE (source/full regression); Native Pilot pending

- Blueprint: current Web main 7e036a0735cc49905e9b0a2434492470551a0c9d `renderShopOrdersModern` provides six status filters, order/customer search, and explicit empty/no-result states. `qgmOrderStatus` maps backend statuses to shared user-facing labels.
- Root cause: Native showed every order with no search/filter controls; `merchantStatus` pas…36225 tokens truncated…d-next policy. Existing callers without financial evidence retain the prior travel-only behavior.
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
