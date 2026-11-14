# Rider future-route partial-refresh recovery — 2026-11-02

## Observed source defect

On Native PR #824 head `a4c749eaa0a46fdf08365918ff003ba0ac48530a`, `buildFutureRoutes` requests each future leg sequentially and stops at the first routing API failure. PR #826 preserved cached future geometry only when the refreshed list was **empty**. If the first leg succeeded and the second failed, the nonempty partial result replaced the entire prior list, erasing the still-valid second leg. This is a P1 resilience/visual continuity defect, not nationwide offline routing.

## Narrow source change

`riderFutureRoutesAfterRefresh` now derives the expected consecutive future leg IDs from the **current** ordered, uncompleted stops. For each expected leg it prefers fresh renderable geometry matching both `future:<from.id>><to.id>` and `toStopId`, otherwise retains the exact matching previously verified segment. It rejects wrong topology, mismatched destination, invalid geometry and obsolete/completed stops. The caller already keys retained state by `stateKey + stopTopology` including stop coordinates, sequence and completion; this change does not bypass that isolation.

Regression tests cover transient total outage, successful replacement, cold offline start, partial failure after one successful leg, mismatched identity/topology, invalid fresh geometry and stop completion/removal. No fake path, straight line, offline graph, GPS/dispatch authority or Production data is introduced.

## Release gates and validation

- Run exact-commit Native Pilot Kotlin/JVM and Rider APK tests; compare all three roles and RPC job outcomes. Inspect actual failing logs before merging.
- Confirm on authenticated emulator with a real previously loaded multi-stop route: partial routing API failure retains only still-valid previously fetched future segments, and topology change clears stale geometry. Capture timestamped screenshot and logs. This is **not** yet a device-verified result.
- Nationwide offline route graph/pathfinding and airplane-mode reroute remain blocked by #801. PMTiles only render offline map tiles. Bangkok street label visibility #750, GPS #822, Backend RPC #740, and physical/E2E acceptance remain open.
- No Supabase migration, order/finance/Auth/RLS changes or Production APK publication.
