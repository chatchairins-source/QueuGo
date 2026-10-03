-- Delivery completion follows the owner's rider-first instruction.
-- Merchant acknowledgement remains an independent reconciliation action.
-- Rider ownership, recorded cash advances and verified customer PIN are still required.
BEGIN;
CREATE OR REPLACE FUNCTION public.rider_order_action(p_order_id uuid, p_action text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid; v_user uuid; v_order public.orders%rowtype; v_next text; v_note text;
begin
 select r.id,r.user_id into v_rider,v_user from public.rider_profiles r join public.users u on u.id=r.user_id where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if v_rider is null then raise exception 'active rider login required'; end if;
 select * into v_order from public.orders where id=p_order_id and rider_id=v_rider for update;
 if not found then raise exception 'order unavailable'; end if;
 if p_action='pickup_cash' and v_order.status='ready' then
   v_next:='picked_up';
   insert into public.rider_cash_advances(order_id,rider_id,shop_id,amount) values(p_order_id,v_rider,v_order.shop_id,v_order.subtotal) on conflict (order_id) do nothing;
   update public.deliveries set status='picked_up',picked_up_at=coalesce(picked_up_at,now()),updated_at=now() where order_id=p_order_id;
 elsif p_action='deliver' and v_order.status='picked_up' then
   if not exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then raise exception 'cash advance not recorded'; end if; v_next:='in_progress';
 elsif p_action='arrive' and v_order.status='in_progress' and coalesce(v_order.note,'') not like '__QT_ORDER_STATUS__=arrived%' then
   v_next:='in_progress'; v_note:='__QT_ORDER_STATUS__=arrived'||chr(10)||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^'||chr(10)||']*'||chr(10)||'?','');
 elsif p_action='complete' and v_order.status='in_progress' and coalesce(v_order.note,'') like '__QT_ORDER_STATUS__=arrived%' then
   if not exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then raise exception 'cash advance not recorded'; end if;

   if not exists(select 1 from public.qg_delivery_proofs p where p.order_id=p_order_id and p.rider_id=v_rider and p.otp_verified=true) then raise exception 'verified delivery proof required'; end if;
   v_next:='completed';
   update public.deliveries set status='delivered',delivered_at=coalesce(delivered_at,now()),updated_at=now() where order_id=p_order_id;
 else raise exception 'order status changed; refresh'; end if;
 perform set_config('queuego.v22_transition','rpc',true);
 update public.orders set status=v_next,note=coalesce(v_note,case when v_next='picked_up' then '__QT_ORDER_STATUS__=picked_up'||E'\n'||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g') when v_next='in_progress' then '__QT_ORDER_STATUS__=delivering'||E'\n'||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g') when v_next='completed' then '__QT_ORDER_STATUS__=completed'||E'\n'||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g') else v_order.note end),updated_at=now(),
 picked_up_at=case when v_next='picked_up' then coalesce(picked_up_at,now()) else picked_up_at end,
 delivering_at=case when v_next='in_progress' and p_action='deliver' then coalesce(delivering_at,now()) else delivering_at end,
 completed_at=case when v_next='completed' then coalesce(completed_at,now()) else completed_at end
 where id=p_order_id;
 if p_action='complete' then update public.payments set status='paid',paid_at=coalesce(paid_at,now()),updated_at=now() where order_id=p_order_id and payment_method='cash' and status='pending'; end if;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(v_user,'rider_order_action','order',p_order_id,p_action,jsonb_build_object('from',v_order.status,'to',v_next,'cash_paid_to_shop',p_action='pickup_cash'));
 if v_order.customer_id is not null then insert into public.notifications(user_id,title,message,type,reference_id) values(v_order.customer_id,'อัปเดตออเดอร์',case p_action when 'pickup_cash' then 'ไรเดอร์ชำระเงินสดให้ร้านและรับสินค้าแล้ว' when 'deliver' then 'ไรเดอร์กำลังนำสินค้าไปส่ง' when 'arrive' then 'ไรเดอร์ถึงจุดส่งแล้ว' else 'จัดส่งสำเร็จ' end,'order',p_order_id); end if;
 return case when p_action='arrive' then 'arrived' else v_next end;
end $function$;

CREATE OR REPLACE FUNCTION public.qg_complete_market_with_proof(p_market_order_id uuid, p_pin text, p_lat double precision DEFAULT NULL::double precision, p_lng double precision DEFAULT NULL::double precision, p_photo_path text DEFAULT NULL::text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid;v_lead uuid;v_pin text;v_order record;
begin
 select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if v_rider is null then raise exception 'active rider required'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_market_order_id::text,991));
 select id into v_lead from public.orders where market_order_id=p_market_order_id and rider_id=v_rider and status<>'cancelled' order by created_at,id limit 1;
 if v_lead is null then raise exception 'market order unavailable'; end if;
 if not exists(select 1 from public.orders where market_order_id=p_market_order_id and status<>'cancelled' and (rider_id is distinct from v_rider or status<>'completed' or not exists(select 1 from public.qg_delivery_proofs p where p.order_id=orders.id and p.rider_id=v_rider and p.otp_verified=true))) then return 'completed'; end if;
 if exists(select 1 from public.orders where market_order_id=p_market_order_id and status<>'cancelled' and (rider_id is distinct from v_rider or status<>'in_progress' or coalesce(note,'') not like '__QT_ORDER_STATUS__=arrived%')) then raise exception 'market order is not ready to complete'; end if;
 select pin into v_pin from public.qg_delivery_pins where order_id=v_lead;
 if v_pin is null or p_pin is distinct from v_pin then raise exception 'incorrect delivery PIN'; end if;
 if (p_lat is null)<>(p_lng is null) or (p_lat is not null and(abs(p_lat)>90 or abs(p_lng)>180)) then raise exception 'invalid GPS'; end if;
 if p_photo_path is not null and(p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$') or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=p_photo_path)) then raise exception 'invalid delivery evidence'; end if;
 for v_order in select * from public.orders where market_order_id=p_market_order_id and status<>'cancelled' for update loop
  if not exists(select 1 from public.rider_cash_advances where order_id=v_order.id and rider_id=v_rider and amount=v_order.subtotal) then raise exception 'cash advance missing for shop'; end if;

  if not exists(select 1 from public.qg_delivery_proofs where order_id=v_order.id and otp_verified=true) then
   insert into public.qg_delivery_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path,otp_verified) values(v_order.id,v_rider,p_lat,p_lng,p_photo_path,true);
  end if;
 end loop;
 perform set_config('queuego.v22_transition','rpc',true);
 update public.orders set status='completed',completed_at=coalesce(completed_at,now()),note='__QT_ORDER_STATUS__=completed'||E'
'||regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^
]*
?','','g'),updated_at=now() where market_order_id=p_market_order_id and status<>'cancelled';
 update public.deliveries d set status='delivered',delivered_at=coalesce(d.delivered_at,now()),updated_at=now() from public.orders o where o.id=d.order_id and o.market_order_id=p_market_order_id and o.status='completed';
 update public.payments p set status='paid',paid_at=coalesce(p.paid_at,now()),updated_at=now() from public.orders o where o.id=p.order_id and o.market_order_id=p_market_order_id and p.payment_method='cash' and p.status='pending';
 update public.market_orders set status='COMPLETED',updated_at=now() where id=p_market_order_id;
 return 'completed';
end $function$;

CREATE OR REPLACE FUNCTION public.qg_complete_with_proof(p_order_id uuid, p_pin text, p_lat double precision DEFAULT NULL::double precision, p_lng double precision DEFAULT NULL::double precision, p_photo_path text DEFAULT NULL::text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid;v_order public.orders%rowtype;v_pin text;
begin
 select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id
 where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if v_rider is null then raise exception 'active rider required'; end if;
 select * into v_order from public.orders where id=p_order_id and rider_id=v_rider for update;
 if found and v_order.status='completed' and exists(select 1 from public.qg_delivery_proofs where order_id=p_order_id and rider_id=v_rider and otp_verified=true) then return 'completed'; end if;
 if not found or v_order.status<>'in_progress' or coalesce(v_order.note,'') not like '__QT_ORDER_STATUS__=arrived%' then
  raise exception 'order is not ready to complete'; end if;

 select pin into v_pin from public.qg_delivery_pins where order_id=p_order_id;
 if v_pin is null or p_pin is distinct from v_pin then raise exception 'incorrect delivery PIN'; end if;
 if (p_lat is null)<>(p_lng is null) or (p_lat is not null and (abs(p_lat)>90 or abs(p_lng)>180)) then raise exception 'invalid GPS'; end if;
 if p_photo_path is not null and (p_photo_path !~ ('^'||auth.uid()::text||'/[a-zA-Z0-9_-]+[.](jpg|jpeg|png|webp)$')
   or not exists(select 1 from storage.objects where bucket_id='qg-evidence' and name=p_photo_path)) then raise exception 'invalid delivery evidence'; end if;
 insert into public.qg_delivery_proofs(order_id,rider_id,gps_lat,gps_lng,photo_path,otp_verified)
 values(p_order_id,v_rider,p_lat,p_lng,p_photo_path,true);
 perform public.rider_order_action(p_order_id,'complete');
 insert into public.audit_logs(user_id,action,entity_type,entity_id,metadata)
 values((select user_id from public.rider_profiles where id=v_rider),'delivery_proof','order',p_order_id,
 jsonb_build_object('pin_verified',true,'has_gps',p_lat is not null,'photo_path',p_photo_path));
 return 'completed';
end $function$;

COMMIT;

