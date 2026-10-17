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
- Current HEAD: `93cd3ee2388ba63c4087a93bc7a9495cc2324bc8`
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
