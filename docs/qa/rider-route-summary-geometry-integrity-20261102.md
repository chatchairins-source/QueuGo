# Rider route summary vs geometry integrity — 2026-11-02

## Exact source and problem
This isolated change starts from Native PR #824 merged HEAD `0a94d90465c30e62351ea8c1c55e1ae79a7a9671`, preserving merged PR #832 route endpoint and interior-coordinate guards. An otherwise valid response could contain a huge disconnected interior jump yet report a short `distance`, giving apparently authoritative route/ETA despite impossible geometry. Endpoint-only checks do not catch this.

## Production-source change (unreleased)
Compute the actual haversine sum over returned LineString vertices. When a positive finite server route distance exists, reject responses whose reported distance is less than 90% of that polyline sum; a simplification can shorten the polyline but not make the real road much shorter than the displayed geometry. If the backend omits a positive finite distance, retain the existing measured-geometry fallback. No fabricated road segments or offline route provider.

## Tests and gates
Four new Kotlin/JVM tests: disconnected interior detour with a false short summary must fail; a short summary for otherwise contiguous geometry must also fail; legitimate road distance above simplified geometry must pass; absent summary must use measured geometry. Adjust an old duration fixture with an impossible 100m summary over ~1km geometry to 1200m without changing its duration assertion. Run exact-head Native Pilot (Customer/Merchant/Rider JVM+APK, isolated PostgreSQL RPC and Native checks), then integrated #824 head after merge. Review actual route summary distributions on isolated backend samples before considering stricter tolerances. No Production/Auth/RLS/finance or schema changes.

## Release blockers unchanged
#801 nationwide offline graph/reroute/instructions; #750 real Bangkok road-label visibility; #822 authenticated GPS state; #740 missing RPC/live contracts; physical GPS, navigation UI screenshots, speed zoom and end-to-end orders. This PR does not certify nationwide offline routing, on-device rendering or road-test APK readiness.
