# QueueGo Maps place data

Map POIs and place search are served only by the shared QueueGo Places backend (`qg_search_places`). The web client must not bundle a region-specific catalog or fall back to an external geocoder for persisted/search authority.

QueueGo Places ingestion must register every source, license, attribution requirement, update policy, storage permission and redistribution permission before importing records. See the source registry migration and importer contracts in `supabase/migrations/` and `scripts/`.
