create table if not exists qg_private.rider_missions (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  description text,
  target_jobs integer not null,
  reward_note text,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  active boolean not null default true,
  created_by uuid not null references public.users(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint rider_missions_title_len check (char_length(btrim(title)) between 1 and 120),
  constraint rider_missions_description_len check (description is null or char_length(description) <= 500),
  constraint rider_missions_reward_len check (reward_note is null or char_length(reward_note) <= 160),
  constraint rider_missions_target_jobs check (target_jobs between 1 and 500),
  constraint rider_missions_time_window check (
    ends_at > starts_at
    and ends_at <= starts_at + interval '90 days'
  )
);

revoke all on table qg_private.rider_missions from public, anon, authenticated;
grant all on table qg_private.rider_missions to service_role;

create index if not exists rider_missions_active_window_idx
  on qg_private.rider_missions(active, starts_at, ends_at);

create index if not exists qg_orders_rider_completed_at
  on public.orders(rider_id, completed_at)
  where order_type='shopping' and status='completed' and completed_at is not null;

create or replace function qg_private.rider_missions_for_current_rider()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
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

  with mission_rows as (
    select
      m.id,
      m.title,
      m.description,
      m.target_jobs,
      m.reward_note,
      m.starts_at,
      m.ends_at,
      case when now() < m.starts_at then 'upcoming' else 'active' end as state,
      coalesce((
        select count(distinct coalesce(o.market_order_id::text,o.id::text))::integer
        from public.orders o
        where o.rider_id=v_rider
          and o.order_type='shopping'
          and o.status='completed'
          and o.completed_at is not null
          and o.completed_at >= m.starts_at
          and o.completed_at < m.ends_at
      ),0) as progress_jobs
    from qg_private.rider_missions m
    where m.active=true
      and m.ends_at > now()
      and m.starts_at <= now() + interval '7 days'
    order by
      case when now() between m.starts_at and m.ends_at then 0 else 1 end,
      m.starts_at,
      m.id
    limit 20
  )
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id',x.id,
        'title',x.title,
        'description',x.description,
        'target_jobs',x.target_jobs,
        'reward_note',x.reward_note,
        'starts_at',x.starts_at,
        'ends_at',x.ends_at,
        'state',case when x.progress_jobs >= x.target_jobs then 'completed' else x.state end,
        'progress_jobs',least(x.progress_jobs,x.target_jobs),
        'completed',x.progress_jobs >= x.target_jobs
      )
      order by
        case when x.progress_jobs >= x.target_jobs then 1 else 0 end,
        x.starts_at,
        x.id
    ),
    '[]'::jsonb
  )
  into v_result
  from mission_rows x;

  return v_result;
end $$;

revoke all on function qg_private.rider_missions_for_current_rider() from public, anon;
grant execute on function qg_private.rider_missions_for_current_rider() to authenticated;

create or replace function public.qg_rider_missions()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_missions_for_current_rider(); $$;

revoke all on function public.qg_rider_missions() from public, anon;
grant execute on function public.qg_rider_missions() to authenticated, service_role;

create or replace function qg_private.admin_rider_missions()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_result jsonb;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id',m.id,
        'title',m.title,
        'description',m.description,
        'target_jobs',m.target_jobs,
        'reward_note',m.reward_note,
        'starts_at',m.starts_at,
        'ends_at',m.ends_at,
        'active',m.active,
        'created_at',m.created_at
      )
      order by m.starts_at desc,m.id
    ),
    '[]'::jsonb
  )
  into v_result
  from (
    select *
    from qg_private.rider_missions
    order by starts_at desc,id
    limit 100
  ) m;

  return v_result;
end $$;

revoke all on function qg_private.admin_rider_missions() from public, anon;
grant execute on function qg_private.admin_rider_missions() to authenticated;

create or replace function public.qg_admin_rider_missions()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.admin_rider_missions(); $$;

revoke all on function public.qg_admin_rider_missions() from public, anon;
grant execute on function public.qg_admin_rider_missions() to authenticated, service_role;

create or replace function qg_private.admin_create_rider_mission(
  p_title text,
  p_description text,
  p_target_jobs integer,
  p_reward_note text,
  p_starts_at timestamptz,
  p_ends_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin uuid;
  v_row qg_private.rider_missions%rowtype;
  v_title text := nullif(btrim(coalesce(p_title,'')),'');
  v_description text := nullif(btrim(coalesce(p_description,'')),'');
  v_reward text := nullif(btrim(coalesce(p_reward_note,'')),'');
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  select u.id into v_admin
  from public.users u
  where u.auth_user_id=auth.uid()
    and u.role='admin'
    and u.status='active'
  limit 1;

  if v_admin is null then raise exception 'admin required' using errcode='42501'; end if;
  if v_title is null or char_length(v_title)>120 then raise exception 'invalid mission title'; end if;
  if v_description is not null and char_length(v_description)>500 then raise exception 'mission description too long'; end if;
  if v_reward is not null and char_length(v_reward)>160 then raise exception 'mission reward note too long'; end if;
  if p_target_jobs is null or p_target_jobs not between 1 and 500 then raise exception 'invalid mission target'; end if;
  if p_starts_at is null or p_ends_at is null or p_ends_at<=p_starts_at
     or p_ends_at>p_starts_at+interval '90 days' then
    raise exception 'invalid mission window';
  end if;

  insert into qg_private.rider_missions(
    title,description,target_jobs,reward_note,starts_at,ends_at,created_by
  )
  values(v_title,v_description,p_target_jobs,v_reward,p_starts_at,p_ends_at,v_admin)
  returning * into v_row;

  return jsonb_build_object(
    'id',v_row.id,'title',v_row.title,'target_jobs',v_row.target_jobs,
    'starts_at',v_row.starts_at,'ends_at',v_row.ends_at,'active',v_row.active
  );
end $$;

revoke all on function qg_private.admin_create_rider_mission(text,text,integer,text,timestamptz,timestamptz) from public, anon;
grant execute on function qg_private.admin_create_rider_mission(text,text,integer,text,timestamptz,timestamptz) to authenticated;

create or replace function public.qg_admin_create_rider_mission(
  p_title text,
  p_description text,
  p_target_jobs integer,
  p_reward_note text,
  p_starts_at timestamptz,
  p_ends_at timestamptz
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.admin_create_rider_mission(p_title,p_description,p_target_jobs,p_reward_note,p_starts_at,p_ends_at); $$;

revoke all on function public.qg_admin_create_rider_mission(text,text,integer,text,timestamptz,timestamptz) from public, anon;
grant execute on function public.qg_admin_create_rider_mission(text,text,integer,text,timestamptz,timestamptz) to authenticated, service_role;

create or replace function qg_private.admin_set_rider_mission_active(
  p_mission_id uuid,
  p_active boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_row qg_private.rider_missions%rowtype;
begin
  perform qg_private.assert_live_session();
  if not public.is_active_admin() then
    raise exception 'admin required' using errcode='42501';
  end if;

  update qg_private.rider_missions
  set active=coalesce(p_active,false),updated_at=now()
  where id=p_mission_id
  returning * into v_row;

  if v_row.id is null then raise exception 'mission not found'; end if;
  return jsonb_build_object('id',v_row.id,'active',v_row.active);
end $$;

revoke all on function qg_private.admin_set_rider_mission_active(uuid,boolean) from public, anon;
grant execute on function qg_private.admin_set_rider_mission_active(uuid,boolean) to authenticated;

create or replace function public.qg_admin_set_rider_mission_active(
  p_mission_id uuid,
  p_active boolean
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select qg_private.admin_set_rider_mission_active(p_mission_id,p_active); $$;

revoke all on function public.qg_admin_set_rider_mission_active(uuid,boolean) from public, anon;
grant execute on function public.qg_admin_set_rider_mission_active(uuid,boolean) to authenticated, service_role;
