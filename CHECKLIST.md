# QueueGo Active Work Checklist

Last governance checkpoint: `WORKFLOW.md` commit `9439569c15f72568b3398a1072f6d2285c8e7ce7`

This checklist is the repository continuation ledger. Read `WORKFLOW.md` first.

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
