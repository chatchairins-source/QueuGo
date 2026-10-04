-- One authoritative rider-first delivery state transition for Merchant and POS staff.
-- The internal function is not callable by client roles; entry RPCs retain role/shop permissions.
BEGIN;

CREATE OR REPLACE FUNCTION public.queuego_shop_delivery_transition(
 p_order_id uuid,p_action text,p_reason text,p_actor_user uuid
) RETURNS text LANGUAGE plpgsql SECURITY INVOKER SET search_path TO public,pg_temp AS $function$
declare v_order public.orders%rowtype;v_next text;v_note text;v_rider_user uuid;v_message text;
begin
 select * into v_order from public.orders where id=p_order_id for update;
 if not found then raise exception 'order unavailable'; end if;
 if p_action='cancel' then
   if v_order.status not in ('pending','accepted','searching_rider','rider_assigned','preparing','ready') then raise exception 'rider has already picked up or order finished'; end if;
   if trim(coalesce(p_reason,''))='' or length(p_reason)>500 then raise exception 'cancellation reason required (max 500)'; end if;
   if exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then raise exception 'cannot cancel after rider cash payment'; end if;
   v_next:='cancelled'; v_message:='ร้านค้ายกเลิกออเดอร์: '||trim(p_reason);
 elsif p_action='accepted' and v_order.status='pending' then v_next:='searching_rider'; v_message:='ร้านรับออเดอร์แล้ว กำลังค้นหาไรเดอร์';
 elsif p_action='preparing' and v_order.status='rider_assigned' and v_order.rider_id is not null then v_next:='preparing'; v_message:='ร้านกำลังเตรียมสินค้า';
 elsif p_action='ready' and v_order.status='preparing' and v_order.rider_id is not null then v_next:='ready'; v_message:='สินค้าเตรียมเสร็จแล้ว ไรเดอร์สามารถรับสินค้าได้';
 else raise exception 'order status changed; refresh'; end if;
 v_note:=regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g');
 v_note:=regexp_replace(v_note,'^__QT_CANCEL_REASON__=[^\n]*\n?','','g');
 v_note:='__QT_ORDER_STATUS__='||v_next||E'\n'||(case when v_next='cancelled' then '__QT_CANCEL_REASON__='||replace(trim(p_reason),E'\n',' ')||E'\n' else '' end)||v_note;
 perform set_config('queuego.v22_transition','rpc',true);
 update public.orders set status=v_next,note=v_note,updated_at=now(),
 merchant_accepted_at=case when v_next='searching_rider' then coalesce(merchant_accepted_at,now()) else merchant_accepted_at end,
 rider_search_started_at=case when v_next='searching_rider' then coalesce(rider_search_started_at,now()) else rider_search_started_at end,
 preparing_at=case when v_next='preparing' then coalesce(preparing_at,now()) else preparing_at end,
 ready_at=case when v_next='ready' then coalesce(ready_at,now()) else ready_at end,
 cancelled_at=case when v_next='cancelled' then coalesce(cancelled_at,now()) else cancelled_at end where id=p_order_id;
 if v_next='searching_rider' then
  insert into public.deliveries(order_id,rider_id,status,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,delivery_fee,note)
  values(p_order_id,null,'pending',v_order.pickup_address,v_order.pickup_latitude,v_order.pickup_longitude,v_order.delivery_address,v_order.delivery_latitude,v_order.delivery_longitude,v_order.delivery_fee,'Rider-first search')
  on conflict(order_id) do nothing;
 elsif v_next='cancelled' then
  update public.deliveries set status='cancelled',updated_at=now() where order_id=p_order_id and status in ('pending','assigned');
 end if;
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
 values(p_actor_user,'merchant_order_action','order',p_order_id,p_action,jsonb_build_object('from',v_order.status,'to',v_next,'reason',p_reason));
 if v_order.customer_id is not null then insert into public.notifications(user_id,title,message,type,reference_id) values(v_order.customer_id,'อัปเดตออเดอร์',v_message,'order',p_order_id); end if;
 if v_next='cancelled' and v_order.rider_id is not null then
  select user_id into v_rider_user from public.rider_profiles where id=v_order.rider_id;
  if v_rider_user is not null then insert into public.notifications(user_id,title,message,type,reference_id) values(v_rider_user,'ออเดอร์ถูกยกเลิกโดยร้านค้า',v_message,'order',p_order_id); end if;
 end if;
 return v_next;
end $function$;
REVOKE ALL ON FUNCTION public.queuego_shop_delivery_transition(uuid,text,text,uuid) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.merchant_order_action(p_order_id uuid,p_action text,p_reason text DEFAULT NULL)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path TO public,pg_temp AS $function$
declare v_user uuid;
begin
 select id into v_user from public.users where auth_user_id=auth.uid() and role='shop' and status='active';
 if v_user is null then raise exception 'shop login required'; end if;
 perform 1 from public.orders o join public.shop_profiles p on p.id=o.shop_id
 where o.id=p_order_id and p.user_id=v_user for update of o;
 if not found then raise exception 'order unavailable'; end if;
 return public.queuego_shop_delivery_transition(p_order_id,p_action,p_reason,v_user);
end $function$;

CREATE OR REPLACE FUNCTION public.pos_delivery_kitchen_action(p_order uuid,p_action text)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path TO public,pg_temp AS $function$
declare v_order public.orders%rowtype;v_next text;v_permission text;v_user uuid;
begin
 v_permission:=case p_action when 'accepted' then 'receive_order' when 'preparing' then 'cook_order' when 'ready' then 'ready_order' end;
 if v_permission is null or not public.pos_allowed(v_permission) then raise exception 'kitchen permission denied'; end if;
 select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop()
 and (sales_channel='QUEUEGO_DELIVERY' or (sales_channel is null and order_type='shopping')) for update;
 if not found then raise exception 'delivery order not found in this shop'; end if;
 select id into v_user from public.users where auth_user_id=auth.uid();
 v_next:=public.queuego_shop_delivery_transition(p_order,p_action,null,v_user);
 insert into public.pos_events(shop_id,order_id,actor_id,entity,action,before_state,after_state)
 values(v_order.shop_id,p_order,auth.uid(),'orders','DELIVERY_KITCHEN',jsonb_build_object('status',v_order.status),jsonb_build_object('status',v_next));
 return v_next;
end $function$;

COMMIT;
