# Rider route-cache clock rollback guard — 2026-11-02

## P1 source defect
On Native #824 exact parent `ed64b0d052ac8b785764c27de2527aca093a9c95`, `CachingNavigationRouteProvider.pruneLocked` evicted only entries older than TTL by evaluating `now - createdAtMillis > ttl`. If Android wall-clock time moved **backwards** (e.g. time synchronization) during the running process, cached future route geometry acquired a negative age and remained eligible for reuse far beyond its intended eight-minute TTL.

## Narrow fix
Evict any cache entry whose creation timestamp is in the future relative to the current clock, as well as normally expired entries. Keep the original TTL boundary and exact stop/destination/origin restrictions; reroute still bypasses cache. No new route geometry, no offline pathfinding, no API/auth/backend changes.

## Tests
Two deterministic JVM cases cover backwards clock correction and TTL boundary preservation. Exact-head Native Pilot (Customer/Merchant/Rider JVM/APK and isolated RPC) must pass before integrating into unreleased Native #824. This does not close #801 nationwide offline routing, #750 labels, #822 GPS UI, #740 RPC, or physical-device release gates.
