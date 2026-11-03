# Rider stale-route invalidation signal without GPS — 2026-11-03

Source base: Native #824 `e788ba3a3299b47afc271b80d1c6d916d1f88e50` after merging #843. Isolated branch, no Production or order mutation.

## Defect and bounded change
`NavigationRouteCoordinator.synchronize` removed a completed pickup's or corrected destination's old route from `mapState.routes`, but returned `routeChanged=false` when GPS was missing or the new route provider failed. A consumer relying on `routeChanged` could retain a stale visual line. The existing `safeState` filtering is preserved; the result now reports a route change when and only when its route list differs from the incoming state, even before a new route is available. It does not invent a replacement line or offline navigation.

## Regression
Three existing Kotlin/JVM route-coordinator cases now assert the invalidation signal: completed pickup with no GPS, failed provider for next stop, and same-stop-ID corrected coordinates with no GPS. Exact-HEAD hosted Native Pilot, Customer/Merchant/Rider Kotlin+APK, and isolated RPC must pass before merging.

## Remaining gates
#801 nationwide offline graph/routing, #750 visually readable road labels, #822 authenticated GPS permission and UI evidence, #740 missing backend RPCs, physical road tests and Web visual parity remain open. This change does not authorize APK release or Production deployment.
