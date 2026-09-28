-- QueueGo POS. Existing shopping/service orders remain untouched.
begin;

create table if not exists public.pos_tables (
 id uuid primary key default gen_random_uuid(), shop_id uuid not null references public.shop_profiles(id),
 label text not null check (length(trim(label)) between 1 and 40),
 active boolean not null default true, created_at timestamptz not null default now(),
 unique(shop_id,label), unique(id,shop_id)
);
create table if not exists public.pos_staff (
 user_id uuid primary key references auth.users(id), shop_id uuid not null references public.shop_profiles(id),
 display_name text not null check(length(trim(display_name)) between 1 and 100),
 permissions jsonb not null default '{}'::jsonb, active boolean not null default true,
 created_at timestamptz not null default now()
);
create index if not exists pos_staff_shop_idx on public.pos_staff(shop_id);
create table if not exists public.pos_invites (
 id uuid primary key default gen_random_uuid(), shop_id uuid not null references public.shop_profiles(id),
 secret_hash text not null unique, permissions jsonb not null default '{}'::jsonb,
 expires_at timestamptz not null default (now()+interval '24 hours'),
 used_by uuid references auth.users(id), created_at timestamptz not null default now()
);
create index if not exists pos_invites_shop_idx on public.pos_invites(shop_id);
create table if not exists public.pos_events (
 id uuid primary key default gen_random_uuid(), shop_id uuid not null references public.shop_profiles(id),
 order_id uuid, actor_id uuid, entity text not null, action text not null,
 before_state jsonb, after_state jsonb, created_at timestamptz not null default now()
);
create index if not exists pos_events_shop_created_idx on public.pos_events(shop_id,created_at desc);
alter publication supabase_realtime add table public.pos_tables,public.pos_staff,public.order_items;

alter table public.orders drop constraint if exists orders_order_type_check;
alter table public.orders add constraint orders_order_type_check check(order_type in ('shopping','service','DINE_IN','TAKEAWAY','DELIVERY'));
alter table public.orders add column if not exists table_id uuid references public.pos_tables(id);
alter table public.orders add column if not exists staff_id uuid references auth.users(id);
alter table public.orders add column if not exists kitchen_status text;
alter table public.orders add column if not exists payment_status text;
alter table public.orders add column if not exists payment_method text;
alter table public.orders add column if not exists sales_channel text;
alter table public.orders add column if not exists discount_amount numeric not null default 0 check(discount_amount>=0);
alter table public.orders add constraint pos_kitchen_status_check check(kitchen_status is null or kitchen_status in ('NEW','SENT_TO_KITCHEN','COOKING','READY','SERVED'));
alter table public.orders add constraint pos_payment_status_check check(payment_status is null or payment_status in ('UNPAID','PAID','REFUNDED'));
alter table public.orders add constraint pos_sales_channel_check check(sales_channel is null or sales_channel in ('POS','QUEUEGO_DELIVERY'));
alter table public.orders add constraint pos_channel_integrity_check check(
 (order_type in ('DINE_IN','TAKEAWAY') and sales_channel='POS' and customer_id is null and rider_id is null
  and delivery_fee=0 and gp_rate=0 and gp_amount=0 and subtotal>=0 and total_amount=subtotal-discount_amount)
 or (order_type not in ('DINE_IN','TAKEAWAY') and sales_channel is distinct from 'POS' and discount_amount=0)
);
create unique index if not exists pos_one_open_bill_per_table on public.orders(table_id) where order_type='DINE_IN' and payment_status='UNPAID' and status<>'cancelled';
create index if not exists pos_orders_shop_created_idx on public.orders(shop_id,created_at desc) where sales_channel='POS';
alter table public.order_items add column if not exists product_id uuid references public.products(id);
alter table public.products add column if not exists pos_available boolean not null default true;
alter table public.products add column if not exists delivery_available boolean not null default true;
alter table public.products add column if not exists pos_price numeric check(pos_price is null or pos_price>=0);
alter table public.products add column if not exists delivery_price numeric check(delivery_price is null or delivery_price>=0);

create or replace function public.orders_set_gp() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if new.order_type in ('DINE_IN','TAKEAWAY') then
   new.gp_rate:=0; new.gp_amount:=0;
 elsif tg_op='INSERT' then
   new.gp_rate:=public.effective_gp_rate(new.shop_id,(now() at time zone 'Asia/Bangkok')::date);
   new.gp_amount:=round(coalesce(new.subtotal,0)*new.gp_rate/100,2);
 else
   new.gp_rate:=old.gp_rate;
   if new.subtotal is distinct from old.subtotal then
     new.gp_amount:=round(coalesce(new.subtotal,0)*coalesce(old.gp_rate,10)/100,2);
   else new.gp_amount:=old.gp_amount; end if;
 end if;
 return new;
end $$;

-- Counter orders are accepted even if the shop has closed its delivery channel.
create or replace function public.queuego_reject_closed_shop_order() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_open boolean;v_resume timestamptz;v_now timestamp;v_special public.shop_special_hours%rowtype;v_hours public.shop_business_hours%rowtype;
begin
 if new.order_type in ('DINE_IN','TAKEAWAY') then return new; end if;
 select s.is_open,s.resume_at into v_open,v_resume from public.shop_open_states s where s.shop_id=new.shop_id;
 if found and not v_open and (v_resume is null or v_resume>now()) then raise exception 'ร้านปิดรับออเดอร์ชั่วคราว'; end if;
 v_now:=now() at time zone 'Asia/Bangkok';
 select * into v_special from public.shop_special_hours where shop_id=new.shop_id and day=v_now::date;
 if found then
  if v_special.is_closed or v_special.opens_at is null or v_special.closes_at is null or v_now::time<v_special.opens_at or v_now::time>=v_special.closes_at then raise exception 'ร้านปิดตามเวลาพิเศษ'; end if;
 else
  select * into v_hours from public.shop_business_hours where shop_id=new.shop_id and weekday=extract(dow from v_now);
  if found and (v_hours.is_closed or v_now::time<v_hours.opens_at or v_now::time>=v_hours.closes_at) then raise exception 'ร้านปิดตามเวลาทำการ'; end if;
 end if;
 return new;
end $$;

create or replace function public.pos_my_shop() returns uuid language sql stable security definer set search_path=public,pg_temp as $$
 select coalesce((select sp.id from public.shop_profiles sp join public.users u on u.id=sp.user_id
   where u.auth_user_id=(select auth.uid()) and u.role='shop' and u.status='active' and sp.status='active' limit 1),
  (select s.shop_id from public.pos_staff s join public.shop_profiles sp on sp.id=s.shop_id
   join public.users u on u.id=sp.user_id where s.user_id=(select auth.uid()) and s.active
   and sp.status='active' and u.status='active' limit 1))
$$;
create or replace function public.pos_is_owner() returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.shop_profiles sp join public.users u on u.id=sp.user_id
 where u.auth_user_id=(select auth.uid()) and u.role='shop' and u.status='active' and sp.status='active')
$$;
create or replace function public.pos_allowed(p_permission text) returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select public.pos_is_owner() or exists(select 1 from public.pos_staff s where s.user_id=(select auth.uid())
 and s.shop_id=public.pos_my_shop() and s.active and s.permissions->p_permission='true'::jsonb)
$$;

alter table public.pos_tables enable row level security;
alter table public.pos_staff enable row level security;
alter table public.pos_invites enable row level security;
alter table public.pos_events enable row level security;
create policy pos_tables_read on public.pos_tables for select to authenticated using(shop_id=public.pos_my_shop());
create policy pos_staff_read on public.pos_staff for select to authenticated using(user_id=(select auth.uid()) or (shop_id=public.pos_my_shop() and public.pos_is_owner()));
create policy pos_staff_manager_read on public.pos_staff for select to authenticated using(shop_id=public.pos_my_shop() and public.pos_allowed('manage_staff'));
create policy pos_invites_owner_read on public.pos_invites for select to authenticated using(shop_id=public.pos_my_shop() and public.pos_is_owner());
create policy pos_events_owner_read on public.pos_events for select to authenticated using(shop_id=public.pos_my_shop() and public.pos_is_owner());
create policy pos_orders_read on public.orders for select to authenticated using(sales_channel='POS' and shop_id=public.pos_my_shop());
create policy pos_items_read on public.order_items for select to authenticated using(exists(select 1 from public.orders o where o.id=order_id and o.sales_channel='POS' and o.shop_id=public.pos_my_shop()));
create policy pos_payments_read on public.payments for select to authenticated using(exists(select 1 from public.orders o where o.id=order_id and o.sales_channel='POS' and o.shop_id=public.pos_my_shop()));
create policy pos_products_read on public.products for select to authenticated using(shop_id=public.pos_my_shop());
grant select on public.pos_tables,public.pos_staff,public.pos_invites to authenticated;
grant select on public.pos_events to authenticated;

create or replace function public.pos_record_event() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order uuid;v_shop uuid;v_row jsonb;
begin
 v_row:=case when tg_op='DELETE' then to_jsonb(old) else to_jsonb(new) end;
 if tg_table_name='orders' then
  if coalesce(v_row->>'sales_channel','')<>'POS' then return case when tg_op='DELETE' then old else new end; end if;
  v_order:=(v_row->>'id')::uuid;v_shop:=(v_row->>'shop_id')::uuid;
 elsif tg_table_name='pos_staff' then
  v_shop:=(v_row->>'shop_id')::uuid;
 else
  v_order:=(v_row->>'order_id')::uuid;
  select shop_id into v_shop from public.orders where id=v_order and sales_channel='POS';
 end if;
 if v_shop is not null then
  insert into public.pos_events(shop_id,order_id,actor_id,entity,action,before_state,after_state)
  values(v_shop,v_order,auth.uid(),tg_table_name,tg_op,
    case when tg_op='INSERT' then null else to_jsonb(old) end,
    case when tg_op='DELETE' then null else to_jsonb(new) end);
 end if;
 return case when tg_op='DELETE' then old else new end;
end $$;
create trigger pos_audit_orders after insert or update or delete on public.orders for each row execute function public.pos_record_event();
create trigger pos_audit_items after insert or update or delete on public.order_items for each row execute function public.pos_record_event();
create trigger pos_audit_payments after insert or update or delete on public.payments for each row execute function public.pos_record_event();
create trigger pos_audit_staff after insert or update or delete on public.pos_staff for each row execute function public.pos_record_event();
revoke all on function public.pos_record_event() from public,anon;

-- Direct Data API writes to POS financial records are blocked. The RPC alone owns mutations.
create or replace function public.pos_guard_write() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order uuid;v_channel text;
begin
 if tg_table_name='orders' then
  v_channel:=case when tg_op='DELETE' then old.sales_channel else new.sales_channel end;
  if tg_op='UPDATE' and old.sales_channel='POS' then v_channel:='POS'; end if;
 else
  v_order:=case when tg_op='DELETE' then old.order_id else new.order_id end;
  select sales_channel into v_channel from public.orders where id=v_order;
 end if;
 if v_channel='POS' and current_setting('queuego.pos_rpc',true) is distinct from 'on' and not public.is_active_admin() then
  raise exception 'POS records require server RPC';
 end if;
 return case when tg_op='DELETE' then old else new end;
end $$;
create trigger pos_orders_guard before insert or update or delete on public.orders for each row execute function public.pos_guard_write();
create trigger pos_items_guard before insert or update or delete on public.order_items for each row execute function public.pos_guard_write();
create trigger pos_payments_guard before insert or update or delete on public.payments for each row execute function public.pos_guard_write();
revoke all on function public.pos_guard_write() from public,anon;

create or replace function public.pos_create_invite(p_secret text,p_permissions jsonb default '{}'::jsonb)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid;
begin
 if not public.pos_allowed('manage_staff') then raise exception 'staff management denied'; end if;
 if length(p_secret)<32 then raise exception 'invite secret too short'; end if;
 if jsonb_typeof(p_permissions)<>'object' then raise exception 'invalid permissions'; end if;
 if exists(select 1 from jsonb_each(p_permissions) x where x.key not in
   ('cancel_bill','discount','refund','close_bill','view_sales','edit_price','manage_staff') or jsonb_typeof(x.value)<>'boolean')
 then raise exception 'invalid permission values'; end if;
 if not public.pos_is_owner() and p_permissions<>'{}'::jsonb then raise exception 'staff cannot delegate permissions'; end if;
 insert into public.pos_invites(shop_id,secret_hash,permissions) values(public.pos_my_shop(),encode(sha256(convert_to(p_secret,'utf8')),'hex'),p_permissions) returning id into v_id;
 return v_id;
end $$;
create or replace function public.pos_join_shop(p_secret text,p_display_name text)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_inv public.pos_invites%rowtype;
begin
 if auth.uid() is null or length(trim(p_display_name)) not between 1 and 100 then raise exception 'invalid staff account'; end if;
 select * into v_inv from public.pos_invites where secret_hash=encode(sha256(convert_to(p_secret,'utf8')),'hex') for update;
 if not found or v_inv.used_by is not null or v_inv.expires_at<now() then raise exception 'invite invalid or expired'; end if;
 if exists(select 1 from public.pos_staff where user_id=auth.uid()) or public.pos_is_owner() then raise exception 'account already linked'; end if;
 insert into public.pos_staff(user_id,shop_id,display_name,permissions) values(auth.uid(),v_inv.shop_id,trim(p_display_name),v_inv.permissions);
 update public.pos_invites set used_by=auth.uid() where id=v_inv.id;
 return v_inv.shop_id;
end $$;
create or replace function public.pos_set_staff(p_user uuid,p_active boolean,p_permissions jsonb)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not public.pos_allowed('manage_staff') then raise exception 'staff management denied'; end if;
 if jsonb_typeof(p_permissions)<>'object' then raise exception 'invalid permissions'; end if;
 if exists(select 1 from jsonb_each(p_permissions) x where x.key not in
   ('cancel_bill','discount','refund','close_bill','view_sales','edit_price','manage_staff') or jsonb_typeof(x.value)<>'boolean')
 then raise exception 'invalid permission values'; end if;
 if not public.pos_is_owner() then
  if p_user=auth.uid() or exists(select 1 from jsonb_each_text(p_permissions) x where x.value='true' and not public.pos_allowed(x.key))
  then raise exception 'permission escalation denied'; end if;
 end if;
 update public.pos_staff set active=p_active,permissions=p_permissions where user_id=p_user and shop_id=public.pos_my_shop();
 if not found then raise exception 'staff not found'; end if;
end $$;
create or replace function public.pos_save_table(p_id uuid,p_label text,p_active boolean default true)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid;
begin
 if not public.pos_allowed('manage_staff') then raise exception 'table management denied'; end if;
 if length(trim(p_label)) not between 1 and 40 then raise exception 'invalid table name'; end if;
 if p_id is null then
  insert into public.pos_tables(shop_id,label,active) values(public.pos_my_shop(),trim(p_label),p_active) returning id into v_id;
 else
  update public.pos_tables set label=trim(p_label),active=p_active where id=p_id and shop_id=public.pos_my_shop() returning id into v_id;
  if v_id is null then raise exception 'table not found'; end if;
 end if;
 return v_id;
end $$;

create or replace function public.pos_edit_bill(p_order uuid,p_type text,p_table uuid,p_product uuid,p_quantity integer,p_note text default '')
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop uuid;v_order public.orders%rowtype;v_product public.products%rowtype;v_price numeric;v_id uuid;v_count integer;
begin
 v_shop:=public.pos_my_shop();
 if v_shop is null then raise exception 'POS access denied'; end if;
 if p_type not in ('DINE_IN','TAKEAWAY') or p_quantity not between -99 and 99 or length(coalesce(p_note,''))>500 then raise exception 'invalid bill input'; end if;
 if p_order is null then
  if p_type='DINE_IN' and not exists(select 1 from public.pos_tables where id=p_table and shop_id=v_shop and active) then raise exception 'table not found'; end if;
  if p_type='TAKEAWAY' and p_table is not null then raise exception 'takeaway has no table'; end if;
  if p_quantity<=0 or p_product is null then raise exception 'bill needs a product'; end if;
  perform set_config('queuego.pos_rpc','on',true);
  insert into public.orders(order_number,shop_id,order_type,sales_channel,table_id,staff_id,status,kitchen_status,payment_status,subtotal,total_amount,delivery_fee,gp_rate,gp_amount)
  values('POS-'||upper(substr(replace(gen_random_uuid()::text,'-',''),1,12)),v_shop,p_type,'POS',p_table,auth.uid(),'pending','NEW','UNPAID',0,0,0,0,0)
  returning id into v_id;
 else
  select * into v_order from public.orders where id=p_order and shop_id=v_shop and sales_channel='POS' for update;
  if not found or v_order.payment_status<>'UNPAID' or v_order.status='cancelled' then raise exception 'bill not editable'; end if;
  if v_order.kitchen_status not in ('NEW','SENT_TO_KITCHEN','COOKING','READY','SERVED') then raise exception 'invalid kitchen state'; end if;
  v_id:=p_order;
  if p_type<>v_order.order_type or p_table is distinct from v_order.table_id then raise exception 'bill identity cannot change'; end if;
  perform set_config('queuego.pos_rpc','on',true);
 end if;
 if p_product is not null then
  select * into v_product from public.products where id=p_product and shop_id=v_shop and pos_available and available;
  if not found then raise exception 'product unavailable'; end if;
  if v_order.kitchen_status is not null and v_order.kitchen_status<>'NEW' and p_quantity<0 then raise exception 'sent items cannot be reduced'; end if;
  v_price:=coalesce(v_product.pos_price,v_product.price);
  if p_quantity>0 then
   insert into public.order_items(order_id,product_id,item_name,description,quantity,unit_price,total_price)
   values(v_id,p_product,v_product.name,nullif(trim(p_note),''),p_quantity,v_price,p_quantity*v_price);
  elsif p_quantity<0 then
   -- Reductions only before kitchen submission; oldest matching line is locked.
   if v_order.kitchen_status<>'NEW' then raise exception 'sent items cannot be reduced'; end if;
   delete from public.order_items i where i.id=(select id from public.order_items where order_id=v_id and product_id=p_product
     and coalesce(description,'')=trim(p_note) order by created_at,id limit 1 for update) and i.quantity=-p_quantity;
   if not found then
    update public.order_items i set quantity=i.quantity+p_quantity,total_price=(i.quantity+p_quantity)*i.unit_price
    where i.id=(select id from public.order_items where order_id=v_id and product_id=p_product
     and coalesce(description,'')=trim(p_note) order by created_at,id limit 1 for update) and i.quantity+p_quantity>0;
    if not found then raise exception 'cannot reduce this line'; end if;
   end if;
  end if;
 end if;
 select count(*),coalesce(sum(total_price),0) into v_count,v_price from public.order_items where order_id=v_id;
 update public.orders set subtotal=v_price,discount_amount=least(discount_amount,v_price),total_amount=v_price-least(discount_amount,v_price),
  kitchen_status=case when p_order is not null and p_quantity>0 and kitchen_status<>'NEW' then 'SENT_TO_KITCHEN' else kitchen_status end,
  updated_at=now() where id=v_id;
 return v_id;
end $$;

create or replace function public.pos_bill_action(p_order uuid,p_action text,p_method text default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;v_next text;v_permission text;v_result jsonb;
begin
 select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
 if not found then raise exception 'bill not found'; end if;
 if p_action='send' and v_order.kitchen_status='NEW' and v_order.subtotal>0 then v_next:='SENT_TO_KITCHEN';
 elsif p_action='cook' and v_order.kitchen_status='SENT_TO_KITCHEN' then v_next:='COOKING';
 elsif p_action='ready' and v_order.kitchen_status in ('SENT_TO_KITCHEN','COOKING') then v_next:='READY';
 elsif p_action='serve' and v_order.kitchen_status='READY' then v_next:='SERVED';
 elsif p_action='pay' and v_order.kitchen_status in ('READY','SERVED') and v_order.payment_status='UNPAID' then
  v_permission:='close_bill';
  if p_method not in ('cash','bank_transfer','promptpay','card','other') then raise exception 'invalid payment method'; end if;
  perform set_config('queuego.pos_rpc','on',true);
  insert into public.payments(order_id,amount,payment_method,status,paid_at) values(p_order,v_order.total_amount,p_method,'paid',now());
  update public.orders set payment_status='PAID',payment_method=p_method,status='completed',updated_at=now() where id=p_order;
 elsif p_action='close' and v_order.payment_status='PAID' and v_order.status='completed' then
  v_permission:='close_bill';
  perform set_config('queuego.pos_rpc','on',true);
  update public.orders set updated_at=now() where id=p_order;
 elsif p_action='cancel' and v_order.payment_status='UNPAID' then
  v_permission:='cancel_bill';
  perform set_config('queuego.pos_rpc','on',true);
  update public.orders set status='cancelled',updated_at=now() where id=p_order;
 else raise exception 'invalid POS transition'; end if;
 if v_permission is not null and not public.pos_allowed(v_permission) then raise exception 'permission denied'; end if;
 if v_next is not null then
  perform set_config('queuego.pos_rpc','on',true);
  update public.orders set kitchen_status=v_next,updated_at=now() where id=p_order;
 end if;
 select to_jsonb(o) into v_result from public.orders o where o.id=p_order;
 return v_result;
end $$;

-- Server confirms channel-specific Delivery price and rejects POS-only products.
do $$
declare v_sql text;
begin
 select pg_get_functiondef(p.oid) into v_sql from pg_proc p where p.pronamespace='public'::regnamespace and p.proname='queuego_place_cash_order';
 if position('available=true for share' in v_sql)=0 then raise exception 'delivery placement function changed; inspect before POS migration'; end if;
 v_sql:=replace(v_sql,'available=true for share','available=true and delivery_available=true for share');
 v_sql:=replace(v_sql,'v_product.price<0','coalesce(v_product.delivery_price,v_product.price)<0');
 v_sql:=replace(v_sql,'v_product.price*v_qty','coalesce(v_product.delivery_price,v_product.price)*v_qty');
 v_sql:=replace(v_sql,'v_qty,v_product.price,round(','v_qty,coalesce(v_product.delivery_price,v_product.price),round(');
 v_sql:=replace(v_sql,'insert into public.orders(id,order_number,customer_id,shop_id,order_type,status,','insert into public.orders(id,order_number,customer_id,shop_id,order_type,sales_channel,status,');
 v_sql:=replace(v_sql,'values(p_order_id,v_number,v_customer,p_shop_id,''shopping'',''pending'',','values(p_order_id,v_number,v_customer,p_shop_id,''shopping'',''QUEUEGO_DELIVERY'',''pending'',');
 v_sql:=replace(v_sql,'insert into public.order_items(order_id,item_type,item_name,description,quantity,unit_price,total_price)','insert into public.order_items(order_id,product_id,item_type,item_name,description,quantity,unit_price,total_price)');
 v_sql:=replace(v_sql,'values(p_order_id,''product'',v_product.name','values(p_order_id,v_product_id,''product'',v_product.name');
 execute v_sql;
end $$;

-- POS activity stays out of Delivery GP settlements.
create or replace function public.pos_sales_report(p_days integer default 1)
returns jsonb language sql stable security definer set search_path=public,pg_temp as $$
 select jsonb_build_object('DINE_IN',coalesce(sum(total_amount) filter(where order_type='DINE_IN'),0),
 'TAKEAWAY',coalesce(sum(total_amount) filter(where order_type='TAKEAWAY'),0),
 'orders',count(*),'gp',0) from public.orders where shop_id=public.pos_my_shop()
 and sales_channel='POS' and payment_status='PAID' and status='completed'
 and created_at>=(((now() at time zone 'Asia/Bangkok')::date-(least(greatest(p_days,1),30)-1))::timestamp at time zone 'Asia/Bangkok')
$$;

create or replace function public.pos_apply_discount(p_order uuid,p_amount numeric)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;
begin
 if not public.pos_allowed('discount') then raise exception 'discount permission denied'; end if;
 select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
 if not found or v_order.payment_status<>'UNPAID' or v_order.status='cancelled' then raise exception 'bill not editable'; end if;
 if p_amount is null or p_amount<0 or p_amount>v_order.subtotal then raise exception 'invalid discount'; end if;
 perform set_config('queuego.pos_rpc','on',true);
 update public.orders set discount_amount=round(p_amount,2),total_amount=subtotal-round(p_amount,2),updated_at=now() where id=p_order;
 return (select to_jsonb(o) from public.orders o where o.id=p_order);
end $$;

create or replace function public.pos_refund_bill(p_order uuid,p_reason text)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;
begin
 if not public.pos_allowed('refund') then raise exception 'refund permission denied'; end if;
 if length(trim(coalesce(p_reason,'')))<5 or length(p_reason)>500 then raise exception 'refund reason required'; end if;
 select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
 if not found or v_order.payment_status<>'PAID' then raise exception 'paid bill required'; end if;
 perform set_config('queuego.pos_rpc','on',true);
 update public.payments set status='refunded',note=trim(p_reason),updated_at=now() where order_id=p_order and status='paid';
 update public.orders set payment_status='REFUNDED',status='cancelled',note=trim(p_reason),updated_at=now() where id=p_order;
 return (select to_jsonb(o) from public.orders o where o.id=p_order);
end $$;

create or replace function public.pos_change_price(p_product uuid,p_price numeric)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not public.pos_allowed('edit_price') then raise exception 'price permission denied'; end if;
 if p_price is null or p_price<0 or p_price>999999 then raise exception 'invalid price'; end if;
 update public.products set pos_price=round(p_price,2),updated_at=now() where id=p_product and shop_id=public.pos_my_shop();
 if not found then raise exception 'product not found'; end if;
end $$;

-- Existing Delivery screens and GP reports must never count POS rows.
do $$
declare v_sql text;
begin
 select pg_get_functiondef(p.oid) into v_sql from pg_proc p where p.pronamespace='public'::regnamespace and p.proname='get_my_shop_orders';
 if position('where o.shop_id = v_shop_id;' in v_sql)=0 then raise exception 'get_my_shop_orders changed; inspect before POS migration'; end if;
 execute replace(v_sql,'where o.shop_id = v_shop_id;','where o.shop_id = v_shop_id and o.order_type in (''shopping'',''DELIVERY'');');
 select pg_get_functiondef(p.oid) into v_sql from pg_proc p where p.pronamespace='public'::regnamespace and p.proname='report_shop_gp';
 if position('and o.status in (' in v_sql)=0 then raise exception 'report_shop_gp changed; inspect before POS migration'; end if;
 execute replace(v_sql,'and o.status in (','and o.order_type in (''shopping'',''DELIVERY'') and o.status in (');
 select pg_get_functiondef(p.oid) into v_sql from pg_proc p where p.pronamespace='public'::regnamespace and p.proname='qg_merchant_analytics';
 if position('and o.status in (' in v_sql)=0 then raise exception 'merchant analytics changed; inspect before POS migration'; end if;
 execute replace(v_sql,'and o.status in (','and o.order_type in (''shopping'',''DELIVERY'') and o.status in (');
end $$;

revoke all on function public.pos_my_shop(),public.pos_is_owner(),public.pos_allowed(text),public.pos_create_invite(text,jsonb),public.pos_join_shop(text,text),public.pos_set_staff(uuid,boolean,jsonb),public.pos_save_table(uuid,text,boolean),public.pos_edit_bill(uuid,text,uuid,uuid,integer,text),public.pos_bill_action(uuid,text,text),public.pos_sales_report(integer),public.pos_apply_discount(uuid,numeric),public.pos_refund_bill(uuid,text),public.pos_change_price(uuid,numeric) from public,anon;
grant execute on function public.pos_my_shop(),public.pos_is_owner(),public.pos_allowed(text),public.pos_create_invite(text,jsonb),public.pos_join_shop(text,text),public.pos_set_staff(uuid,boolean,jsonb),public.pos_save_table(uuid,text,boolean),public.pos_edit_bill(uuid,text,uuid,uuid,integer,text),public.pos_bill_action(uuid,text,text),public.pos_sales_report(integer),public.pos_apply_discount(uuid,numeric),public.pos_refund_bill(uuid,text),public.pos_change_price(uuid,numeric) to authenticated;
commit;
