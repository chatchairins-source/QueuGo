-- QueueGo table QR v1. Existing pos_tables, orders and POS workflows are retained.
-- Apply atomically through Supabase migration tooling.
begin;
create extension if not exists pgcrypto with schema extensions;
alter table public.pos_tables add column if not exists qr_token uuid not null default gen_random_uuid();
create unique index if not exists pos_tables_qr_token_key on public.pos_tables(qr_token);
create table if not exists public.qg_table_sessions (
 id uuid primary key default gen_random_uuid(),
 shop_id uuid not null references public.shop_profiles(id),
 table_id uuid not null,
 device_hash text not null check(length(device_hash)=64),
 created_at timestamptz not null default now(),
 expires_at timestamptz not null,
 revoked_at timestamptz,
 constraint qg_table_sessions_table_shop_fk foreign key(table_id,shop_id) references public.pos_tables(id,shop_id),
 constraint qg_table_sessions_one_hour check(expires_at=created_at+interval '1 hour')
);
create index if not exists qg_table_sessions_table_expiry_idx on public.qg_table_sessions(table_id,expires_at desc);
alter table public.qg_table_sessions enable row level security;
revoke all on public.qg_table_sessions from anon,authenticated;
alter table public.orders add column if not exists table_session_id uuid references public.qg_table_sessions(id);
create index if not exists orders_table_session_idx on public.orders(table_session_id,created_at desc) where table_session_id is not null;
-- Retain the existing one-open-staff-bill rule; QR orders have independent receipts.
drop index if exists public.pos_one_open_bill_per_table;
create unique index pos_one_open_bill_per_table on public.orders(table_id)
 where order_type='DINE_IN' and payment_status='UNPAID' and status<>'cancelled' and table_session_id is null;
create table if not exists public.qg_table_requests (
 session_id uuid not null references public.qg_table_sessions(id),
 request_id uuid not null,
 payload_hash text not null check(length(payload_hash)=64),
 order_id uuid not null references public.orders(id),
 created_at timestamptz not null default now(),
 primary key(session_id,request_id)
);
create index if not exists qg_table_requests_recent_idx on public.qg_table_requests(session_id,payload_hash,created_at desc);
alter table public.qg_table_requests enable row level security;
revoke all on public.qg_table_requests from anon,authenticated;

create or replace function public.qg_table_distance_m(p_lat double precision,p_lng double precision,v_lat double precision,v_lng double precision)
returns double precision language sql immutable set search_path=pg_catalog as $$
 select 6371000*2*asin(sqrt(least(1,power(sin(radians((p_lat-v_lat)/2)),2)+cos(radians(p_lat))*cos(radians(v_lat))*power(sin(radians((p_lng-v_lng)/2)),2))))
$$;
revoke all on function public.qg_table_distance_m(double precision,double precision,double precision,double precision) from public,anon,authenticated;

create or replace function public.qg_table_scan(p_qr_token uuid,p_device_key uuid,p_lat double precision,p_lng double precision)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_table public.pos_tables%rowtype;v_shop public.shop_profiles%rowtype;v_session public.qg_table_sessions%rowtype;
begin
 if p_device_key is null or p_lat is null or p_lng is null or p_lat not between -90 and 90 or p_lng not between -180 and 180 then raise exception 'กรุณาเปิดตำแหน่งที่ตั้งเพื่อสแกน QR โต๊ะ'; end if;
 select * into v_table from public.pos_tables where qr_token=p_qr_token and active;
 if not found then raise exception 'QR โต๊ะไม่พร้อมใช้งาน กรุณาติดต่อร้าน'; end if;
 select * into v_shop from public.shop_profiles where id=v_table.shop_id and status not in ('suspended','rejected');
 if not found or v_shop.latitude is null or v_shop.longitude is null then raise exception 'ร้านยังไม่ได้ตั้งพิกัด'; end if;
 if public.qg_table_distance_m(p_lat,p_lng,v_shop.latitude,v_shop.longitude)>100 then raise exception 'กรุณาสแกน QR ภายในระยะ 100 เมตรจากร้าน'; end if;
 insert into public.qg_table_sessions(shop_id,table_id,device_hash,created_at,expires_at)
 values(v_table.shop_id,v_table.id,encode(extensions.digest(p_device_key::text,'sha256'),'hex'),now(),now()+interval '1 hour') returning * into v_session;
 return jsonb_build_object('session_id',v_session.id,'shop_id',v_shop.id,'shop_name',v_shop.shop_name,'table_id',v_table.id,'table_name',v_table.label,'expires_at',v_session.expires_at,'server_time',now());
end $$;

create or replace function public.qg_table_session_status(p_session uuid,p_device_key uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v public.qg_table_sessions%rowtype;t public.pos_tables%rowtype;s public.shop_profiles%rowtype;
begin
 select * into v from public.qg_table_sessions where id=p_session and device_hash=encode(extensions.digest(p_device_key::text,'sha256'),'hex');
 if not found then raise exception 'ไม่พบสิทธิ์การสั่งอาหารบนเครื่องนี้'; end if;
 select * into t from public.pos_tables where id=v.table_id and shop_id=v.shop_id;
 select * into s from public.shop_profiles where id=v.shop_id;
 return jsonb_build_object('session_id',v.id,'shop_id',v.shop_id,'shop_name',s.shop_name,'table_id',v.table_id,'table_name',t.label,'expires_at',v.expires_at,'server_time',now(), 'active',v.revoked_at is null and v.expires_at>now() and t.active and s.status not in ('suspended','rejected'));
end $$;

create or replace function public.qg_table_catalog(p_session uuid,p_device_key uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v public.qg_table_sessions%rowtype;
begin
 select * into v from public.qg_table_sessions where id=p_session and device_hash=encode(extensions.digest(p_device_key::text,'sha256'),'hex') and revoked_at is null and expires_at>now();
 if not found or not exists(select 1 from public.pos_tables where id=v.table_id and active) then raise exception 'สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง'; end if;
 return (select coalesce(jsonb_agg(jsonb_build_object('id',p.id,'name',p.name,'description',p.description,'image',p.image,'category',p.category,'price',coalesce(p.pos_price,p.price)) order by p.name),'[]'::jsonb)
 from public.products p where p.shop_id=v.shop_id and p.available and p.pos_available);
end $$;

create or replace function public.qg_table_checkout(p_session uuid,p_device_key uuid,p_request uuid,p_items jsonb,p_lat double precision,p_lng double precision)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v public.qg_table_sessions%rowtype;t public.pos_tables%rowtype;s public.shop_profiles%rowtype;
 v_order uuid;v_hash text;v_old public.qg_table_requests%rowtype;v_line jsonb;v_product public.products%rowtype;
 v_quantity integer;v_count integer:=0;v_total numeric:=0;v_note text;v_seen uuid[]:='{}';
begin
 if p_session is null or p_device_key is null or p_request is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items) not between 1 and 50 then raise exception 'รายการอาหารไม่ถูกต้อง'; end if;
 if p_lat is null or p_lng is null or p_lat not between -90 and 90 or p_lng not between -180 and 180 then raise exception 'ต้องยืนยันตำแหน่งภายในร้านก่อนส่งออเดอร์'; end if;
 select * into v from public.qg_table_sessions where id=p_session and device_hash=encode(extensions.digest(p_device_key::text,'sha256'),'hex') for update;
 if not found then raise exception 'ไม่พบสิทธิ์การสั่งอาหารบนเครื่องนี้'; end if;
 v_hash:=encode(extensions.digest(p_items::text,'sha256'),'hex');
 select * into v_old from public.qg_table_requests where session_id=p_session and request_id=p_request;
 if found then
  if v_old.payload_hash<>v_hash then raise exception 'รหัสคำขอถูกใช้กับรายการอาหารอื่น'; end if;
  return jsonb_build_object('order_id',v_old.order_id,'duplicate',true,'reason','same_request');
 end if;
 if v.revoked_at is not null or now()>=v.expires_at then raise exception 'สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง'; end if;
 select * into t from public.pos_tables where id=v.table_id and shop_id=v.shop_id and active;
 select * into s from public.shop_profiles where id=v.shop_id and status not in ('suspended','rejected');
 if not found or t.id is null or s.latitude is null or s.longitude is null then raise exception 'ร้านหรือโต๊ะไม่พร้อมรับออเดอร์'; end if;
 if public.qg_table_distance_m(p_lat,p_lng,s.latitude,s.longitude)>100 then raise exception 'กรุณาสั่งอาหารภายในระยะ 100 เมตรจากร้าน'; end if;
 -- Separate request UUIDs with an identical cart arriving within eight seconds are accidental duplicates.
 select * into v_old from public.qg_table_requests where session_id=p_session and payload_hash=v_hash and created_at>now()-interval '8 seconds' order by created_at desc limit 1;
 if found then
  insert into public.qg_table_requests(session_id,request_id,payload_hash,order_id) values(v.id,p_request,v_hash,v_old.order_id);
  return jsonb_build_object('order_id',v_old.order_id,'duplicate',true,'reason','recent_same_cart');
 end if;
 perform set_config('queuego.pos_rpc','on',true);
 insert into public.orders(order_number,shop_id,order_type,sales_channel,table_id,table_session_id,status,kitchen_status,payment_status,bill_status,subtotal,total_amount,delivery_fee,gp_rate,gp_amount)
 values('QR-'||upper(substr(replace(gen_random_uuid()::text,'-',''),1,12)),v.shop_id,'DINE_IN','POS',v.table_id,v.id,'pending','SENT_TO_KITCHEN','UNPAID','OPEN',0,0,0,0,0) returning id into v_order;
 for v_line in select value from jsonb_array_elements(p_items) loop
  if jsonb_typeof(v_line)<>'object' or (v_line-'id'-'quantity'-'note')<>'{}'::jsonb then raise exception 'รูปแบบสินค้าไม่ถูกต้อง'; end if;
  v_quantity:=(v_line->>'quantity')::integer;v_note:=coalesce(v_line->>'note','');
  if v_quantity not between 1 and 99 or length(v_note)>500 then raise exception 'จำนวนหรือหมายเหตุไม่ถูกต้อง'; end if;
  if (v_line->>'id')::uuid=any(v_seen) then raise exception 'มีสินค้าเดียวกันซ้ำในคำขอ'; end if;
  select * into v_product from public.products where id=(v_line->>'id')::uuid and shop_id=v.shop_id and available and pos_available;
  if not found then raise exception 'สินค้าไม่พร้อมขาย'; end if;
  v_seen:=array_append(v_seen,v_product.id);v_count:=v_count+1;
  insert into public.order_items(order_id,product_id,item_type,item_name,description,quantity,unit_price,total_price,pos_kitchen_status,pos_batch)
  values(v_order,v_product.id,'product',v_product.name,nullif(trim(v_note),''),v_quantity,coalesce(v_product.pos_price,v_product.price),v_quantity*coalesce(v_product.pos_price,v_product.price),'SENT_TO_KITCHEN',1);
  v_total:=v_total+v_quantity*coalesce(v_product.pos_price,v_product.price);
 end loop;
 if v_count=0 then raise exception 'ไม่มีสินค้า'; end if;
 update public.orders set subtotal=v_total,total_amount=v_total,updated_at=now() where id=v_order;
 insert into public.qg_table_requests(session_id,request_id,payload_hash,order_id) values(v.id,p_request,v_hash,v_order);
 return jsonb_build_object('order_id',v_order,'duplicate',false,'total',v_total,'table_name',t.label);
end $$;

create or replace function public.qg_table_rotate_qr(p_table uuid)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_token uuid;
begin
 if not public.pos_allowed('manage_staff') then raise exception 'ไม่มีสิทธิ์จัดการ QR'; end if;
 update public.pos_tables set qr_token=gen_random_uuid() where id=p_table and shop_id=public.pos_my_shop() returning qr_token into v_token;
 if v_token is null then raise exception 'ไม่พบโต๊ะ'; end if;
 update public.qg_table_sessions set revoked_at=now() where table_id=p_table and revoked_at is null and expires_at>now();
 return v_token;
end $$;
revoke all on function public.qg_table_scan(uuid,uuid,double precision,double precision),public.qg_table_session_status(uuid,uuid),public.qg_table_catalog(uuid,uuid),public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision),public.qg_table_rotate_qr(uuid) from public;
grant execute on function public.qg_table_scan(uuid,uuid,double precision,double precision),public.qg_table_session_status(uuid,uuid),public.qg_table_catalog(uuid,uuid),public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision) to anon,authenticated;
grant execute on function public.qg_table_rotate_qr(uuid) to authenticated;
commit;
