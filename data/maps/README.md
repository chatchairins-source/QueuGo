# QueueGo Maps place data

Map POIs and place search are served only by the shared QueueGo Places backend (`qg_search_places`). The web client must not bundle a region-specific catalog or fall back to an external geocoder for persisted/search authority.

QueueGo Places ingestion must register every source, license, attribution requirement, update policy, storage permission and redistribution permission before importing records. See the source registry migration and importer contracts in `supabase/migrations/` and `scripts/`.

`dopa-gis01-resources.json` records reviewed DOPA GIS-01 resource identities only. A listed resource is **not** approved for import until `sha256` and `validated_row_count` are filled from the exact downloaded bytes and `import_eligible` is explicitly true after importer validation. Catalog metadata, mirrors, file-size labels, or a reachable URL are not substitutes for checksum verification.


## DOPA GIS-01 batch staging

Use `scripts/stage-dopa-village-batch.cjs` only after exact province payload bytes have been obtained from the registered official DOPA resource URL. For each payload, place these two files in the staging input directory:

- `<resource-uuid>.json` — the exact downloaded bytes.
- `<resource-uuid>.meta.json` — `resource_id`, the exact manifest `resource_url`, and a valid `retrieved_at` timestamp.

The batch stage computes SHA-256, validates province identity, village codes, names and Thailand coordinate bounds through the single-province importer, and writes reviewable SQL plus `dopa-gis01-stage-report.json`. Missing resources remain `missing`; payloads without matching official metadata or valid content are blocked and do not produce SQL.

Batch staging deliberately does **not** modify `dopa-gis01-resources.json` and does not make a payload import-eligible. Promote a resource only after the staged checksum and validated row count have been independently reviewed against the exact official bytes.

## OpenStreetMap Thailand staging

The source registry allows storage and redistribution of OSM-derived records under ODbL-1.0 and requires visible OpenStreetMap attribution. The source is registered in 20261023144405_queuego_places_search_foundation.sql; this does not imply that its data has been imported or that the database is live.

Use a locally obtained Geofabrik Thailand PBF at https://download.geofabrik.de/asia/thailand-latest.osm.pbf. Record its exact retrieval time, snapshot date, and SHA-256. Export a privacy-minimal GeoJSON Text Sequence with only the OSM identity and object timestamp attributes:

    osmium export thailand-latest.osm.pbf --output=thailand.geojsonseq --output-format=geojsonseq --geometry-types=point,polygon --attributes=type,id,timestamp

Then stage review SQL:

    node scripts/stage-osm-places.cjs thailand.geojsonseq thailand-latest.osm.pbf /tmp/queuego-osm-review 2026-10-24T00:00:00Z 2026-10-24 PBF_SHA256

The script independently hashes both the exact PBF file and exported bytes, verifies the supplied PBF checksum, requires a fresh empty output directory, streams records into bounded SQL chunks, keeps only named place/search tags from its allowlist, filters coordinates to Thailand, and records OSM object ID, source-object timestamp, per-record payload hash, PBF hash, export hash, license and attribution. It preserves addr:subdistrict / addr:district / addr:province (including explicit Thai variants), village and moo fields when present. The controlled tag map includes named shopping, food, leisure, historic, craft, office, tourism and transit facilities. Point features retain their mapped coordinates. Polygon and multipolygon features retain their full geometry as a candidate boundary; generated SQL uses PostGIS ST_PointOnSurface for an interior search coordinate and stores the boundary. Lines and other unsupported geometries are counted as skipped. The generated SQL keeps records unverified; it is a review artifact, not an approved migration and never executes itself. Preserve ODbL attribution and share-alike obligations in any database distribution.

## QueueGo verified operational places

`scripts/import-queuego-verified-places.cjs` stages QueueGo-owned public shop and verified-market records into the same Places authority without copying private account data. The input must be a **complete public snapshot** with `snapshot_complete: true`, a valid `generated_at`, and separate `shops` / `markets` arrays.

The importer only keeps public place fields (name, public category, public address hierarchy, coordinates, operational source ID and timestamps). It rejects snapshots containing direct identity/contact fields such as phone, email, user/auth IDs, owner/contact names or national-ID fields. Active shops are eligible; Market-category shops additionally require approved market membership. Markets require both `active=true` and `verified=true`.

The generated SQL is review-only. It upserts deterministic `queuego_verified` source records and field provenance, maps cafés into the `food` nearby category while preserving `cafe` as the subtype, and marks prior QueueGo shop/market Places as rejected only when they are absent from a complete snapshot. Operational Shop/Market Places must be map-addressable: rows without coordinates are excluded and reported, while partial/out-of-range coordinate pairs fail closed. This matches QueueGo Map/Search, which discards results without valid coordinates.

Do not run the generated SQL until the QueueGo Places migrations have passed the approved release gate. This importer never applies Production changes itself.


### Operational runtime authority

`scripts/generate-queuego-shop-place-sync.cjs` generates review-only SQL for the future approved migration path. Its private `qg_private.queuego_upsert_operational_place` / `queuego_retire_operational_place` functions are the single write authority for both Shop and Market Places. Runtime triggers and bootstrap snapshots use the same `shop:<uuid>` / `market:<uuid>` source IDs and derive the same `queuego:<source-id>` dedupe key, preventing duplicate POIs across live sync and snapshot reconciliation.

The snapshot importer is therefore a bootstrap/audit path, not a second Places writer: generated snapshot SQL calls the same private runtime authority. Do not apply either generated SQL artifact directly; first create the reviewed migration through the approved Supabase migration workflow and pass the release/security gates.
