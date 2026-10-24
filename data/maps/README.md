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
