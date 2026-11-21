# QueueGo map-data source and licence gate

Reviewed 2026-10-17. Public access alone does not grant redistribution rights. Only the sources marked **active** may enter the canonical graph/package. Engine software licences and road-database licences are separate.

| Source | Licence / attribution | Download / licence authority | Cadence | Allowed use and derived-data implications | Gate |
|---|---|---|---|---|---|
| OpenStreetMap, Geofabrik Thailand extract | ODbL 1.0; © OpenStreetMap contributors | https://download.geofabrik.de/asia/thailand-latest.osm.pbf ; https://www.openstreetmap.org/copyright ; https://opendatacommons.org/licenses/odbl/1-0/ | Geofabrik daily; capture PBF replication timestamp and SHA256, never identify a snapshot solely as `latest` | Local extraction, graph generation, routing and distribution with attribution and ODbL notice. Publicly used/distributed adapted databases must satisfy ODbL share-alike/source offer. Retain extract and build method with packages. Map tiles and the graph remain separately versioned. | **Active** canonical base |
| Overture transportation segments/connectors | ODbL; preserve per-record `sources` and dataset notices | https://docs.overturemaps.org/guides/transportation/ ; https://docs.overturemaps.org/attribution/ ; https://registry.opendata.aws/overture/ | Monthly; pin exact release and object hashes | Transportation includes OSM-derived data. Do not assume other Overture themes have identical licences. Eligible comparison/enrichment after exact release/record provenance capture; not automatically conflated into canonical OSM topology. | Eligible, **not ingested** in this build |
| DOH / MOT NGIS national-highway geometry | Exact dataset terms must be verified; no blanket licence assigned | Published service recorded in docs/QUEUEGO-MAP-LAB.md; https://data.go.th/ ; originating ministry catalogue | Dataset-specific, currently unknown | Existing browser research overlay does not establish offline redistribution permission. Keep geometry out of graph/packages until exact publisher licence, attribution, update and derivative conditions are archived. | **Quarantined** |
| DRR road / bridge services | Catalogue describes a public/open-data service, but exact licence identity and redistribution scope still need confirmation | Published service and catalogue links in docs/QUEUEGO-MAP-LAB.md; https://data.go.th/ | Dataset-specific, currently unknown | Do not interpret an ambiguous “Open Data Common” label as a verified ODbL/CC licence. No graph merge or offline redistribution until verified. | **Quarantined** |
| Other data.go.th datasets | Per-dataset licence, not a portal-wide permission | Exact dataset page and resource URL required | Dataset-specific | Record licence text/version, attribution, commercial use, derivatives and redistribution before ingestion. Historical accidents are not live closures. | **Quarantined** |
| Future QueueGo telemetry / surveys | No dataset collected for this build | Consent, purpose, retention and ownership must be specified before collection | Future | No production locations queried. Private telemetry must remain separate from the OSM-derived database until privacy and licence treatment are reviewed. Estimated speed is not observed speed. | **Not collected** |
| Google / Grab proprietary map content | No permission | Not a permitted source | N/A | No scraping, tracing, geometry ingestion or proprietary endpoints. | **Excluded** |

## Conflation / deduplication contract

1. OSM object identity `(type,id,version)` and ordered node references are canonical within a snapshot. Shared node IDs establish connections; visual line crossings do not. Bridge/tunnel/layer geometry must never be joined just because coordinates intersect.
2. Preserve all restriction relation members, via-way chains, `except` and conditional tags. Keep unresolved/unsupported conditions explicit and fail validation; do not silently drop a restriction to make a route pass.
3. An Overture/official feature may be linked as an enrichment candidate only after licence clearance and identity/provenance retention. Geometric proximity alone never creates an edge or intersection. Candidate review requires compatible class, direction/access, level, endpoints and topology; ambiguous/parallel/frontage roads remain separate.
4. No automatic union of overlapping lines, no random geometry merge, no overwrite of a source's object identity by another source.
5. `last_verified` means independent real-world verification and is null unless evidence exists. OSM object timestamp is `source_updated_at`, not a road-survey timestamp. Confidence is explicitly `open_data_unverified`; no invented accuracy score.

## Software

- Valhalla: MIT, https://github.com/valhalla/valhalla/blob/master/COPYING . Official PyPI package `pyvalhalla` supplies bindings and C++ build tools. Pin version and record runtime version.
- GraphHopper: Apache-2.0, https://github.com/graphhopper/graphhopper/blob/master/LICENSE.txt . Benchmark self-hosted open-source web JAR; hosted commercial API capabilities are not attributed to the open-source build.
- OSRM backend: BSD-2-Clause, https://github.com/Project-OSRM/osrm-backend/blob/master/LICENSE.TXT . Benchmark own graph/build, never the public demo endpoint.
- MapLibre: BSD-3-Clause; existing renderer and attribution remain unchanged by graph/service work.

This is an engineering licence gate, not a claim that all public Thai government geometry is redistributable. New sources require a recorded review before entering a release package.
