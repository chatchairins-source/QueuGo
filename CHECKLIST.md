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

- Exact-head QueueGo Map Engine Buriram run `39083016044` on `e8446b540566d012d83acf8520578bdbd4f16ce0`: SUCCESS. Dependencies/regression, pinned OSM/vector package, normalized database/routing tiles, real graph/restriction evidence, browser-with-external-requests-blocked, offline package/rollback, candidate offline container, loopback staging contract and release-gate report all completed successfully.
- Source/test conclusion: DONE for the current #414 successor. This does not certify physical GPS/road behavior, real-device navigation continuity, signed release or Production/public hosting.
- Release blockers: physical Rider device/GPS/road certification in Buriram, exact-device background/foreground navigation acceptance, and the parent QueueGo Map runtime/offline release gates.
- OWNER ACTION TO UNBLOCK RELEASE: install the certified Rider build for this exact successor on a real Android device and perform the recorded Buriram GPS/road/navigation acceptance route; provide physical evidence/logs. Production/public Map hosting remains separately gated.
- NEXT: continue to the next unblocked source/test workstream in dependency order; do not modify #414 again unless a newer successor or a new regression appears.

- Successor `43325ab6899b5d4dc08e67215abf7eadd03694ad` expanded Native Pilot triggers to shared Web sources. Exact-head Native Pilot run `39083523167` then failed first in `tests/native-release-gate.cjs` at `Native pilot CI must pin the observed Longdo SDK SHA-256`.
- Root cause: workflow commit `0b181932df2cd774b93ae8d533b81f10524193e6` intentionally removed the retired Longdo Android SDK download/SHA gate when Native Pilot switched to QueueGo MapLibre, but the release regression from 2026-10-10 still required the deleted Longdo artifact.
- Fix commit: `575a10a1e1d05e6f437dbfe7ca5002f0e9967d3a` updates only the release test: it requires exact active MapLibre `13.5.3`, requires QueueGo MapLibre display/lifecycle CI gates, and forbids reintroducing the retired Longdo SDK download/integrity path.
- NEXT: obtain exact-head Native Pilot and Map Engine results for `575a10a1e1d05e6f437dbfe7ca5002f0e9967d3a`; if red, fix only the new first failure. Do not resume Passenger Ride until #414 exact-head gates are green.

- Exact-head Native Pilot run `39083719573` on `575a10a1e1d05e6f437dbfe7ca5002f0e9967d3a` advanced past the MapLibre release-gate regression, then failed Kotlin compilation. The first chronological compile failure was `RiderNavigationMap.kt:465` because MapLibre `cameraPosition.target` is nullable and the code dereferenced `latitude/longitude` directly.
- Fix commit: `0360622b086ab4e1bd844cbe5310a7716d04dfa6` changes only `publishCamera()` to return when `camera.target` is null before constructing `NavigationMapPoint`. Later Customer/Merchant compile failures from the same run are intentionally deferred until the next exact-head first failure.
- NEXT: obtain exact-head Native Pilot and Map Engine results for `0360622b086ab4e1bd844cbe5310a7716d04dfa6`; if red, fix only the new first failure. Do not resume Passenger Ride until #414 exact-head gates are green.

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

## 2026-10-18 live Rider Web camera / one-photo repair
- Stream: owner-authorized website camera and single-proof correction, deployment branch `work-web-rider-camera-single-proof-20261018`, base current public main `ded5dab4dc663c389810c007764d92cbbb3ce369`. Bounded delta comes from newest strict source successor of73c458c5; no old PR reset/revert and no staged backend/service/cap changes are published for this repair.
- Evidence: owner road recording shows camera reversals and anchor drift. Source regression reproduces missing heading becoming0°/north. Current public main lacked newer direction/overlap/continuity and repeated off-route guards. Fix deploys those existing guards, preserves unknown optional GPS values, rejects stale/duplicate callbacks and shares marker/camera timing.
- Evidence: real production-script DOM before-fix test fails because cancelling retake clears the selected proof. Same-order reentry previously rebuilt the page; Web retained obsolete separate cash/collection screens. Fix makes one pickup photo+one action and one delivery photo+one completion action, with cash shown inline. Back/reopen/cancel/retry retains the File/preview/uploaded path; double tap/session scope guarded; logout releases proof memory. Backend proof requirement and audited RPCs remain unchanged.
- Local focused tests PASS: camera65, actual proof DOM34, GPS9, live-provider route/cache94, UI102, runtime83, source88 scripts/73 local refs. Full clean main-deployment-tree npm regression REQUIRED before publish. New tests are part of npm test. Native source already has one photo/one simple action/no slider and passed inherited contracts; not rebuilt/replaced.
- Build marker: `rider-20261018-camera-proof1`; cache-bust shared script `queuego-map-web.js?v=20261018-12`.
- STATUS: IN PROGRESS pending full test + Pages deploy. NEXT: commit tested Web checkpoint, full npm test, publish only bounded Web tree to latest main, inspect RC + Pages build, fetch public HTML/JS and verify exact build/one-proof/missing-heading guards. Do not call deployed until public bytes verify.
- Existing live route transport remains unchanged until separate QueueGo Map HTTPS release gate. This repair does not publish a nonexistent /map-api endpoint, enable Route Bundle3–5, change Production DB, create test orders or add a dispatcher/payment/proof system. Physical Safari/GPS/camera road acceptance remains BLOCKED until owner tests the new build; source DOM is not physical certification.

## 2026-10-18 Rider Web deployment VERIFIED
- Stream: camera course/GPS/puck + single-photo pickup/delivery. Runtime Web HEAD `f8986c1b8c9f9d86cf43c20fc1eded18b81f37e6`, tested tree `67b1058c075bfaccfda44ef1c3fab8f00f6a08fa`; Pages1432/run39129222693 SUCCESS; RC1869/run39129222944 SUCCESS; Chat Retention134/run39129222931 SUCCESS. Full local clean-tree npm test EXIT0 before publication.
- Public URL verified: https://chatchairins-source.github.io/QueuGo/rider/?v=rider-20261018-camera-proof1 . HTML returns the exact build tag, script20261018-12 and proof resume helper; obsolete cash-confirm function is absent. Public shared-camera JS bytes equal the tested file, SHA256 cef3140b63cbe3f8b332dfa2c49c79b7146d65f9c861366630e17d66628641d1.
- Exact production-script parity verified between live Web projection and source PR436 for proof workflow, GPS callback and speed/marker functions. Strict source branch `work-native-route-bundle-web-camera-20261018` at `d32fe66dd87e934133352e4e287805c6c4905996`; Native3413/run39129128696 and Map155/run39129128721 are its separate exact gates. Main and staged Native history are divergent; do not full-merge unrelated older main implementations over this strict successor. Preserve the bounded Web projection and both lineages.
- STATUS: SOURCE DONE / WEB CI DONE / WEB DEPLOYED. This documentation-only receipt changes no public runtime. Physical Safari camera/GPS acceptance remains BLOCKED until owner confirms the new build. No database, live cap, wallet, proof authority or staged service was activated.
- OWNER ACTION REQUIRED: on iPhone Safari open the versioned URL; during an authorized order take one pickup photo then press receive, and one delivery photo then press complete. Cancelling retake, back/reopen and retry must retain preview without recapture. Record exact build/order/device and screen recording; for GPS compare on a safe real route with observer. Do not substitute this source test for road certification.
- NEXT: inspect this docs-only receipt's automatic RC/Pages run; no further runtime fix is required unless real evidence shows a remaining fault. Continue staged source only from PR436/newest descendant after exact CI, never by deploying its gated backend to static Pages.

## 2026-10-18 bounded remaining-route HUD correction
- Camera route reuse must not freeze the distance/ETA card. Store existing authoritative route distance/time with its cache; read the accepted route match on each fresh GPS fix and render remaining road distance plus explicitly approximate ETA. Unknown, opposite, off-route or inaccurate fixes do not fabricate progress. Geometry, route authority, quote/pricing/payout and business states unchanged.
- Tests: production helper14/0, GPS9/0, proof DOM34/0, camera65/0 and existing Rider route/source suites PASS. Build rider-20261018-camera-proof2; one-photo fix retained. Full clean-tree regression REQUIRED before this runtime checkpoint is published.
- STATUS: IN PROGRESS until exact CI/public bytes verify. NEXT: publish tested bounded progress delta and verify build2 on the public Rider URL; physical road/camera acceptance remains separate.


## 2026-10-18 owner video 20:19: bold navigation route
- Stream: Rider Web/Android route visibility. Branch: main; verified parent HEAD `bad7dcbae235c86a7185efa7f7acabaf3fc25d5b`. Source parent exact Native3414/run39129868715 and Map156/run39129868717 SUCCESS; live parent RC1871/run39129856790 and Pages1434/run39129856588 SUCCESS. Local backup backup/route-stroke-before-20261018 preserves source parent.
- Evidence: owner real iPhone Safari video20:19 shows a thin red stroke on broad pale roads; existing Web uses6px with no casing, Native active8/future6. This bounded visual revision uses Web12px red/16px white casing and Android active12/future8 with4 wider white casing, same authoritative geometry and cleanup owner. Build rider-20261018-route-bold1. No route engine/camera/GPS/proof/backend/pricing/cap change.
- TEST: focused source Rider121/0, live Rider96/0, camera65/0, proof DOM34/0, progress14/0, source integrity PASS; Native road-display/navigation/marker/source contracts PASS. Runtime confirms shared casing/fill geometry and both clean up during reroute/dispose. Full clean-tree regression and exact successor CI REQUIRED.
- STATUS: IN PROGRESS. NEXT: run relevant/full checks, publish bounded Web for owner review and run exact successor Native/Map gates. Physical turn/GPS/camera and visual acceptance remain BLOCKED; see existing owner account/device/action/evidence table. Route cap remains2.


## 2026-10-18 route disappearance / puck alignment / direction arrows
- Stream: Rider Web and Android; branch main; verified parent HEAD `e8d3716910d08a804c978bc738ab9ec021f0aa70`, source Native3415/run39131732532 and Map157/run39131732523 SUCCESS, live RC1872/run39131720597 and Pages1435/run39131720132 SUCCESS. Backup backup/route-arrow-before-20261018.
- Real owner20:43 recording shows no active route initially, then a distance badge without visible red line. Actual runtime reproduction on parent fails: adding casing starts source loading; isStyleLoaded=false defers red fill to an already-fired load event. Fix existing line helper with owned style.load replay and immediate idempotent application; removal cancels pending replay. Direction chevrons use a local canvas glyph, no remote glyph/font dependency.
- Presentation trims only the displayed route to the interpolated puck; full authoritative road geometry/ETA/reroute remains unchanged. Web confidence-bounded road alignment requires known accuracy<=35m, match<=12m and no opposite heading; camera/puck use the same presentation point while raw GPS stays untouched. Native reuses existing signal/hybrid snap authority and trim helper; active route head moves with existing puck animator, white direction chevrons share route cleanup. No second engine/dispatcher/GPS service or backend/cap/proof/payment change. Build rider-20261018-route-arrow1 / map asset20261018-13.
- TEST: parent red-fill runtime FAIL reproduced. New actual renderer runtime22/0, camera65/0, Rider source121/live96, GPS9/0, progress14/0, one-photo DOM34/0 and Native source/navigation/road contracts PASS; full clean regression and exact successor CI REQUIRED.
- STATUS: IN PROGRESS. NEXT: full regression, publish tested Web first, verify public HTML/JS and exact source CI. BLOCKER: physical acceptance remains required. OWNER ACTION REQUIRED: approved Rider on real iPhone Safari/Android opens route-arrow1 during approved active route; observer records device/OS/build/time/order, route line at animated puck, direction through turns, parallel-road/off-route recovery and background/foreground. Do not fabricate a route without a destination or activate cap3-5.


## 2026-10-18 shared Rider heading follow
- Stream Rider Web/Android camera. Parent HEAD `b00c575794e4aa1b968da97596933f0e6532d293`; source branch work-native-route-bundle-web-camera-20261018 / live main. Previous route visibility/alignment/arrows checkpoint passed full local regression; its exact CI may be superseded by this requested camera successor.
- Owner requests camera follow Rider arrow. Existing Web camera uses road look-ahead yaw while puck smooths GPS separately. Use one cached smoothed heading per accepted fix for camera and puck; marker rotation interpolates the same shortest angle/easing. Gesture/overview ownership, unknown-heading and opposite-road guards preserved. Native FOLLOW explicitly prefers existing filtered signal/puck course while retaining fallback/quality gates and existing camera policy; added actual Kotlin policy test. Raw GPS/route authority unchanged. Build route-arrow2 / map asset20261018-14.
- TEST: renderer runtime23/0, camera65/0, GPS9/0, Rider source121/live96, proof34/0 and Native navigation/road/source contracts PASS; new Kotlin shared-course case requires CI. Full clean regression/exact HEAD Native/Map/RC/Pages REQUIRED. STATUS IN PROGRESS. NEXT publish tested successor and verify public bytes plus exact CI; physical road/heading/parallel-road/foreground acceptance remains BLOCKED using owner action table above.


## 2026-10-18 completed/multipart route overlay cleanup
- Stream Rider Web renderer; branch main, parent HEAD 2f778b6f85e312d8bea07ea94c2860d5dc76a9e5. Parent full local regression PASS; public arrow2 HTML/JS byte-match verified; RC1874/run39134784093 and Pages1437/run39134784706 SUCCESS. Native3417/run39134801387 and Map159/run39134801473 were still pending at review.
- Actual helper replay at the route endpoint reproduces a stale red/casing tail: zero remaining lines skipped the update loop without deleting owned layers. Multipart render copies could also leave obsolete parts. Reconcile exact owned IDs on each valid render, delete exhausted parts, retain only the matched remainder and subsequent authoritative parts. Only first part is anchored to the animated puck; later parts stay untouched. No route/heading/financial/GPS authority change.
- TEST: endpoint before-fix assertion FAIL (layer remains true); renderer now29/0 incl exhausted cleanup, no jitter regrowth and multipart retention. Camera/Rider/GPS/proof + full regression and exact successor CI REQUIRED. STATUS IN PROGRESS. Build route-arrow3 / asset20261018-15. NEXT publish tested bounded renderer repair and follow exact current CI; physical acceptance still BLOCKED.


## 2026-10-19 public Web places projection
- Stream business places Web; live main parentf836f05da7a3aa5465f9648e7d83997e32fe406b, source strict successor PR437/data29f135a3. Pinned real OSM catalog3079 records/1284 business-service categories, Oct15 snapshot, original identity/source/ODbL/known metadata retained. No imported business becomes a merchant or orderable.
- Project only shared Web helper, exact catalog, picker/search hooks and existing Map-view local-first search/nearby onto current live Web. Existing live route transport and newer camera/proof fixes retained; do not copy staged backend/hosting configuration.
- TEST catalog24643/search13/DOM7/camera65/lifecycle28 PASS; full clean regression and exact main RC/Pages REQUIRED. STATUS IN PROGRESS. NEXT publish tested Web and verify local search/place dots/labels/details; then continue native shared asset/picker. Physical entrance/contact freshness remains BLOCKED, no Production DB/provider/cap activation.

## 2026-10-19 Rider Home Web review projection
- Based on current public main4b3915dec74b20c4e592b92f4a1e0c0cb3630873, preserving current route transport and all newer real-place assets. UI-only projection of source PR438: one Ready switch, no top duplicate/large buttons/stats, calmer readable profile/settings rows, normal idle camera, red existing puck unchanged.
- Switch uses existing rider_profiles.metadata online/available; writes serialized/acknowledged/session-fenced, late polling responses fenced after writes. No approval bypass or new availability schema; existing active-work restriction retained.
- Full TZ=Asia/Bangkok npm pre/test/post exit0. Rider runtime83/map96 and actual projected Home DOM27 PASS (isolated, no live account/order writes). Build rider-20261019-ready1. Native candidate/build evidence tracked separately in PR438.
- Web review deployment requested by owner. Physical/authenticated switch/dispatch/reconnect acceptance, screenshots and Web preference parity remain pending; no release/DONE claim. No staged Native backend/schema/config/financial deployment.

## 2026-10-19 Rider Web My Destination settings review
- Current main baseline e37dc61cd37ec6f24460d69f12dd17b8d2ba72e3; source successor187ac19d0f7acdd7e3633fda8831bba77930dd90/tree536479b995bdaf7e4157e1e7e9e0615e3cc5c960. Existing Home Ready card, red marker and live routing transport preserved.
- Profile Settings adds My Destination via existing installed destination read/write RPCs and shared map/search/GPS helpers. Saved preferences load from backend; only matching acknowledgement or confirmed lost-response reconciliation shows saved. No new backend/schema/availability/dispatch source or real account/order write in QA.
- Actual Settings DOM36 PASS covers read/write/duplicate-submit/late response/account fencing/invalid coordinates/GPS disposal. Full TZ=Asia/Bangkok npm pre/test/post EXIT0 on Web projection; same source full suite EXIT0. Production read-only ACL audit confirms anon execute denied, authenticated wrappers call existing live-session/active-Rider guards.
- Native9e actual three APK/JVM/16KB/full source and five SQL races PASS; containing runtime final result still required. Public Web review activation is this bounded projection only, with existing map transport retained. Authenticated destination save/reopen and phone visual/GPS checks remain BLOCKED by missing usable Rider browser session/physical device; no credential bypass or fabricated Home screenshot.


## 2026-10-19 — Market Master Catalog successor v2 (PR #441)
- Branch: `work-market-master-catalog-successor-v2-20261019`
- Base: latest main `fa0b7ac941311772b24af8af6565dde7ed9a3f11`; PR #440 is superseded and closed
- Recovered the existing Market catalog work from `work-market-master-catalog-20261015` onto a clean descendant of current main; the stale branch itself was not reused.
- Added source-backed Thai Market master identities, authenticated Merchant catalog browse/add flow, idempotent add receipts, GP-derived Delivery preview, separate manual add, and pending-Merchant access for catalog preparation.
- Staged new migration as `20261019114500_market_master_catalog.sql`; no Production apply in this checkpoint.
- Source contract gate: `tests/market-master-catalog.cjs`; Merchant DOM flow gate: `tests/market-master-catalog-ui.cjs` (browse/category/image-status/1-2-3 kg/add/reload/no double add); exact-head full CI still required.

- Fresh-source expansion: added 33 current public product facts from Makro PRO and Lotus's covering pork, chicken, duck, beef, mushrooms and common vegetables. Retail prices are intentionally not imported.
- Weight-selling UX: catalog items sold by kilogram now expose 1 kg / 2 kg / 3 kg quick presets while keeping custom quantity input.

- Market catalog source coverage now: 25 starter + 33 fresh Makro/Lotus + 70 seafood/fruit/veg = 128 sourced catalog identities.
- Image-review coverage: 128/128 items have a source/review record; 121 have verified direct product-image candidates, 7 retain verified product/source pages pending an exact reusable image, and 0 remain family-reference-only. Approved Production images remain 0 until reuse-rights evidence is documented; no retailer art is published as QueueGo-owned.
- Added sourced กุ้งแชบ๊วย and ปูทะเล variants; phase-2 migration `20261019150000_market_phase2_chaebow_mudcrab.sql` stages facts only, no retail price/stock.
- Code checkpoint before this checklist update: `a5c758e4174d5d9587f0629e5948c3b3b03708d3`. Fixed the first exact-CI source-contract failure (invalid decimal slug), retained successful catalog-add authoritative reload/no-double-add behavior, and continued exact image-candidate verification.
- NEXT: exact-head CI on this successor; fix the first failure only. Continue resolving the 7 remaining source-page-only images, then authenticated/physical Merchant Web acceptance before any Production apply.



## 2026-10-19 — Market Catalog image/source checkpoint (PR #441)
- Branch: `work-market-master-catalog-successor-v2-20261019`; current code checkpoint before this checklist update: `945bbe4ad54e698786b10777c516a8c4862cf5a6`; base main: `fa0b7ac941311772b24af8af6565dde7ed9a3f11`.
- Catalog coverage: 128/128 sourced identities with direct image evidence. Approved open-license/public-domain images: 28; retailer/direct candidates still rights-pending: 100; source-page-only: 0; family-reference-only: 0.
- Approved batches now include commodity staples plus exact Nam Dok Mai mango, Red Lady papaya, Monthong durian and Shine Muscat; Merchant renders approved image attribution and never renders rights-pending retailer art as approved.
- Phase 2 remains 70 seafood/fruit/vegetable identities (ปลา 13, กุ้ง 8, ปู 7, หอย 9, ปลาหมึก 4, ผลไม้ 16, ผักสด 13); 1/2/3 kg presets + custom quantity retained. No Production migration/apply performed.
- TEST: exact-head RC for `945bbe4...` pending GitHub scheduling. NEXT: if RC fails, fix first exact failure only; if green, continue open-license replacements for the remaining 100 rights-pending images and authenticated/physical Merchant Web acceptance before Production.


## 2026-10-19 — Market Catalog image batch 9 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 9 test checkpoint: `753adb4fe41bf3fb89caf30f0695fbd92b6b1939`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased from 33 to 46, rights-pending retailer candidates decreased from 95 to 82; source-page-only 0, family-reference-only 0.
- Batch 9 adds cabbage (2 catalog identities), pork belly, raw chicken drumstick, eggs (2 sizes), enoki (2 identities), dried shiitake (2 sizes), tomato, long bean and mackerel using Commons licensing/attribution evidence.
- Staged migration: `20261019174500_market_open_license_images_batch9.sql`; no Production apply. Merchant approved-image attribution rendering remains required.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 82 rights-pending images, then authenticated/physical Merchant Web acceptance before Production.


## 2026-10-19 — Market Catalog image batch 10 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 10 test checkpoint: `d51d1bce8bcb2f6a0c4591870c8d6318caf773c7`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 54, rights-pending retailer candidates reduced to 74; source-page-only 0, family-reference-only 0.
- Batch 10 approves choy sum (2 identities), peeled garlic, banana bunch, watermelon, orange, pineapple and dragon fruit with Commons source/license evidence.
- Staged migration: `20261019180000_market_open_license_images_batch10.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 74 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 11 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 11 test checkpoint: `17042a52251c48f9850cf6d562df018fd6f1a1f8`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 59, rights-pending retailer candidates reduced to 69; source-page-only 0, family-reference-only 0.
- Batch 11 approves fresh shiitake, white shimeji, beef shank, pork loin and fresh lemongrass with Commons source/license evidence.
- Staged migration: `20261019181500_market_open_license_images_batch11.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 69 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 12 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 12 test checkpoint: `7bcd9b3f44c0a02927e5f3f2780de3ff3dca44dc`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 64, rights-pending retailer candidates reduced to 64; source-page-only 0, family-reference-only 0.
- Batch 12 approves Nile tilapia, snakehead, giant river prawn, whole barramundi/seabass and blood cockle using verified Commons license evidence.
- Staged migration: `20261019183000_market_open_license_images_batch12.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue open-license replacements for the remaining 64 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 13 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 13 test checkpoint: `3e717b0122b387910eb05ce1f991efa202e89ea4`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 67, rights-pending retailer candidates reduced to 61; source-page-only 0, family-reference-only 0.
- Batch 13 approves raw pork shoulder, raw bone-in beef short ribs and raw beef round/top-round for the matching shoulder/rib/hip catalog identities; cooked pork-shoulder/tenderloin candidates were explicitly rejected as misleading.
- Staged migration: `20261019184500_market_open_license_images_batch13.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact raw/open-license replacements for the remaining 61 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 14 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 14 test checkpoint: `dea3647634cf21b65e882b6d25c3aac653094e76`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 75, rights-pending retailer candidates reduced to 53; source-page-only 0, family-reference-only 0.
- Batch 14 approves kale (2 catalog identities), celery, half pumpkin, key lime, lychee, rambutan and cantaloupe using verified Commons licensing/attribution evidence.
- Staged migration: `20261019190000_market_open_license_images_batch14.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 53 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 15 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 15 test checkpoint: `5c8881a28733d35d4cda77b84f9b415a4c92be86`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 81, rights-pending retailer candidates reduced to 47; source-page-only 0, family-reference-only 0.
- Batch 15 approves king oyster mushroom, whole king mackerel, whole white shrimp, frozen squid rings, chilled squid tentacles and whole blue crab using verified Commons license/attribution evidence.
- Staged migration: `20261019191500_market_open_license_images_batch15.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 47 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 16 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 16 test checkpoint: `069e3c3eb61b5fcb96d85f91a030cf7462f8cd66`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 84, rights-pending retailer candidates reduced to 44; source-page-only 0, family-reference-only 0.
- Batch 16 approves raw chicken leg quarter, sliced catfish and raw soft-shell crab on ice with verified Commons license/attribution evidence.
- Staged migration: `20261019193000_market_open_license_images_batch16.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 44 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market Catalog image batch 17 checkpoint
- Branch: `work-market-master-catalog-successor-v2-20261019`; batch 17 test checkpoint: `1e6dbe80898e6b9b581dc6401622f09c4abe23d1`.
- Image coverage: 128/128 catalog identities retain direct image evidence; approved open-license/public-domain images increased to 88, rights-pending retailer candidates reduced to 40; source-page-only 0, family-reference-only 0.
- Batch 17 approves dried bamboo shoots, fresh tofu skin, textured vegetable protein and peeled ginkgo nuts using verified Commons licensing/attribution evidence.
- Staged migration: `20261019194500_market_open_license_images_batch17.sql`; no Production apply.
- NEXT: exact-head RC; fix first failure only. If green, continue exact open-license replacements for the remaining 40 rights-pending images and Merchant Web authenticated/physical acceptance.


## 2026-10-19 — Market image add-form verification
- IN PROGRESS; branch `work-market-master-catalog-successor-v2-20261019`; base `c9c7c040917867e86978e975dcf7ba87ec3a8ddc`; local source/test commit `6cfb031b3214f717c4b5c046dccea35b91dbef85`.
- Reused image/credit/status renderer for list and add form, accessible broken-image feedback; versioned assets. Market data and DOM tests now execute in npm test/RC.
- TEST: full npm test EXIT0 on pre-batch17 UI tree; focused source integrity PASS (90 scripts/77 refs), catalog contracts PASS, DOM 29 checks PASS. Exact successor CI required.
- Counts inherited from batch17: 128 items; 88 approved images; 40 rights-pending direct candidates. No Production apply or real Merchant save certified.
- NEXT: exact successor CI; remaining image rights, shared manual editor weight presets and authenticated Merchant Web acceptance.


## 2026-10-19 — Shared manual/catalog Market weight presets
- IN PROGRESS; branch `work-market-master-catalog-successor-v2-20261019`; published predecessor `840771cdb6b9b23b1665800affd4559930bc9963`; local tested source `53097a3e`.
- One shared implementation serves manual add/edit and catalog add: 1/2/3kg updates unit, pack and kg weight; custom quantities remain editable, gram/hectogram packs synchronize kg weight. Asset versions updated together. Existing add receipts and source image rights guards preserved.
- TEST: full npm test EXIT0 on integrated batch17 source plus weight changes; focused DOM 42 checks PASS, catalog data/image/category/access contracts PASS, source integrity PASS. Exact successor CI still required.
- Catalog: 128 sourced identities; 88 approved images; 40 rights-pending direct candidates; Phase2 70 identities retained. No Production apply, authenticated Merchant save or physical-device certification claimed.
- NEXT: exact successor CI, remaining image rights; authenticated Merchant browse/filter/add/custom weight/shop-list acceptance before release. No need to repeat already-tested catalog/category implementation.


## 2026-10-19 — Actual SQL Market release gate
- IN PROGRESS; branch `work-market-master-catalog-successor-v2-20261019`; predecessor `83b7bac11b3478ad19d496acf774fc2d5c206f10` passed exact RC2008/run39233210924, including mobile WebKit. Local source commit `3336a6d1`.
- Actual migration execution found malformed rambutan slug in the staged phase2 seed. Production migration history confirms only Grocery grant `20261019051041` applied today, not these Market migrations. Corrected the unapplied seed to the canonical JSON/image slug; no applied history edited.
- Actual live read-only function audit confirms POS helpers exclude pending shops. New scoped owner-preparation helper reused by existing Market catalog page/add/manual-save functions allows only owned pending/active nonarchived Market/Grocery profiles; POS, approval, public shop visibility, checkout and financial triggers are unchanged. Owner-only stock/movement read policies enable pending-shop readback.
- TEST: full npm regression EXIT0; actual isolated SQL migrations + pending browse/add/readback/receipt replay/body mismatch/no duplicate/stock movement once/staff+archived+suspended+anon denial PASS19; Merchant DOM PASS42; source/data/image/category contracts PASS. SQL gate now included in npm test/RC. No Production writes.
- Counts unchanged: 128 sourced products, 88 approved images, 40 rights-pending. Browser binary download in this host returned truncated archives; use CI WebKit and do not claim signed-in live Merchant acceptance from isolated tests.
- NEXT: exact successor CI then release-gate review, remaining40 image rights and authenticated Merchant acceptance. No Production apply until exact successor passes.


## 2026-10-19 — Market catalog exact-code release verification
- Source/test status: DONE for code `e37d3546aff1a1f02f696a165ce8ba4967c95b8a`; exact RC2009/run39234168435 SUCCESS, every step including npm regression, complete Merchant mobile WebKit boot and visual component gates passed.
- Branch: `work-market-master-catalog-successor-v2-20261019`; 128 sourced products, 88 approved images, 40 explicitly rights-pending direct candidates. No source/image/category/preset implementation restart is needed.
- Release status: BLOCKED by authenticated actual Merchant add/shop-list acceptance. Live browser inspection at the public Merchant URL showed the login form, with no usable signed-in Merchant session. Real merchant-selected price/cost/stock must be used for a live save; no fabricated Production fixture or account bypass is permitted.
- Remaining rights work: obtain documented retailer reuse permission or an exact open-license image for the 40 pending rows. Current fallback/status is intentional and approved-image rendering is covered by DOM tests; do not relabel these rows as approved merely to clear the count.
- Production remains unchanged: Market catalog tables do not yet exist; read-only migration history confirms staged Market files have not been applied. No merge, Production migration, real product mutation or physical-device acceptance is claimed.
- NEXT: authenticated Merchant acceptance on an authorized review environment, then apply the staged files in order and verify counts/ownership/public asset versions before public activation. Use secure browser authentication if the owner elects to supply a test session; never request secrets in chat. This is a concrete release dependency, not a new coding permission request.


## 2026-10-19 — Owner-authorized test shop and real backend acceptance
- Owner explicitly authorized creating a test shop. Created a dedicated test account through normal Auth signup and owner-scoped registration; marked test metadata, shop pending/draft, product Delivery availability false. No real customer account impersonation, approval bypass or public fake storefront.
- Pre-release function/policy backup committed on `backup/market-catalog-pre-release-20261019-e273cc59` at `aa57572411bfb5a9e12f65c4f068150dab035d64`. Exact HEAD e273cc59 passed RC2010/run39234648240. All 25 staged Market migrations passed a real-database transaction rehearsal and rollback before atomic activation. Original source migration receipts recorded with their versions to prevent CLI replay. Production readback:128 master rows,88 approved,40 rights-pending.
- Real authenticated test-shop REST PASS: paging128/no duplicate source IDs, category/search, approved+pending image fields, custom1.75kg, original receipt replay, new request/same master no duplicate, stock unchanged on replay. Actual GP trigger persisted storefront100 and Delivery110. Product availability false; shop remains pending/draft.
- Live frontend module with actual authenticated REST exposed a real optional-GP403 bug: Promise.all blocked the catalog even though browse/add were authorized. Shared nonblocking GP read now preserves unknown as null and shows server-calculated-after-save copy; no zero GP invented and no financial RPC grants changed. Manual stock and catalog reuse it. Versioned assets `20261019-market-gp5`.
- Local source/test commit25f8db44: full npm regression EXIT0; Merchant DOM45 and SQL19 PASS. Actual frontend module + real authenticated REST PASS:50 rows,1/2/3kg,custom2.25kg,double-submit1 write,readback correct. This is DOM-harness frontend acceptance, not a claim of physical-device or full signed-in-browser appearance certification.
- Opt-in live QA harness: `ops/market/live-catalog-qa.cjs`; requires explicit env opt-in, private session file and owned pending/draft shop with is_test metadata. Credentials/tokens never committed. Advisors reviewed: private receipts intentionally deny all direct access; authenticated definer warnings are intentional guarded RPCs, with owner/anon/staff/suspended checks tested.
- Image HTTP audit:79 unique URLs for88 approved rows;24 HTTP200 image responses,55 rate-limited429 during batch HEAD audit; no404 observed. Stop bulk retrying; keep explicit image failure UI and rights-pending labels. Remaining40 need exact reusable images/rights evidence, not a coding permission.
- NEXT: exact GP-fix CI, then merge/deploy Web and verify public gp5 bytes; keep test shop unpublished. Physical review and remaining image rights remain separate acceptance limits.


## 2026-10-19 — Market Catalog Web activation VERIFIED
- Branch: main; deployed runtime commit `532bb70d3c971effe0a07ab50175b6fbeadc8387`, merged PR441 from exact source `42ae26fd9390c8e54a01ff152d49bee53b8bf36f`. Source/test DONE and Web activation DONE for the stated data/add flow.
- Exact source RC2011/run39236477702 SUCCESS; merged-main RC2012/run39236885146 SUCCESS; Pages build/deploy39236883990 SUCCESS. Public Merchant HTML and actual browser reload expose `market-gp5` for CSS/Market/catalog scripts. Public JS exact-byte match with tested source:market.js SHA256986a44763cbf83b194eec4448ffee41bbd45f36c86903c92dd7107df80a8037b; market-catalog.js SHA2569ed01ab70446382aaf2b53dffd8ddf04bc2ba201f37597b49206781551aaa3b4.
- Backend activated25 tested source migrations; source-version receipts recorded atomically. Production readback128 total/88 approved/40 rights-pending. Backup aa57572411bfb5a9e12f65c4f068150dab035d64 retains pre-release functions/policies. POS/checkout/approval/public storefront conditions preserved.
- Owner-authorized test shop remains pending/draft and marked is_test. Real authenticated REST+frontend module acceptance PASS:search/category/paging,image states,presets,custom1.75/2.25kg,GP100→110,duplicate receipt/new request/double-submit1 write,authoritative stock readback. Actual manual stock screen reads all3 fixture products. Anonymous REST returns zero test products. No fake products exposed to customers; no actual orders/payment generated.
- This supersedes the previous login-blocked release checkpoint: authenticated backend and module flow is verified using the authorized test shop. Full signed-in-browser appearance/physical-device acceptance remains explicitly unclaimed; anonymous browser verifies deployed asset tags only.
- Remaining image work:40 retailer candidates still need exact reusable replacements or rights evidence; keep pending placeholders.79 unique approved-image URLs audited:24 HTTP200,55 HTTP429 batch-rate-limited,zero404; do not falsely mark these as deleted or auto-approve pending retailer art.
- NEXT: continue only remaining exact image/rights work from newest main descendant; keep test shop unpublished. Do not recreate catalog, migrations, presets, optional-GP fix or add receipts, and do not restart PR440/closed441.

## 2026-10-19 — Image batch18 review checkpoint
- Branch: work-market-image-rights-20261019; base HEAD 76f472a3b9da9e679b8a4bb8eedc520c1e30272a. Pre-edit checkpoint branch backup/market-images-before-batch18-20261019 at the same SHA.
- Added CC0 raw whole-duck photo after visual review and license verification; generic whole-duck only. CP S/M/XL remain pending. Rejected Commons Tenderloin_packages.jpg for pork: source categorizes it as beef. Cooked pork tenderloin photos also excluded.
- Staged catalog128 / approved89 / pending39; production remains128 / 88 / 40 until this new migration is activated. No new products, prices, stock, weights, schema or add-flow implementation.
- SQL integration now checks every manifest status and every approved image/source/license against actual migrated rows, replacing fixed approved88 assertion. Validation PASS: full npm test EXIT0; Merchant DOM45; actual SQL236; source/category/image contracts128; git diff --check clean.
- NEXT: full npm regression, publish exact commit/PR, CI; then apply only new batch18 migration and verify count/readback.

## 2026-10-19 — Batch18 activated; batch19 pork review checkpoint
- Branch work-market-image-rights-20261019; verified remote source d2e96ee1b1ac7312404f9c77a735a3b41a711428; merged main 2cf31f6883e1e6eac01d30770f6d149d28b3e5b5. Exact source RC2015/run39240924481 SUCCESS including mobile WebKit. Git-fetched remote tree matches locally tested source byte-for-byte.
- Batch18 real SQL transaction rehearsal/rollback PASS, then new migration activation PASS with original version receipt recorded. Production readback128 total /89 approved /39 pending. Whole-duck prior image null/status pending; prior retailer image source retained in original source migrations. No product/stock/price writes.
- Batch19: reviewed original Julo raw pork tenderloin JPEG2048×1536 and Commons public-domain grant. Approve exact tenderloin and generic lean-pork commodity only; reject beef/cooked substitutes. Pre-edit backup branch backup/market-images-before-batch19-20261019. Staged128 /91 approved /37 pending; production still89/39.
- New authenticated read-only REST recheck unavailable: previous private temporary QA session file is absent in current runtime. Do not claim this recheck passed; earlier authorized live add-flow evidence remains recorded above. No replacement account or products created in this batch.
- Validation PASS: source integrity, catalog128/image91/37 contracts, SQL238, Merchant DOM45, diff whitespace. NEXT: publish batch19 exact HEAD for full CI, then activate only new batch19 migration.

## 2026-10-19 — Image batches18/19 live verification
- Main source HEAD ec765be8366d845e672d60d71eaa316061801c30; merged PR443 exact source 3e4b767c6499547f242aa946883bcb7e3efaa792. Exact source RC2017/run39241481156 SUCCESS (full npm and mobile WebKit). Prior batch18 main RC39241254291 and Pages39241254137 SUCCESS. Both published source trees verified identical to local test commits via git fetch/diff.
- Activated only two new forward image migrations after real transaction rehearsals/rollback; original versions20261019201500 and20261019203000 recorded to prevent replay. Production readback128 total /91 approved /37 pending. Generic whole duck, raw pork tenderloin and lean pork gain licensed commodity illustrations; source product facts retained, no brand/size/pack/weight claims added.
- Local validation: source integrity90 scripts/77 references; full npm PASS for batch18; focused batch19 source/image/category contracts128, SQL238, Merchant DOM45 PASS; complete full regression and WebKit PASS in exact-source CI. No price, stock, order, payment, shop approval or public-sale changes.
- Live owner-scoped RPC rechecks PASS under existing pending test-shop identity via transaction-local authenticated SQL role/claims: whole-duck and pork tenderloin return approved image, source and credit; chicken tenderloin remains pending with no leaked retailer art. This is live SQL RPC permission/readback evidence, not a new signed-in-browser or REST authentication claim; private temporary QA session was unavailable. Earlier full add/idempotency acceptance remains recorded above.
- NEXT:37 exact image/rights replacements remain; keep their explicit pending placeholders. Full signed-in physical-device appearance is not newly certified. Do not duplicate catalog/add/weight/GP logic or replay already applied migrations.

## 2026-10-19 — Image batch20 species review checkpoint
- Branch work-market-image-review-20261019-v3; base HEAD7959ede06024059244b08380999c90f7ccf221ff, latest main descendant after merged PR443; no newer Market successor found. Pre-edit checkpoint backup/market-images-before-batch20-20261019. Latest main RC39241958948 and Pages39241958648 SUCCESS.
- Visually reviewed original raw whole banana prawn and whole Sillago sihama images; reuse existing prawn license and verify fish CC BY3.0. Department of Fisheries page45 confirms Thai ทรายขาว species name. Pictures illustrate commodity/species; retailer count/weight facts remain unchanged. Reject empty mixed-shell Bivalvia.jpg for fresh clam.
- CLI generated new batch20 migration, then renamed only new unapplied file to20261019204500 so it sorts after main's20261019203000. Staged128 total /93 approved /35 pending; Production remains91/37 until verified activation. No schema, price, stock, add flow or weight logic changes.
- Validation PASS: source integrity90 scripts/77 references, source/category/image contracts128, SQL240, Merchant DOM45, diff whitespace. CLI generated file successfully, but automatic approval review rejected subsequent PostHog telemetry with unknown payload; no retry or telemetry bypass. NEXT: commit/exact-source full CI; transaction rehearsal then activate only batch20 through established connector.

## 2026-10-19 — Image batch20 live verified
- Main source HEAD f630086a3d229ea8a1f5c3352dd6d10a0a4763f1; PR444 exact source20829ca22b48cc117f876bb2cccda5db77cea265. RC2020/run39247512452 SUCCESS, including full npm and all mobile WebKit gates. Git-fetched remote tree matches locally tested source e64f8d49.
- Transaction rehearsal/rollback PASS; batch20 applied through established connector with original version20261019204500 receipt. Live readback128 total /93 approved /35 pending. Owner-scoped catalog RPC under existing pending QA identity returns both new approved images, Commons sources and credits correctly. No price, stock, unit/weight, order, approval or public-sale writes.
- CHECKLIST and project memory updated; all128 retain direct reference or approved image state. Signed-in physical-device appearance remains unclaimed; this session's browser has no signed-in tab. No credentials, new account or extra fixture products created.
- NEXT:35 exact image/rights replacements remain. Unapproved research leads (not visually reviewed, do not auto-approve): Commons File:Pure_squid_meat.jpg (CC0; check whole peeled squid versus cut meat) and File:Oysters_at_LFS_Fish_Market_2013_(2).JPG (CC BY-SA3.0; check fully shucked meat versus half-shell). Mixed empty-shell Bivalvia.jpg rejected. CLI telemetry remains rejected; do not retry its PostHog egress.

## 2026-10-20 Admin overhaul — checkpoint 1 (not mission complete)
- CURRENT BASE: b8b9f9883b50d7da3efbd0d37d1e0080bc4b158e; backup/admin-before-overhaul-20261020. Working HEAD: see this checkpoint commit. Main fetched; PR445 already merged. Divergent fix-admin branch repeats the same fast-path concept and is not a newer main descendant. Native/settings successor38baf74c remains separate staged source, not silently merged into Web.
- IMPLEMENTED: Admin hydration dispatches by visible section; overview HEAD counts only; directory coalescing/30s cache; Users joins directory fast path; Audit100; Notifications100; no hidden Rider5s poll; fallback poll throttled60s. Existing RLS/RPC authority unchanged. No Production mutation.
- TEST: full baseline-change npm suite EXIT0 before adding runtime test; executed section runtime19 PASS. Final exact-source npm and CI required. WebKit downloaded but local launch lacks shared libraries; normal dependency install failed setgroups/seteuid. Added whole-document mobile WebKit before/after request/timing gate to existing RC runner (which already installs dependencies).
- REMAINING: scoped directory pagination/summary RPC optimization; broad realtime table subscriptions; full permission inventory and missing operation controls; staged Credit/Ride/Express dependencies; hardened existing mutations/atomic audit; authenticated database permission evidence; exact-source CI/merge/Pages/live readback. No request reduction number or real latency claim until measured.
- BLOCKED: local WebKit runtime dependencies; physical iPhone and live authenticated operation verification not yet available. Source/UI/test work continues.
- NEXT: finish inventory and controls on same successor, run exact CI, repair from its first failure, merge only green Web then verify Pages. Do not mark full Admin mission DONE.

## 2026-10-20 Admin checkpoint 2 — scoped sockets, bounded directory and existing operational surfaces
- BASE:47ead7ca (PR457 checkpoint1). Preserves mainb8b9f988 and existing PR445 changes.
- IMPLEMENTED: account keyset pages200/201 sentinel, profiles restricted to page IDs; realtime visible-section subscriptions with channel retirement; short coalesced GET cache; initial verification shell; real audit reader; existing Credit and Ride modules reused from newest inspected38baf74c, with actor/status fencing; separate Dispatch/Express/Laundry/System and Rider review pages using existing tables/RPCs. No Production write/feature activation.
- EVIDENCE: live read-only ACL/function/trigger audit and rollback-only synthetic authenticated non-Admin denial test pass for summary and shop approval. Live Credit/Admin Ride config functions absent, exposed modules fail closed. Full coverage matrix:docs/ADMIN-OPERATIONS-AUDIT.md, including genuine missing source work.
- TEST: operational DOM24, realtime50, section19, interface28, category22, network26, source94/81 pass. Full exact-source npm still running. Checkpoint1 hosted npm passed; WebKit failed because shallow checkout omitted pinned baseline, now fixed with full-history checkout.
- REMAINING/NEXT: inspect new CI WebKit failure if any; capture before/after metrics; finish server filtering/paging and missing operational controls in matrix. Merge/Pages only green candidate, then actual deployed asset/readback. Whole mission remains IN PROGRESS.

## 2026-10-20 Admin checkpoint 3 — page correctness and broadcast recovery
- BASE:b0abefb9. Fixes a pre-existing out-of-scope desired variable after confirmed shop open/close write. Governance now inserts only its selected panel into DOM, retaining the existing section actions.
- Orders:100-row pages/101 sentinel, deterministic order, server status filter, exact-page Shop/Customer ID reads and Map lookups; page search explicitly labelled. Broadcast no longer depends on the200-user directory cache: reads recipients in200-row server pages, persists notification IDs before each bulk write, uses ignore-duplicates on lost-reply retry and retains cursor until confirmed. No real broadcast sent.
- TEST: new actual Orders/Broadcast runtime13 PASS; Admin interface28/category22/source94/81 pass. Full npm running. Hosted b0abefb9 npm passed; mobile WebKit revealed a test race (directory navigation not awaited before request snapshot). Test now waits for actual directory page completion and reports exact unexpected requests instead of loosening assertion.
- NEXT: exact successor CI; inspect request/timing evidence, repair failures, publish green Web slice. Full permission/missing-control mission remains IN PROGRESS per audit matrix; no financial/backend activation implied.

## 2026-10-20 Web-first receipt / successor workspace split
- DONE — first Web slice only: PR457 source42fcd824 passed exact RC2076/run39348758285 including full npm and complete Admin mobile WebKit. Merge main827b34cd69ab25a93d4692b21a4ce816779edea7 has identical tested tree04584c3bff1fa89065d59e0d8e75fe99ccf75900. Pages1459/run39349127341 SUCCESS.
- PUBLIC VERIFIED: HTTP200 Admin HTML, section-loader, operations, credit, passenger-ride and role-realtime all byte-match main827b34cd. Public browser renders login and loads the new versioned scripts. No signed-in Admin session exists in this browser; no live user mutation or physical-device acceptance claimed.
- MEASURED: complete-document mobile WebKit, same50ms localhost fixture: boot requests43→10; ready3392→828ms; shell478→329ms. These are controlled test values, not Production latency.
- CURRENT SUCCESSOR BASE:827b34cd. Separate18-section navigation, Content/Pricing/Catalog workspaces reusing existing managers, server-filtered50-row Market Master browse, stricter Ride config validation and coalesced section renders. Expand hosted WebKit to every main route and governance section. No new Production schema/data or privileges.
- TEST: workspace DOM18, existing Admin interface28/category22/source95/82 PASS. Full npm first run found an old Shopping banner contract tied to inline Admin calls after Content moved to workspace.js; updated the contract to verify the actual loaded workspace + manager. Final full suite/CI still required.
- REMAINING: scoped readiness RPC internal scan, server-paged Rider review queues, missing audited operational mutation/health APIs, some pricing/eligibility controls, live authenticated/physical acceptance. Coverage matrix remains IN PROGRESS; first successful deployment is not full mission completion.

## 2026-10-20 Admin scoped operational readers checkpoint
- CURRENT BASE:9017567baff0b9fb325245578e3dbef5e7a0efea. PR458 sourceae0b438355b5684a466e742f4df575bfd2def25c passed RC39349869823; main CI39350311495 and Pages39350310925 SUCCESS. Full Admin mobile WebKit74 checks PASS. Latest controlled fixture25→10 requests /1933→762ms ready; prior baseline43 reflects legacy duplicate-boot timing variability, not Production latency.
- IMPLEMENTED: additive page-scoped shop overview overload (max200 IDs, invoker/RLS); bounded Dispatch projection (max100 each); notification outbox status aggregates. Private table grants unchanged. New private readers require active Admin and live session, wrappers authenticated only; no payloads/tokens or business mutations.
- TEST: full npm EXIT0; operational SQL25 and DOM24 PASS; whitespace PASS. SQL verifies anon/non-admin/suspended/session denial and limits. Production migration NOT APPLIED; exact-source hosted CI pending.
- REMAINING/BLOCKED: whole mission IN PROGRESS per audit matrix; Credit/Ride backend rollout, audited missing controls, further paging/abort work, authenticated and physical-device acceptance remain. NEXT: publish checkpoint, exact-source CI, rollback rehearsal before additive migration deployment, merge/Pages and byte verification.

- Reader checkpoint follow-up: rollback-only live-schema rehearsal passed all three non-Admin denials and anon ACL checks; no persisted change. Live column/index review confirmed report compatibility. Added matching global sort/recent-date indexes after finding existing history indexes lead with order_id and cannot support dashboard order. Exact successor CI required.

## 2026-10-20 Admin readers deployed / Support successor
- DONE (PR459 slice): sourceccd9f421 passed RC2084/run39351062344 including full npm and mobile WebKit74. Maina1fb415b94a7ee4b55753c5f98199603ba8d4456 treefbdf1d5694894f044dabc3e96d2fc615fcc77bb2 equals tested source. Pages39351465709 SUCCESS. Controlled fixture43→10 requests; ready3427→754ms; shell435→254ms, not Production latency.
- Migration applied via connector version20261020010500; source CLI file20261020005115. Three public reader wrappers invoker; anon EXECUTE false; authenticated true with server role checks. Rollback rehearsal passed3 non-Admin denials; no business writes. Advisor warning counts unchanged; five new indexes appear as unused INFO immediately after creation (not evidence to drop them). Existing unrelated advisories remain.
- CURRENT BASE:a1fb415b. Support successor: inbox200/201 and per-shop conversation100/101 keyset pages; profile+thread reads parallel; Map grouping instead of repeated full filters; selecting shop only fetches that thread; actor/route fencing and text escaping. Dedicated visible/online60s fallback because live support table is not in Realtime publication; removed unrelated order-chat scope and duplicate shared Support loader.
- TEST: Support DOM18, section19, performance15, source95/82 PASS. Exact successor full suite/CI still required. REMAINING: refund backend atomic workflow (legacy client-only action unsafe), financial/staged backend gates and all unresolved matrix controls. NEXT: exact Support CI/merge/Pages, then remaining permission and performance source work. Whole mission IN PROGRESS; authenticated/physical acceptance unclaimed.

## 2026-10-20 Admin stale-read checkpoint (mission IN PROGRESS)
- CURRENT BASE:b4b3f9f6f15c4b4b178d4fcaa57510cecc5882ae. PR460 source4982883c passed full RC39351721764/mobile WebKit74; Pages39352035615 SUCCESS. Support DOM22/full local npm PASS. No Production messages sent.
- IMPLEMENTED: section/session-aware GET/HEAD cancellation, shared directory scope reuse, pending-read coalescing beyond TTL and correct new-scope handling before hash event delivery. Mutations continue to completion; POST RPC reads are not yet cancelled. Role/RLS guards unchanged.
- TEST: section runtime34, Support22, interrupted network26, source95/82 PASS; exact full suite/CI pending. DONE only deployed slices459/460, not the mission. REMAINING: Rider queue paging, global search, financial/refund authoritative workflows and unresolved feature matrix; scale indexes/aggregates and POST read cancellation. BLOCKED: live authenticated/physical acceptance; staged backend release gates remain separate. NEXT: exact-source CI, merge/Pages/live asset verification, continue remaining source work.

## 2026-10-20 Legacy refund action guard
- CURRENT BASE:3929a38cf61ddda9f26a12d2f0e7508d560386a5 (PR461 exact successor of mainb4b3f9f6). Live function inventory exposes only POS owner refund, not a generic Admin order refund. No provider-backed Admin workflow is deployed.
- IMPLEMENTED: financial requests remain visible; removed legacy client-only approve action and false success notification. Direct old function calls fail closed with a clear unavailable reason, including repeats/non-Admin. No money, payment, order, audit or notification mutation. Does not claim actual refund processing exists.
- TEST: financial failclosed8 PASS; full/exact successor CI pending. REMAINING/BLOCKED: authoritative provider-backed refund review/execution, role validation, atomic audit/idempotency and provider confirmation must precede enabling this control. Other unresolved source tasks remain in audit matrix; whole mission IN PROGRESS.

## 2026-10-20 Customer saved-location coverage checkpoint
- CURRENT BASE:4ae1c89bb5f90f980033384b612988d9cd27791f (PR462 successor). Verified actual Customer source stores one location in users.metadata plus local cache, and immutable per-order delivery snapshots; live schema has no separate address table. Corrected initial inventory's assumed owner address policies.
- IMPLEMENTED: existing guarded Users detail shows saved delivery address, safely escaped; reuses existing read-only location map with validated coordinates. No additional queries, address table, customer edit, order-location rewrite or new privilege. Existing user_view audit retained; clearly distinguishes current account location from the order snapshot.
- TEST: Customer detail DOM11 and source95/82 PASS; full/exact CI pending. PRIOR PR461 passed RC39352282187 (full npm/mobile WebKit74) and merged main791a25340f87f7d47d55ab04569b36e4b2f7f4de; Pages verification pending. Whole mission remains IN PROGRESS per matrix.

## 2026-10-20 Server directory search checkpoint
- CURRENT BASE:283aec9bc977f93e4822e7e3033a96b66192a90c (PR463 successor). Newest main at start02d27a67c87a565fbf75f663032f018ed4f6e581; no reset/revert/cherry-pick. PR461 source3929a38c RC39352282187 PASS and PR462 source4ae1c89b RC39352428793 PASS (full npm/mobile WebKit74 each), merged791a2534/02d27a67 respectively. Pages/live verification follows deployed head.
- IMPLEMENTED: server name/phone search across accounts, max200 rows/201 sentinel, existing page-ID profile/readiness queries retained. Unicode Thai marks preserved, reserved filter/wildcard punctuation removed before building filter. Search resets cursor/history and cancels stale context reads; context-keyed directory coalescing never substitutes an older query. Old replies cannot overwrite current query. Own Admin cache retained for session authority but excluded from result cards when outside returned page.
- TEST: actual directory query16, section34, category22 and source95/82 PASS; exact full suite/CI pending. REMAINING: shop-name-specific global search, Rider queue pagination, search scale indexes/aggregates, unresolved backend/feature matrix and physical/authenticated acceptance. Whole mission IN PROGRESS. NEXT: exact successor CI, merge/Pages and actual deployed asset verification.

- Directory successor follow-up: scoped Realtime invalidates GET cache as well as section TTL; a change during an existing fetch queues exactly one fresh load after completion, avoiding indefinitely cached pre-event data. Unrelated events retain cache. Section orchestration37 and directory16 PASS; exact successor CI pending.


## 2026-10-20 Admin Rider review pagination checkpoint
- BASE: PR465 merged to main `7ba59d04d556f2c1360eff2729f63a86ea7872ab` after RC run `39357745885` SUCCESS. Admin global directory now includes bounded current shop-name/phone search without full hydration.
- PRODUCTION BACKEND: migration `20261020021801 admin_rider_review_pagination` applied after BEGIN/ROLLBACK rehearsal. One centralized private reader pages identity/inspection/vehicle/document queues; legacy four queue RPCs delegate to it for compatibility. Public wrapper is SECURITY INVOKER; anon/PUBLIC EXECUTE denied, authenticated allowed; post-apply advisor has no finding for the new reader.
- WEB: Rider review requests 50 rows/page from `qg_admin_rider_review_page`; next/previous navigation and post-action refresh remain on the current page. Removed browser-side full-queue read followed by `.slice(0,200)`.
- TEST/GATE: source migration contract and Admin operations DOM regression added. Exact successor RC/Pages still required before this slice is DONE.
- REMAINING: search-scale indexes/aggregates, audit/notification paging and remaining safe matrix controls. Refund/provider financial execution, staged Credit/Ride cutover, and authenticated/physical-device acceptance remain separate release gates; do not bypass them.

- SCALE SEARCH follow-up: Production migration `20261020022140 admin_directory_search_trgm_indexes` applied after rollback rehearsal; existing `pg_trgm 1.6` reused. Four partial/full GIN trigram indexes now cover account name/phone and current shop name/phone contains-search. Production currently has 35 users / 10 current shops; indexes are capacity work, not a claim of current latency improvement. Repo regression locks all four index definitions. Search-scale text indexes are no longer a remaining item.


## 2026-10-20 Admin audit / notification paging checkpoint
- BASE: exact descendant of PR466 source `004743628cba7410b9d7b5769356749d90b25aa6`; PR466 RC #2123 SUCCESS and merged main `cd857a2eea894b791f4397ce253aea08f4bc61fb`.
- IMPLEMENTED: Audit Log and Admin's notification history now read 101-row sentinel pages and render at most 100 rows. Older pages use a deterministic `(created_at,id)` keyset cursor instead of growing OFFSET scans; previous-page history is kept only in-session. Cursor identity is part of the read scope so an older in-flight page cannot overwrite the active page. Page changes refresh only the active section; no full Admin hydrate.
- PRODUCTION DB: migration `20261020022520 admin_audit_notification_paging_indexes` applied after rollback rehearsal. Added `audit_logs(created_at desc,id desc)` and `notifications(user_id,created_at desc,id desc)` indexes matching page filters/order. No RLS/grant changes.
- TEST: section-loader regression now asserts 100/101 keyset queries, no OFFSET paging, older/newer cursor navigation, stable order, page metadata/functions, and both migration indexes. Exact successor full RC/mobile WebKit/Pages still required.
- REMAINING SOURCE: read-only POST/RPC cancellation and audited safe controls that already have authoritative backend. Provider-backed refund/credit/top-up/Ride cutover, worker/device delivery evidence and physical authenticated acceptance remain Release Gates, not bypass targets.


## 2026-10-20 Admin shop category editor checkpoint
- BASE: PR467 keyset history successor; intended base is merged main `e3c914428b1d86a459a20b313da76eb54a12f41b`.
- PRODUCTION BACKEND: migrations `20261020023357 admin_shop_category_editor` and `20261020023435 admin_shop_category_invoker_hardening` applied. Final RPC `qg_admin_update_shop_category` is SECURITY INVOKER, requires live session + active Admin, anon/PUBLIC EXECUTE denied, authenticated EXECUTE allowed, and the post-hardening security advisor has no finding for it.
- CONTROL: existing shop-detail dialog now exposes one audited category editor. It reads only the selected current shop, enforces fixed category/subcategory allowlists, requires a 3–500 character reason, requires at least one shopping subcategory, and writes a server Audit Log with before/after values. It does not alter shop approval/open status.
- TEST: dedicated source regression covers the UI control, selected-shop read, server guards/allowlists/audit, explicit grants and final SECURITY INVOKER hardening. Exact RC/mobile WebKit required before merge.


## 2026-10-20 Admin read-only RPC cancellation successor after category-editor sync
- BASE: current main `b11d6767d06340dd771f2f080710f458df80b18c` includes merged PR #471 audited shop-category editor and its Production-backed SECURITY INVOKER RPC.
- IMPLEMENTED: read-only Admin POST/RPC work (System live counts, Dispatch overview, notification health, Rider review paging, Ride config/licence reads, Credit account/merchant-target reads) joins the existing route/account AbortController lifecycle. Mutations continue on normal RPC calls and are never route-aborted.
- FAILURE/FIX EVIDENCE: RC #2195 found a cross-module helper-name collision; successor helpers are module-scoped/uniquely named. RC #2205 then found a stale Rider-review regression requiring direct `qtSupabaseRpc`; the test now requires the cancellable read helper and forbids the old direct read path.
- PRE-SYNC GATE: RC #2209 on `30a327f76293f587a7ed1defaa2c0c69ca3ee25a` passed full npm, Admin mobile WebKit, Merchant, Map and Grocery gates.
- FORWARD SYNC: #471 files were preserved on this successor without reset/revert/force; Admin category UI/migrations/tests and read-cancellation changes coexist.
- NEXT: exact-head RC after the forward sync is required before merge. If green, merge this slice and continue the next authoritative Admin control; external provider/physical release gates remain blocked and must not be bypassed.


## 2026-10-20 Admin Rider Map Feedback / Road Alerts checkpoint
- BASE: stacked on clean RPC-cancellation successor PR #472; this keeps read-only queue RPCs on the cancellable Admin read layer while review/publish/disable remain mutation RPCs.
- REUSED BACKEND: Production migrations already exist as `20261014074523 rider_map_feedback_v1`, `20261014110355 rider_road_alerts_v1`, and `20261014110429 rider_road_alerts_fk_index_v2`. Their exact historical Production-aligned source mirrors were restored to the repository; Production was not re-applied.
- IMPLEMENTED: Rider Admin section restores the existing map-feedback review flow from PR #261 and Road Alert controls from PR #320. Pending reports can be accepted for review or dismissed with reason. Accepted road_closed/bad_road/unsafe_turn reports can publish bounded Road Alerts and active alerts can be disabled.
- MAP AUTHORITY: “ดูบนแผนที่” reuses existing `qgAdminViewLocation()` / QueueGo Map read-only dialog. No Google Maps URL or duplicate map component.
- SECURITY: browser reads only public guarded RPCs; it never reads `qg_private.rider_map_feedback` or `qg_private.rider_road_alerts`. Existing wrappers are SECURITY INVOKER, deny anon, and private implementations require live session + active Admin.
- TEST: dedicated source regression guards RPC/read-vs-mutation wiring, QueueGo Map reuse, no Google Maps fallback/private-table browser access, bounded queue/duration contracts, privilege revocations, and restored migration mirrors. Exact stacked RC/mobile WebKit required before merge.
- NEXT: merge PR #472 first if its exact gate is green, retarget this successor to main, then obtain exact-head RC before merge. Physical navigation/GPS acceptance remains a separate release gate.


## 2026-10-20 Admin Rider Programs successor
- BASE: merged PR #473 / main `93837a8c99b2bb4318ddd1fb122265111b3946d2`; successor branch `work-web-admin-rider-programs-v2-20261020`.
- IMPLEMENTED SOURCE: restores exact Production-aligned source mirrors for Rider Missions, Insurance and Benefits. Adds one separate Admin workspace for real Missions / Benefits / Insurance using the existing public RPCs only.
- READ/WRITE BOUNDARY: list RPCs use the scoped cancellable `qgAdminRpcRead` layer; mutations remain direct `qtSupabaseRpc` so navigation never aborts a committed write.
- AUDIT: new redacted atomic audit trigger records Admin mutations without policy number, support contact/link, coverage text or benefit URL in `audit_logs`.
- SAFETY: no mock program rows, no automatic Mission bonus/credit payout, no direct `qg_private` browser access, no service-role key, and no Credit/Ride/provider Release Gate bypass.
- PRODUCTION: rollback rehearsal passed with insert/update/delete + redaction sentinels; migration `20261020030951 admin_rider_program_audit` is applied. Three triggers exist; direct anon/authenticated/PUBLIC EXECUTE on the private trigger function is denied; Missions/Benefits/Insurance remain 0 real rows.\n- NEXT: run focused/full tests + exact-head RC, then merge/Pages verification. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Rider online-readiness explanation
- BASE: merged PR #474 / main `c1742d8aaa5d4bd7cccc300ae62a44497e080f3f`; branch `work-web-admin-rider-online-readiness-20261020`.
- IMPLEMENTED SOURCE: existing Rider review queues now state whether each row is a blocker for the existing server online guards: identity re-verification, vehicle inspection, selected vehicle approval, and expired national ID / driver licence / selected-vehicle PRB.
- NO NEW AUTHORITY: reuses `qg_admin_rider_review_page`; adds zero queries, zero polling, zero table/RPC/schema changes, and no `set_rider_availability` / force-online action.
- DEVICE BOUNDARY: Network, Firebase, notification permission, GPS/location service, Google Maps and overlay-return readiness remain device-side evidence and are explicitly not claimed by Admin.
- SERVICE BOUNDARY: Ride / Express / Laundry can have additional service-specific release gates; this view does not bypass them.
- TEST: dedicated jsdom regression locks server-blocker labels, 50-row paging reuse, no private-table access and non-Admin denial.
- NEXT: exact-head RC/mobile WebKit, merge/Pages, then continue remaining safe matrix gaps. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Rider presence paging
- BASE: merged PR #475 / main `76ba873fdcccb324e45d98e50240658e062affff`; branch `work-web-admin-rider-presence-page-20261020`.
- ROOT CAUSE: Rider status re-read all Rider users + all Rider profiles + up to 2,000 historical presence events even though the Admin directory was already scoped and Production records every `metadata.online` change via trigger `qg_record_rider_presence`.
- CHANGE: presence screen now reads Rider users at 100+1 rows per page, fetches profiles only for those page IDs, and renders the current `rider_profiles.metadata.online` value. Prev/Next preserve access beyond the first page.
- REQUEST/PAYLOAD: removes the `qg_rider_presence_events?...limit=2000` request entirely and removes unbounded Rider user/profile reads. No schema/RLS/polling/service change.
- CONSISTENCY: Production trigger evidence confirms each change of `metadata.online` appends the matching presence event; profile current state and event history are therefore not competing authorities for the current switch.
- SAFETY: actor/page generation fencing prevents a stale response from another page/account repainting the panel.
- TEST: dedicated jsdom runtime regression locks 100+1 paging, page-ID profile lookup, no presence-history query, Prev/Next and account-race fencing.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue remaining Admin performance/coverage gaps. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin scoped shop overview only
- BASE: merged PR #476 / main `0e03fcd1a57a5e2b31177fbd5cefeffab0ce9285`; branch `work-web-admin-scoped-shop-overview-20261020`.
- PRODUCTION AUTHORITY: `queuego_admin_shop_overview(p_user_ids uuid[])` is SECURITY INVOKER, active-Admin guarded and rejects more than 200 account IDs. The legacy no-arg overload is SECURITY DEFINER, computes readiness/order counts for all shops, and Supabase Security Advisor flags its authenticated exposure.
- CHANGE: Admin directory fast path now calls only the scoped overload with the current page IDs. The legacy PGRST202/function-missing fallback to the no-arg overload is removed; an unreachable old no-arg Admin hydrate call is removed too.
- FAILURE MODE: missing scoped RPC now fails closed and surfaces the load error instead of silently falling back to an unbounded all-shop computation.
- TEST: existing Admin directory runtime now verifies every overview call carries `p_user_ids`, caps at 200, simulates PGRST202 and proves no second/unscoped call occurs.
- NO DB MUTATION: no grant/RLS/function/schema change in this source-only checkpoint. Legacy backend function retirement remains a separate compatibility/security decision after deployed callers are proven absent.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue Admin performance/permission coverage. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Orders keyset paging
- BASE: merged PR #477 / main `d6caeebb5ccf0608707ea391be0035e148b04dd6`; branch `work-web-admin-orders-keyset-20261020`.
- ROOT CAUSE: Admin Orders used `offset = page * 100`; database work grows with page depth even though rows are already sorted by `created_at desc, id desc`.
- CHANGE: paging now uses a stable keyset cursor `(created_at,id)` with 100+1 sentinel rows. “ก่อนหน้า” stores cursor history; status-filter changes reset cursor/page; refresh stays on the current cursor.
- CONSISTENCY: visible-page shop/customer names are still fetched only for the 100 visible orders. Trace/delete/status filtering and local within-page search are unchanged.
- RACE SAFETY: request-version + Admin-owner fencing prevents a stale old page/filter response from repainting a newer page/account.
- INDEX EVIDENCE: Production already has `idx_orders_created_at (created_at desc)` and `orders_pkey(id)`; no schema/index mutation is required for this checkpoint.
- TEST: Admin order runtime rejects any `offset=` order request and verifies next cursor, previous cursor, filter reset, exact-page parties and existing broadcast behavior.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue Admin performance/coverage. Whole Admin mission remains IN PROGRESS.


## 2026-10-20 Admin Support fallback refresh scope
- BASE: merged PR #478 / main `0f84fe969b75f3029fad13577c2c4e9ec4b27be2`; branch `work-web-admin-support-fallback-scope-20261020`.
- EXISTING BACKEND: `shop_support_messages` still has no Realtime publication, so the bounded 60s visible-page fallback remains necessary.
- CHANGE: fallback refresh now runs only on `#admin-support` when the tab is visible, browser online, Admin approved, inbox/thread are both on the newest cursor, and the reply draft is empty.
- NO HISTORY JUMP: old inbox/thread pages are never auto-refreshed; manual paging stays under operator control.
- NO TYPING INTERRUPTION: a non-empty reply draft suppresses the timer; explicit refresh and post-send refresh still work.
- REQUEST BOUNDS UNCHANGED: inbox 200/201 + selected thread 100/101 keyset pages; no extra table, no new poller, no interval reduction, no full hydrate.
- TEST: existing `admin-support-runtime.cjs` now locks history/draft suppression, newest-idle fallback, hidden-route suppression and stale-actor fencing.
- NEXT: exact-head RC/mobile WebKit, merge/Pages/post-merge RC, then continue remaining safe Admin source gaps. Whole Admin mission remains IN PROGRESS.
