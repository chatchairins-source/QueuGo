-- Bounded operational projections only. No state, dispatch, financial or permission bypass.
-- Keep the existing no-argument shop overview API for older deployed clients.
create or replace function public.queuego_admin_shop_overview(p_user_ids uuid[])
returns table(shop_id uuid,shop_user_id uuid,readiness jsonb,order_count bigint)
language plpgsql stable security invoker set search_path=''
as $$
begin
 if auth.uid() is null or not public.is_active_admin() then
  raise exception 'active admin required' using errcode='42501';
 end if;
 if p_user_ids is null or cardinality(p_user_ids)>200 then
  raise exception 'expected at most 200 account ids' using errcode='22023';
 end if;
 return query
 with chosen as materialized (
  select distinct on (s.user_id) s.id,s.user_id
  from public.shop_profiles s where s.user_id=any(p_user_ids)
  order by s.user_id,(s.archived_at is null) desc,s.created_at desc,s.id
 ), counts as (
  select o.shop_id,count(*) n from public.orders o
  where o.shop_id in (select c.id from chosen c) group by o.shop_id
 )
 select c.id,c.user_id,public.queuego_shop_readiness(c.id),coalesce(n.n,0::bigint)
 from chosen c left join counts n on n.shop_id=c.id;
end $$;
revoke all on function public.queuego_admin_shop_overview(uuid[]) from public,anon;
grant execute on function public.queuego_admin_shop_overview(uuid[]) to authenticated;

-- Offer/outbox tables deliberately remain direct-table-deny. A checked server projection
-- is needed instead of broad authenticated SELECT grants or exposing tokens/payloads.
create or replace function qg_private.admin_dispatch_overview(p_limit integer)
returns jsonb language plpgsql stable security definer set search_path=''
as $$
declare v_offers jsonb;v_history jsonb;v_bundles jsonb;
begin
 if auth.uid() is null or not public.is_active_admin() then
  raise exception 'active admin required' using errcode='42501';
 end if;
 perform qg_private.assert_live_session();
 if p_limit is null or p_limit<1 or p_limit>100 then
  raise exception 'limit must be 1..100' using errcode='22023';
 end if;
 select coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) into v_offers from (
  select f.order_id,f.rider_id,f.offered_at,f.expires_at,f.attempt,
   (f.expires_at<=statement_timestamp()) as expired
  from public.qg_rider_order_offers f order by f.expires_at,f.order_id limit p_limit
 ) x;
 select coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) into v_history from (
  select h.id,h.order_id,h.rider_id,h.outcome,h.attempt,h.resolved_at
  from public.qg_rider_offer_history h order by h.resolved_at desc,h.id desc limit p_limit
 ) x;
 select coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb) into v_bundles from (
  select b.id,b.rider_id,b.primary_order_id,b.status,b.updated_at
  from public.route_bundles b order by b.updated_at desc,b.id desc limit p_limit
 ) x;
 return jsonb_build_object('as_of',statement_timestamp(),'limit',p_limit,
  'offers',v_offers,'history',v_history,'bundles',v_bundles);
end $$;
revoke all on function qg_private.admin_dispatch_overview(integer) from public,anon;
grant execute on function qg_private.admin_dispatch_overview(integer) to authenticated;
create or replace function public.qg_admin_dispatch_overview(p_limit integer default 100)
returns jsonb language sql stable security invoker set search_path=''
as $$ select qg_private.admin_dispatch_overview(p_limit); $$;
revoke all on function public.qg_admin_dispatch_overview(integer) from public,anon;
grant execute on function public.qg_admin_dispatch_overview(integer) to authenticated;

create or replace function qg_private.admin_notification_health()
returns jsonb language plpgsql stable security definer set search_path=''
as $$
declare v_channels jsonb;
begin
 if auth.uid() is null or not public.is_active_admin() then
  raise exception 'active admin required' using errcode='42501';
 end if;
 perform qg_private.assert_live_session();
 with events as (
  select 'unified'::text channel,p.status,p.created_at,p.retry_at from public.qg_push_outbox p
  where p.created_at>=statement_timestamp()-interval '7 days' or p.status in ('pending','leased')
  union all
  select 'rider'::text channel,p.status,p.created_at,p.retry_at from public.qg_rider_push_outbox p
  where p.created_at>=statement_timestamp()-interval '7 days' or p.status in ('pending','leased')
 ), counts as (
  select channel,status,count(*) n,min(created_at) oldest,
   count(*) filter(where status in ('pending','leased') and retry_at<=statement_timestamp()) due
  from events group by channel,status
 )
 select coalesce(jsonb_agg(to_jsonb(c) order by c.channel,c.status),'[]'::jsonb)
 into v_channels from counts c;
 return jsonb_build_object('as_of',statement_timestamp(),'completed_since',statement_timestamp()-interval '7 days',
  'pending_window','all','channels',v_channels);
end $$;
revoke all on function qg_private.admin_notification_health() from public,anon;
grant execute on function qg_private.admin_notification_health() to authenticated;
create or replace function public.qg_admin_notification_health()
returns jsonb language sql stable security invoker set search_path=''
as $$ select qg_private.admin_notification_health(); $$;
revoke all on function public.qg_admin_notification_health() from public,anon;
grant execute on function public.qg_admin_notification_health() to authenticated;

-- Match bounded dashboard ordering; existing order/rider-prefixed indexes cannot serve these global views.
create index if not exists qg_admin_offers_expiry_idx on public.qg_rider_order_offers(expires_at,order_id);
create index if not exists qg_admin_offer_history_recent_idx on public.qg_rider_offer_history(resolved_at desc,id desc);
create index if not exists qg_admin_bundles_recent_idx on public.route_bundles(updated_at desc,id desc);
create index if not exists qg_admin_push_created_idx on public.qg_push_outbox(created_at);
create index if not exists qg_admin_rider_push_created_idx on public.qg_rider_push_outbox(created_at);
