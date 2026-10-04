-- QueueGo platform feature switches + versioned pricing
-- Additive migration. Existing orders keep their captured prices/GP.

create table if not exists public.queuego_platform_rules (
  id uuid primary key default gen_random_uuid(),
  rule_key text not null,
  value jsonb not null,
  effective_from timestamptz not null default now(),
  note text,
  created_by uuid references public.users(id) on delete set null default public.get_my_user_id(),
  created_at timestamptz not null default now(),
  unique (rule_key, effective_from)
);

create index if not exists queuego_platform_rules_lookup_idx
  on public.queuego_platform_rules(rule_key, effective_from desc);

alter table public.queuego_platform_rules enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='queuego_platform_rules'
      and policyname='queuego_platform_rules_read'
  ) then
    create policy queuego_platform_rules_read
      on public.queuego_platform_rules
      for select to anon, authenticated
      using (true);
  end if;
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='queuego_platform_rules'
      and policyname='queuego_platform_rules_admin_insert'
  ) then
    create policy queuego_platform_rules_admin_insert
      on public.queuego_platform_rules
      for insert to authenticated
      with check (public.get_my_role()='admin');
  end if;
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='queuego_platform_rules'
      and policyname='queuego_platform_rules_admin_update'
  ) then
    create policy queuego_platform_rules_admin_update
      on public.queuego_platform_rules
      for update to authenticated
      using (public.get_my_role()='admin')
      with check (public.get_my_role()='admin');
  end if;
  if not exists (
    select 1 from pg_policies
    where schemaname='public' and tablename='queuego_platform_rules'
      and policyname='queuego_platform_rules_admin_delete'
  ) then
    create policy queuego_platform_rules_admin_delete
      on public.queuego_platform_rules
      for delete to authenticated
      using (public.get_my_role()='admin');
  end if;
end $$;

revoke all on public.queuego_platform_rules from public, anon, authenticated;
grant select on public.queuego_platform_rules to anon, authenticated;
grant insert, update, delete on public.queuego_platform_rules to authenticated;
grant all on public.queuego_platform_rules to service_role;

create or replace function public.queuego_platform_rule_guard()
returns trigger
language plpgsql
set search_path to 'public','pg_temp'
as $$
declare n numeric;
begin
  if new.rule_key in ('feature.market_multi_shop','feature.route_bundle','feature.gp') then
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
    'route_bundle.max_orders'
  ) then
    if jsonb_typeof(new.value) <> 'number' then
      raise exception 'pricing rule must be numeric';
    end if;
    n := (new.value #>> '{}')::numeric;
    if n < 0 then raise exception 'pricing rule cannot be negative'; end if;
    if new.rule_key='pricing.gp_default_rate' and n > 100 then
      raise exception 'GP rate must be 0-100';
    end if;
    if new.rule_key='route_bundle.max_orders' and (n < 1 or n > 5 or n <> trunc(n)) then
      raise exception 'route bundle max orders must be integer 1-5';
    end if;
    if new.rule_key='route_bundle.max_delay_minutes' and n > 240 then
      raise exception 'route bundle delay too large';
    end if;
    if new.rule_key='route_bundle.max_detour_km' and n > 50 then
      raise exception 'route bundle detour too large';
    end if;
  else
    raise exception 'unsupported QueueGo platform rule: %', new.rule_key;
  end if;
  new.created_at := coalesce(new.created_at,now());
  return new;
end $$;

drop trigger if exists queuego_platform_rule_guard_trg on public.queuego_platform_rules;
create trigger queuego_platform_rule_guard_trg
before insert or update on public.queuego_platform_rules
for each row execute function public.queuego_platform_rule_guard();

create or replace function public.queuego_rule_json(
  p_key text,
  p_default jsonb,
  p_at timestamptz default now()
) returns jsonb
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select coalesce(
    (
      select r.value
      from public.queuego_platform_rules r
      where r.rule_key=p_key and r.effective_from<=p_at
      order by r.effective_from desc, r.created_at desc, r.id desc
      limit 1
    ),
    p_default
  );
$$;

create or replace function public.queuego_rule_boolean(
  p_key text,
  p_default boolean,
  p_at timestamptz default now()
) returns boolean
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select coalesce(
    (public.queuego_rule_json(p_key,to_jsonb(p_default),p_at) #>> '{}')::boolean,
    p_default
  );
$$;

create or replace function public.queuego_rule_numeric(
  p_key text,
  p_default numeric,
  p_at timestamptz default now()
) returns numeric
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select coalesce(
    (public.queuego_rule_json(p_key,to_jsonb(p_default),p_at) #>> '{}')::numeric,
    p_default
  );
$$;

create or replace function public.queuego_feature_enabled(
  p_feature text,
  p_at timestamptz default now()
) returns boolean
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select public.queuego_rule_boolean(
    'feature.'||p_feature,
    case p_feature
      when 'market_multi_shop' then true
      when 'gp' then true
      when 'route_bundle' then false
      else false
    end,
    p_at
  );
$$;

create or replace function public.queuego_active_platform_rules(
  p_at timestamptz default now()
) returns table(rule_key text,value jsonb,effective_from timestamptz)
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select distinct on (r.rule_key) r.rule_key,r.value,r.effective_from
  from public.queuego_platform_rules r
  where r.effective_from<=p_at
  order by r.rule_key,r.effective_from desc,r.created_at desc,r.id desc;
$$;

revoke all on function public.queuego_rule_json(text,jsonb,timestamptz) from public;
revoke all on function public.queuego_rule_boolean(text,boolean,timestamptz) from public;
revoke all on function public.queuego_rule_numeric(text,numeric,timestamptz) from public;
revoke all on function public.queuego_feature_enabled(text,timestamptz) from public;
revoke all on function public.queuego_active_platform_rules(timestamptz) from public;
grant execute on function public.queuego_rule_json(text,jsonb,timestamptz) to anon,authenticated,service_role;
grant execute on function public.queuego_rule_boolean(text,boolean,timestamptz) to anon,authenticated,service_role;
grant execute on function public.queuego_rule_numeric(text,numeric,timestamptz) to anon,authenticated,service_role;
grant execute on function public.queuego_feature_enabled(text,timestamptz) to anon,authenticated,service_role;
grant execute on function public.queuego_active_platform_rules(timestamptz) to anon,authenticated,service_role;

-- Preserve current production behavior as the initial version.
insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select 'feature.market_multi_shop','true'::jsonb,'1970-01-01 00:00:00+00','Initial state preserves current market multi-shop behavior',null
where not exists(select 1 from public.queuego_platform_rules where rule_key='feature.market_multi_shop');

insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select 'feature.route_bundle','false'::jsonb,'1970-01-01 00:00:00+00','New route bundle remains off until implementation is certified',null
where not exists(select 1 from public.queuego_platform_rules where rule_key='feature.route_bundle');

insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select 'feature.gp','true'::jsonb,'1970-01-01 00:00:00+00','Initial state preserves current GP behavior',null
where not exists(select 1 from public.queuego_platform_rules where rule_key='feature.gp');

insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select 'pricing.gp_default_rate',
       to_jsonb(coalesce((select (value->>'default_rate')::numeric from public.system_settings where key='gp'),10::numeric)),
       '1970-01-01 00:00:00+00','Initial GP rate copied from existing production setting',null
where not exists(select 1 from public.queuego_platform_rules where rule_key='pricing.gp_default_rate');

insert into public.queuego_platform_rules(rule_key,value,effective_from,note,created_by)
select k,v,'1970-01-01 00:00:00+00',n,null
from (values
 ('pricing.market_base_fee','30'::jsonb,'Market base rider/delivery fee'),
 ('pricing.market_distance_step_fee','10'::jsonb,'Market extra fee per started km after 5 km'),
 ('pricing.market_second_shop_fee','10'::jsonb,'Second market shop rider fee'),
 ('pricing.market_additional_shop_fee','5'::jsonb,'Each market shop after the second'),
 ('route_bundle.max_detour_km','1.5'::jsonb,'Maximum route-bundle detour'),
 ('route_bundle.max_delay_minutes','10'::jsonb,'Maximum route-bundle added delay'),
 ('route_bundle.max_orders','2'::jsonb,'Maximum simultaneous bundled customer orders')
) seed(k,v,n)
where not exists(select 1 from public.queuego_platform_rules r where r.rule_key=seed.k);

alter table public.market_orders
  add column if not exists pricing_snapshot jsonb not null default '{}'::jsonb;

create or replace function public.queuego_market_multi_shop_fee(
  p_shop_count integer,
  p_at timestamptz default now()
) returns numeric
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select case
    when coalesce(p_shop_count,0)<=1 then 0::numeric
    when p_shop_count=2 then public.queuego_rule_numeric('pricing.market_second_shop_fee',10,p_at)
    else public.queuego_rule_numeric('pricing.market_second_shop_fee',10,p_at)
       + (p_shop_count-2)*public.queuego_rule_numeric('pricing.market_additional_shop_fee',5,p_at)
  end;
$$;

create or replace function public.queuego_market_delivery_fee(
  p_distance_km numeric,
  p_at timestamptz default now()
) returns numeric
language sql stable
security invoker
set search_path to 'public','pg_temp'
as $$
  select round(
    public.queuego_rule_numeric('pricing.market_base_fee',30,p_at)
    + greatest(0,ceil(coalesce(p_distance_km,0)-5))
      * public.queuego_rule_numeric('pricing.market_distance_step_fee',10,p_at)
  ,2);
$$;

revoke all on function public.queuego_market_multi_shop_fee(integer,timestamptz) from public;
revoke all on function public.queuego_market_delivery_fee(numeric,timestamptz) from public;
grant execute on function public.queuego_market_multi_shop_fee(integer,timestamptz) to anon,authenticated,service_role;
grant execute on function public.queuego_market_delivery_fee(numeric,timestamptz) to anon,authenticated,service_role;

create or replace function public.effective_gp_rate_at(
  p_shop_profile_id uuid,
  p_at timestamptz default now()
) returns numeric
language sql stable
security definer
set search_path to 'public','pg_temp'
as $$
  select case
    when not public.queuego_feature_enabled('gp',p_at) then 0::numeric
    else coalesce(
      (
        select r.rate_percent
        from public.shop_gp_rates r
        join public.shop_profiles sp on sp.user_id=r.shop_user_id
        where sp.id=p_shop_profile_id
          and (r.promo_until is null or r.promo_until >= (p_at at time zone 'Asia/Bangkok')::date)
        limit 1
      ),
      public.queuego_rule_numeric(
        'pricing.gp_default_rate',
        coalesce((select (value->>'default_rate')::numeric from public.system_settings where key='gp'),10::numeric),
        p_at
      ),
      10::numeric
    )
  end;
$$;

create or replace function public.effective_gp_rate(
  p_shop_profile_id uuid,
  p_day date default current_date
) returns numeric
language sql stable
security definer
set search_path to 'public','pg_temp'
as $$
  select public.effective_gp_rate_at(
    p_shop_profile_id,
    (p_day::timestamp at time zone 'Asia/Bangkok')
  );
$$;

create or replace function public.orders_set_gp()
returns trigger
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
begin
 if new.order_type in ('DINE_IN','TAKEAWAY') then
   new.gp_rate:=0; new.gp_amount:=0;
 elsif tg_op='INSERT' then
   new.gp_rate:=public.effective_gp_rate_at(new.shop_id,now());
   new.gp_amount:=round(coalesce(new.subtotal,0)*new.gp_rate/100,2);
 else
   new.gp_rate:=old.gp_rate;
   if new.subtotal is distinct from old.subtotal then
     new.gp_amount:=round(coalesce(new.subtotal,0)*coalesce(old.gp_rate,10)/100,2);
   else new.gp_amount:=old.gp_amount; end if;
 end if;
 return new;
end $$;

create or replace function public.queuego_place_market_order(
 p_market_order_id uuid,
 p_items jsonb,
 p_delivery_lat double precision,
 p_delivery_lng double precision,
 p_delivery_address text,
 p_note text default null
) returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
 v_customer uuid;v_market uuid;v_shop uuid;v_shoprow public.shop_profiles%rowtype;v_item jsonb;v_prod public.products%rowtype;
 v_pid uuid;v_qty int;v_sub numeric:=0;v_shop_sub numeric;v_shop_count int;v_fee numeric;v_extra numeric;v_total numeric;
 v_dist double precision;v_a double precision;v_oid uuid;v_num text;v_seq int:=0;v_today text;v_next int;v_seen uuid[]:=array[]::uuid[];
 v_now timestamptz:=now();v_snapshot jsonb;
begin
 if auth.uid() is null then raise exception 'customer login required'; end if;
 select id into v_customer from public.users where auth_user_id=auth.uid() and role='customer' and status='active';
 if v_customer is null then raise exception 'active customer required'; end if;
 if p_market_order_id is null then raise exception 'market order id required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(v_customer::text,141));
 if exists(select 1 from public.market_orders where id=p_market_order_id) then
  return (select jsonb_build_object('market_order_id',id,'subtotal',subtotal,'delivery_fee',delivery_fee,'multi_shop_service_fee',multi_shop_service_fee,'total_amount',total_amount,'replayed',true) from public.market_orders where id=p_market_order_id and customer_id=v_customer);
 end if;
 if exists(select 1 from public.orders where customer_id=v_customer and status in('pending','accepted','searching_rider','rider_assigned','preparing','ready','assigned','picked_up','in_progress')) then raise exception 'customer has an active order'; end if;
 if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 60 then raise exception 'invalid cart'; end if;
 if p_delivery_lat is null or p_delivery_lng is null or abs(p_delivery_lat)>90 or abs(p_delivery_lng)>180 then raise exception 'delivery location required'; end if;
 if length(trim(coalesce(p_delivery_address,'')))<3 or length(p_delivery_address)>500 then raise exception 'delivery address required'; end if;
 if length(coalesce(p_note,''))>500 then raise exception 'note too long'; end if;
 perform set_config('queuego.market_checkout','on',true);

 for v_item in select value from jsonb_array_elements(p_items) loop
  if (v_item->>'product_id') is null or (v_item->>'qty') !~ '^[1-9][0-9]?$' then raise exception 'invalid cart item'; end if;
  v_pid:=(v_item->>'product_id')::uuid;v_qty=(v_item->>'qty')::int;
  if v_pid=any(v_seen) then raise exception 'duplicate product'; end if;v_seen:=array_append(v_seen,v_pid);
  select p.* into v_prod from public.products p join public.market_products mp on mp.product_id=p.id
   join public.shop_profiles s on s.id=p.shop_id
   where p.id=v_pid and p.available and p.delivery_available and s.status='active' and s.delivery_enabled and s.market_id is not null
   and mp.stock_quantity>=mp.pack_size*v_qty for share of p;
  if not found then raise exception 'market product unavailable'; end if;
  if v_market is null then select market_id into v_market from public.shop_profiles where id=v_prod.shop_id;
  elsif v_market is distinct from (select market_id from public.shop_profiles where id=v_prod.shop_id) then raise exception 'all shops must be in same market'; end if;
  v_sub:=v_sub+round(coalesce(v_prod.delivery_price,v_prod.price)*v_qty,2);
 end loop;

 select count(distinct p.shop_id) into v_shop_count
 from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid;
 if v_shop_count<1 then raise exception 'empty market cart'; end if;
 if v_shop_count>1 and not public.queuego_feature_enabled('market_multi_shop',v_now) then
   raise exception 'market multi-shop is temporarily disabled';
 end if;

 select s.* into v_shoprow from public.shop_profiles s
 where s.id=(select p.shop_id from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid limit 1);
 v_a:=power(sin(radians(p_delivery_lat-v_shoprow.latitude)/2),2)+cos(radians(v_shoprow.latitude))*cos(radians(p_delivery_lat))*power(sin(radians(p_delivery_lng-v_shoprow.longitude)/2),2);
 v_dist:=6371*2*asin(sqrt(least(1,greatest(0,v_a))));
 v_fee:=public.queuego_market_delivery_fee(v_dist::numeric,v_now);
 v_extra:=public.queuego_market_multi_shop_fee(v_shop_count,v_now);
 v_total:=round(v_sub,2)+v_fee+v_extra;
 v_snapshot:=jsonb_build_object(
   'captured_at',v_now,
   'market_multi_shop_enabled',public.queuego_feature_enabled('market_multi_shop',v_now),
   'market_base_fee',public.queuego_rule_numeric('pricing.market_base_fee',30,v_now),
   'market_distance_step_fee',public.queuego_rule_numeric('pricing.market_distance_step_fee',10,v_now),
   'market_second_shop_fee',public.queuego_rule_numeric('pricing.market_second_shop_fee',10,v_now),
   'market_additional_shop_fee',public.queuego_rule_numeric('pricing.market_additional_shop_fee',5,v_now)
 );

 insert into public.market_orders(
   id,customer_id,market_id,status,shop_count,subtotal,delivery_fee,multi_shop_service_fee,rider_bonus,total_amount,
   delivery_address,delivery_latitude,delivery_longitude,created_at,updated_at,pricing_snapshot
 ) values(
   p_market_order_id,v_customer,v_market,'PENDING',v_shop_count,round(v_sub,2),v_fee,v_extra,v_extra,v_total,
   trim(p_delivery_address),p_delivery_lat,p_delivery_lng,v_now,v_now,v_snapshot
 );

 for v_shop in
  select distinct p.shop_id
  from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid
  order by p.shop_id
 loop
  v_seq:=v_seq+1;v_oid:=gen_random_uuid();
  select * into v_shoprow from public.shop_profiles where id=v_shop;
  select round(sum(coalesce(p.delivery_price,p.price)*(j.value->>'qty')::int),2)
   into v_shop_sub
   from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid
   where p.shop_id=v_shop;
  v_today:=to_char(now() at time zone 'Asia/Bangkok','YYYYMMDD');
  perform pg_advisory_xact_lock(hashtextextended('queuego-number-'||v_today,122));
  select coalesce(max(right(order_number,4)::integer),0)+1 into v_next
   from public.orders where order_number ~ ('^QT-'||v_today||'-[0-9]{4}$');
  v_num:='QT-'||v_today||'-'||lpad(v_next::text,4,'0');
  insert into public.orders(id,order_number,customer_id,shop_id,order_type,sales_channel,status,subtotal,delivery_fee,total_amount,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,note,created_by,market_order_id,multi_shop_service_fee,rider_multi_shop_bonus,market_shop_count)
  values(v_oid,v_num,v_customer,v_shop,'shopping','QUEUEGO_DELIVERY','pending',v_shop_sub,case when v_seq=1 then v_fee else 0 end,v_shop_sub+case when v_seq=1 then v_fee+v_extra else 0 end,v_shoprow.address,v_shoprow.latitude,v_shoprow.longitude,trim(p_delivery_address),p_delivery_lat,p_delivery_lng,nullif(trim(p_note),''),v_customer,p_market_order_id,case when v_seq=1 then v_extra else 0 end,case when v_seq=1 then v_extra else 0 end,v_shop_count);
  for v_item in
   select j.value from jsonb_array_elements(p_items) j join public.products p on p.id=(j.value->>'product_id')::uuid where p.shop_id=v_shop
  loop
   v_pid=(v_item->>'product_id')::uuid;v_qty=(v_item->>'qty')::int;select * into v_prod from public.products where id=v_pid;
   insert into public.order_items(order_id,product_id,item_type,item_name,description,quantity,unit_price,total_price)
   values(v_oid,v_pid,'product',v_prod.name,v_prod.description,v_qty,coalesce(v_prod.delivery_price,v_prod.price),round(coalesce(v_prod.delivery_price,v_prod.price)*v_qty,2));
  end loop;
  insert into public.market_order_pickups(market_order_id,order_id,shop_id,pickup_sequence,status,shop_amount)
   values(p_market_order_id,v_oid,v_shop,v_seq,'PENDING',v_shop_sub);
  insert into public.payments(order_id,payer_id,amount,payment_method,status,note)
   values(v_oid,v_customer,v_shop_sub+case when v_seq=1 then v_fee+v_extra else 0 end,'cash','pending','Market multi-shop cash order');
  insert into public.deliveries(order_id,status,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,distance_km,delivery_fee)
   values(v_oid,'pending',v_shoprow.address,v_shoprow.latitude,v_shoprow.longitude,trim(p_delivery_address),p_delivery_lat,p_delivery_lng,round(v_dist::numeric,2),case when v_seq=1 then v_fee else 0 end);
  insert into public.queuego_cash_order_locks(order_id) values(v_oid);
  insert into public.notifications(user_id,title,message,type,reference_id)
   values(v_shoprow.user_id,'มีคำสั่งซื้อจากตลาด','กรุณาตรวจสอบคำสั่งซื้อ '||v_num,'order',v_oid);
 end loop;
 return jsonb_build_object('market_order_id',p_market_order_id,'shop_count',v_shop_count,'subtotal',round(v_sub,2),'delivery_fee',v_fee,'multi_shop_service_fee',v_extra,'total_amount',v_total,'replayed',false);
end $$;

create or replace function public.qg_market_recalculate_group(p_market_order_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $$
declare
 v_count int;v_sub numeric;v_fee numeric;v_extra numeric;v_total numeric;v_lead uuid;v_snapshot jsonb;
 v_second numeric;v_additional numeric;
begin
 select count(*),coalesce(sum(subtotal),0) into v_count,v_sub from public.orders
 where market_order_id=p_market_order_id and status<>'cancelled';
 select delivery_fee,pricing_snapshot into v_fee,v_snapshot from public.market_orders where id=p_market_order_id for update;
 if not found then raise exception 'market order unavailable'; end if;
 if v_count=0 then
   update public.market_orders set status='CANCELLED',shop_count=0,subtotal=0,multi_shop_service_fee=0,rider_bonus=0,total_amount=0,updated_at=now() where id=p_market_order_id;
   return jsonb_build_object('status','CANCELLED','shop_count',0,'total_amount',0);
 end if;
 v_second:=coalesce(nullif(v_snapshot->>'market_second_shop_fee','')::numeric,public.queuego_rule_numeric('pricing.market_second_shop_fee',10,now()));
 v_additional:=coalesce(nullif(v_snapshot->>'market_additional_shop_fee','')::numeric,public.queuego_rule_numeric('pricing.market_additional_shop_fee',5,now()));
 v_extra:=case when v_count<=1 then 0 when v_count=2 then v_second else v_second+(v_count-2)*v_additional end;
 v_total:=round(v_sub,2)+v_fee+v_extra;
 select id into v_lead from public.orders where market_order_id=p_market_order_id and status<>'cancelled' order by created_at,id limit 1;
 perform set_config('queuego.v22_transition','rpc',true);
 update public.orders set delivery_fee=case when id=v_lead then v_fee else 0 end,
   multi_shop_service_fee=case when id=v_lead then v_extra else 0 end,
   rider_multi_shop_bonus=case when id=v_lead then v_extra else 0 end,
   market_shop_count=v_count,
   total_amount=subtotal+case when id=v_lead then v_fee+v_extra else 0 end,updated_at=now()
 where market_order_id=p_market_order_id and status<>'cancelled';
 update public.payments p set amount=o.total_amount,updated_at=now() from public.orders o
 where p.order_id=o.id and o.market_order_id=p_market_order_id and o.status<>'cancelled' and p.status='pending';
 update public.deliveries d set delivery_fee=o.delivery_fee,updated_at=now() from public.orders o
 where d.order_id=o.id and o.market_order_id=p_market_order_id and o.status<>'cancelled';
 update public.market_orders set shop_count=v_count,subtotal=round(v_sub,2),multi_shop_service_fee=v_extra,rider_bonus=v_extra,total_amount=v_total,
 status=case when status='CANCELLED' then 'PENDING' else status end,updated_at=now() where id=p_market_order_id;
 return jsonb_build_object('status','ACTIVE','shop_count',v_count,'subtotal',round(v_sub,2),'delivery_fee',v_fee,'multi_shop_service_fee',v_extra,'total_amount',v_total);
end $$;

-- Explicit RPC grants: customer market checkout remains authenticated-only.
revoke all on function public.effective_gp_rate_at(uuid,timestamptz) from public;
grant execute on function public.effective_gp_rate_at(uuid,timestamptz) to authenticated,service_role;
revoke all on function public.queuego_place_market_order(uuid,jsonb,double precision,double precision,text,text) from public,anon;
grant execute on function public.queuego_place_market_order(uuid,jsonb,double precision,double precision,text,text) to authenticated,service_role;
