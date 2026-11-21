# Rider overview camera / corrected destination CI regression — 2026-11-03

## Exact-source failure
- Source: PR #837 `ebb043d988ac2732d8d4f4c1decad63d05d88cc9`, based on Native #824 `7b5e8826a768c421e591238fd16cd4d9590ffdf7`.
- GitHub Actions Native Pilot run [41481607280](https://github.com/chatchairins-source/QueuGo/actions/runs/41481607280), job `125403552377`: `:rider-navigation-map:testDebugUnitTest` failed **1 of 157 tests**: `NavigationOverviewPolicyTest.overviewIncludesRiderRemainingStopsAndRouteGeometry` at line 49.
- Customer, Merchant and Rider parallel JVM/APK jobs and isolated RPC catalog succeeded; dependent runtime and release jobs were skipped. Do not mark PR #837 green.
- Root cause: the inherited overview test route ended at (15.0100, 103.1500), over 750m from its pickup (15.0000, 103.1100). PR #837 intentionally rejects such stale route endpoints, so the overview no longer included the route's eastern bend, contradicting the fixture's expected bounds. This is a stale test geometry, not a reason to relax the production guard.

## Isolated correction
- Keep the bend (15.0100, 103.1500) and append the **actual pickup endpoint** (15.0000, 103.1100). The test still verifies overview includes road geometry that extends beyond the stop markers.
- Add `staleFutureRouteDoesNotDistortOverviewAfterDestinationCorrection` to verify that a future route ending far from a corrected destination cannot stretch the overview camera to the stale coordinate. Current pickup/drop pins remain in the overview.
- Production Kotlin+Compose, route provider, Auth, financial/RLS, and Supabase are untouched.

## Acceptance / next actions
1. Run exact-head Native Pilot on this branch. Require full native-pilot plus all three role JVM/APK and isolated RPC jobs PASS; inspect job logs for any failures.
2. Merge this isolated QA fix into PR #837 branch **only after exact-head CI passes**. Re-run and verify the new combined PR #837 HEAD, then reconcile with moving #824 before merging.
3. Preserve release blockers: #801 nationwide offline routing, #750 visual road labels, #822 authenticated GPS/UI, #740 RPC contracts and physical-device navigation. Do not publish a road-test APK or claim Production readiness from these unit tests.
