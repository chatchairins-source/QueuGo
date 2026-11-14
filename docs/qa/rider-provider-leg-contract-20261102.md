# Rider provider-boundary navigation integrity — 2026-11-02

Source: Native #824 at 7b5e8826a768c421e591238fd16cd4d9590ffdf7, isolated successor branch.

## Observed defect
NavigationRouteCoordinator previously trusted any NavigationRouteProvider result (including a future offline provider) without checking requested segment identity, destination, endpoint proximity or summary validity. An incorrect provider response could become active guidance, and an invalid reroute could replace a previously verified leg. Once every business stop completed, reset() cleared internal state but returned the old route geometry in mapState.

## Narrow fix
Validate candidate identity, stop ID, both endpoints (750 m, matching existing online-client bound), finite positive distance and nonnegative finite optional duration **before** committing activeLeg or rerouteVersion. Fail through existing route error/backoff handling; retain the last verified leg on a rejected reroute. Clear old map routes and activeStopId once no stops remain. No straight-line fallback or fabricated offline routing.

## Regression
Four new deterministic Kotlin/JVM tests: all-stops-completed cleanup, mismatched provider stop, mismatched origin, rejected reroute preserving verified leg. Run exact-commit Native Pilot and parallel role APK/JVM + isolated RPC jobs; inspect actual HEAD SHA before merge.

## Remaining release gates
#801 real nationwide offline routing/turn restrictions, #750 visible Bangkok names, #822 authenticated GPS permission/dispatch, #740 missing RPC, UI screenshots and physical E2E. This does not authorize APK publication, Production changes, or claims of road-test readiness.
