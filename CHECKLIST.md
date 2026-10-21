Warning: truncated output (original token count: 38958)
Total output lines: 1005

# QueueGo Active Work Checklist

Last governance checkpoint: North Star product/release ledger on branch `work-queuego-north-star-governance-20261021` (documentation PR pending)

This checklist is the repository continuation ledger. Read `WORKFLOW.md` first.

## 2026-10-21 North Star recovery checkpoint

- Current verified `main`: `6669dc108137f8119c86d4de9709dacba7a301d7`.
- This checkpoint adds `PRODUCT_NORTH_STAR.md`, `FEATURE_GAP_MATRIX.md`, `RELEASE_GATE.md`, and `NEXT.md`; these are the mission, evidence matrix, launch gate and precise continuation pointer. Older snapshots below are historical evidence; fetch current main and the relevant newest successor before resuming.
- **POS cash in/out shift ledger — source/test IN PROGRESS, release BLOCKED.** Open PR #568; branch `work-pos-complete-next-20261021`; exact HEAD `0f9af0cbc91af0bf7d964c9c52665ceb4e19cb9a`, based on the verified latest main. Prior RC run `39617058579` passed at `119c68e`; updated exact-head RC run `39619648922` is IN PROGRESS. Focused POS tests (36/73/18) and full `npm test` passed on the updated tree in this session. Latest change adds transaction-scoped serialization for same-key retries.
- Production migration read-only check: history currently ends at `20261021133330_pos_owner_item_sales_report`; staged `20261021211709_pos_cash_register_movements` is absent. Do not present cash in/out as live. Review schema/ACL/concurrency and run the relevant release process before activation.
- Whole-platform status: NOT READY. Authenticated role E2E, current-device acceptance, printer/scanner/drawer validation, Android signing/Closed Testing, and other feature-specific gates remain separate; see `RELEASE_GATE.md`.
- NEXT: re-fetch main/open PRs and the newest POS descendant; inspect the staged migration against Production grants/RLS and concurrent retry behavior; then finish authorized cashier journey and migration gate. Continue other unblocked product rows by dependency without resetting or duplicating implementations.

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

- 2026-10-18 runtime blob verification: PR #413 and unified PR #417 have identical `rider/index.html` (`fd412052...`), `queuego-map-web.js` (`d48480fe...`) and `tests/rider-map.cjs` (`e9614e3b...`). #413 carries the newer corrected action regression test; no runtime merge/reimplementation is required.
- OWNER ACTION TO UNBLOCK RELEASE: approve/host the QueueGo Map runtime and provide physical Rider Web GPS/road evidence for the Buriram route/camera/reroute acceptance. Until then release remains BLOCKED despite source/test DONE.
- NEXT: no code work on Rider Web unless a new regression or newer runtime successor appears; continue to Passenger Ride source/test gate.

### 3. Rider Android navigation / QueueGo route authority
- Source/test status: DONE
- Release status: BLOCKED
- Active PR: #414
- Branch: `work-native-rider-queuego-route-authority-20261017`
- Current HEAD: `0360622b086ab4e1bd844cbe5310a7716d04dfa6`
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
- Fix commit: `c9b9c420fd…26958 tokens truncated…e: Admin Market Master preferred approved generic reuse art over the populated dedicated catalog_preview_image, while Merchant correctly used the clean catalog image. Thus Admin could still display the old commodity pictures despite all128 catalog previews being present.
- Change: Admin now prefers the same dedicated official catalog preview as Merchant; approved reuse art is only a fallback where the catalog preview is absent. Actual image errors show an accessible failure message.
- Test: workspace DOM21/0 proves official preview wins when both image fields exist, unsafe URLs remain rejected and broken-image state is visible; source97/83; full npm EXIT0. Exact hosted RC required before merge/Pages.
- NEXT: exact RC, merge after parent green, inspect actual deployed Admin image-priority bytes. Preserve image_status reuse approval and all source/RLS/data fields.


## 2026-10-20 Admin selected-shop products and stock inspection
- Status: IN PROGRESS; branch work-web-admin-shop-product-stock-read-20261020, successor of PR493/a5d4823b116fde71ba1789b58fa88411bab15673.
- Coverage gap: Admin shop detail exposed only one sample product, with no shop-specific product/stock browser. Existing Production SELECT policies already authorize Admin reads: products_admin_select and market_stock_owner_read; no new grant or RLS mutation is needed.
- Change: existing shop detail opens one read-only product/stock dialog for that selected shop. Uses50/51 ID keyset pages, server name search and same-page refresh; Market stock reads only visible product IDs (<=50). Market decimal stock/units/pack/minimum come from market_products; ordinary product stock remains products.stock. Missing values are shown as unknown.
- Safety: no POST/PATCH/DELETE, price/stock writes or owner impersonation. Text uses textContent, images require HTTPS, and close/search/account generations fence stale results. A session-storage actor change closes the view.
- Test: actual DOM/query runtime25/0 covers limits, selected shop and page stock IDs,4.5kg/2.25 pack, previous/next/refresh/search, unsafe image/text and close/account races; source98 scripts/84 references; full npm EXIT0. Exact hosted RC/mobile WebKit required before merge/Pages.
- NEXT: exact RC/merge/Pages and deployed bytes; continue the remaining Admin audit matrix. This closes the product/stock VIEW inventory gap without adding stock mutation authority.


## 2026-10-20 Admin GP mobile rate cards
- Status: IN PROGRESS; branch work-web-admin-gp-mobile-cards-20261020; newest main1b2240250686de5b32075ddf0d1044570b34a5e2 includes PR494.
- Evidence: supplied iPhone screenshot shows unseparated rate forms. The shipped qg-gp-rate-card defined only margin and inherited no card surface, border or padding. Date and numeric controls inherited automatic height.
- Change: add scoped white bordered cards and explicit44px date/numeric controls, preserving44px touch targets and existing rate save logic. Bump stylesheet URL to refresh cached clients.
- Validation: add whole-document mobile WebKit checks for real GP renderer/card surfaces, control heights and overflow at320/390/430px. Local full regression and exact hosted WebKit gate pending; physical iPhone not claimed.
- Deployment receipt: PR494 main1b224025 Pages39397596995 and RC39397597468 SUCCESS.
- NEXT: complete regression, exact hosted WebKit, merge and verify served CSS. Continue Native491 diagnostic CI separately; do not merge Web-only tree onto Native.


## 2026-10-20 Cross-stream verified checkpoint after GP mobile fix
- DONE (Web source/deployed): PR489 Rider presence keysets, PR490 page-scoped Market applicants, PR492 exact HEAD counts, PR493 official Market preview priority and PR494 selected-shop product/stock VIEW are merged. PR494 main1b224025 Pages39397596995 / RC39397597468 SUCCESS.
- DONE (GP mobile source/deployed): PR496 exactf8c3f8b6d327fed98845d6644088f898b2fb035a passed complete RC39399307537. Real whole-document mobile WebKit checks card surfaces,44px date/numeric controls and no horizontal overflow at320/390/430px. Main817fbe350acf4ba55a615251b43d49e633b77887 Pages39399766747 SUCCESS. Actual HTTPS200 HTML SHA256485ac0c4ac81853c5cd58f8cb05a11d94a410c932ca34b1f6e5768ac0e4a861c; interface.css SHA256f197f9ec9df50d7002dff16aa84ab3016448efada9dde965c8bd6f06f159ad6e match main. PR495 is superseded by496; no old implementation overlay.
- DONE (Native Market source/CI): PR491 exact64a8a2b689181d735a83699b20d0ff9a07231f20 passed Native39396673821 (JVM,3 APKs,16KB, Android launch/map/voice/system-kill recovery). Merged only into Native integrationbb9f41e6ccc99e9acd0e56744db050f4bf53182c, not Web main. Pilot APK artifact12251938112 / launch evidence12252428412. First run's Customer kill failure remains unexplained; diagnostics added without relaxing assertions.
- IN PROGRESS (financial source compatibility): Native PR497/fe3438a2046dcbd2b4f13f2fd5832d270990a129, treef56d790f394f2d8bcc13031311909eb93ad4e38a, restores credit/dispatch rule validation dropped by later Ride migration. Focused actual SQL and full clean local suite PASS; exact Native39399467533 running. Production NOT APPLIED. Preserve this Native successor; do not copy Web-only tree over native-android.
- BLOCKED (release/acceptance): real authenticated Admin/Customer/Merchant/Rider and physical iPhone/Android/GPS/road acceptance; signed AAB/Play; map HTTPS runtime hosting; full deployed migration/function reconciliation and approved payment-provider/credit-webhook/Express rollout. Source screens or emulator tests do not satisfy these. No fake balances/orders or service activation performed.
- NEXT: finish exact PR497 CI and Native integration39398702208/39398702254; merge only the green Native descendant. Continue unresolved Admin audit controls with authoritative policy/RPCs; do not enable unavailable financial/assignment controls. Whole mission remains IN PROGRESS.


## 2026-10-20 Admin source-freeze candidate
- OWNER SCOPE: no new Admin features after this checkpoint. Finish only correctness, security, performance, CI/Pages and existing-control defects; then move engineering focus to Android / Play Store.
- BASE: current main `0ce02788416f88965e4b0636c6fca727f7b0e45e`; branch `work-web-admin-freeze-shop-state-scope-20261020`.
- CLEANUP: removed dead `qgRefreshAdminShopStates()` legacy code. It had no callers/current markup target but still contained unbounded all-current-shop + all-open-state reads. Current shop open/close control remains `qtAdminToggleShop` with the existing selected-shop reads/mutation authority.
- PERFORMANCE REGRESSION: `tests/admin-performance.cjs` now forbids the removed refresher and both legacy full-table shop-state query strings. Admin full-hydrate legacy code remains unreachable for Admin because `qtHydrateDatabase()` returns through `qtAdminRefreshScoped()` before the non-Admin hydrate body.
- SECURITY RECHECK: Production public Admin RPC inventory has anon/PUBLIC EXECUTE denied for inspected Admin functions. Legacy SECURITY DEFINER functions are still generic-advisor findings, but inspected endpoints enforce active-Admin role/status through direct checks or `get_my_role()/is_active_admin()`.
- ROLLBACK-ONLY GUARD EVIDENCE: with a real active non-Admin auth subject, `admin_suspicious_orders()` returned zero rows and both `admin_delete_user()` / `queuego_admin_approve_shop()` denied with 42501. Transaction rolled back; no user/shop/order data changed.
- FREEZE BOUNDARY: Merchant/Rider Credit/Top-up backend rollout, Ride/Express controlled activation, worker/device-delivery evidence, physical GPS/map/browser acceptance, signed AAB and Play Console/Closed Testing are release gates outside the frozen Admin feature scope. Existing Admin surfaces must remain fail-closed where their authoritative backend is absent.
- NEXT: exact-head full RC + Admin mobile WebKit; merge only green; verify Pages/post-merge RC and served bytes. If green, mark current Admin source set FROZEN and move to Android Play Store readiness. Do not open new Admin feature work unless a regression/security defect is discovered.


## 2026-10-20 Parallel completion: Rider proof and Admin freeze
- DONE (Rider Web source merge): PR508 exact f1359784406cf6a625455fbcf213ac49bbc7de90 passed RC39420653591 and RC39420648835; merged as44c97c04384fcdb93be1801687a30c71bcd6a4e3. Camera-image normalization retains the Storage5MB cap; selected proof preview uses compact two-column image/retake controls.
- DONE (Admin source merge/hosting): PR507 exact9726fbdefff5226530e12cf283756ff49be0eb09 passed full RC39418117904 and RC39418123012 including mobile WebKit. Merge9bb6e436aee941e1223ae3d941b40c53d4c27f8c preserves508 and509. Pages39426694695 SUCCESS. Actual HTTPS200 Admin HTML SHA25623471ca18ac32835c0c2e0999846ae54c582142e22720e4b4ba51cfa194b60b9 matches that main; dead full-shop refresher absent. Postmerge RC39426694586 still running at this checkpoint.
- DONE (focused uploader runtime test): commit5a34149c exercises actual support uploader with safe PNG passthrough, HEIC, blank-MIME HEIF, oversized JPEG, source rejection, decode failure and Storage rejection. Combined actual proof DOM/upload suite65 checks/0 failures. Decode and transport are isolated substitutes; no physical iPhone or live upload is certified. Full local regression running before publication.
- IN PROGRESS: Native Rider latest descendant CI, shared release manifest cleanup, Credit/Ride/Express reconciliation, Map runtime transport and Catalog/Laundry checks run independently. Shared branches are not overwritten while other writers advance them.
- NEXT: complete local full suite and exact hosted CI for the uploader test successor, merge and verify Web bytes; record final Admin RC receipt. Continue newest Native descendant and each unblocked source lane. Physical GPS/authenticated-device, approved hosting/provider/Production release and signed AAB/Play gates remain separate.


## 2026-10-21 Admin final freeze
- Component: Admin Web
- HEAD: main `2ef2ac18b23a14b5ad8e79d6bc2b0abad3f999e4`; frozen Admin implementation merged via `9bb6e436aee941e1223ae3d941b40c53d4c27f8c` from exact source `9726fbdefff5226530e12cf283756ff49be0eb09`.
- Build/Test: exact source RC `39418123012` SUCCESS; post-merge RC `39426694586` job `118641612148` SUCCESS; Pages `39426694695` SUCCESS.
- Integrity: main is 9 commits ahead of the Admin merge checkpoint with no changes in `admin/`, `tests/admin*`, or `role-realtime.js`.
- Release blocker: none in Admin source. External gates remain for Credit/Top-up rollout, Ride/Express activation, authenticated/physical-device acceptance and Android/Play release.
- Status: DONE / SOURCE FROZEN. Do not reopen superseded Admin branches without a reproducible regression/security defect.
- NEXT: resume Android Customer/Merchant/Rider Google Play Closed Testing Release Gate from newest release Source of Truth.


## 2026-10-21 Merchant Home cleanup / unified credit successor
- Status: IN PROGRESS (publication); source change removes the latest-orders heading, cards and unused Home slice. Orders remain accessible through existing Orders navigation; no order data is deleted.
- Source: PR525 branch work-web-credit-unified-merchant-rider-20261021, descendant of main60d5fa5a via97ace8fb. Preserves unified Merchant/Rider balance and finance-only Merchant credit access.
- Evidence: merchant-boot9 and credit-web31 PASS; git diff --check PASS. Full local regression and exact-head CI are required before merge/publication.
- Native: newest credit PR526 already has no latest-orders Home section; no duplicate Native implementation required.
- NEXT: finish full regression, exact-head RC/mobile WebKit, merge PR525 and verify successful Pages deployment plus served Merchant HTML. Native credit PR526 retains its separate Android release gate.


## 2026-10-21 Merchant Home publication receipt
- Status: DONE (Web source/test/hosting). Source59c1039f78b4896159d4d7a99a300f7e5d06054a; merge ede4914e8bc9b9aa8e0546d1efd8a98defa146ee.
- Evidence: full local TZ=Asia/Bangkok npm test EXIT0 on exact source tree9a93abdf; exact-head RC39520610148 SUCCESS including Merchant mobile WebKit; Pages39520939348 SUCCESS.
- Live Merchant HTML SHA2567133a3540e69401b0fdad4cf19a1e9897d57de5181063039480362a30d01e0cb equals tested/merged bytes. Latest-orders section and Home credit mount are absent. Existing Orders navigation and revenue/finance credit access remain. Shared Merchant/Rider Web credit shows one usable balance.
- Native credit PR526 HEAD021caf67fc422a3219c07ce2b4fba5ba3423272c: CI39520235790 IN PROGRESS at APK build; completed credit/continuous-dispatch/passenger/route-bundle race and navigation-map-runtime jobs SUCCESS. Skipped jobs are not certified.
- NEXT: verify postmerge RC39520939484 and exact Native39520235790 result; preserve latest release successor. Authenticated physical device, signing/AAB, Play Console and backend/provider activation remain separate gates. Do not repeat Home or credit implementation.


## 2026-10-21 Correct Merchant header credit removal
- Owner clarification: remove only the large Home credit card; retain the compact usable-balance badge beside the notification bell. Previous PR525 removed that badge too broadly.
- Restore existing shared mountBadge in Merchant Home header, with one unified available balance and existing revenue/finance destination. Large credit card and latest-orders list remain removed. No ledger, schema or order change.
- NEXT: run credit/merchant/source checks and full RC, publish and verify actual served header mount.


## 2026-10-21 PromptPay LIVE mission / current checkpoint
- Scope: existing Merchant/Rider credit ledger only. Public Web parent52597021; backend source021caf67 (Native PR526), integrated with public Web asfcad9069. No wallet replacement or fake credit.
- Production: queuego-credit v2 deployed with boolean-only startup readiness telemetry; queuego-credit-webhook v1 deployed with raw-body HMAC and provider retrieve. Readiness observed environment_ready=true, live_key_configured=false, webhook_signing_configured=false, live_intake_enabled=false. Required settings: OMISE_SECRET_KEY (LIVE), OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED. Provider PromptPay LIVE capability/signing configuration remains unverified until those are supplied securely. No flags enabled and no real charge attempted.
- Production Admin Rider grant migration applied: same credit_accounts/credit_entries, active Admin/live-session check, integer satang, durable request UUID, account lock, actor/reason/audit. Rider Admin grants use existing cash balance because the original Rider account forbids promo balances; event source remains distinguishable in audit. No user balance was seeded or modified for testing. Anon/service ACL denied; rollback-only unauthorized call denied.
- Source tests PASS: Admin Rider20, Merchant38, Credit Edge82 (HMAC/raw-body/mode/currency/source/ref/metadata/recovery), Web37 (single balance/server pending recovery/duplicate click). Ledger59 and complete13-migration rollout chain PASS. These are isolated tests, not real bank settlement evidence.
- Web changes: same shared client; recover pending server UUID after storage loss, recheck pending via queuego-credit before authoritative summary refresh, PromptPay labels/amount/QR/status and Admin Rider form. Merchant compact header preserved; large card/latest orders stay removed.
- LIVE payment gate: BLOCKED on provider secrets/settings above. Feature flags remain off. Owner real Merchant/Rider bank payment, persistent post-login balance, and provider signed delivery are not certified.
- NEXT: finish exact Web full RC/mobile WebKit; publish and verify served bytes. Sync shared Native client only after Web source publication; build exact Native descendant. Keep payment LIVE BLOCKED until verified credentials, provider/signature/endpoints and actual successful paid charge evidence.


## 2026-10-21 Credit publication and Admin immediate refresh
- Web PR528 source22253875 merged cee5a58d; full local tests/RC39523018771/Pages39523292975 PASS. Actual shared credit and Admin credit files match tested served bytes.
- Root cause follow-up: qgAdminRpcRead caches account reads for5 seconds while direct mutation RPCs do not invalidate it. Clear the existing read cache after acknowledged grant/reversal, before reloading credit history; no financial mutation/authority changes. Rider durable-grant DOM5 and shared Credit Web37 PASS; full source RC required.
- Native current successor: PR529 HEAD2693396fa09e8432cf525a30c491f527ae53d3a5, strict descendant021caf67. Android sync uses same queuego-credit/ledger. CI39523534966 IN PROGRESS; credit, passenger, continuous-dispatch and stack SQL race jobs already SUCCESS. Do not use unpublished experimental public-Web/Native integrationfcad9069 as a source successor; no changes from that integration were published.
- Production readiness remains environment_ready=true/live_key_configured=false/webhook_signing_configured=false/live_intake_enabled=false; credit flags stay off. BLOCKED: OMISE_SECRET_KEY LIVE, OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED and provider LIVE PromptPay/signing configuration.
- NEXT: publish the immediate audit refresh fix after exact RC; finish Native39523534966 compile/runtime gates and record receipts. Real bank payment and signed provider delivery remain uncertified until secure provider setup.


## 2026-10-21 PromptPay final source/build receipt (LIVE payment still BLOCKED)
- Web runtime: PR528 source22253875 -> mergecee5a58d; PR530 sourceac9c58d3 -> mergeb91d0ea8058d5a6fe2e19eade3b9f9b7619281bf. Full local Web regression EXIT0; exact RC39523018771 and39523725062 SUCCESS; postmerge RC39524163429 SUCCESS. Pages39523292975 and39524163519 SUCCESS; shared credit/Admin served bytes verified identical, including post-grant/reversal cache invalidation.
- Android active successor: PR529 HEAD2693396fa09e8432cf525a30c491f527ae53d3a5/tree69a1fb85891f31330d26c7b68a00eee394ec7cfe. Full clean local pretest/test/posttest EXIT0. Exact Native39523534966 native-pilot SUCCESS: complete regression, JVM, three APKs, 16KB/no-WebView, real emulator launch/map/voice lifecycle. Navigation-map-runtime and four required SQL race jobs SUCCESS. Skipped Express/map-display jobs are not additional certification. Native remains separate from public Web main, with same payment backend/ledger.
- Production: queuego-credit v2 and signed queuego-credit-webhook v1 deployed. Admin Rider grant RPC installed with active-Admin/live-session and immutable ledger audit; anon/service EXECUTE denied. No real user grant or bank payment was executed for evidence.
- Actual readiness remains environment_ready=true, live_key_configured=false, webhook_signing_configured=false, live_intake_enabled=false. Merchant/Rider feature flags both false. No fake key, QR, seed money, client ledger write or payment enablement.
- Browser acceptance: actual Merchant finance URL renders login, with no authenticated Merchant session available. No real-account topup, bank settlement, provider webhook delivery or post-login persistence certified.
- Mission: BLOCKED (payment-live only), NOT DONE. Required settings: OMISE_SECRET_KEY LIVE, OMISE_WEBHOOK_SECRET, QUEUEGO_CREDIT_LIVE_ENABLED and Omise LIVE PromptPay/signing configuration.
- NEXT: securely configure real LIVE provider/signing secrets; verify provider capabilities and dynamic webhook endpoint https://pkypiqhlrmzocysgeqew.supabase.co/functions/v1/queuego-credit-webhook; recheck readiness, then activate existing flags/intake and perform Owner Merchant and Rider paid20+ THB tests, duplicate/retry checks, refresh and logout/login. Do not duplicate or restart completed code.


## 2026-10-21 Verified existing Omise key is TEST (not absent)
- Owner confirmed Omise account already exists. Successful secure GitHub sign-in to Supabase verified the existing OMISE_SECRET_KEY and OMISE_PUBLIC_KEY entries were configured on11 October. Do not ask Owner to open/register Omise again.
- Root cause verified by boolean/classification-only server telemetry at2026-10-21T01:09:23Z: omise_secret_present=true, omise_secret_kind=test, outer_whitespace=false, wrapped_in_quotes=false. No secret values read, printed or copied. Earlier wording that the Omise key was absent was incorrect; the installed key is TEST.
- queuego-credit v3 logs only classification/presence flags, with unchanged authentication/payment gates. Intake and both credit feature flags remain off. No live charge or fake QR created.
- Secure Omise sign-in was attempted once; provider returned 'Request could not be completed due to an internal error'. No repeated credential submission or inferred account rejection. Automated sign-in stopped.
- NEXT: Owner completes Omise sign-in through the prepared secure browser/handoff, then configure real LIVE secret and webhook signing secret in existing Supabase project without sending values in chat; verify provider readiness/capability and signed endpoint before enabling intake/flags. All prior Web/Android green receipts remain valid for their exact source scope.


## 2026-10-21 Admin unified workspace continuation
- Owner reopens Admin scope for unified presentation and complete real-system access. Base main1f6bacbfc4b7588f5bf9ee50827483061ea928f7 contains final-freeze and visible-credit successors (verified git ancestry); no older branch overlay. Backup: backup/admin-before-unified-workspace-20261021.
- IN PROGRESS: grouped existing navigation into providers, services and finance/system; all existing destinations remain once in each menu. Shared light sidebar, readable type scale, standard page titles and44px controls reuse the existing shell and route authorities.
- Existing Laundry/Express pages read queuego_feature_enabled with p_feature only (server default time), show enabled/disabled/unknown separately and preserve paged history even when availability cannot be checked. No feature flags or balances changed.
- Production READ-ONLY: Laundry enabled=true, one active hub. Function signature includes p_at DEFAULT now(); previous main fixes avoid explicit null time. Six inspected Admin dispatch/count/notification/credit RPCs deny anon EXECUTE and allow authenticated (role checks remain server-side). This is not authenticated acceptance evidence.
- Test-harness repair: merchant-routing still expected removed revenue explanatory copy; current main uses automatic GP-credit copy. Updated the assertion to current text; no Merchant runtime changed.
- NEXT: full regression, hosted WebKit at320/390/768/1440, exact-head CI, merge then Pages byte verification. Local WebKit archive download truncated/503; hosted CI required. Authenticated real-account read/save/reopen and physical acceptance remain BLOCKED without an authorized Admin browser session. Payment provider, Ride/Express activation and physical release gates remain separate; do not claim whole-system done.


## 2026-10-21 QueueGo Web design language (PR #566)
- Source/test status: DONE. PR #566 merged to main as `beac57b9c66972fcaeb4253e2c05116c7617b30f`.
- Branch checkpoint: `29a7a1f9004e25fdd5112e4b791f32f64d0e5345`; exact-head RC `39614031789` SUCCESS, all15 steps completed, including full regression and mobile WebKit.
- Scope: QueueGo wordmark Queue dark / Go red across Customer, Merchant, Rider and Admin. No Production data, schema or business-flow changes.
- Web deployment: Pages build/deploy run `39614671640` SUCCESS on merge commit. Main Customer asset blob `f7c4082c0d211a93df003ae3473fac181e6178ce` has the tested singular cache keys; Admin CSS contains the shared wordmark colors.
- Owner visual acceptance: BLOCKED until Owner opens the deployed site in iPhone Safari; this environment could not read the live Pages response to independently compare served bytes.
- NEXT: Owner reviews QueueGo in iPhone Safari. After review, record any requested visual corrections; only then decide on Android sync.
