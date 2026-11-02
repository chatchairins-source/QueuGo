# Rider route endpoint safety — 2026-11-02

## Observed source defect
The Native Rider `QueueGoNavigationRouteClient.parseQueueGoRoute` accepted any valid LineString returned by `/map-api/route` and labelled it as the requested stop's route. It did not check that the last point was near the pickup/drop-off coordinate or that the first point was near the actual Rider GPS request. A stale/misrouted backend response could therefore be drawn as an apparently authoritative navigation line to the wrong destination.

## Isolated source fix
- Validate that a returned route ends within **750 meters** of the requested stop. For live requests also require its first point within 750 meters of the requested Rider origin. These are bounded road-snap tolerances, **not** route-generation or road-access guarantees. A route exceeding either tolerance is rejected and existing coordinator retry/error handling applies. Small valid road snaps continue working.
- Six deterministic JVM parser regressions cover wrong stop, wrong Rider origin, acceptable snap, out-of-range snap, malformed interior coordinates and a null interior vertex. The original parser tests remain.
- Previously malformed middle points were silently skipped, drawing a false straight line between non-adjacent road vertices. Reject malformed or incomplete coordinate arrays rather than inventing an unsafe shortcut.
- Kotlin+Compose, Customer/Merchant online maps, Rider PMTiles, API, Auth/RLS, financial and Production data remain untouched. No fallback line is fabricated.

## Gates
Built on exact unreleased #824 HEAD `829627936999b24130bf0e59f6bef906fd4d80b6` after #831 merge. Must pass exact-head Native Pilot, 3-role JVM/APK and isolated RPC jobs before merging into #824. Run authenticated emulator GPS/route screenshots and device checks; this source guard **does not** implement offline routing. Issue #801 nationwide offline graph, #750 Bangkok labels, #822 GPS readiness, #740 live RPC and all release gates remain open.

## Next
Verify backend's actual route snap distribution on an isolated route dataset before tightening this bound; ensure safe user-facing error when no navigable road is within 750m. Implement licensed nationwide offline road graph/provider, turn restrictions, offline reroute and guidance, then physical GPS/airplane-mode acceptance. No APK publication.
