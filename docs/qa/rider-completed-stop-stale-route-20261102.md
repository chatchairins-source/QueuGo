# Rider completed-stop stale active route — 2026-11-02

## Exact source and P1 defect
Base: unreleased Native PR #824 `0a94d90465c30e62351ea8c1c55e1ae79a7a9671`. In `NavigationRouteCoordinator.synchronize`, an active stop ID change resets the internal route, but the previous active pickup's rendered route is not removed if GPS is missing or the new route request fails. Only a corrected coordinate under the *same* stop ID was handled by the previous stale-line guard. This can display a completed pickup as the active route after progressing to dropoff.

## Narrow fix
Remember the prior active stop ID before reset. On active stop ID change, remove only routes targeting the old active stop; preserve the new stop's existing future leg. Keep same-ID coordinate correction handling, successful route replacement, and retry logic unchanged. No fake route or straight-line fallback.

## Deterministic regression
1. Complete pickup, remove GPS: old active route disappears; previously verified future route remains; GPS restoration requests a fresh active dropoff leg.
2. Complete pickup and fail new route API: old active pickup route does not remain visible; error and future leg are preserved.

Exact-commit Native Pilot, three-role Kotlin/JVM+APK and isolated RPC CI must pass before merge; then re-run integrated Native #824 HEAD. #801 offline graph, #750 label visibility, #822 authenticated GPS, #740 live RPC and physical/E2E remain open. No Production writes.
