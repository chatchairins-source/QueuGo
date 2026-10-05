CREATE OR REPLACE FUNCTION public.market_rider_confirm_pickup_cash(p_pickup_id uuid, p_amount numeric)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v public.market_order_pickups%rowtype;v_rider uuid;v_remaining integer;v_order_status text;
begin
 select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if v_rider is null then raise exception 'active rider required'; end if;
 select mp.* into v from public.market_order_pickups mp join public.orders o on o.id=mp.order_id where mp.id=p_pickup_id and o.rider_id=v_rider for update of mp;
 if not found then raise exception 'pickup unavailable'; end if;
 select o.status into v_order_status from public.orders o where o.id=v.order_id;
 if v.status='PICKED_UP' then return jsonb_build_object('pickup_id',v.id,'status',v.status,'shop_amount',v.shop_amount,'cash_paid_amount',v.cash_paid_amount,'replayed',true); end if;
 if v.status<>'READY' or v_order_status<>'ready' then raise exception 'merchant must mark pickup ready first'; end if;
 if round(coalesce(p_amount,0),2)<>round(v.shop_amount,2) then raise exception 'cash amount mismatch'; end if;
 insert into public.rider_cash_advances(order_id,rider_id,shop_id,amount) values(v.order_id,v_rider,v.shop_id,v.shop_amount) on conflict(order_id) do nothing;
 update public.market_order_pickups set status='PICKED_UP',cash_paid_amount=v.shop_amount,cash_paid_at=now(),picked_up_at=now(),updated_at=now() where id=v.id;
 select count(*) into v_remaining from public.market_order_pickups where market_order_id=v.market_order_id and status not in('PICKED_UP','CANCELLED');
 if v_remaining=0 then
  perform set_config('queuego.v22_transition','rpc',true);
  update public.orders o set status='picked_up',picked_up_at=coalesce(o.picked_up_at,now()),note='__QT_ORDER_STATUS__=picked_up'||E'
'||regexp_replace(coalesce(o.note,''),'^__QT_ORDER_STATUS__=[^
]*
?','','g'),updated_at=now() where o.market_order_id=v.market_order_id and o.status<>'cancelled';
  update public.deliveries d set status='picked_up',picked_up_at=coalesce(d.picked_up_at,now()),updated_at=now() from public.orders o where o.id=d.order_id and o.market_order_id=v.market_order_id and o.status<>'cancelled';
 end if;
 return jsonb_build_object('pickup_id',v.id,'status','PICKED_UP','shop_amount',v.shop_amount,'cash_paid_amount',v.shop_amount,'remaining_pickups',v_remaining,'all_picked_up',v_remaining=0,'replayed',false);
end $function$
;
CREATE OR REPLACE FUNCTION public.market_rider_group_action(p_market_order_id uuid, p_action text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_rider uuid;
begin
 select r.id into v_rider from public.rider_profiles r join public.users u on u.id=r.user_id where u.auth_user_id=auth.uid() and u.role='rider' and u.status='active';
 if v_rider is null then raise exception 'active rider required'; end if;
 if not exists(select 1 from public.orders where market_order_id=p_market_order_id and rider_id=v_rider and status<>'cancelled') then raise exception 'market order unavailable'; end if;
 perform set_config('queuego.v22_transition','rpc',true);
 if p_action='deliver' then
  if not public.qg_market_group_can_deliver(p_market_order_id,v_rider) then raise exception 'all pickups must be completed first'; end if;
  if exists(select 1 from public.orders where market_order_id=p_market_order_id and status not in('picked_up','cancelled')) then raise exception 'market group not ready to deliver'; end if;
  update public.orders set status='in_progress',delivering_at=coalesce(delivering_at,now()),note='__QT_ORDER_STATUS__=delivering'||E'
'||regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^
]*
?','','g'),updated_at=now() where market_order_id=p_market_order_id and status='picked_up';
  update public.deliveries d set status='delivering',updated_at=now() from public.orders o where o.id=d.order_id and o.market_order_id=p_market_order_id and o.status='in_progress';
  update public.market_orders set status='IN_PROGRESS',updated_at=now() where id=p_market_order_id;
  return 'in_progress';
 elsif p_action='arrive' then
  if exists(select 1 from public.orders where market_order_id=p_market_order_id and status not in('in_progress','cancelled')) then raise exception 'market group not in progress'; end if;
  update public.orders set note='__QT_ORDER_STATUS__=arrived'||E'
'||regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^
]*
?','','g'),updated_at=now() where market_order_id=p_market_order_id and status='in_progress';
  return 'arrived';
 else raise exception 'unsupported market action'; end if;
end $function$
;
CREATE OR REPLACE FUNCTION public.qg_guard_delivery_proof()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
 if new.order_type='shopping' and new.status='completed' and old.status is distinct from 'completed'
  and exists(select 1 from public.queuego_cash_order_locks l where l.order_id=new.id)
  and not exists(select 1 from public.qg_delivery_proofs p where p.order_id=new.id and p.rider_id=new.rider_id and p.otp_verified) then
  raise exception 'delivery PIN proof required before completing order';
 end if;
 return new;
end $function$
;
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
 if v_order.status='completed' then return 'completed'; end if;
 if p_action='pickup_cash' and v_order.status='ready' then
   v_next:='picked_up';
   insert into public.rider_cash_advances(order_id,rider_id,shop_id,amount) values(p_order_id,v_rider,v_order.shop_id,v_order.subtotal) on conflict (order_id) do nothing;
   update public.deliveries set status='picked_up',picked_up_at=coalesce(picked_up_at,now()),updated_at=now() where order_id=p_order_id;
 elsif p_action='deliver' and v_order.status='picked_up' then
   if not exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then raise exception 'cash advance not recorded'; end if; v_next:='in_progress';
 elsif p_action='arrive' and v_order.status='in_progress' and coalesce(v_order.note,'') not like '__QT_ORDER_STATUS__=arrived%' then
   v_next:='in_progress'; v_note:='__QT_ORDER_STATUS__=arrived'||chr(10)||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^'||chr(10)||']*'||chr(10)||'?','');
 elsif p_action='complete' and v_order.status='in_progress' then
   if not exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then raise exception 'cash advance not recorded'; end if;

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
end $function$
;