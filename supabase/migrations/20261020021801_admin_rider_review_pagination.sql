create or replace function qg_private.admin_rider_review_page(
  p_kind text,
  p_limit integer default 50,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path=''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit,50),1),200);
  v_offset integer := coalesce(p_offset,0);
  v_rows jsonb := '[]'::jsonb;
  v_more boolean := false;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;
  if p_kind not in ('identity','inspection','vehicle','documents') then
    raise exception 'unsupported rider review queue' using errcode='22023';
  end if;
  if v_offset < 0 or v_offset > 100000 then
    raise exception 'invalid rider review offset' using errcode='22023';
  end if;

  if p_kind='identity' then
    select coalesce(jsonb_agg(jsonb_build_object(
      'rider_id',x.rider_id,
      'rider_name',x.rider_name,
      'request_id',x.request_id,
      'status',x.status,
      'reason',x.reason,
      'selfie_path',x.selfie_path,
      'requested_at',x.requested_at,
      'submitted_at',x.submitted_at,
      'reviewed_at',x.reviewed_at,
      'review_note',x.review_note
    ) order by x.sort_at desc,x.rider_id),'[]'::jsonb)
    into v_rows
    from (
      select r.id rider_id,
             coalesce(nullif(r.rider_name,''),u.name,'Rider') rider_name,
             rr.id request_id,rr.status,rr.reason,rr.selfie_path,rr.requested_at,rr.submitted_at,rr.reviewed_at,rr.review_note,
             coalesce(rr.updated_at,r.updated_at) sort_at
      from public.rider_profiles r
      join public.users u on u.id=r.user_id
      left join lateral (
        select z.*
        from qg_private.rider_reverification_requests z
        where z.rider_id=r.id
        order by z.requested_at desc
        limit 1
      ) rr on true
      where u.role='rider' and u.status<>'deleted'
      order by coalesce(rr.updated_at,r.updated_at) desc,r.id
      limit v_limit+1 offset v_offset
    ) x;
  elsif p_kind='inspection' then
    select coalesce(jsonb_agg(jsonb_build_object(
      'rider_id',x.rider_id,
      'rider_name',x.rider_name,
      'request_id',x.request_id,
      'status',x.status,
      'reason',x.reason,
      'vehicle_photo_path',x.vehicle_photo_path,
      'requested_at',x.requested_at,
      'submitted_at',x.submitted_at,
      'reviewed_at',x.reviewed_at,
      'review_note',x.review_note
    ) order by x.sort_at desc,x.rider_id),'[]'::jsonb)
    into v_rows
    from (
      select r.id rider_id,
             coalesce(nullif(r.rider_name,''),u.name,'Rider') rider_name,
             ri.id request_id,ri.status,ri.reason,ri.vehicle_photo_path,ri.requested_at,ri.submitted_at,ri.reviewed_at,ri.review_note,
             coalesce(ri.updated_at,r.updated_at) sort_at
      from public.rider_profiles r
      join public.users u on u.id=r.user_id
      left join lateral (
        select z.*
        from qg_private.rider_vehicle_inspection_requests z
        where z.rider_id=r.id
        order by z.requested_at desc
        limit 1
      ) ri on true
      where u.role='rider' and u.status<>'deleted'
      order by coalesce(ri.updated_at,r.updated_at) desc,r.id
      limit v_limit+1 offset v_offset
    ) x;
  elsif p_kind='vehicle' then
    select coalesce(jsonb_agg(jsonb_build_object(
      'rider_id',x.rider_id,
      'vehicle_id',x.vehicle_id,
      'rider_name',x.rider_name,
      'vehicle_type',x.vehicle_type,
      'vehicle_name',x.vehicle_name,
      'vehicle_plate',x.vehicle_plate,
      'vehicle_capacity_kg',x.vehicle_capacity_kg,
      'photo_path',x.photo_path,
      'status',x.status,
      'is_selected',x.is_selected,
      'legacy_seed',x.legacy_seed,
      'review_note',x.review_note,
      'created_at',x.created_at,
      'updated_at',x.updated_at
    ) order by x.pending_rank,x.selected_rank,x.updated_at desc,x.vehicle_id),'[]'::jsonb)
    into v_rows
    from (
      select v.rider_id,v.id vehicle_id,
             coalesce(nullif(r.rider_name,''),u.name,'Rider') rider_name,
             v.vehicle_type,v.vehicle_name,v.vehicle_plate,v.vehicle_capacity_kg,v.photo_path,v.status,v.is_selected,v.legacy_seed,v.review_note,v.created_at,v.updated_at,
             case when v.status='pending' and not v.legacy_seed then 0 else 1 end pending_rank,
             case when v.is_selected then 0 else 1 end selected_rank
      from qg_private.rider_vehicles v
      join public.rider_profiles r on r.id=v.rider_id
      join public.users u on u.id=r.user_id
      where u.role='rider' and u.status<>'deleted'
      order by
        case when v.status='pending' and not v.legacy_seed then 0 else 1 end,
        case when v.is_selected then 0 else 1 end,
        v.updated_at desc,v.id
      limit v_limit+1 offset v_offset
    ) x;
  else
    select coalesce(jsonb_agg(jsonb_build_object(
      'rider_id',x.rider_id,
      'rider_name',x.rider_name,
      'selected_vehicle_id',x.selected_vehicle_id,
      'selected_vehicle_plate',x.selected_vehicle_plate,
      'national_id_expires_on',x.national_id_expires_on,
      'driver_license_expires_on',x.driver_license_expires_on,
      'vehicle_prb_expires_on',x.vehicle_prb_expires_on
    ) order by x.rider_name,x.rider_id),'[]'::jsonb)
    into v_rows
    from (
      select r.id rider_id,
             coalesce(nullif(r.rider_name,''),u.name,'Rider') rider_name,
             sv.id selected_vehicle_id,
             sv.vehicle_plate selected_vehicle_plate,
             nid.expires_on national_id_expires_on,
             lic.expires_on driver_license_expires_on,
             prb.expires_on vehicle_prb_expires_on
      from public.rider_profiles r
      join public.users u on u.id=r.user_id
      left join lateral (
        select v.id,v.vehicle_plate
        from qg_private.rider_vehicles v
        where v.rider_id=r.id and v.is_selected
        order by v.updated_at desc,v.id
        limit 1
      ) sv on true
      left join qg_private.rider_document_expiries nid
        on nid.rider_id=r.id and nid.document_key='national_id' and nid.vehicle_id is null
      left join qg_private.rider_document_expiries lic
        on lic.rider_id=r.id and lic.document_key='driver_license' and lic.vehicle_id is null
      left join qg_private.rider_document_expiries prb
        on prb.rider_id=r.id and prb.document_key='vehicle_prb' and prb.vehicle_id=sv.id
      where u.role='rider' and u.status<>'deleted'
      order by rider_name,r.id
      limit v_limit+1 offset v_offset
    ) x;
  end if;

  v_more := jsonb_array_length(v_rows)>v_limit;
  if v_more then
    v_rows := v_rows - v_limit;
  end if;

  return jsonb_build_object(
    'items',v_rows,
    'limit',v_limit,
    'offset',v_offset,
    'more',v_more,
    'next_offset',case when v_more then v_offset+v_limit else null end
  );
end
$$;

revoke all on function qg_private.admin_rider_review_page(text,integer,integer) from public;
grant execute on function qg_private.admin_rider_review_page(text,integer,integer) to authenticated,service_role;

create or replace function public.qg_admin_rider_review_page(
  p_kind text,
  p_limit integer default 50,
  p_offset integer default 0
)
returns jsonb
language sql
stable
set search_path=''
as $$ select qg_private.admin_rider_review_page(p_kind,p_limit,p_offset); $$;

revoke all on function public.qg_admin_rider_review_page(text,integer,integer) from public,anon;
grant execute on function public.qg_admin_rider_review_page(text,integer,integer) to authenticated,service_role;

create or replace function qg_private.admin_rider_reverification_queue()
returns jsonb
language sql
stable
security definer
set search_path=''
as $$ select qg_private.admin_rider_review_page('identity',200,0)->'items'; $$;

create or replace function qg_private.admin_rider_vehicle_inspection_queue()
returns jsonb
language sql
stable
security definer
set search_path=''
as $$ select qg_private.admin_rider_review_page('inspection',200,0)->'items'; $$;

create or replace function qg_private.admin_rider_vehicle_queue()
returns jsonb
language sql
stable
security definer
set search_path=''
as $$ select qg_private.admin_rider_review_page('vehicle',200,0)->'items'; $$;

create or replace function qg_private.admin_rider_document_queue()
returns jsonb
language sql
stable
security definer
set search_path=''
as $$ select qg_private.admin_rider_review_page('documents',200,0)->'items'; $$;
