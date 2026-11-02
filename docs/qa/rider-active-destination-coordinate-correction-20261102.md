# Rider active destination coordinate correction — 2026-11-02

## Source defect and scope
Native #824 exact parent `ed64b0d052ac8b785764c27de2527aca093a9c95` was inspected after PR #828 merge. `NavigationRouteCoordinator` invalidated its active leg only when the **stop ID** changed. A Customer/merchant corrected GPS pickup or drop-off could retain the same business stop ID but move the coordinates. The coordinator then continued guiding along the old active leg without asking the route provider for the new destination. This is a P1 navigation correctness and safety issue, separate from offline graph #801.

## Narrow remediation
Track the last active stop point alongside its ID. If either changes, clear active route/progress/reroute state and request a fresh authoritative leg. On same-ID coordinate correction, also remove any previously rendered route to that active stop from the incoming map state before processing; do this even if there is no GPS fix or the online route request fails. Preserve unrelated future-stop geometry. No straight-line substitute, no offline routing claims, no Auth/RLS/finance/Production changes.

## Acceptance
Three deterministic Kotlin/JVM regressions: same-ID coordinate correction obtains a fresh leg ending at the corrected point; online failure does not leave the old active line; absent GPS clears the old line without requesting a route. Run exact-commit Native Pilot and all-role smoke tests, then authenticated emulator screenshot/GPS readback and real-device acceptance. This source test is not evidence of physical navigation, offline route graph, #750 labels, #822 readiness, or #740 RPC remediation.
