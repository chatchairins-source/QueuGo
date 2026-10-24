-- Read-only QueueGo operational-place snapshot for scripts/import-queuego-verified-places.cjs.
-- Run with an authorized database role. This query intentionally excludes account/contact PII.
select jsonb_build_object(
  'snapshot_complete', true,
  'generated_at', now(),
  'shops', coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id', s.id,
        'shop_name', s.shop_name,
        'public_category', s.public_category,
        'status', s.status,
        'onboarding_status', s.onboarding_status,
        'archived_at', s.archived_at,
        'market_membership_status', s.market_membership_status,
        'address', s.address,
        'latitude', s.latitude,
        'longitude', s.longitude,
        'updated_at', s.updated_at
      )
      order by s.id
    )
    from public.shop_profiles s
  ), '[]'::jsonb),
  'markets', coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id', m.id,
        'name', m.name,
        'active', m.active,
        'verified', m.verified,
        'address', m.address,
        'subdistrict', m.subdistrict,
        'district', m.district,
        'province', m.province,
        'latitude', m.latitude,
        'longitude', m.longitude,
        'updated_at', m.updated_at
      )
      order by m.id
    )
    from public.markets m
  ), '[]'::jsonb)
) as queuego_verified_places_snapshot;
