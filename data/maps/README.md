# QueueGo Maps place data

Map POIs and place search are served only by the shared QueueGo Places backend (`qg_search_places`). The web client must not bundle a region-specific catalog or fall back to an external geocoder for persisted/search authority.

QueueGo Places ingestion must register every source, license, attribution requirement, update policy, storage permission and redistribution permission before importing records. See the source registry migration and importer contracts in `supabase/migrations/` and `scripts/`.

`dopa-gis01-resources.json` records reviewed DOPA GIS-01 resource identities only. A listed resource is **not** approved for import until `sha256` and `validated_row_count` are filled from the exact downloaded bytes and `import_eligible` is explicitly true after importer validation. Catalog metadata, mirrors, file-size labels, or a reachable URL are not substitutes for checksum verification.
