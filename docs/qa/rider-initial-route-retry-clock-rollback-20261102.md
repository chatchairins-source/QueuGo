# Rider initial-route retry after backward device-clock correction — 2026-11-02

## Source and P1 defect
Based on unreleased Native #824 exact HEAD `0a94d90465c30e62351ea8c1c55e1ae79a7a9671`. `NavigationRouteCoordinator` stores an initial-route retry deadline as wall-clock milliseconds. After a routing API failure, a backward device clock correction can leave that deadline arbitrarily far in the future, suppressing fresh route requests even when connectivity returns. Existing backoff tests covered forward-moving time only.

## Narrow change
Record the timestamp of the last initial-route failure. If a later synchronization observes time earlier than that failure, reset **only** the initial retry backoff and attempt the current active stop again. Ordinary 1.5s-to-12s exponential backoff, cancellation propagation, completed-stop handling and active-route geometry remain unchanged. No offline routing claim.

## Regression
Two new deterministic Kotlin/JVM tests: (1) backward correction after a failure triggers immediate recovery, (2) a second failure after correction still enforces normal backoff before a successful retry. Existing forward-clock backoff test must continue to pass. Run exact-head Native Pilot and three-role matrix, then revalidate integrated #824 if merged.

## Gates
No Supabase Production write, Auth/RLS/finance/schema change, WebView, fake map or APK release. Offline Thailand routing #801, actual road-label screenshots #750, authenticated GPS #822, backend contract #740 and physical road/E2E release gates remain open.
