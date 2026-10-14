create table if not exists qg_private.rider_road_alerts (
  id uuid primary key default gen_random_uuid(),
  feedback_id uuid not null unique references qg_private.rider_map_feedback(id) on delete cascade,
  category text not null,
  latitude double precision not null,
  longitude double precision not null,
  note text,
  published_by uuid not null references public.users(id) on delete restrict,
  published_at timestamptz not null default now(),
  expires_at timestamptz not null,
  disabled_at timestamptz,
  constraint rider_road_alert_category
    check (category in ('road_closed','bad_road','unsafe_turn')),
  constraint rider_road_alert_coords
    check (latitude between 5.0 and 21.0 and longitude between 97.0 and 106.0),
  constraint rider_road_alert_note_length
    check (note is null or char_length(note) <= 500),
  constraint rider_road_alert_expiry
    check (expires_at > published_at)
);

alter table qg_private.rider_road_alerts enable row level security;

create index if not exists rider_road_alerts_active_expiry_idx
  on qg_private.rider_road_alerts(expires_at)
  where disabled_at is null;

create index if not exists rider_road_alerts_location_idx
  on qg_private.rider_road_alerts(latitude,longitude);

revoke all on table qg_private.rider_road_alerts from public, anon, authenticated;
grant all on table qg_private.rider_road_alerts to service_role;

create or replace function qg_private.admin_publish_rider_road_alert(
  p_feedback_id uuid,
  p_duration_minutes integer default 360
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_user uuid;
  v_duration integer := coalesce(p_duration_minutes,360);
  v_feedback qg_private.rider_map_feedback%rowtype;
  v_alert qg_private.rider_road_alerts%rowtype;
begin
  perform qg_private.assert_live_session();

  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  select u.id into v_admin_user
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='admin'
    and u.status='active'
  limit 1;

  if v_admin_user is null then
    raise exception 'admin required' using errcode='42501';
  end if;

  if p_feedback_id is null then
    raise exception 'feedback id required';
  end if;

  if v_duration not between 15 and 10080 then
    raise exception 'alert duration must be 15-10080 minutes';
  end if;

  select f.* into v_feedback
  from qg_private.rider_map_feedback f
  where f.id=p_feedback_id
    and f.status='accepted_for_review'
    and f.category in ('road_closed','bad_road','unsafe_turn')
  limit 1;

  if v_feedback.id is null then
    raise exception 'verified road feedback required';
  end if;

  insert into qg_private.rider_road_alerts(
    feedback_id,
    category,
    latitude,
    longitude,
    note,
    published_by,
    published_at,
    expires_at,
    disabled_at
  )
  values(
    v_feedback.id,
    v_feedback.category,
    v_feedback.latitude,
    v_feedback.longitude,
    v_feedback.note,
    v_admin_user,
    now(),
    now()+make_interval(mins => v_duration),
    null
  )
  on conflict(feedback_id) do update
  set category=excluded.category,
      latitude=excluded.latitude,
      longitude=excluded.longitude,
      note=excluded.note,
      published_by=excluded.published_by,
      published_at=excluded.published_at,
      expires_at=excluded.expires_at,
      disabled_at=null
  returning * into v_alert;

  return jsonb_build_object(
    'alert_id',v_alert.id,
    'feedback_id',v_alert.feedback_id,
    'category',v_alert.category,
    'published_at',v_alert.published_at,
    'expires_at',v_alert.expires_at,
    'active',true
  );
end $$;

revoke all on function qg_private.admin_publish_rider_road_alert(uuid,integer)
  from public, anon;
grant execute on function qg_private.admin_publish_rider_road_alert(uuid,integer)
  to authenticated;

create or replace function public.qg_admin_publish_rider_road_alert(
  p_feedback_id uuid,
  p_duration_minutes integer default 360
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select qg_private.admin_publish_rider_road_alert(
    p_feedback_id,p_duration_minutes
  );
$$;

revoke all on function public.qg_admin_publish_rider_road_alert(uuid,integer)
  from public, anon;
grant execute on function public.qg_admin_publish_rider_road_alert(uuid,integer)
  to authenticated, service_role;

create or replace function qg_private.admin_disable_rider_road_alert(
  p_alert_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_alert qg_private.rider_road_alerts%rowtype;
begin
  perform qg_private.assert_live_session();

  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  if p_alert_id is null then
    raise exception 'alert id required';
  end if;

  update qg_private.rider_road_alerts a
  set disabled_at=coalesce(a.disabled_at,now())
  where a.id=p_alert_id
  returning a.* into v_alert;

  if v_alert.id is null then
    raise exception 'road alert unavailable';
  end if;

  return jsonb_build_object(
    'alert_id',v_alert.id,
    'active',false,
    'disabled_at',v_alert.disabled_at
  );
end $$;

revoke all on function qg_private.admin_disable_rider_road_alert(uuid)
  from public, anon;
grant execute on function qg_private.admin_disable_rider_road_alert(uuid)
  to authenticated;

create or replace function public.qg_admin_disable_rider_road_alert(
  p_alert_id uuid
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select qg_private.admin_disable_rider_road_alert(p_alert_id);
$$;

revoke all on function public.qg_admin_disable_rider_road_alert(uuid)
  from public, anon;
grant execute on function public.qg_admin_disable_rider_road_alert(uuid)
  to authenticated, service_role;

create or replace function qg_private.rider_nearby_road_alerts(
  p_latitude double precision,
  p_longitude double precision,
  p_radius_km double precision default 5.0
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_radius double precision := coalesce(p_radius_km,5.0);
  v_result jsonb;
begin
  perform qg_private.assert_live_session();

  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and r.status='active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  if p_latitude is null or p_longitude is null
     or p_latitude not between 5.0 and 21.0
     or p_longitude not between 97.0 and 106.0 then
    raise exception 'invalid road alert coordinates';
  end if;

  if not isfinite(v_radius) or v_radius < 0.5 or v_radius > 20.0 then
    raise exception 'road alert radius must be 0.5-20 km';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'alert_id',q.id,
        'category',q.category,
        'latitude',q.latitude,
        'longitude',q.longitude,
        'note',q.note,
        'distance_km',round(q.distance_km::numeric,2),
        'published_at',q.published_at,
        'expires_at',q.expires_at
      )
      order by q.distance_km asc,q.published_at desc
    ),
    '[]'::jsonb
  )
  into v_result
  from (
    select d.*
    from (
      select
        a.id,
        a.category,
        a.latitude,
        a.longitude,
        a.note,
        a.published_at,
        a.expires_at,
        6371.0 * 2.0 * asin(
          least(
            1.0,
            sqrt(
              power(sin(radians(a.latitude-p_latitude)/2.0),2.0) +
              cos(radians(p_latitude)) *
              cos(radians(a.latitude)) *
              power(sin(radians(a.longitude-p_longitude)/2.0),2.0)
            )
          )
        ) as distance_km
      from qg_private.rider_road_alerts a
      where a.disabled_at is null
        and a.published_at <= now()
        and a.expires_at > now()
    ) d
    where d.distance_km <= v_radius
    order by d.distance_km asc,d.published_at desc
    limit 20
  ) q;

  return v_result;
end $$;

revoke all on function qg_private.rider_nearby_road_alerts(double precision,double precision,double precision)
  from public, anon;
grant execute on function qg_private.rider_nearby_road_alerts(double precision,double precision,double precision)
  to authenticated;

create or replace function public.qg_rider_nearby_road_alerts(
  p_latitude double precision,
  p_longitude double precision,
  p_radius_km double precision default 5.0
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select qg_private.rider_nearby_road_alerts(
    p_latitude,p_longitude,p_radius_km
  );
$$;

revoke all on function public.qg_rider_nearby_road_alerts(double precision,double precision,double precision)
  from public, anon;
grant execute on function public.qg_rider_nearby_road_alerts(double precision,double precision,double precision)
  to authenticated, service_role;

create or replace function qg_private.admin_rider_map_feedback_queue(
  p_status text default 'pending',
  p_limit integer default 100
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_status text := lower(btrim(coalesce(p_status,'pending')));
  v_limit integer := greatest(1,least(coalesce(p_limit,100),200));
  v_result jsonb;
begin
  perform qg_private.assert_live_session();

  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  if v_status not in ('pending','accepted_for_review','dismissed','all') then
    raise exception 'invalid map feedback status';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id',q.id,
        'rider_name',q.rider_name,
        'category',q.category,
        'latitude',q.latitude,
        'longitude',q.longitude,
        'note',q.note,
        'status',q.status,
        'created_at',q.created_at,
        'reviewed_at',q.reviewed_at,
        'review_note',q.review_note,
        'alert_id',q.alert_id,
        'alert_active',q.alert_active,
        'alert_expires_at',q.alert_expires_at,
        'alert_disabled_at',q.alert_disabled_at
      )
      order by q.created_at desc
    ),
    '[]'::jsonb
  )
  into v_result
  from (
    select
      f.id,
      coalesce(nullif(btrim(r.rider_name),''), nullif(btrim(u.name),''), 'Rider') as rider_name,
      f.category,
      f.latitude,
      f.longitude,
      f.note,
      f.status,
      f.created_at,
      f.reviewed_at,
      f.review_note,
      a.id as alert_id,
      (a.id is not null and a.disabled_at is null and a.expires_at>now()) as alert_active,
      a.expires_at as alert_expires_at,
      a.disabled_at as alert_disabled_at
    from qg_private.rider_map_feedback f
    join public.rider_profiles r on r.id=f.rider_id
    join public.users u on u.id=r.user_id
    left join qg_private.rider_road_alerts a on a.feedback_id=f.id
    where v_status='all' or f.status=v_status
    order by f.created_at desc
    limit v_limit
  ) q;

  return v_result;
end $$;

revoke all on function qg_private.admin_rider_map_feedback_queue(text,integer)
  from public, anon;
grant execute on function qg_private.admin_rider_map_feedback_queue(text,integer)
  to authenticated;
