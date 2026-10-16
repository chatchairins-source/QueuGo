# QueueGo Map Lab — Buriram web-first navigation data prototype

Status: experimental web-only research surface. It is isolated at /map-lab/ and does not alter Customer, Merchant, Rider order state, payments, Supabase production data, or Android navigation.

## Goal

Build the first visible QueueGo-owned map data workspace before moving the navigation stack into Android. The browser page is deliberately a data and safety lab, not a claim that turn-by-turn navigation is production-ready.

The prototype covers Buriram Province using the working bounding box:

- south 14.132135
- west 102.442357
- north 15.789217
- east 103.499967

## Data sources in the first prototype

1. OpenStreetMap through Overpass
   - traffic signals
   - stop / give-way controls
   - railway level crossings
   - traffic calming
   - speed cameras where mapped
   - optional hospitals and schools
   - attribution is always shown on the map
   - OSM data remains subject to ODbL

2. Department of Rural Roads (กรมทางหลวงชนบท / DRR)
   - the web prototype discovers road-like layers from the published DRR FeatureServer and queries only the Buriram envelope
   - failure is isolated; DRR downtime or CORS must not break the base map or route audit
   - imported features retain source and source_layer provenance

3. Ministry of Transport / NGIS national-highway layer
   - the web prototype now queries the published NGIS ArcGIS Feature Service for ทางหลวงแผ่นดิน inside the Buriram envelope
   - the layer is a verification/enrichment overlay only; it is not silently merged into the OSM routing geometry
   - imported features retain source and source_layer provenance
   - endpoint/CORS failure is isolated and must not break the base map or route audit
   - redistribution/licence terms still require review before any NGIS geometry is baked into QueueGo offline packages

4. QueueGo observations
   - reserved for rider road reports, GPS traces, missed turns, closure reports and verified local corrections
   - no production telemetry write is introduced by Map Lab

## Important safety rule: traffic-light timing

The map may show where a traffic signal exists. It must NOT show “green in N seconds” or “red in N seconds” unless QueueGo has an authoritative, time-synchronised signal controller/SPaT feed for that intersection.

Current signal records therefore use signal_timing=unknown by default.

Future canonical signal record:

- signal_id
- intersection_id
- geometry
- approach
- source
- source_feature_id
- controller_owner
- timing_mode: fixed | actuated | adaptive | unknown
- phase_feed: authoritative endpoint or null
- observed_cycle_seconds
- observation_count
- last_verified_at
- confidence
- provenance[]

Observed rider waiting time may improve ETA statistics, but it must never be presented as a live signal countdown.

## Web Map Lab behaviour

- QueueGo-branded MapLibre surface
- Buriram province working extent
- live OSM safety inventory with redundant Overpass endpoints
- NGIS national-highway overlay with ArcGIS JSON conversion and timeout isolation\n- DRR road layer with source discovery and timeout isolation
- optional school/hospital layer
- click start + destination and request a prototype route
- audit loaded traffic signals and caution points within 45 metres of the route
- never fabricate a straight-line route if the routing provider fails
- export currently loaded features as GeoJSON with source attribution/provenance
- exact live signal phase is shown as unavailable unless an authoritative feed exists

The routing request currently uses the public OSRM demo endpoint only to prove the UI and route-audit workflow. This endpoint is not a production dependency.

## Production target architecture

Raw licensed/open datasets
→ immutable source snapshots
→ source-specific normalizers
→ geometry/topology validation
→ provenance-preserving canonical road store
→ road graph build
→ turn/access/restriction validation
→ QueueGo vector tiles / PMTiles
→ offline regional packages
→ routing engine
→ map matching
→ turn-by-turn guidance
→ navigation safety gates

Recommended initial routing build:

- OSM Thailand/region PBF as the routing-capable base because it carries one-way/access/turn restriction semantics
- official DRR/DOH/MOT road data as verification/enrichment layers where licensing permits
- QueueGo verified corrections as a separate provenance layer
- Valhalla or GraphHopper for the first self-controlled routing graph evaluation
- MapLibre for rendering
- PMTiles/vector-tile packages for offline delivery

## Canonical road segment

Every merged road segment should retain provenance instead of becoming an untraceable blended line.

Suggested fields:

- segment_id
- geometry
- road_name
- road_ref
- road_class
- one_way
- access_motorcycle
- access_car
- maxspeed_kph
- surface
- lanes
- bridge
- tunnel
- junction
- turn_restriction_refs[]
- sources[]
- confidence
- first_seen_at
- last_verified_at
- last_changed_at

Do not copy or trace Google Maps geometry, road attributes, imagery-derived geometry, or proprietary navigation content into this dataset.

## Data confidence

Suggested confidence policy:

- verified official + OSM geometry agree: high
- multiple independent open sources agree: high
- one open source only: medium
- rider report not yet corroborated: low / pending
- geometry conflict, grade separation ambiguity or turn restriction conflict: quarantine for review

For safety-sensitive routing, an unresolved conflict should prefer a known public road over a shorter ambiguous connector.

## Release gate before Android integration

Do not call QueueGo Navigation production-ready until:

1. Buriram road graph builds reproducibly from pinned source snapshots.
2. one-way, access, bridge/tunnel grade separation and turn restrictions are tested.
3. map matching survives parallel roads and GPS jumps.
4. rerouting ignores isolated bad GPS samples.
5. routing has an offline fallback and a versioned regional package.
6. route output is checked against known field routes.
7. no unlicensed/proprietary map data is in the generated database.
8. attribution and source provenance survive export/build steps.
9. physical motorcycle road testing passes before Rider uses it as the sole navigation system.

## Public-source notes

- OSM: Open Database License (ODbL); attribution required.
- DRR open-data catalogue exposes road-network web services and describes the data as public/Open Data Common.
- Thailand Ministry of Transport open-data catalogue publishes road-network map layers as WMS, but the catalogue entry should not be treated as a routing graph by itself.
- Each non-OSM dataset must be reviewed for licence and redistribution conditions before being baked into QueueGo offline packages.
