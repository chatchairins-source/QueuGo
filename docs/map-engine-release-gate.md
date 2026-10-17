# QueueGo Map Buriram release gate — OPEN

Candidate branch: work-map-roadgraph-buriram-20261017. Baseline main: a78bca91f723db3f2117030e91c5d9901413e00a. Use git log and evidence/map-engine for exact checkpoints. No Production deployment or database mutation.

| Gate | Evidence / state |
|---|---|
| Real road graph build | PASS: normalized 233,320 segments; all three benchmark engines built |
| A→B, alternatives, multi-stop, matrix, matching, reroute, signals | PASS local actual graph runtime (273 assertions); controlled timing tests are not road recordings |
| One-way | PASS sampled directed-edge checks from 12 actual OSM ways; not national coverage |
| Turn restrictions | OPEN: 62 source relations, directional fixture evidence in turn-restriction-audit.json. No false pass for uncovered approaches |
| Buriram road categories / physical validation | OPEN: current query/one-way evidence does not cover every required category or physical road condition |
| Web calls new core | PASS local actual Chromium: route/matrix/nearest/search, actual PMTiles, external requests blocked, retained map and idle pitch/bearing zero |
| Offline data integrity/activation/rollback | PASS local 6 actual-package checks; data package is not an Android runtime |
| Full npm | PASS locally after service wiring; rerun after further changes |
| CI on latest candidate | PENDING new QueueGo Map Engine Buriram workflow |
| No fake / proprietary map data | OSM source SHA/provenance retained; no Google Maps geometry or proprietary service endpoints |
| Licence documentation | docs/map-data-sources.md, map-engine-selection.md, map-offline-package.md; unknown official-source terms remain quarantined |
| Public hosting / publication | OPEN: local service only; no Production route fallback silently introduced |

Two malformed OSM restrictions are also malformed in the upstream OSM API, independently checked on 2026-10-17:

- https://api.openstreetmap.org/api/0.6/relation/2148441.json — version 2, 2017-11-21: only_straight_on has only a from-way (160599219); no to/via members.
- https://api.openstreetmap.org/api/0.6/relation/17320674.json — version 1, 2024-03-08: no_straight_on has node 3500528677 and ways 170350136/1258987446, all member roles empty.

Do not invent missing restrictions or claim their enforcement. Source remains preserved as unverified; field/authoritative verification is needed before certifying affected junctions. A to-way may cross both sides of an interior via node: no_left_turn must check the outgoing turn direction, not reject a legal right turn simply because it shares the same OSM way ID. The audit models this explicitly.

The CI candidate job fails on observed illegal turns but does not silently convert uncovered or malformed restrictions to PASS. A green candidate workflow alone does not close this release gate. No sole-navigation/Android production release until these gaps and physical operation are evidenced.
