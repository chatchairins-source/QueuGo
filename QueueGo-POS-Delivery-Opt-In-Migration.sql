-- Existing active merchants keep Delivery enabled; new merchants opt in after POS setup.
begin;
alter table public.shop_profiles add column if not exists delivery_enabled boolean not null default false;
update public.shop_profiles set delivery_enabled=true where status='active' and delivery_enabled=false;

create or replace function public.pos_guard_delivery_enabled() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if new.delivery_enabled is distinct from old.delivery_enabled
 and current_setting('queuego.delivery_optin_rpc',true) is distinct from 'on'
 and not public.is_active_admin() then raise exception 'use delivery readiness RPC'; end if;
 return new;
end $$;
create trigger pos_delivery_enabled_guard before update of delivery_enabled on public.shop_profiles
for each row execute function public.pos_guard_delivery_enabled();
revoke all on function public.pos_guard_delivery_enabled() from public,anon;

create or replace function public.pos_enable_delivery() returns jsonb
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop public.shop_profiles%rowtype;v_hours boolean;v_menu boolean;
begin
 if not public.pos_is_owner() then raise exception 'shop owner required'; end if;
 select * into v_shop from public.shop_profiles where id=public.pos_my_shop() for update;
 if not found or v_shop.status<>'active' or length(trim(coalesce(v_shop.shop_name,'')))=0 then raise exception 'shop profile not approved'; end if;
 if length(trim(coalesce(v_shop.address,'')))=0 or v_shop.latitude is null or v_shop.longitude is null then raise exception 'shop address and coordinates required'; end if;
 select exists(select 1 from public.products p where p.shop_id=v_shop.id and p.available and p.delivery_available
  and coalesce(p.delivery_price,p.price)>0) into v_menu;
 if not v_menu then raise exception 'available Delivery product and price required'; end if;
 select exists(select 1 from public.shop_business_hours h where h.shop_id=v_shop.id and not h.is_closed and h.opens_at is not null and h.closes_at is not null)
  or (v_shop.public_open_time is not null and v_shop.public_close_time is not null) into v_hours;
 if not v_hours then raise exception 'opening hours required'; end if;
 perform set_config('queuego.delivery_optin_rpc','on',true);
 update public.shop_profiles set delivery_enabled=true where id=v_shop.id;
 insert into public.shop_open_states(shop_id,is_open,resume_at,updated_at) values(v_shop.id,true,null,now())
 on conflict(shop_id) do update set is_open=true,resume_at=null,updated_at=now();
 return jsonb_build_object('shop_id',v_shop.id,'delivery_enabled',true,'is_open',true);
end $$;

create or replace function public.queuego_reject_closed_shop_order() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_open boolean;v_resume timestamptz;v_now timestamp;v_special public.shop_special_hours%rowtype;v_hours public.shop_business_hours%rowtype;
begin
 if new.order_type in ('DINE_IN','TAKEAWAY') then return new; end if;
 if not exists(select 1 from public.shop_profiles where id=new.shop_id and delivery_enabled) then raise exception 'shop has not enabled Delivery'; end if;
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
revoke all on function public.pos_enable_delivery() from public,anon;
grant execute on function public.pos_enable_delivery() to authenticated;
commit;
