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
