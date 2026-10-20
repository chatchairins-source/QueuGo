create table if not exists qg_private.rider_map_feedback (
  id uuid primary key default gen_random_uuid(),
  rider_id uuid not null references public.rider_profiles(id) on delete cascade,
  category text not null,
  latitude double precision not null,
  longitude double precision not null,
  note text,
  status text not null default 'pending',
  reviewed_by uuid references public.users(id) on delete set null,
  reviewed_at timestamptz,
  review_note text,
  created_at timestamptz not null default now(),
  constraint rider_map_feedback_category
    check (category in ('road_closed','wrong_route','missing_road','bad_road','unsafe_turn','other')),
  constraint rider_map_feedback_coords
    check (latitude between 5.0 and 21.0 and longitude between 97.0 and 106.0),
  constraint rider_map_feedback_note_length
    check (note is null or char_length(note) <= 500),
  constraint rider_map_feedback_status
    check (status in ('pending','accepted_for_review','dismissed')),
  constraint rider_map_feedback_review_note_length
    check (review_note is null or char_length(review_note) <= 500)
);

create index if not exists rider_map_feedback_rider_created_idx
  on qg_private.rider_map_feedback(rider_id, created_at desc);

create index if not exists rider_map_feedback_status_created_idx
  on qg_private.rider_map_feedback(status, created_at desc);

revoke all on table qg_private.rider_map_feedback from public, anon, authenticated;
grant all on table qg_private.rider_map_feedback to service_role;

create or replace function qg_private.rider_submit_map_feedback(
  p_category text,
  p_latitude double precision,
  p_longitude double precision,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_category text := lower(btrim(coalesce(p_category,'')));
  v_note text := nullif(btrim(coalesce(p_note,'')),'');
  v_id uuid;
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

  if v_category not in ('road_closed','wrong_route','missing_road','bad_road','unsafe_turn','other') then
    raise exception 'invalid map feedback category';
  end if;

  if p_latitude is null or p_longitude is null
     or p_latitude not between 5.0 and 21.0
     or p_longitude not between 97.0 and 106.0 then
    raise exception 'invalid map feedback coordinates';
  end if;

  if v_note is not null and char_length(v_note)>500 then
    raise exception 'map feedback note too long';
  end if;

  if (
    select count(*)
    from qg_private.rider_map_feedback f
    where f.rider_id=v_rider
      and f.created_at >= now()-interval '1 hour'
  ) >= 5 then
    raise exception 'map feedback rate limit reached';
  end if;

  insert into qg_private.rider_map_feedback(
    rider_id,category,latitude,longitude,note,status
  )
  values(v_rider,v_category,p_latitude,p_longitude,v_note,'pending')
  returning id into v_id;

  return jsonb_build_object(
    'id',v_id,
    'status','pending',
    'category',v_category,
    'created_at',now()
  );
end $$;

revoke all on function qg_private.rider_submit_map_feedback(text,double precision,double precision,text)
  from public, anon;
grant execute on function qg_private.rider_submit_map_feedback(text,double precision,double precision,text)
  to authenticated;

create or replace function public.qg_rider_submit_map_feedback(
  p_category text,
  p_latitude double precision,
  p_longitude double precision,
  p_note text default null
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select qg_private.rider_submit_map_feedback(
    p_category,p_latitude,p_longitude,p_note
  );
$$;

revoke all on function public.qg_rider_submit_map_feedback(text,double precision,double precision,text)
  from public, anon;
grant execute on function public.qg_rider_submit_map_feedback(text,double precision,double precision,text)
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
        'review_note',q.review_note
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
      f.review_note
    from qg_private.rider_map_feedback f
    join public.rider_profiles r on r.id=f.rider_id
    join public.users u on u.id=r.user_id
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

create or replace function public.qg_admin_rider_map_feedback_queue(
  p_status text default 'pending',
  p_limit integer default 100
)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$
  select qg_private.admin_rider_map_feedback_queue(p_status,p_limit);
$$;

revoke all on function public.qg_admin_rider_map_feedback_queue(text,integer)
  from public, anon;
grant execute on function public.qg_admin_rider_map_feedback_queue(text,integer)
  to authenticated, service_role;

create or replace function qg_private.admin_review_rider_map_feedback(
  p_feedback_id uuid,
  p_decision text,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_user uuid;
  v_decision text := lower(btrim(coalesce(p_decision,'')));
  v_note text := nullif(btrim(coalesce(p_note,'')),'');
  v_row qg_private.rider_map_feedback%rowtype;
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

  if v_decision not in ('accepted_for_review','dismissed') then
    raise exception 'invalid map feedback decision';
  end if;

  if v_note is not null and char_length(v_note)>500 then
    raise exception 'map feedback review note too long';
  end if;

  update qg_private.rider_map_feedback f
  set status=v_decision,
      reviewed_by=v_admin_user,
      reviewed_at=now(),
      review_note=v_note
  where f.id=p_feedback_id
    and f.status='pending'
  returning f.* into v_row;

  if v_row.id is null then
    raise exception 'map feedback unavailable';
  end if;

  return jsonb_build_object(
    'id',v_row.id,
    'status',v_row.status,
    'reviewed_at',v_row.reviewed_at
  );
end $$;

revoke all on function qg_private.admin_review_rider_map_feedback(uuid,text,text)
  from public, anon;
grant execute on function qg_private.admin_review_rider_map_feedback(uuid,text,text)
  to authenticated;

create or replace function public.qg_admin_review_rider_map_feedback(
  p_feedback_id uuid,
  p_decision text,
  p_note text default null
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select qg_private.admin_review_rider_map_feedback(
    p_feedback_id,p_decision,p_note
  );
$$;

revoke all on function public.qg_admin_review_rider_map_feedback(uuid,text,text)
  from public, anon;
grant execute on function public.qg_admin_review_rider_map_feedback(uuid,text,text)
  to authenticated, service_role;
