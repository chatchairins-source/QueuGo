create index if not exists admin_order_delete_archive_deleted_by_idx
  on public.admin_order_delete_archive (deleted_by);

create index if not exists pos_invites_used_by_idx
  on public.pos_invites (used_by);

create index if not exists promotions_approved_by_idx
  on public.promotions (approved_by);

create index if not exists qg_support_tickets_admin_id_idx
  on public.qg_support_tickets (admin_id);

create index if not exists qg_ticket_events_actor_id_idx
  on public.qg_ticket_events (actor_id);

create index if not exists queuego_platform_rules_created_by_idx
  on public.queuego_platform_rules (created_by);

create index if not exists route_bundles_rider_id_idx
  on public.route_bundles (rider_id);

create index if not exists shop_profiles_market_reviewed_by_idx
  on public.shop_profiles (market_reviewed_by);
