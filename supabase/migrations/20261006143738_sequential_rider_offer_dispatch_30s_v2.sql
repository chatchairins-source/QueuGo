-- QueueGo sequential rider dispatch: one server-owned offer per order, 30 seconds.\ncreate table if not exists public.qg_rider_order_offers (order_id uuid primary key references public.orders(id) on delete cascade,rider_id uuid not null references public.rider_profiles(id) on delete cascade,offered_at timestamptz not null default now(),expires_at timestamptz not null,attempt integer not null default 1,constraint qg_rider_order_offers_expiry check(expires_at>offered_at));\ncreate index if not exists qg_rider_order_offers_rider_exp_idx on public.qg_rider_order_offers(rider_id,expires_at); alter table public.qg_rider_order_offers enable row level security; revoke all on public.qg_rider_order_offers from anon,authenticated;\ncreate table if not exists public.qg_rider_offer_history (id bigint generated always as identity primary key,order_id uuid not null references public.orders(id) on delete cascade,rider_id uuid not null references public.rider_profiles(id) on delete cascade,offered_at timestamptz not null,resolved_at timestamptz not null default now(),outcome text not null check(outcome in ('declined','expired','accepted')),attempt integer not null);\ncreate index if not exists qg_rider_offer_history_order_recent_idx on public.qg_rider_offer_history(order_id,resolved_at desc); alter table public.qg_rider_offer_history enable row level security; revoke all on public.qg_rider_offer_history from anon,authenticated;\n\nCREATE OR REPLACE FUNCTION public.get_rider_delivery_pool()
 RETURNS TABLE(delivery_id uuid, order_id uuid, order_number text, delivery_status text, shop_id uuid, shop_name text, pickup_latitude numeric, pickup_longitude numeric, delivery_address text, delivery_latitude numeric, delivery_longitude numeric, distance_km numeric, delivery_fee numeric, created_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare rr uuid;
begin
 select r.id into rr from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
  and coalesce((r.metadata->>'online')::boolean,false) and coalesce((r.metadata->>'available')::boolean,false);
 if rr is null then return; end if;
 perform public.qg_dispatch_rider_offers();
 return query
 select d.id,d.order_id,o.order_number,d.status,o.shop_id,s.shop_name,d.pickup_latitude,d.pickup_longitude,d.delivery_address,d.delivery_latitude,d.delivery_longitude,d.distance_km,
  case when o.market_order_id is not null then (select mo.delivery_fee+mo.rider_bonus from public.market_orders mo where mo.id=o.market_order_id) else d.delivery_fee end,d.created_at
 from public.qg_rider_order_offers f join public.orders o on o.id=f.order_id
 join public.deliveries d on d.order_id=o.id join public.shop_profiles s on s.id=o.shop_id
 where f.rider_id=rr and f.expires_at>now() and o.status='searching_rider' and o.rider_id is null and d.status='pending'
 order by f.offered_at limit 1;
end $function$


CREATE OR REPLACE FUNCTION public.qg_accept_offer_receipt(p_order_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare f public.qg_rider_order_offers%rowtype;
begin
 select * into f from public.qg_rider_order_offers where order_id=p_order_id for update;
 if found then
  insert into public.qg_rider_offer_history(order_id,rider_id,offered_at,outcome,attempt)
  values(f.order_id,f.rider_id,f.offered_at,'accepted',f.attempt);
  delete from public.qg_rider_order_offers where order_id=p_order_id;
 end if;
end $function$


CREATE OR REPLACE FUNCTION public.qg_dispatch_on_searching_rider()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
 if new.order_type='shopping' and new.status='searching_rider' and (tg_op='INSERT' or old.status is distinct from new.status) then perform public.qg_dispatch_rider_offers(); end if;
 return new;
end $function$


CREATE OR REPLACE FUNCTION public.qg_dispatch_rider_offers()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare j record; rr uuid; a int; n int:=0;
begin
 insert into public.qg_rider_offer_history(order_id,rider_id,offered_at,resolved_at,outcome,attempt)
 select f.order_id,f.rider_id,f.offered_at,now(),'expired',f.attempt
 from public.qg_rider_order_offers f join public.orders o on o.id=f.order_id
 where f.expires_at<=now() and o.status='searching_rider' and o.rider_id is null;
 delete from public.qg_rider_order_offers f using public.orders o
 where f.order_id=o.id and (f.expires_at<=now() or o.status<>'searching_rider' or o.rider_id is not null);

 for j in
  select o.* from public.orders o
  where o.order_type='shopping' and o.status='searching_rider' and o.rider_id is null
   and (o.market_order_id is null or o.id=(select o2.id from public.orders o2 where o2.market_order_id=o.market_order_id and o2.status<>'cancelled' order by o2.created_at,o2.id limit 1))
   and (o.market_order_id is null or not exists(select 1 from public.orders o3 where o3.market_order_id=o.market_order_id and o3.status not in('searching_rider','cancelled')))
   and not exists(select 1 from public.qg_rider_order_offers f where f.order_id=o.id)
  order by o.created_at for update skip locked
 loop
  select r.id into rr
  from public.rider_profiles r join public.users u on u.id=r.user_id
  where u.role='rider' and u.status='active' and r.status='active'
   and coalesce((r.metadata->>'online')::boolean,false)
   and coalesce((r.metadata->>'available')::boolean,false)
   and r.latitude is not null and r.longitude is not null
   and not exists(select 1 from public.orders b where b.rider_id=r.id and b.status in('rider_assigned','preparing','ready','assigned','picked_up','in_progress'))
   and not exists(select 1 from public.qg_rider_order_offers h where h.rider_id=r.id and h.expires_at>now())
   and not exists(select 1 from public.qg_rider_offer_history h where h.order_id=j.id and h.rider_id=r.id and h.resolved_at>now()-interval '10 minutes')
   and (
    (j.market_order_id is null and j.fulfillment_vertical='food')
    or (j.market_order_id is not null and r.vehicle_type in('motorcycle','car','saleng') and r.vehicle_status='active' and r.vehicle_verified_at is not null
      and r.vehicle_capacity_kg >= (select coalesce(sum(public.market_order_weight(o4.id)),0) from public.orders o4 where o4.market_order_id=j.market_order_id and o4.status<>'cancelled'))
    or (j.market_order_id is null and j.fulfillment_vertical in('market','grocery') and r.vehicle_type in('motorcycle','car','saleng')
      and r.vehicle_status='active' and r.vehicle_verified_at is not null and r.vehicle_capacity_kg>=public.market_order_weight(j.id) and public.market_order_weight(j.id)>0)
   )
  order by ((r.latitude-j.pickup_latitude)*(r.latitude-j.pickup_latitude)+(r.longitude-j.pickup_longitude)*(r.longitude-j.pickup_longitude)),r.updated_at,r.id
  limit 1 for update of r skip locked;

  if rr is not null then
   select coalesce(max(attempt),0)+1 into a from public.qg_rider_offer_history where order_id=j.id;
   insert into public.qg_rider_order_offers(order_id,rider_id,offered_at,expires_at,attempt)
   values(j.id,rr,now(),now()+interval '30 seconds',a) on conflict(order_id) do nothing;
   if found then n:=n+1; end if;
  end if;
 end loop;
 return n;
end $function$


CREATE OR REPLACE FUNCTION public.qg_get_my_rider_offer()
 RETURNS TABLE(order_id uuid, expires_at timestamp with time zone, attempt integer)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare rr uuid;
begin
 select r.id into rr from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active';
 if rr is null then return; end if;
 perform public.qg_dispatch_rider_offers();
 return query select f.order_id,f.expires_at,f.attempt from public.qg_rider_order_offers f
 join public.orders o on o.id=f.order_id
 where f.rider_id=rr and f.expires_at>now() and o.status='searching_rider' and o.rider_id is null
 order by f.offered_at limit 1;
end $function$


CREATE OR REPLACE FUNCTION public.qg_require_live_rider_offer(p_order_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare rr uuid; f public.qg_rider_order_offers%rowtype;
begin
 select r.id into rr from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active';
 if rr is null then raise exception 'active rider required'; end if;
 select * into f from public.qg_rider_order_offers where order_id=p_order_id for update;
 if not found or f.rider_id<>rr or f.expires_at<=now() then raise exception 'rider offer expired or unavailable'; end if;
end $function$


CREATE OR REPLACE FUNCTION public.qg_rider_action_once(p_request_id uuid, p_kind text, p_payload jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_user uuid;v_existing public.qg_rider_action_receipts%rowtype;v_result jsonb;v_order uuid;
begin
 select u.id into v_user from public.users u join public.rider_profiles r on r.user_id=u.id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if v_user is null or p_request_id is null or p_payload is null or jsonb_typeof(p_payload)<>'object' then raise exception 'active rider request required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_request_id::text,0));
 select * into v_existing from public.qg_rider_action_receipts where request_id=p_request_id;
 if found then
  if v_existing.user_id<>v_user or v_existing.kind<>p_kind or v_existing.payload<>p_payload then raise exception 'request id already in use'; end if;
  return v_existing.result;
 end if;
 if p_kind in('claim','market_claim') then
  v_order:=(p_payload->>'p_order_id')::uuid;
  perform public.qg_require_live_rider_offer(v_order);
 end if;
 case p_kind
  when 'bundle_claim' then select to_jsonb(public.queuego_claim_route_bundle((p_payload->>'p_order_id')::uuid)) into v_result;
  when 'claim' then select to_jsonb(public.rider_claim_order((p_payload->>'p_order_id')::uuid)) into v_result;
  when 'market_claim' then select to_jsonb(public.market_rider_claim_group((p_payload->>'p_order_id')::uuid)) into v_result;
  when 'order' then select to_jsonb(public.rider_order_action((p_payload->>'p_order_id')::uuid,p_payload->>'p_action')) into v_result;
  when 'market' then select to_jsonb(public.market_rider_group_action((p_payload->>'p_market_order_id')::uuid,p_payload->>'p_action')) into v_result;
  when 'market_pickup' then select to_jsonb(public.market_rider_confirm_pickup_cash((p_payload->>'p_pickup_id')::uuid,(p_payload->>'p_amount')::numeric)) into v_result;
  when 'laundry_mode' then select to_jsonb(public.queuego_set_laundry_rider_mode((p_payload->>'p_enabled')::boolean)) into v_result;
  when 'laundry_invite' then select to_jsonb(public.queuego_laundry_rider_invite_action((p_payload->>'p_invite_id')::uuid,(p_payload->>'p_accept')::boolean)) into v_result;
  when 'laundry_claim' then select to_jsonb(public.queuego_claim_laundry_job((p_payload->>'p_job_id')::uuid)) into v_result;
  when 'laundry_action' then select to_jsonb(public.queuego_laundry_rider_action((p_payload->>'p_job_id')::uuid,p_payload->>'p_action')) into v_result;
  else raise exception 'unsupported rider action';
 end case;
 if p_kind in('claim','market_claim') then perform public.qg_accept_offer_receipt(v_order); end if;
 insert into public.qg_rider_action_receipts(request_id,user_id,kind,payload,result)
 values(p_request_id,v_user,p_kind,p_payload,coalesce(v_result,'null'::jsonb));
 return v_result;
end $function$


CREATE OR REPLACE FUNCTION public.qg_rider_decline_offer(p_order_id uuid)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare rr uuid; f public.qg_rider_order_offers%rowtype;
begin
 select r.id into rr from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active';
 if rr is null then raise exception 'active rider required'; end if;
 select * into f from public.qg_rider_order_offers where order_id=p_order_id for update;
 if not found or f.rider_id<>rr or f.expires_at<=now() then raise exception 'offer unavailable'; end if;
 insert into public.qg_rider_offer_history(order_id,rider_id,offered_at,outcome,attempt)
 values(f.order_id,f.rider_id,f.offered_at,'declined',f.attempt);
 delete from public.qg_rider_order_offers where order_id=p_order_id;
 perform public.qg_dispatch_rider_offers();
 return true;
end $function$


revoke all on function public.qg_dispatch_rider_offers() from public,anon,authenticated;
revoke all on function public.qg_require_live_rider_offer(uuid) from public,anon,authenticated;\nrevoke all on function public.qg_accept_offer_receipt(uuid) from public,anon,authenticated;\nrevoke all on function public.qg_rider_decline_offer(uuid) from public,anon; grant execute on function public.qg_rider_decline_offer(uuid) to authenticated;\nrevoke all on function public.qg_get_my_rider_offer() from public,anon; grant execute on function public.qg_get_my_rider_offer() to authenticated;\nrevoke all on function public.get_rider_delivery_pool() from public,anon; grant execute on function public.get_rider_delivery_pool() to authenticated;\nrevoke all on function public.qg_rider_action_once(uuid,text,jsonb) from public,anon; grant execute on function public.qg_rider_action_once(uuid,text,jsonb) to authenticated;\nrevoke execute on function public.rider_claim_order(uuid) from authenticated,anon; revoke execute on function public.market_rider_claim_group(uuid) from authenticated,anon;\nrevoke all on function public.qg_dispatch_on_searching_rider() from public,anon,authenticated;\ndrop trigger if exists qg_orders_dispatch_rider_offer on public.orders; create trigger qg_orders_dispatch_rider_offer after insert or update of status on public.orders for each row execute function public.qg_dispatch_on_searching_rider();\nselect cron.schedule('queuego-rider-dispatch-5s','5 seconds','select public.qg_dispatch_rider_offers();');\n