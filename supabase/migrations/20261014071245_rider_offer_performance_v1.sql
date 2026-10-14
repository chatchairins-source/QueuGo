create or replace function qg_private.rider_offer_performance(p_days integer default 1)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_days integer := coalesce(p_days, 1);
  v_rider uuid;
  v_start timestamptz;
  v_offered integer := 0;
  v_accepted integer := 0;
  v_declined integer := 0;
  v_expired integer := 0;
  v_avg_response_seconds numeric;
begin
  perform qg_private.assert_live_session();

  if v_days not in (1, 7, 30) then
    raise exception 'days must be 1, 7 or 30';
  end if;

  select r.id
  into v_rider
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

  v_start := (
    date_trunc('day', now() at time zone 'Asia/Bangkok')
      - make_interval(days => v_days - 1)
  ) at time zone 'Asia/Bangkok';

  select
    count(*)::integer,
    count(*) filter (where h.outcome = 'accepted')::integer,
    count(*) filter (where h.outcome = 'declined')::integer,
    count(*) filter (where h.outcome = 'expired')::integer,
    round(
      avg(extract(epoch from (h.resolved_at - h.offered_at)))
        filter (
          where h.outcome in ('accepted','declined')
            and h.resolved_at is not null
            and h.resolved_at >= h.offered_at
        )::numeric,
      1
    )
  into
    v_offered,
    v_accepted,
    v_declined,
    v_expired,
    v_avg_response_seconds
  from public.qg_rider_offer_history h
  where h.rider_id = v_rider
    and h.offered_at >= v_start
    and h.offered_at <= now();

  return jsonb_build_object(
    'days', v_days,
    'offered', coalesce(v_offered, 0),
    'accepted', coalesce(v_accepted, 0),
    'declined', coalesce(v_declined, 0),
    'expired', coalesce(v_expired, 0),
    'acceptance_rate',
      case when coalesce(v_offered, 0) > 0
        then round((v_accepted::numeric * 100.0) / v_offered, 1)
        else null end,
    'response_rate',
      case when coalesce(v_offered, 0) > 0
        then round(((v_accepted + v_declined)::numeric * 100.0) / v_offered, 1)
        else null end,
    'decline_rate',
      case when coalesce(v_offered, 0) > 0
        then round((v_declined::numeric * 100.0) / v_offered, 1)
        else null end,
    'timeout_rate',
      case when coalesce(v_offered, 0) > 0
        then round((v_expired::numeric * 100.0) / v_offered, 1)
        else null end,
    'average_response_seconds', v_avg_response_seconds
  );
end $$;

revoke all on function qg_private.rider_offer_performance(integer) from public, anon;
grant execute on function qg_private.rider_offer_performance(integer) to authenticated;

create or replace function public.qg_rider_offer_performance(p_days integer default 1)
returns jsonb
language sql
stable
security invoker
set search_path = ''
as $$ select qg_private.rider_offer_performance(p_days); $$;

revoke all on function public.qg_rider_offer_performance(integer) from public, anon;
grant execute on function public.qg_rider_offer_performance(integer) to authenticated, service_role;
