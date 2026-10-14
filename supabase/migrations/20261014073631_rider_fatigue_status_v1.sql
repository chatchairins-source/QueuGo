create or replace function qg_private.rider_fatigue_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rider uuid;
  v_latest_online boolean;
  v_online_since timestamptz;
  v_continuous_minutes integer := 0;
  v_today_minutes integer := 0;
  v_day_start timestamptz;
  v_level text := 'normal';
begin
  perform qg_private.assert_live_session();

  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id = r.user_id
  where u.auth_user_id = auth.uid()
    and u.role = 'rider'
    and u.status = 'active'
    and r.status = 'active'
  order by r.created_at desc
  limit 1;

  if v_rider is null then
    raise exception 'active rider required' using errcode='42501';
  end if;

  select e.online, e.occurred_at
  into v_latest_online, v_online_since
  from public.qg_rider_presence_events e
  where e.rider_id = v_rider
  order by e.occurred_at desc
  limit 1;

  if coalesce(v_latest_online, false) and v_online_since is not null then
    v_continuous_minutes := greatest(
      0,
      floor(extract(epoch from (now() - v_online_since)) / 60.0)::integer
    );
  else
    v_online_since := null;
  end if;

  v_day_start := (
    date_trunc('day', now() at time zone 'Asia/Bangkok')
    at time zone 'Asia/Bangkok'
  );

  with seed as (
    select x.online, x.occurred_at
    from (
      select e.online, e.occurred_at
      from public.qg_rider_presence_events e
      where e.rider_id = v_rider
        and e.occurred_at < v_day_start
      order by e.occurred_at desc
      limit 1
    ) x

    union all

    select e.online, e.occurred_at
    from public.qg_rider_presence_events e
    where e.rider_id = v_rider
      and e.occurred_at >= v_day_start
      and e.occurred_at <= now()
  ),
  timeline as (
    select
      s.online,
      greatest(s.occurred_at, v_day_start) as started_at,
      least(
        coalesce(lead(s.occurred_at) over(order by s.occurred_at), now()),
        now()
      ) as ended_at
    from seed s
  )
  select coalesce(
    floor(
      sum(
        case
          when t.online and t.ended_at > t.started_at
            then extract(epoch from (t.ended_at - t.started_at))
          else 0
        end
      ) / 60.0
    )::integer,
    0
  )
  into v_today_minutes
  from timeline t;

  v_level := case
    when v_continuous_minutes >= 240 then 'strong_rest_recommended'
    when v_continuous_minutes >= 120 then 'rest_recommended'
    else 'normal'
  end;

  return jsonb_build_object(
    'online', coalesce(v_latest_online, false),
    'online_since', v_online_since,
    'continuous_online_minutes', v_continuous_minutes,
    'online_minutes_today', greatest(v_today_minutes, 0),
    'level', v_level,
    'basis', 'presence_history',
    'medical_detection', false
  );
end $$;

revoke all on function qg_private.rider_fatigue_status() from public, anon;
grant execute on function qg_private.rider_fatigue_status() to authenticated;

create or replace function public.qg_rider_fatigue_status()
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_fatigue_status(); $$;

revoke all on function public.qg_rider_fatigue_status() from public, anon;
grant execute on function public.qg_rider_fatigue_status() to authenticated, service_role;
