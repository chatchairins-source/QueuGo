-- Cover QueueGo Places source foreign keys used by provenance/search joins.
create index if not exists queuego_places_source_idx
 on public.queuego_places(source);
create index if not exists queuego_place_aliases_source_idx
 on public.queuego_place_aliases(source);
