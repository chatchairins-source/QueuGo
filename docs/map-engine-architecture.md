# Shared core and Thailand expansion

Implemented first region: padded Buriram operating envelope, not an administrative province polygon. Source snapshot identity and bounds are part of every graph/service response. Requests outside the installed envelope fail; they do not quietly switch to a public engine.

| Layer | Implemented boundary |
|---|---|
| Source ledger | Dataset-specific licence, timestamp/hash, OSM IDs/versions/raw tags; unknown official-source terms quarantined |
| Canonical road database | Normalized SQLite nodes/segments/restrictions/attributes/signals/barriers plus spatial/name indexes. Shared OSM node IDs create connections; geometry crossings do not |
| Routing | One Valhalla Actor with native vehicle costing, restrictions, alternatives, matrix, optimization and HMM; shared JSON API |
| Navigation | Shared maneuvers/progress/ETA, fix rejection, repeated off-route/arrival evidence, cooldown; client preserves intent and recalculates on restore |
| Display | MapLibre + independently versioned Shortbread PMTiles; local assets and attribution. No routing assumptions inferred from visible tiles |
| Updates | Immutable per-file checksummed packages; verify/install/activate/rollback. Runtime restart/reload at the release gate; no implemented delta/signing/hot-swap claim |
| Consumers | One SDK/API contract; apps own presentation and business state, never separate route engines |

Expansion must preserve these boundaries:

1. Build the Thailand parent graph from one pinned national OSM snapshot and common Valhalla configuration. Export consistent regional tile subsets with a routing halo and shared global tile/object IDs. Independently building arbitrary province graphs and concatenating tile directories is NOT a supported national update strategy.
2. Define package ownership by stable geographic tile IDs, with explicit dependency/halo manifests and country-level version compatibility. Deduplicate canonical OSM `(type,id,version)` records by identity; conflicting versions block activation. A route crossing regions requires a compatible union of installed tiles or the national server graph, never a straight-line bridge or public fallback.
3. Keep administrative boundary/coverage claims separate from padded operating envelopes. Country/province attribution and left-hand driving metadata are compiled from the source admin dataset. Validation must include border routes and missing-package behaviour before expansion.
4. Store Overture/DOH/DRR candidates in a separate provenance namespace after licence clearance. Conflation requires reviewed topology, level, access, direction and endpoint compatibility. Parallel carriageways and frontage roads must not merge by proximity. Preserve source records and review evidence.
5. QueueGo historical speeds/closures need consent/provenance, a separate versioned observation overlay, sample-count/time/quality checks and a verified ingestion pipeline. No such production feed has been collected. Unknown speed/closure/safety stays unknown; current estimated speeds and surface penalties are disclosed.
6. Android offline integration must bind the same selected native core/package contract and benchmark JNI, disk, memory, battery, background service and actual riding. Data packages alone do not provide an Android runtime. Keep map renderer identity/lifecycle independent from route/GPS state; no style reload per fix.

National package partition/export, incremental updates, signatures, history/traffic overlays and Android runtime remain future work, not hidden completed features. Current evidence and unresolved release gates: docs/map-engine-release-gate.md.
