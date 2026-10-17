# QueueGo Map Buriram release gate — OPEN

Candidate branch: work-map-roadgraph-buriram-20261017. Integrated main: 513398a1f6f09cb8b58aceabe8dcb32616d2ae2c. Use git log and evidence/map-engine for exact checkpoints. No Production deployment or database mutation.

| Gate | Evidence / state |
|---|---|
| Real road graph build | PASS: normalized 233,320 segments; all three benchmark engines built |
| A→B, alternatives, multi-stop, matrix, matching, reroute, signals | PASS local actual graph runtime (283 assertions); controlled timing tests are not road recordings |
| One-way | PASS sampled directed-edge checks from 12 actual OSM ways; not national coverage |
| Turn restrictions | OPEN: 62 source relations, directional fixture evidence in turn-restriction-audit.json. No false pass for uncovered approaches |
| Buriram road categories / physical validation | OPEN: current query/one-way evidence does not cover every required category or physical road condition |
| Web calls new core | PASS local actual Chromium: route/matrix/nearest/search, actual PMTiles, external requests blocked, retained map and idle pitch/bearing zero |
| Offline data integrity/activation/rollback | PASS local 6 actual-package checks; data package is not an Android runtime |
| Full npm | PASS locally after service wiring; rerun after further changes |
| CI on latest candidate | Prior head 2a1b6307 GREEN (Map Engine38986601941/38986605164; RC38986605032); merged-main checkpoint pending its own runs |
| No fake / proprietary map data | OSM source SHA/provenance retained; no Google Maps geometry or proprietary service endpoints |
| Licence documentation | docs/map-data-sources.md, map-engine-selection.md, map-offline-package.md; unknown official-source terms remain quarantined |
| Public hosting / publication | OPEN: local service only; no Production route fallback silently introduced |

Two malformed OSM restrictions are also malformed in the upstream OSM API, independently checked on 2026-10-17:

- https://api.openstreetmap.org/api/0.6/relation/2148441.json — version 2, 2017-11-21: only_straight_on has only a from-way (160599219); no to/via members.
- https://api.openstreetmap.org/api/0.6/relation/17320674.json — version 1, 2024-03-08: no_straight_on has node 3500528677 and ways 170350136/1258987446, all member roles empty.

Do not invent missing restrictions or claim their enforcement. Source remains preserved as unverified; field/authoritative verification is needed before certifying affected junctions. A to-way may cross both sides of an interior via node: no_left_turn must check the outgoing turn direction, not reject a legal right turn simply because it shares the same OSM way ID. The audit models this explicitly.

The CI candidate job fails on observed illegal turns but does not silently convert uncovered or malformed restrictions to PASS. A green candidate workflow alone does not close this release gate. No sole-navigation/Android production release until these gaps and physical operation are evidenced.

Runtime policy now fails closed on **all three member ways** of the two malformed relations. Route/alternatives/optimization/matching validate actual traversed way IDs; matrix reachable pairs are audited against the same exclusions and return null if rejected. Native graph data stays unchanged and source tags are preserved; this is an explicit QueueGo uncertainty policy, not an invented traffic rule. Region coverage around those ways is unavailable until independent verification. Map rendering/GPS display can continue.

Field inspection reference: relation 17320674 has source node at **14.9939137, 103.1069056**; its approach/departure roles are unknown. Relation 2148441 has only from-way 160599219; **15.0133018, 102.8373594** is a representative source vertex on that way, NOT an asserted junction location. Verify actual signs/directions before replacing quarantine. No OSM edits or guessed source repairs were made.


## Physical field QA tooling — READY, evidence still OPEN

The candidate now includes an opt-in `/map-view/?field-qa=1` recorder plus `ops/maps/validate_field_evidence.py`. The recorder stays hidden during normal map use, never uploads raw GPS automatically, captures package identity, accepted GPS fixes, shared-core map matching, active-route progress, GPS gaps and background/foreground transitions, and exports versioned JSON for owner-controlled review.

Validation is fail-closed: a tool pass is only **candidate physical evidence**, never certification and never an automatic release-gate close. Required real sessions remain village lane, frontage/service-road behavior, U-turn, parallel-road GPS, signal-loss/recovery, background→foreground, reroute and arrival. See `docs/map-field-qa.md`.


## Source-direction conflict quarantine

Restriction relation **1538817** is structurally complete but conflicts with the current one-way direction of its own OSM members in the installed 2026-10-15 extract:

- relation: `only_straight_on`
- from way: `1090716630`
- to way: `1090716624`
- via node: `370559614`
- both member ways are `oneway=yes`
- the from way currently begins at the via node, so legal one-way travel cannot approach the via on that member
- the to way currently ends at the via node, so legal one-way travel cannot depart the via on that member

QueueGo does not swap the relation roles or ignore the one-way tags. The relation is exposed as a `source_direction_conflict` and all its member ways are quarantined from usable routes until independently verified. This is separate from the two malformed relations 2148441 and 17320674. Rendering can continue, but route/matrix/optimization output must fail closed around these uncertain source rules.
