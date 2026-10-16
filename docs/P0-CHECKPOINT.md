# QueueGo P0 resume checkpoint

Base: `dda4d1c4b912a9bbb2a9e8b4aaeb05f12a391870` on `work-native-credit-live-enablement-20261016`.
Current exact checkpoint SHA: obtain with `git rev-parse HEAD` (a commit cannot embed its own SHA).

## Completed
- Fetched open PRs, working branches, recent workflows, artifacts and releases.
- Proved latest native successor descends from online compatibility fix e2f64360.
- Read AGENTS.md and PROJECT-MEMORY.md; current owner mission takes precedence over prior deployment objective.

## Latest completed source checkpoints
- b77a2894: reject cross-shop payloads and project rendered catalog by selected shop; 30 rapid-visit JVM test added.
- d1bd7152: committed real Longdo picker UI harness and API26/30/33/35 runner matrix (run38853538310). JVM tests/builds passed in matrix. Runtime evidence still pending/failing infrastructure.
- Address draft fixes: GPS/permission belong to picker, not global delivery location; generated address invalidates on pin changes; exact revision rejects A-B-A late geocode; manual address wins; draft has SavedState saver.

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
