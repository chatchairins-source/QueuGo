Warning: truncated output (original token count: 44635)
Total output lines: 1131

# QueueGo Active Work Checklist

Last governance checkpoint: 2026-10-22; latest verified main `aca988473d9f5132141dc1df379f0028d3ff64a2`

This checklist is the repository continuation ledger. Read `WORKFLOW.md` first.

## Current Production POS checkpoint — 2026-10-22

- Verified main: `aca988473d9f5132141dc1df379f0028d3ff64a2`; QueueGo RC `39793297082` SUCCESS. Open PR #623 is Rider-only; no POS successor PR is open.
- Production versions `20261022141101`, `20261022141117`, `20261022144627`, `20261022145405` contain the generic reservation, adjustment, manager permission and Market owner guards. Readback confirms trigger definitions and fixed-search-path SECURITY DEFINER functions with direct EXECUTE revoked. Market mapping count 8; generic-stock overlap 0.
- Full local `npm test` passed on `aca98847`; POS inventory 67 PASS; adjustment UI 9 PASS. No synthetic Production sales, payments or stock rows were created.
- Source filenames and the inventory fixture now use Production versions exactly; focused migration/UI/source-integrity checks pass. NEXT: publish this history alignment, pass exact-head RC, merge, and verify the remote history still has exactly one copy of each migration. Then continue the next unblocked POS task.
- Release gates still open: real authenticated cashier/device flow, live kitchen/QR/staff journeys, offline reconnect, Android parity, hardware and final Release Gate. QueueGo/POS remain **NOT DONE**.


## 2026-10-22 current head and next slice

- Current main `aeedb1df2ef4654ee68ed26b25b4f34a01e441de`: PR #584 merged after exact-head RC `39699366405` passed on `9e9202bf3b688a816eef41cd3fe2eacd0e852562`. Main RC `39699765279` and Pages deployment `39699764713` passed on the merge SHA.
- PR #585 POS offline recovery design merged at `d67f8dd8f79bcc991c447376cdd838988c816884`; exact RC `39698519445`, main RC `39698920070`, and Pages deployment `39698919956` all passed.
- Active next action: implement Phase 1 durable local cart drafts only. Submission needs an atomic/idempotent server RPC because standard `pos_edit_bill` is not idempotent.
- Current environment blocker: `skills.read` cannot load the required Supabase skill package. No schema/RPC code has been written. Reload the skill before any Postgres change; do not substitute a client-only unsafe multi-line submission. Cover scope isolation, reload/reopen, account switch, duplicate tabs, changed price/availability, and idempotent online submission. Do not create offline completed sales or touch Production schema/data.
- Release blockers remain: authorized cashier/device acceptance, full authenticated real-user journeys, Production migration approval/apply/readback, and deployed runtime acceptance. QueueGo/POS remain **NOT DONE**.

## 2026-10-21 POS scan continuation checkpoint

- **POS SKU/barcode registry and scan-to-bill — source/test complete locally, release BLOCKED.** Branch `queuego-pos-sku-scan` is based on the merged latest main and the current #568 POS successor tree; pre-change HEAD `a0b3109a3f8ae6970f2e865e0760b7e8ddef4e3d`. Verified latest main `643a115129b0bf1d6b05de261baa0aa959310a1b`; checked open PR #570 overlap, POS asset references unaffected.
- Added shop-unique product code table, guarded manager CRUD/cashier lookup RPCs, scanner UI and queued existing bill flow. Added server-validated menu option selection, price snapshot, total recalc and replay receipt. Migrations `20261021211800_pos_product_code_registry` and `20261021223000_pos_menu_option_pricing` are staged; neither is in Production. The option SQL test executes the existing Grocery Master stock trigger and verifies no double deduction on retry/price update.
- Verification: POS runtime 73, cash shifts 36, item sales 18, registry SQL/security 31, scan-to-bill UI 11, option pricing/security/replay 27; Security Definer review, source integrity and full `npm test` PASS on local source commit tree. CI pending publication. No Production mutation, cashier acceptance, or physical scanner certification.
- **NEXT:** publish exact branch and open a dependent PR against #568; local Git push currently cannot authenticate (`could not read Username for https://github.com`), so an authenticated GitHub write path is required. After publishing, run hosted CI; then review migration/security and apply through release gate; authenticated cashier scan→options→bill→kitchen→payment→receipt and physical scanner acceptance. Variant price/snapshot is now source complete. Next inventory audit: generic non-Grocery-Master stock tracking across POS/Delivery; keep Grocery Master on its existing reservation trigger. Keep cash-movement PR #568 acceptance separately open.

## 2026-10-21 North Star recovery checkpoint

- Initial verified `main` at this recovery checkpoint: `6669dc108137f8119c86d4de9709dacba7a301d7` (superseded by merged North Star docs commit `643a115129b0bf1d6b05de261baa0aa959310a1b`).
- This checkpoint adds `PRODUCT_NORTH_STAR.md`, `FEATURE_GAP_MATRIX.md`, `RELEASE_GATE.md`, and `NEXT.md`; these are the mission, evidence matrix, launch gate and precise continuation pointer. Older snapshots below are historical evidence; fetch current main and the relevant newest successor before resuming.
- **POS cash in/out shift ledger — source/test IN PROGRESS, release BLOCKED.** Open PR #568; branch `work-pos-complete-next-20261021`; exact HEAD `0f9af0cbc91af0bf7d964c9c52665ceb4e19cb9a`, based on the verified latest main. Prior RC run `39617058579` passed at `119c68e`; updated exact-head RC run `39619648922` completed SUCCESS. Focused POS tests (36/73/18) and full `npm test` passed on the updated tree in this session. Latest change adds transaction-scoped serialization for same-key retries.
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
- Fix commit: `478a7dfc9d973dc3a859bdc9b15f60b1da7576d8` updates only the shared picker lifecycle gate to current QueueGo MapLibre ownership/destroy semantics; native run…32635 tokens truncated…B/no-WebView, real emulator launch/map/voice lifecycle. Navigation-map-runtime and four required SQL race jobs SUCCESS. Skipped Express/map-display jobs are not additional certification. Native remains separate from public Web main, with same payment backend/ledger.
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


## 2026-10-21 QueueGo POS generic inventory continuation
- Implemented on the existing dependent POS successor: opt-in generic inventory tracking and private reservations across shared POS/QR/Delivery order items. Grocery Master products are explicitly excluded to preserve their existing single stock authority. A constraint prevents enabling both tracking owners.
- Generic product editor marks the field as sellable stock and blocks tracking-mode changes while an open order contains the SKU. POS displays available units and disables a tracked SKU at zero. Order item changes are serialized on the order and product rows; insufficient-stock writes fail transactionally; pre-completion cancel releases once while completed refund remains sold until a separately authorized physical return/restock flow.
- `tests/pos-generic-inventory.cjs` 22 PASS; POS option 27, SKU SQL 31, scanner 11, cash shifts 36, Merchant product reliability 72; full hosted `npm test` EXIT0. Shopify official inventory-state docs informed the available/committed label. Isolated PGlite tests do not certify production concurrency/RLS or real register behavior.
- Migration `supabase/migrations/20261021233000_pos_generic_inventory_reservations.sql` is staged, not in Production. No migration applied and no real product/order writes made. Latest main snapshot `643a115129b0bf1d6b05de261baa0aa959310a1b`; PR #568 head `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`; tested source head for PR #572 `e40dcd17480cd6068d2eda4ef9d04bdc67f6771f`. Exact tree source is verified against local tested commit `1eafe37ed1eb19ec6d017a0ace9219cfe0b85659`. Hosted RC run `39630180090` PASS includes full tests, WebKit install, Admin/Merchant mobile checks, Map Lab/search and Grocery rendering. PR #572 remains stacked and no merge/deploy/readback/cashier acceptance is claimed.
- Next: resolve #568 release gate, rebase/retarget #572 onto merged main and rerun exact-head RC; review/apply migrations through release process and run authenticated boundary sale/cancel/refund acceptance. Then build stock adjustment audit/history and supplier purchase receiving. QueueGo/POS remain NOT DONE.


## 2026-10-21 POS inventory adjustment audit/history continuation
- Scope: extend the same generic inventory owner used by POS/QR/Delivery. Grocery Master and Market inventory ledgers remain untouched.
- Migration `20261021233100_pos_inventory_adjustments.sql`: manager-only idempotent stock adjustment RPC; append-only movement rows with actor/reason/note/before/after; opening balance; linked order reservation/release/cancellation events; tracked stock direct-write guard; shop-scoped paginated history RPC. Four migrations in this dependent POS stack remain unapplied in Production.
- Merchant product editor: tracked quantity is read-only on general save; dedicated mobile dialog requires integer delta, reason and note. Ambiguous retries reuse the same session request key; successful change refreshes product cache and history. History output is escaped and keyset-paginated.
- Tests: generic inventory/adjustment SQL 38 PASS; adjustment UI runtime 9 PASS; Merchant product reliability 79 PASS; full `npm test` EXIT0; `git diff --check` PASS. Exact-source hosted RC `39633575108` PASS on `3ac73c2c1aa9221c392e5cfcaca5afd82653dd15`, including WebKit, Admin/Merchant, Map and Grocery checks.
- Release gates remain BLOCKED: #572 is stacked on open #568; Production schema review/apply, live concurrent boundary, manager adjustment, authenticated cashier acceptance, deployment/readback and real register verification remain open. No live stock writes or Production migration performed.
- NEXT: review CI evidence and resolve #568 before rebasing #572 onto main; rerun exact-source RC on the merged-main descendant. Production migration review and authorized live cashier/device acceptance remain required. After the stock slice is released, add supplier purchase receiving without duplicating Grocery/Market ledgers. QueueGo/POS remain NOT DONE.

## 2026-10-21 QueueGo POS release continuation after owner `ต่อ`
- Startup recovery: read `WORKFLOW.md`, this checklist and `docs/PROJECT-MEMORY.md`; fetched latest main/open PRs and checked the #572 successor. Main before this work was `643a115129b0bf1d6b05de261baa0aa959310a1b`; #568 head `a1592df31c41cbb6bbfdf13e166d977c9bcf901c`, #572 head `330d900e5b78191ab012905e4f42bbca09889a2c`.
- #568 exact-head QueueGo RC `39622237860`: SUCCESS, all 12 test/browser steps completed. Merged using expected-head lock; merge commit `c79640e0c896cc81961e8ba2dc69d336a9f519b0` is now main. No Production migration or data mutation was made.
- Retargeted #572 to `main`; its current HEAD remains docs-only `330d900e5b78191ab012905e4f42bbca09889a2c`, inventory code SHA `3ac73c2c1aa9221c392e5cfcaca5afd82653dd15`. Pre-retarget exact-source RC `39633575108` PASS. Fresh exact-head RC after retarget is still required.
- Supabase Production read-only check at `2026-10-21`: latest migration `20261021133330`; `pos_register_cash_events` has RLS enabled, anon SELECT false, authenticated SELECT true/INSERT false. No `event_type` or `idempotency_key` columns; `pos_record_cash_movement(text,numeric,text,uuid)` absent. Therefore merged cash movement is not live. Four #572 migrations are not applied.
- Supabase security/performance advisors were inspected. They return global findings (private RLS/no-policy tables, public SECURITY DEFINER endpoints requiring intent review, and unindexed foreign keys); no blanket policy change was made. These findings need endpoint-level ownership review before future database activation.
- Updated `NEXT.md`, `FEATURE_GAP_MATRIX.md`, and `RELEASE_GATE.md` to the current merge/Production evidence and preserved the NOT DONE status.
- STATUS: #568 source DONE / Production activation BLOCKED. #572 source/test DONE on prior exact-source CI / fresh post-retarget CI and Production acceptance OPEN.
- NEXT: trigger/run a fresh full exact-head RC on the retargeted #572 successor. If green, complete staged migration/RLS/trigger order review and authorized real cashier/scanner/cross-channel acceptance before controlled activation. Then resume supplier/PO receiving on the same inventory authority; do not add a second ledger. Overall QueueGo/POS remain NOT DONE.

## 2026-10-21 POS inventory toggle authorization follow-up
- After PR #572 merged to main at `6050a51ab7cb06001fb4163b5ff88c890fb4950d`, reviewed Production `products_shop_update` RLS and found authenticated shop users can PATCH their own product rows. Existing migration guarded tracked stock quantity changes but did not require manager permission to enable/disable the inventory tracking owner flag (nor to insert a product with that flag set).
- Production `public.pos_allowed(text)` is a SECURITY DEFINER permission check: owner or active shop staff with the named capability. Used only read-only function definition; no user/product data read or write.
- Added successor migration `20261021233200_pos_inventory_tracking_manager_guard.sql`: the tracking guard now covers INSERT and UPDATE, requires authenticated `pos_allowed('manage_staff')` when tracking starts/is switched, preserves Grocery Master exclusion and open-order lock, and revokes direct trigger-function execution.
- Regression: `tests/pos-generic-inventory.cjs` denies unauthorised direct tracking toggle and tracked-product insertion; focused test passes 40 checks. Full `npm test` EXIT 0; inventory adjustment UI test 9 PASS; `git diff --check` PASS. Exact-source RC `39638917216` passed all 12 steps; PR #573 merged as `ab36bfb9da66d5d4a43a83011e4a50fb08c27213`. Migration has not been applied to Production.
- NEXT: continue actual schema/trigger compatibility review for the five inventory migrations. Real manager/cashier and physical device acceptance remains blocked; do not apply Production migration or claim POS release readiness.

## 2026-10-21 POS Market stock authority isolation — merged
- Read-only Production catalog inspection found `market_item_stock_guard()` decrements `market_products.stock_quantity` and appends `market_stock_movements`; it is the authoritative Market ledger. PR #572 generic reservation skipped only Grocery Master, so a Market-linked `products` row with generic tracking enabled could be double-decremented.
- Added `20261021233300_pos_market_inventory_owner_guard.sql`: migrate preflight rejects any existing tracked Market overlap; product toggle and Market mapping triggers lock/check `products` so ownership cannot be assigned concurrently; remaps lock both products in stable order and deletes serialize on the old product; generic order-item reservation rechecks Market mapping after row lock and leaves it to the existing Market trigger.
- `tests/pos-generic-inventory.cjs` simulates the Market item trigger and an inconsistent legacy overlap; asserts Market count changes while `products.stock` remains unchanged and no generic reservation exists. It also covers both rejection directions for future ownership changes. Focused test 47 PASS after adding remap/delete guards; full `npm test` EXIT 0 on the final local tree; `git diff --check` PASS. Exact hosted RC remains pending on the latest branch HEAD.
- PR #574 merged as `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1` after exact QueueGo RC `39643279380` passed all 12 steps. Pages build/deploy `39643901620` succeeded on this merge SHA. Production migrations/data were not changed.

### Current continuation checkpoint — PR #574 merged
- `qg_inventory_tracking_guard()` now runs SECURITY DEFINER with fixed `search_path` because Production `market_products` is RLS-enabled without authenticated read policies; manager check still uses the caller JWT. Regression now applies migrations in timestamp order and tests an authenticated product update with RLS active.
- Local focused inventory test: 47 PASS; adjustment UI: 9 PASS; full `npm test` EXIT 0; `git diff --check`: PASS. Exact-source hosted RC `39643279380` passed all 12 steps; Pages deploy `39643901620` succeeded on merge SHA.
- Map remap/delete serialization now locks both affected products and serializes unmapping; Main merge SHA is `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1`; six POS migrations remain unapplied and production acceptance is open.
- No Production DDL/data write. Next: continue read-only schema/ACL/RLS/trigger/recovery review before any controlled Production migration. Production activation still requires authorized cashier/device acceptance and controlled migration/readback.


## 2026-10-21 POS Market inventory post-merge
- Merge `2d0c6b58a45689585dc7653bd18bcf89dfd1e4c1`; exact RC `39643279380` passed all 12 steps; Pages deployment workflow `39643901620` succeeded. Browser runtime readback unavailable due environment disconnection.
- Production remains unmodified; latest migration `20261021133330`. The inventory tracking column is absent until prerequisite migration 330, so Market overlap preflight must run only after ordered feature schema installation.
- QueueGo/POS remain NOT DONE until authorized user flows and Release Gate evidence are complete.


## 2026-10-21 POS numeric quantity regression follow-up
- Production read-only schema shows `order_items.quantity numeric` and `market_products.stock_quantity numeric`; Market mapping uses composite product/shop FK. Existing Market and Grocery item stock triggers are SECURITY DEFINER.
- PR #576 changed inventory PGlite quantity to numeric and adds fractional generic rejection plus fractional Market ledger decrement assertions. Exact RC `39647073978` passed; merged at `a4f12dd39b751e7e05a2d37b54e47863d477f391`. No Production DDL/data write.


## 2026-10-21 POS numeric quantity compatibility — merged
- Production schema readback confirmed `order_items.quantity numeric`, `market_products.stock_quantity numeric`, composite product/shop FK and SECURITY DEFINER Market/Grocery stock triggers.
- PR #576 changed the generic inventory PGlite fixture to numeric; fractional generic quantity is rejected while fractional Market quantity affects only the Market ledger. Exact RC `39647073978` passed all 12 steps. Merged to main at `a4f12dd39b751e7e05a2d37b54e47863d477f391`; post-merge main RC `39647725266` passed and Pages deploy `39647725023` succeeded.
- Production migration/data remain untouched. Latest migration `20261021133330`; inventory tracking column absent until ordered feature migrations. Cashier/device and controlled migration gates remain open; QueueGo/POS NOT DONE.


## 2026-10-21 main RC and POS Production preflight
- PR #577 merged main SHA `e70961d5b70fa7f84519fbebf473659603e35132`; exact PR RC `39648478732` passed. Main RC `39649126036` first failed Admin WebKit assertion at `tests/admin-boot-browser.cjs:69`; retry of exact failed job passed every step. Pages deploy `39649125931` succeeded. First-attempt failure is retained; no source runtime edit was made.
- Supabase read-only: migration history ends `20261021133330`; POS inventory chain `20261021211800`–`20261021233300` and cash movement migration `20261021211709` are unapplied. Existing Grocery/Market stock triggers remain authoritative. Generic POS tables and triggers are absent. No Production mutation.
- Activation gate: authorized cashier/device flow, ordered migrations, overlap preflight after 330, post-apply ACL/RLS/trigger readback, then real sale/cancel/refund/adjustment regression. QueueGo/POS NOT DONE.


## Post-merge receipt — 2026-10-22
- PR #578 merged at `cc036ee001bf23425d055977f8330e3753749743`. Exact PR RC `39650817495` passed all 12 required stages. The PR stabilizes the existing Admin WebKit route assertion by waiting for the rendered heading, with no runtime UI change.
- Main push RC `39694314231` passed all stages on the merge SHA, including npm test, Admin WebKit boot/layout, Merchant mobile WebKit, Map Lab gestures, QueueGo Map search and Grocery mobile/tablet rendering. Pages deployment `39694313643` succeeded on the same SHA. Main ref verified at `cc036ee001bf23425d055977f8330e3753749743`.
- PR #578's change is test stabilization and documentation; Pages success is recorded as deployment evidence, not a claim of end-user UI behavior change. The prior main RC failure on #577 remains recorded as a separate earlier attempt.
- Production database remains unmodified. POS feature migrations remain unapplied; continue authorized cashier/device acceptance and controlled migration gates. QueueGo/POS are **NOT DONE**.


## POS ambiguous bill session-scope guard — 2026-10-22
- Read current main `6acd48ef3c130cc1c6b7f4c0958afcc228920414`, no newer POS successor was open. PR #580 guards the ambiguous bill retry with its captured shop and cashier identity; identity mismatch drops the tab-scoped pending request before any order RPC. Regression covers session change after the request is loaded.
- PR #580 exact RC `39695677982` passed all 12 required stages and merged as `fbdf63707a5eba1b9bf703891330c7b132e6bb54`. Post-merge main RC `39696051611` passed all stages. Pages deployment `39696050699` succeeded on the merge SHA.
- This recovers an ambiguous online create request safely; it is not an offline sale queue. POS offline sale/outbox/conflict/reconciliation remains unimplemented and unaudited. Production database remains unmodified; cash/inventory migrations and authorized cashier/device acceptance gates remain open. QueueGo/POS are **NOT DONE**.


## Latest main successor receipt — 2026-10-22
- PR #581 merged at `c740e9321ec5e3d1fb2d6acafc382180e7166121` after its exact-head QueueGo RC passed. Its immediate push RC `39696877230` was cancelled when main advanced; the descendant main `a71131aa0027b0616260ce6881ef708e99e9e214` added Admin exclusions for POS DINE_IN/TAKEAWAY orders in Admin order reads, count KPI and GP totals.
- Successor main RC `39697139447` passed all required stages, and Pages deployment `39697139101` succeeded on the current main SHA. The earlier cancellation is preserved as evidence; no failure was hidden.
- Current main therefore includes #580's cashier/shop-scoped ambiguous bill retry guard and the Admin POS-order reporting exclusion. Production remains unmodified. POS feature migrations remain unapplied, physical cashier/device acceptance is not claimed, and a durable offline-sale queue remains unimplemented. QueueGo/POS **NOT DONE**.


## POS offline recovery discovery — 2026-10-22
- Current source blocks offline sale writes. Ambiguous online bill creation and cash movement use tab-scoped idempotent retries; #580 added actor/shop protection for bill replay. These are not durable offline sale queues.
- Reviewed current official Square and Shopify POS offline documentation. Both distinguish pending offline work from server-accepted state and expose permission, risk, limits, recovery, or unsupported-operation boundaries. Research and the recommended QueueGo Phase 1 are in `docs/POS-OFFLINE-RECOVERY.md`.
- Recommended next implementation: durable, identity-scoped offline cart drafts that require server price/stock/permission validation and cashier review before online submission. Do not mark as paid, send to kitchen, or reserve stock while disconnected. Actual offline completed sales require owner decisions about payment, multi-terminal inventory conflict, limits, shift/accounting, receipts and customer recovery.
- No Production migration or data change. QueueGo/POS remain **NOT DONE**.


## 2026-10-22 POS Product Code Production receipt
- Main: `bc8bdc173a8b798e84dee09833b76caf8b546614`. PR #617 exact RC `39769241578` PASS, merge `30865060c4238ecbaf2b2ece83a108caeab3b454`; Production migration `20261022113556`.
- PR #618 exact RC `39772099278` PASS, merge `bc8bdc173a8b798e84dee09833b76caf8b546614`; Production FK index migration `20261022114328`.
- Readback: registry empty, RLS on, direct anon/authenticated table access denied, four guarded authenticated-only RPCs, POS storefront price lookup, audit writes in save/remove. Product Code FK lints resolved; no sample transactions created.
- Still open: generic inventory chain, authorized cashier/device end-to-end and remaining POS Release Gate. POS is NOT DONE.
