# QueueGo P0 resume checkpoint

Base: `dda4d1c4b912a9bbb2a9e8b4aaeb05f12a391870` on `work-native-credit-live-enablement-20261016`.
Current exact checkpoint SHA: obtain with `git rev-parse HEAD` (a commit cannot embed its own SHA).

## Completed
- Fetched open PRs, working branches, recent workflows, artifacts and releases.
- Proved latest native successor descends from online compatibility fix e2f64360.
- Read AGENTS.md and PROJECT-MEMORY.md; current owner mission takes precedence over prior deployment objective.

## Resume checkpoint after 604e5d8c verification
- e2657df9 probe run38861458147 failed BEFORE the process probe: allowed-permission screenshot has a central missing tile (gray fraction0.1162898) after20s, surrounding tiles render; no crash/ANR/map setup error. Artifact12029590947 SHA2564a5283b1e5b3886fe3342a73edc6a961da69bf703e42c9383e364fcf453f68fd verified and image/log inspected. Do not weaken the pixel gate or claim the process probe ran. A same-job rerun request was refused because other jobs were still running; wait for completion before retrying.
- SDK0.1.8 bytecode confirms setLocation(point) and setLocation(point,animate) both delegate with isUserAction=true. The picker therefore treated the initial15,101 fallback as a delivery selection and reacted with an extra zoom/recenter. Fix app-owned camera calls to explicit three-argument isUserAction=false, consume initial recenter token without an unsolicited first-pan zoom, and add real-SDK Android callback/gesture regression. This is a confirmed camera-ownership/wrong-default-selection defect; its relationship to the intermittent missing tile remains a hypothesis pending runtime/visual retest. Process route-loss probe still pending; do not fix/certify that solely from source.
- IN PROGRESS after verified Guest30 PASS: reproduce actual unsaved-picker process-death restoration. Existing saved-draft component tests do not prove the parent route survives. Temporarily skip the already-passed30-cycle stress while running this targeted real-App probe; re-enable full stress after any product fix. No product change in this probe checkpoint. Runtime expected outcome must come from the uploaded XML/screenshot/log/result, not source inference. Finish this chunk first after interruption.
- VERIFIED9df7e8c1751f31dbaf0710f5d0b947f9be749dbe: Native Pilot38859123459 SUCCESS. Actual Guest Home→GPS/pan/reverse geocode→Save→Home30/30 PASS,6 early background/resume cases,3 confirmed system-kill/new-PID/local-address restores. Artifact12028264236 archive SHA2560a2209bc702acfa97c773d2fa5585342462d0d9661fd6c84c2d63c43b1ac3423 verified; results.json exact-source identity/count checked, continuous log has0 matched crash/ANR/native-map failures. First/last Home screenshots inspected; last is immediate cold-start loading, not a fully settled-shop certification. No injected GPS/address and no Production writes. This does not certify authenticated Save, shop switching or physical GPS.
- Exact9df7e8c full local npm regression PASS. All3 fresh APKs artifact12028816850 archive SHA2569e5d1b0a6271329df93a587d44dd199cb8b98b9e2916cf951b574c5970856e85 verified; actual manifests, DEX source identity and official apksig API26–36 all PASS. Customer SHA256e64f4f9a89988d6d9359bca7a60a972dab8c27f11b9aacdc831768281a5f7f79; Merchant e979487284367e5b13478f2ac887756dcab59d2d166db29ab54e37dcee6017a3; Rider f34a327022d01f729628890b6cf9debbc61bfcb67c4a3fd9bf8faa7a7d3b0f82. CI Firebase/16KB/navigation/role startup gates PASS.
- NEW CONFIRMED RELEASE BLOCKER: all3 share signer f5218612ebbb9f6db59ecab35a994d020350206a448f374d9cf38e4445478da5, different from604e5d8c signer c4fa2eb356114221f9e107cd2ea2fc4ab27da22bf8bc6968a6c755f10b334d32. Pilot uses AGP default debug signing on fresh hosted runners, which generates a new key each run; same package/version cannot update across these identities. Do not publish these as an in-place update, instruct uninstall/data loss, or pretend an old private key can be reconstructed from an APK. Secure stable test-signing integration is required; existing release signing env is separate and must not leak into public clients/artifacts.
- Exact604e5d8c: Customer runtime38856784243 SUCCESS API26/30/33/35; Native Pilot38856784397 SUCCESS including actual guest permission/GPS/pan/background/back. APK artifact12027567705 contains three same-source APKs; full hashes/signatures recorded in PR366. No overall authenticated/physical acceptance claimed.
- Owner explicitly authorizes existing-account QA without entering passwords or creating accounts. SQL connection is not an Auth session: Vault has no matching Auth/service-role/QA secret names; inherited CI38837872201 reports credential_not_available_to_ci. No passwords changed, sessions taken over, or Auth bypass added. Authentication remains technically blocked, not waiting for owner permission.
- The30-cycle guest Save subtask is complete on9df7e8c with the evidence above. Next: address remaining process/draft restoration and shop-switching gates, plus secure stable-signing integration. Authenticated and physical gates remain blocked; do not start design.

## Latest completed source checkpoints
- b77a2894: reject cross-shop payloads and project rendered catalog by selected shop; 30 rapid-visit JVM test added.
- d1bd7152: committed real Longdo picker UI harness and API26/30/33/35 runner matrix (run38853538310). JVM tests/builds passed in matrix. Runtime evidence still pending/failing infrastructure.
- Address draft fixes: GPS/permission belong to picker, not global delivery location; generated address invalidates on pin changes; exact revision rejects A-B-A late geocode; manual address wins; draft has SavedState saver.

## Completed 78bb8507 verification checkpoint
- Exact source78bb8507671313f477ae242abb5e30fb6e6d190e: matrix38855082527 SUCCESS on API26/30/33/35;120 isolated real-GL picker cycles, draft SavedState restore and Build Info. Continuous log scanning and required app-private screenshot extraction passed.
- Exact-source Native Pilot38855082561 SUCCESS: full regression, all-role JVM, all3 fresh APKs, Firebase,16KB, SQL race gates, Rider map/voice/location lifecycle and9 role/viewport startup cases. System-kill/cold-relaunch smoke PASS for all3 on API30. These are fresh-session checks, not authenticated state restoration.
- APK artifact12027375827 archive SHA25639d38d503947e245df345210a683af39018f66e04e476bdf7727de86b8b826d5 verified. Actual manifests parsed, source78bb8507 present in each DEX; official Google apksig9.4.0 verifies all3 signatures on API26–36 with no errors and same signer certificate. Full per-APK hashes/versions and evidence links are recorded in PR366.
- API30 harness screenshots inspected: round29 has rendered real map tiles; round0 captures initial tile loading. Harness theme is not product-design certification. No Design Pass started.
- Read-only Production audit: optional order columns still absent; PR365 fallback preserved. Only the default Supabase branch exists, and no physical-device runner/test login is accessible. No Production writes/deployment.
- Found coverage gap: actual Customer Home→Location→Android allow/deny/GPS/pan/background/back helper exists but its job was skipped on this branch. Enable that EXISTING job here and rerun exact-source gates. A same-SHA QA ref alone did not dispatch Actions; no alternate implementation was created.
- Release remains BLOCKED pending real authenticated Online30cycles, full shop/location UI switching, physical crash reproduction, authenticated process-death and complete role E2E. Do not replace these with isolated harness PASS.

## Current exact-source evidence
- 0ea86732: clean full local npm regression PASS; Customer compile/JVM PASS on all4 API jobs.
- Matrix38854480449 API30:3 real Android UI tests PASS, including30 picker/early-background cycles, SavedState restoration and exact Build Info; no matching crash/ANR/map lifecycle errors. API33/35 still running when this checkpoint was written.
- QA bugs found and fixed: API26 logd rejects log clearing even through device shell; use continuous tail capture without destructive clears. API30 adb shell cannot pull app-scoped external evidence; screenshots now live in app-private files and are extracted using debug run-as. Missing screenshot now FAILS the test instead of silently skipping visual evidence.
- No product/runtime PASS claimed for API26 yet. No physical/authenticated Online gate. PR366 stays Draft, no release.

## Latest verification / next checkpoint
- Full local `TZ=Asia/Bangkok npm test` PASS on clean a60904b3. Default PGlite host timezone Etc/GMT+8 made a pre-existing current_date-vs-Bangkok fixture fail; no product SQL was changed to bypass it.
- Matrix38854044566: Customer Kotlin/APK and JVM tests PASS, including new catalog/draft behavioral tests. UI-harness compile exposed an unavailable Espresso reference; replace it with Android InputMethodManager and rerun. Do not claim Android runtime PASS for a60904b3.
- Earlier d1bd7152 API35 isolated real-GL picker harness completed30 cycles without crash/ANR or swallowed map lifecycle errors. That is baseline only, not final patched-source acceptance.
- New Build Info: Account-only test dialog for all3 roles; one root Gradle SHA/time source. Pending all-role compile/runtime verification. No redesign.

## Local validation
- Native source272, stability106 and Actions pinning80 PASS.
- Full npm regression reached release-evidence-bundler, which requires a clean checkout; current uncommitted source correctly rejected. Rerun from committed clean HEAD, do not weaken that guard.
- Baseline API26 runtime blocked before instrumentation by new host logcat clear vs old logd. Runner now invokes device-matched shell logcat for clearing; retest required. This is NOT an app crash finding.

## In progress
- Customer address lifecycle/GPS and stale shop/location investigation.
- Preserve existing catalog generation cancellation; verify rendered screen owner and returned product shop IDs.

## Evidence and limitations
- Inherited Native Pilot 38837872255 SUCCESS is baseline evidence only.
- This host has no adb, Android SDK/emulator or /dev/kvm. Use GitHub Android runners for runtime tests; never claim local device testing.
- Git HTTPS push is unavailable on this host; connected GitHub Git-data API supports checkpoint commits with an expected-head lease.
- No Production writes, deployments, real orders, payments or account changes.

## Remaining gate
- Address crash reproduction with logcat/native/ANR evidence, fix and retest.
- 30-cycle address, shop/location and authenticated Rider Online tests.
- API26/30/33/35 runtime matrix; permissions, background/foreground and process death.
- Same-source clean three-app build with Build Info and full provenance/signature/16KB evidence.
- Design work is NOT started. Release BLOCKED; no new public APK delivery.
