-- QueueGo Laundry v1 hardening and server workflow.
-- Reuses the existing Laundry tables. No duplicate order/job system.

create or replace function public.queuego_platform_rule_guard()
returns trigger
language plpgsql
set search_path to 'public','pg_temp'
as $qg$
declare n numeric;
begin
  if new.rule_key in ('feature.market_multi_shop','feature.route_bundle','feature.gp','feature.laundry') then
    if jsonb_typeof(new.value) <> 'boolean' then
      raise exception 'feature rule must be boolean';
    end if;
  elsif new.rule_key in (
    'pricing.gp_default_rate',
    'pricing.market_base_fee',
    'pricing.market_distance_step_fee',
    'pricing.market_second_shop_fee',
    'pricing.market_additional_shop_fee',
    'route_bundle.max_detour_km',
    'route_bundle.max_delay_minutes',
    'route_bundle.max_orders',
    'route_bundle.min_rider_extra_fee'
  ) then
    if jsonb_typeof(new.value) <> 'number' then raise exception 'pricing rule must be numeric'; end if;
    n := (new.value #>> '{}')::numeric;
    if n < 0 then raise exception 'pricing rule cannot be negative'; end if;
    if new.rule_key='pricing.gp_default_rate' and n > 100 then raise exception 'GP rate must be 0-100'; end if;
    if new.rule_key='route_bundle.max_orders' and (n < 1 or n > 5 or n <> trunc(n)) then raise exception 'route bundle max orders must be integer 1-5'; end if;
    if new.rule_key='route_bundle.max_delay_minutes' and n > 240 then raise exception 'route bundle delay too large'; end if;
    if new.rule_key='route_bundle.max_detour_km' and n > 50 then raise exception 'route bundle detour too large'; end if;
  else
    raise exception 'unsupported QueueGo platform rule: %', new.rule_key;
  end if;
  new.created_at := coalesce(new.created_at,now());
  return new;
end
$qg$;

insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select 'feature.laundry','false'::jsonb,'2000-01-01 00:00:00+07'::timestamptz,
       'Laundry closed-beta kill switch; enable from Admin after regression',null
where not exists(select 1 from public.queuego_platform_rules where rule_key='feature.laundry');

-- Correct owner identity checks: shop_profiles.user_id / rider_profiles.user_id reference public.users.id,
-- while auth.uid() is the Auth user id.
drop policy if exists laundry_rider_pref_insert on public.laundry_rider_preferences;
drop policy if exists laundry_rider_pref_select on public.laundry_rider_preferences;
drop policy if exists laundry_rider_pref_update on public.laundry_rider_preferences;
create policy laundry_rider_pref_insert on public.laundry_rider_preferences
  for insert to authenticated
  with check (exists(
    select 1 from public.rider_profiles r
    where r.id=laundry_rider_preferences.rider_id
      and r.user_id=public.get_my_user_id()
  ));
create policy laundry_rider_pref_select on public.laundry_rider_preferences
  for select to authenticated
  using (exists(
    select 1 from public.rider_profiles r
    where r.id=laundry_rider_preferences.rider_id
      and r.user_id=public.get_my_user_id()
  ));
create policy laundry_rider_pref_update on public.laundry_rider_preferences
  for update to authenticated
  using (exists(
    select 1 from public.rider_profiles r
    where r.id=laundry_rider_preferences.rider_id
      and r.user_id=public.get_my_user_id()
  ))
  with check (exists(
    select 1 from public.rider_profiles r
    where r.id=laundry_rider_preferences.rider_id
      and r.user_id=public.get_my_user_id()
  ));

drop policy if exists laundry_settings_read on public.laundry_shop_settings;
create policy laundry_settings_read on public.laundry_shop_settings
  for select to authenticated
  using (
    enabled
    or exists(
      select 1
      from public.laundry_hubs h
      join public.shop_profiles s on s.id=h.shop_id
      where h.id=laundry_shop_settings.hub_id
        and s.user_id=public.get_my_user_id()
    )
    or public.is_active_admin()
  );

drop policy if exists laundry_services_read on public.laundry_services;
create policy laundry_services_read on public.laundry_services
  for select to authenticated
  using (
    active
    or exists(
      select 1
      from public.laundry_hubs h
      join public.shop_profiles s on s.id=h.shop_id
      where h.id=laundry_services.hub_id
        and s.user_id=public.get_my_user_id()
    )
    or public.is_active_admin()
  );

-- Sensitive order/job mutations are server-RPC only.
revoke insert,update,delete on public.laundry_orders from authenticated;
revoke insert,update,delete on public.laundry_rider_jobs from authenticated;
revoke insert,update,delete on public.laundry_order_events from authenticated;
grant select on public.laundry_orders,public.laundry_rider_jobs,public.laundry_order_events to authenticated;

create or replace function public.queuego_place_laundry_order(
  p_request_id uuid,
  p_hub_id uuid,
  p_service_id uuid,
  p_pickup_address text,
  p_pickup_latitude double precision,
  p_pickup_longitude double precision,
  p_note text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_customer public.users%rowtype;
  v_hub public.laundry_hubs%rowtype;
  v_service public.laundry_services%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
  v_shop public.shop_profiles%rowtype;
  v_existing public.laundry_orders%rowtype;
  v_order public.laundry_orders%rowtype;
begin
  if auth.uid() is null then raise exception 'customer login required'; end if;
  if not public.queuego_feature_enabled('laundry',now()) then raise exception 'laundry is temporarily disabled'; end if;
  if p_request_id is null or p_hub_id is null or p_service_id is null then raise exception 'invalid laundry request'; end if;
  if length(trim(coalesce(p_pickup_address,'')))<3 or length(p_pickup_address)>500 then raise exception 'invalid pickup address'; end if;
  if p_pickup_latitude is null or p_pickup_longitude is null
     or abs(p_pickup_latitude)>90 or abs(p_pickup_longitude)>180 then
    raise exception 'invalid pickup coordinates';
  end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  select * into v_customer
  from public.users
  where auth_user_id=auth.uid() and role='customer' and status='active';
  if not found then raise exception 'active customer required'; end if;

  perform pg_advisory_xact_lock(hashtextextended('laundry-'||p_request_id::text,481));

  select * into v_existing
  from public.laundry_orders
  where request_key=p_request_id;
  if found then
    if v_existing.customer_id<>v_customer.id then raise exception 'request id already used'; end if;
    return jsonb_build_object(
      'id',v_existing.id,'order_number',v_existing.order_number,'status',v_existing.status,
      'replayed',true
    );
  end if;

  select * into v_hub from public.laundry_hubs where id=p_hub_id and active=true for share;
  if not found then raise exception 'laundry hub unavailable'; end if;
  select * into v_shop from public.shop_profiles where id=v_hub.shop_id and status='active' for share;
  if not found then raise exception 'laundry shop unavailable'; end if;
  select * into v_settings
  from public.laundry_shop_settings
  where hub_id=v_hub.id and enabled=true and accepts_pickup=true
  for share;
  if not found then raise exception 'laundry pickup unavailable'; end if;
  select * into v_service
  from public.laundry_services
  where id=p_service_id and hub_id=v_hub.id and active=true
  for share;
  if not found then raise exception 'laundry service unavailable'; end if;

  insert into public.laundry_orders(
    customer_id,hub_id,status,service_type,
    pickup_address,pickup_latitude,pickup_longitude,note,
    service_id,service_name_snapshot,pricing_type_snapshot,unit_price_snapshot,
    pickup_fee_snapshot,return_fee_snapshot,round_trip_fee_snapshot,
    estimated_amount,request_key
  ) values(
    v_customer.id,v_hub.id,'pending',v_service.code,
    trim(p_pickup_address),p_pickup_latitude,p_pickup_longitude,nullif(trim(coalesce(p_note,'')),''),
    v_service.id,v_service.name,v_service.pricing_type,v_service.price,
    v_settings.base_pickup_fee,v_settings.return_fee,v_settings.round_trip_fee,
    case when v_service.pricing_type='fixed' then greatest(v_service.price,v_settings.minimum_order) else null end,
    p_request_id
  )
  returning * into v_order;

  insert into public.laundry_order_events(
    laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note
  ) values(v_order.id,null,'pending',auth.uid(),'customer','Laundry request created');

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_shop.user_id,'มีคำขอฝากซักใหม่','คำขอ '||v_order.order_number||' รอร้านรับ','order',v_order.id);

  return jsonb_build_object(
    'id',v_order.id,'order_number',v_order.order_number,'status',v_order.status,
    'service_name',v_order.service_name_snapshot,
    'unit_price',v_order.unit_price_snapshot,
    'pickup_fee',v_order.pickup_fee_snapshot,
    'return_fee',v_order.return_fee_snapshot,
    'round_trip_fee',v_order.round_trip_fee_snapshot,
    'replayed',false
  );
end
$$;

revoke all on function public.queuego_place_laundry_order(uuid,uuid,uuid,text,double precision,double precision,text) from public,anon;
grant execute on function public.queuego_place_laundry_order(uuid,uuid,uuid,text,double precision,double precision,text) to authenticated,service_role;

create or replace function public.queuego_laundry_shop_action(
  p_order_id uuid,
  p_action text,
  p_actual_kg numeric default null,
  p_note text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_actor uuid:=public.get_my_user_id();
  v_order public.laundry_orders%rowtype;
  v_settings public.laundry_shop_settings%rowtype;
  v_shop public.shop_profiles%rowtype;
  v_from text;
  v_to text;
  v_amount numeric;
begin
  if v_actor is null or public.get_my_role()<>'shop' then raise exception 'shop login required'; end if;
  if p_order_id is null then raise exception 'invalid laundry order'; end if;
  if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;

  select o.* into v_order
  from public.laundry_orders o
  join public.laundry_hubs h on h.id=o.hub_id
  join public.shop_profiles s on s.id=h.shop_id
  where o.id=p_order_id and s.user_id=v_actor
  for update of o;
  if not found then raise exception 'laundry order unavailable'; end if;

  select * into v_settings from public.laundry_shop_settings where hub_id=v_order.hub_id;
  select s.* into v_shop
  from public.laundry_hubs h join public.shop_profiles s on s.id=h.shop_id
  where h.id=v_order.hub_id;

  v_from:=v_order.status;

  if p_action='accept' then
    if v_order.status<>'pending' then raise exception 'laundry status changed; refresh'; end if;
    v_to:='accepted';
    insert into public.laundry_rider_jobs(laundry_order_id,leg,status)
    values(v_order.id,'pickup','waiting')
    on conflict(laundry_order_id,leg) do nothing;

  elsif p_action='start_washing' then
    if v_order.status<>'at_hub' then raise exception 'laundry must be at hub first'; end if;
    v_to:='washing';

  elsif p_action='ready_return' then
    if v_order.status<>'washing' then raise exception 'laundry is not washing'; end if;
    if v_order.pricing_type_snapshot='per_kg' then
      if p_actual_kg is null or p_actual_kg<=0 or p_actual_kg>200 then raise exception 'actual kg required'; end if;
      v_amount:=greatest(
        round(coalesce(v_order.unit_price_snapshot,0)*p_actual_kg,2),
        coalesce(v_settings.minimum_order,0)
      );
    else
      v_amount:=greatest(coalesce(v_order.unit_price_snapshot,0),coalesce(v_settings.minimum_order,0));
    end if;
    v_to:='ready_return';
    if coalesce(v_settings.accepts_return,false) then
      insert into public.laundry_rider_jobs(laundry_order_id,leg,status)
      values(v_order.id,'return','waiting')
      on conflict(laundry_order_id,leg) do nothing;
    end if;

  elsif p_action='complete_at_hub' then
    if v_order.status<>'ready_return' or coalesce(v_settings.accepts_return,false) then
      raise exception 'return delivery is enabled for this laundry';
    end if;
    v_to:='completed';

  elsif p_action='cancel' then
    if v_order.status not in ('pending','accepted') then raise exception 'laundry can no longer be cancelled by shop'; end if;
    if exists(
      select 1 from public.laundry_rider_jobs
      where laundry_order_id=v_order.id and status not in ('waiting','cancelled')
    ) then raise exception 'rider already started laundry pickup'; end if;
    v_to:='cancelled';
    update public.laundry_rider_jobs
    set status='cancelled',updated_at=now()
    where laundry_order_id=v_order.id and status='waiting';

  else
    raise exception 'invalid laundry shop action';
  end if;

  update public.laundry_orders
  set status=v_to,
      actual_kg=case when p_action='ready_return' and pricing_type_snapshot='per_kg' then p_actual_kg else actual_kg end,
      final_amount=case when p_action='ready_return' then v_amount else final_amount end,
      note=case when nullif(trim(coalesce(p_note,'')),'') is not null then trim(p_note) else note end,
      updated_at=now()
  where id=v_order.id
  returning * into v_order;

  insert into public.laundry_order_events(
    laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note
  ) values(v_order.id,v_from,v_to,auth.uid(),'shop',nullif(trim(coalesce(p_note,'')),''));

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(
    v_order.customer_id,
    'อัปเดตงานฝากซัก',
    case v_to
      when 'accepted' then 'ร้านรับคำขอแล้ว กำลังหา Rider ไปรับผ้า'
      when 'washing' then 'ร้านกำลังซักผ้าของคุณ'
      when 'ready_return' then 'ผ้าพร้อมส่งคืนแล้ว'
      when 'completed' then 'งานฝากซักเสร็จสมบูรณ์'
      when 'cancelled' then 'ร้านยกเลิกคำขอฝากซัก'
      else 'สถานะงานฝากซักมีการเปลี่ยนแปลง'
    end,
    'order',v_order.id
  );

  return jsonb_build_object(
    'id',v_order.id,'order_number',v_order.order_number,'status',v_order.status,
    'actual_kg',v_order.actual_kg,'final_amount',v_order.final_amount
  );
end
$$;

revoke all on function public.queuego_laundry_shop_action(uuid,text,numeric,text) from public,anon;
grant execute on function public.queuego_laundry_shop_action(uuid,text,numeric,text) to authenticated,service_role;

create or replace function public.queuego_set_laundry_rider_mode(p_enabled boolean)
returns boolean
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare v_rider uuid;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active';
  if v_rider is null then raise exception 'active rider required'; end if;
  insert into public.laundry_rider_preferences(rider_id,laundry_mode_enabled,updated_at)
  values(v_rider,coalesce(p_enabled,false),now())
  on conflict(rider_id) do update
    set laundry_mode_enabled=excluded.laundry_mode_enabled,updated_at=now();
  return coalesce(p_enabled,false);
end
$$;

revoke all on function public.queuego_set_laundry_rider_mode(boolean) from public,anon;
grant execute on function public.queuego_set_laundry_rider_mode(boolean) to authenticated,service_role;

create or replace function public.queuego_laundry_rider_pool()
returns table(
  job_id uuid,
  laundry_order_id uuid,
  order_number text,
  leg text,
  hub_id uuid,
  hub_name text,
  shop_name text,
  from_address text,
  from_latitude double precision,
  from_longitude double precision,
  to_address text,
  to_latitude double precision,
  to_longitude double precision,
  job_fee numeric,
  service_name text,
  actual_kg numeric,
  created_at timestamptz
)
language plpgsql
stable
security definer
set search_path to 'public','pg_temp'
as $$
declare v_rider uuid;
begin
  if not public.queuego_feature_enabled('laundry',now()) then return; end if;
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  join public.laundry_rider_preferences p on p.rider_id=r.id and p.laundry_mode_enabled=true
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true;
  if v_rider is null then return; end if;

  if exists(
    select 1 from public.orders
    where rider_id=v_rider and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
  ) or exists(
    select 1 from public.laundry_rider_jobs
    where rider_id=v_rider and status in ('assigned','accepted','arrived','collected')
  ) then return; end if;

  return query
  select j.id,o.id,o.order_number,j.leg,h.id,h.name,s.shop_name,
         case when j.leg='pickup' then s.address else s.address end,
         s.latitude,s.longitude,
         case when j.leg='pickup' then o.pickup_address else o.pickup_address end,
         o.pickup_latitude,o.pickup_longitude,
         case when j.leg='pickup' then coalesce(o.pickup_fee_snapshot,0) else coalesce(o.return_fee_snapshot,0) end,
         o.service_name_snapshot,o.actual_kg,j.created_at
  from public.laundry_rider_jobs j
  join public.laundry_orders o on o.id=j.laundry_order_id
  join public.laundry_hubs h on h.id=o.hub_id
  join public.shop_profiles s on s.id=h.shop_id
  join public.laundry_hub_riders hr on hr.hub_id=h.id and hr.rider_id=v_rider and hr.active=true
  where j.status='waiting' and j.rider_id is null
    and (
      (j.leg='pickup' and o.status='accepted')
      or (j.leg='return' and o.status='ready_return')
    )
  order by j.created_at
  limit 20;
end
$$;

revoke all on function public.queuego_laundry_rider_pool() from public,anon;
grant execute on function public.queuego_laundry_rider_pool() to authenticated,service_role;

create or replace function public.queuego_claim_laundry_job(p_job_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rider uuid;
  v_user uuid;
  v_job public.laundry_rider_jobs%rowtype;
  v_order public.laundry_orders%rowtype;
  v_from text;
  v_to text;
begin
  select r.id,r.user_id into v_rider,v_user
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  join public.laundry_rider_preferences p on p.rider_id=r.id and p.laundry_mode_enabled=true
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active'
    and r.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true
  for update of r;
  if v_rider is null then raise exception 'active laundry rider required'; end if;
  if not public.queuego_feature_enabled('laundry',now()) then raise exception 'laundry is disabled'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_rider::text,582));

  if exists(
    select 1 from public.orders
    where rider_id=v_rider and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
  ) or exists(
    select 1 from public.laundry_rider_jobs
    where rider_id=v_rider and status in ('assigned','accepted','arrived','collected')
  ) then raise exception 'finish current job before claiming laundry'; end if;

  select * into v_job from public.laundry_rider_jobs where id=p_job_id for update;
  if not found or v_job.status<>'waiting' or v_job.rider_id is not null then raise exception 'laundry job no longer available'; end if;
  if not exists(
    select 1 from public.laundry_orders o
    join public.laundry_hub_riders hr on hr.hub_id=o.hub_id
    where o.id=v_job.laundry_order_id and hr.rider_id=v_rider and hr.active=true
  ) then raise exception 'rider is not assigned to this laundry hub'; end if;

  select * into v_order from public.laundry_orders where id=v_job.laundry_order_id for update;
  v_from:=v_order.status;
  if v_job.leg='pickup' and v_order.status='accepted' then v_to:='pickup_assigned';
  elsif v_job.leg='return' and v_order.status='ready_return' then v_to:='return_assigned';
  else raise exception 'laundry order status changed'; end if;

  update public.laundry_rider_jobs
  set rider_id=v_rider,status='assigned',updated_at=now()
  where id=v_job.id
  returning * into v_job;

  update public.laundry_orders set status=v_to,updated_at=now() where id=v_order.id returning * into v_order;

  insert into public.laundry_order_events(laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note)
  values(v_order.id,v_from,v_to,auth.uid(),'rider','Laundry rider claimed job');

  insert into public.notifications(user_id,title,message,type,reference_id)
  values(v_order.customer_id,'พบ Rider สำหรับงานฝากซักแล้ว',
         case when v_job.leg='pickup' then 'Rider กำลังไปรับผ้าของคุณ' else 'Rider กำลังนำผ้ากลับไปส่งให้คุณ' end,
         'order',v_order.id);

  return jsonb_build_object(
    'job_id',v_job.id,'laundry_order_id',v_order.id,'order_number',v_order.order_number,
    'leg',v_job.leg,'job_status',v_job.status,'order_status',v_order.status
  );
end
$$;

revoke all on function public.queuego_claim_laundry_job(uuid) from public,anon;
grant execute on function public.queuego_claim_laundry_job(uuid) to authenticated,service_role;

create or replace function public.queuego_laundry_rider_action(
  p_job_id uuid,
  p_action text
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
  v_rider uuid;
  v_job public.laundry_rider_jobs%rowtype;
  v_order public.laundry_orders%rowtype;
  v_from text;
  v_order_to text;
  v_job_to text;
begin
  select r.id into v_rider
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active';
  if v_rider is null then raise exception 'active rider required'; end if;

  select * into v_job
  from public.laundry_rider_jobs
  where id=p_job_id and rider_id=v_rider
  for update;
  if not found then raise exception 'laundry job unavailable'; end if;

  select * into v_order from public.laundry_orders where id=v_job.laundry_order_id for update;
  v_from:=v_order.status;

  if p_action='arrive' and v_job.status='assigned' then
    v_job_to:='arrived';
    v_order_to:=v_order.status;

  elsif p_action='collect' and v_job.status in ('assigned','arrived') then
    v_job_to:='collected';
    if v_job.leg='pickup' and v_order.status='pickup_assigned' then v_order_to:='picked_up';
    elsif v_job.leg='return' and v_order.status='return_assigned' then v_order_to:='out_for_return';
    else raise exception 'laundry order status changed'; end if;

  elsif p_action='deliver' and v_job.status='collected' then
    v_job_to:='delivered';
    if v_job.leg='pickup' and v_order.status='picked_up' then v_order_to:='at_hub';
    elsif v_job.leg='return' and v_order.status='out_for_return' then v_order_to:='completed';
    else raise exception 'laundry order status changed'; end if;

  else
    raise exception 'invalid laundry rider transition';
  end if;

  update public.laundry_rider_jobs set status=v_job_to,updated_at=now() where id=v_job.id returning * into v_job;
  if v_order_to<>v_order.status then
    update public.laundry_orders set status=v_order_to,updated_at=now() where id=v_order.id returning * into v_order;
    insert into public.laundry_order_events(laundry_order_id,from_status,to_status,actor_auth_user_id,actor_role,note)
    values(v_order.id,v_from,v_order_to,auth.uid(),'rider','Laundry rider action: '||p_action);
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(
      v_order.customer_id,'อัปเดตงานฝากซัก',
      case v_order_to
        when 'picked_up' then 'Rider รับผ้าแล้ว กำลังนำไปที่ร้าน'
        when 'at_hub' then 'ผ้าถึงร้านแล้ว'
        when 'out_for_return' then 'Rider รับผ้าสะอาดแล้ว กำลังนำไปส่งคืน'
        when 'completed' then 'ส่งผ้าคืนเรียบร้อยแล้ว'
        else 'สถานะงานฝากซักมีการเปลี่ยนแปลง'
      end,
      'order',v_order.id
    );
  end if;

  return jsonb_build_object(
    'job_id',v_job.id,'job_status',v_job.status,
    'laundry_order_id',v_order.id,'order_status',v_order.status,'leg',v_job.leg
  );
end
$$;

revoke all on function public.queuego_laundry_rider_action(uuid,text) from public,anon;
grant execute on function public.queuego_laundry_rider_action(uuid,text) to authenticated,service_role;
