-- Evidence cache coordination, never order assignment. No enable flag or secret is installed.
create table qg_private.route_matrix_refresh_lease (
 singleton boolean primary key default true check(singleton),
 lease_id uuid, expires_at timestamptz not null default '-infinity'
);
insert into qg_private.route_matrix_refresh_lease(singleton) values(true);
alter table qg_private.route_matrix_refresh_lease enable row level security;
revoke all on qg_private.route_matrix_refresh_lease from public,anon,authenticated;

create or replace function public.queuego_route_matrix_refresh_claim(p_limit integer default 2)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_lease uuid; v_pairs jsonb;
begin
 if p_limit is null or p_limit not between 1 and 2 then raise exception 'refresh limit must be 1-2'; end if;
 if public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())<>1 then return '{}'::jsonb; end if;
 perform 1 from qg_private.route_matrix_refresh_lease where singleton and expires_at<=now() for update skip locked;
 if not found then return '{}'::jsonb; end if;
 -- A bounded cache prefilter, not an assignment decision. Final quote/claim revalidate every guard.
 select coalesce(jsonb_agg(jsonb_build_object('primaryOrderId',x.primary_id,'candidateOrderId',x.candidate_id)),'[]'::jsonb)
 into v_pairs from (
  select a.id primary_id,b.id candidate_id
  from public.orders a join public.rider_profiles r on r.id=a.rider_id
  join public.users u on u.id=r.user_id
  join qg_private.rider_dispatch_preferences pref on pref.rider_id=r.id
  cross join public.orders b
  where a.order_type='shopping' and a.fulfillment_vertical='food' and a.market_order_id is null
   and a.route_bundle_id is null and a.next_after_order_id is null
   and a.status in ('rider_assigned','preparing','ready','in_progress')
   and (a.status<>'in_progress' or pref.auto_accept)
   and u.status='active' and u.role='rider' and r.status='active'
   and coalesce((r.metadata->>'online')::boolean,false) and coalesce((r.metadata->>'available')::boolean,false)
   and pref.ready_until>now() and not pref.stop_after_work
   and r.latitude is not null and r.longitude is not null
   and b.id<>a.id and b.order_type='shopping' and b.fulfillment_vertical='food'
   and b.market_order_id is null and b.rider_id is null and b.status='searching_rider'
   and b.route_bundle_id is null and b.next_after_order_id is null
   and b.pickup_latitude is not null and b.pickup_longitude is not null
   and b.delivery_latitude is not null and b.delivery_longitude is not null
   and a.pickup_latitude is not null and a.pickup_longitude is not null
   and a.delivery_latitude is not null and a.delivery_longitude is not null
   and (select count(*) from public.orders o where o.rider_id=r.id and o.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress'))=1
   and not exists(select 1 from public.laundry_rider_jobs j where j.rider_id=r.id and j.status in ('assigned','accepted','arrived','collected'))
   and not exists(select 1 from public.qg_rider_delivery_returns d where d.rider_id=r.id and d.status not in ('resolved','returned','cancelled'))
   and case when a.status='in_progress' then
    qg_private.approx_distance_km(r.latitude,r.longitude,a.delivery_latitude,a.delivery_longitude)<=1.5
    and qg_private.approx_distance_km(a.delivery_latitude,a.delivery_longitude,b.pickup_latitude,b.pickup_longitude)
      <=public.queuego_rule_numeric('dispatch.max_next_link_km',1.5,now())
   else qg_private.approx_distance_km(a.pickup_latitude,a.pickup_longitude,b.pickup_latitude,b.pickup_longitude)<=1.5 end
   and not exists(select 1 from qg_private.route_bundle_road_matrices m where m.primary_order_id=a.id and m.candidate_order_id=b.id
    and m.primary_updated_at=a.updated_at and m.candidate_updated_at=b.updated_at and m.expires_at>now()+interval '30 seconds'
    and qg_private.approx_distance_km(r.latitude,r.longitude,m.rider_latitude,m.rider_longitude)<0.1)
  order by b.created_at,b.id,a.created_at,a.id limit p_limit
 ) x;
 if jsonb_array_length(v_pairs)=0 then return '{}'::jsonb; end if;
 v_lease:=gen_random_uuid();
 update qg_private.route_matrix_refresh_lease set lease_id=v_lease,expires_at=now()+interval '90 seconds' where singleton;
 return jsonb_build_object('leaseId',v_lease,'pairs',v_pairs);
end $$;

create or replace function public.queuego_route_matrix_refresh_release(p_lease uuid)
returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
begin
 update qg_private.route_matrix_refresh_lease set lease_id=null,expires_at=now() where singleton and lease_id=p_lease;
 return found;
end $$;
revoke all on function public.queuego_route_matrix_refresh_claim(integer),public.queuego_route_matrix_refresh_release(uuid) from public,anon,authenticated;
grant execute on function public.queuego_route_matrix_refresh_claim(integer),public.queuego_route_matrix_refresh_release(uuid) to service_role;

create or replace function qg_private.wake_route_matrix_refresh()
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare v_token text;
begin
 if public.queuego_rule_numeric('dispatch.continuous_enabled',0,now())<>1 then return; end if;
 if exists(select 1 from qg_private.route_matrix_refresh_lease where singleton and expires_at>now()) then return; end if;
 if not exists(select 1 from public.orders where rider_id is null and status='searching_rider' and order_type='shopping') then return; end if;
 if to_regclass('vault.decrypted_secrets') is null then return; end if;
 select decrypted_secret into v_token from vault.decrypted_secrets where name='queuego_route_matrix_worker_token' limit 1;
 if v_token is null or length(v_token)<32 then return; end if;
 perform net.http_post(
  url:='https://pkypiqhlrmzocysgeqew.supabase.co/functions/v1/queuego-route-bundle-matrix',
  headers:=jsonb_build_object('Content-Type','application/json','x-queuego-route-worker',v_token),
  body:='{"action":"refresh"}'::jsonb,timeout_milliseconds:=75000);
end $$;
revoke all on function qg_private.wake_route_matrix_refresh() from public,anon,authenticated;
-- Supabase has pg_cron; isolated engines can validate all guards without extensions.
do $$ begin
 if to_regnamespace('cron') is not null then
  perform cron.schedule('queuego-route-matrix-refresh-20s','20 seconds','select qg_private.wake_route_matrix_refresh();');
 end if;
end $$;
-- Existing five-second qg_dispatch_rider_offers remains unchanged and solely assigns Orders.
