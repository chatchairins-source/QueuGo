-- Arrival is an audited event; order state remains unchanged.
BEGIN;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS rider_arrived_shop_at timestamptz;
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
 if p_action='arrive_shop' then
   if v_order.status not in ('rider_assigned','assigned','preparing','ready') then raise exception 'order is not at pickup stage'; end if;
   if v_order.rider_arrived_shop_at is not null then return v_order.status; end if;
   perform set_config('queuego.v22_transition','rpc',true);
   update public.orders set rider_arrived_shop_at=now(),updated_at=now() where id=p_order_id;
   insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
   values(v_user,'rider_arrived_shop','order',p_order_id,'rider arrived at pickup',
    jsonb_build_object('rider_id',v_rider,'shop_id',v_order.shop_id,'customer_id',v_order.customer_id,'order_status',v_order.status));
   insert into public.notifications(user_id,title,message,type,reference_id)
   select sp.user_id,'ไรเดอร์ถึงร้านแล้ว','ไรเดอร์มาถึงร้านเพื่อรับออเดอร์ '||coalesce(v_order.order_number,p_order_id::text),'order',p_order_id
   from public.shop_profiles sp where sp.id=v_order.shop_id;
   return v_order.status;
 end if;
 if v_order.status='completed' then return 'completed'; end if;
 if p_action='pickup_cash' and v_order.status='ready' then
   v_next:='picked_up';
   insert into public.rider_cash_advances(order_id,rider_id,shop_id,amount) values(p_order_id,v_rider,v_order.shop_id,v_order.subtotal) on conflict (order_id) do nothing;
   insert into public.merchant_cash_receipts(order_id,shop_id,amount,confirmed_by) values(p_order_id,v_order.shop_id,v_order.subtotal,v_user) on conflict(order_id) do nothing;
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
 insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata) values(v_user,'rider_order_action','order',p_order_id,p_action,jsonb_build_object('from',v_order.status,'to',v_next,'cash_paid_to_shop',p_action='pickup_cash','cash_collected',p_action='complete','source','order_action','customer_id',v_order.customer_id,'shop_id',v_order.shop_id,'rider_id',v_rider));
 if v_order.customer_id is not null then insert into public.notifications(user_id,title,message,type,reference_id) values(v_order.customer_id,'อัปเดตออเดอร์',case p_action when 'pickup_cash' then 'ไรเดอร์ชำระเงินสดให้ร้านและรับสินค้าแล้ว' when 'deliver' then 'ไรเดอร์กำลังนำสินค้าไปส่ง' when 'arrive' then 'ไรเดอร์ถึงจุดส่งแล้ว' else 'จัดส่งสำเร็จ' end,'order',p_order_id); end if;
 return case when p_action='arrive' then 'arrived' else v_next end;
end $function$
;
CREATE OR REPLACE FUNCTION public.qg_v22_order_transition_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
 if new.rider_arrived_shop_at is distinct from old.rider_arrived_shop_at
 and public.get_my_role() is distinct from 'admin' then
  if old.rider_arrived_shop_at is not null
   or current_setting('queuego.v22_transition',true) is distinct from 'rpc'
   or new.rider_id is distinct from old.rider_id
   or not exists(select 1 from public.rider_profiles r join public.users u on u.id=r.user_id
      where r.id=old.rider_id and u.auth_user_id=auth.uid() and u.role='rider' and u.status='active')
  then raise exception 'shop arrival requires assigned rider RPC'; end if;
 end if;
 if exists(select 1 from public.queuego_cash_order_locks where order_id=old.id)
 and public.get_my_role()<>'admin'
 and (new.status is distinct from old.status or new.rider_id is distinct from old.rider_id)
 and current_setting('queuego.v22_transition',true) is distinct from 'rpc'
 then raise exception 'order transition requires server RPC'; end if;
 return new;
end $function$
;
CREATE OR REPLACE FUNCTION public.queuego_shop_delivery_transition(p_order_id uuid, p_action text, p_reason text, p_actor_user uuid)
 RETURNS text
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_order public.orders%rowtype;
  v_next text;
  v_note text;
  v_rider_user uuid;
  v_message text;
  v_parent_status text;
begin
  select * into v_order from public.orders where id=p_order_id for update;
  if not found then raise exception 'order unavailable'; end if;

  if p_action='cancel' then
    if v_order.status not in ('pending','accepted','searching_rider','rider_assigned','preparing','ready') then
      raise exception 'rider has already picked up or order finished';
    end if;
    if trim(coalesce(p_reason,''))='' or length(p_reason)>500 then
      raise exception 'cancellation reason required (max 500)';
    end if;
    if exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then
      raise exception 'cannot cancel after rider cash payment';
    end if;
    v_next:='cancelled';
    v_message:='ร้านค้ายกเลิกออเดอร์: '||trim(p_reason);

  elsif p_action='accepted' and v_order.status='pending' then
    if v_order.market_order_id is not null and v_order.rider_id is not null then
      select status into v_parent_status from public.market_orders where id=v_order.market_order_id;
      if upper(coalesce(v_parent_status,'')) in ('IN_PROGRESS','COMPLETED','CANCELLED') then
        raise exception 'market trip already left market';
      end if;
      v_next:='rider_assigned';
      v_message:='ร้านรับออเดอร์แล้ว ใช้ไรเดอร์คนเดิมของ Market Trip';
    else
      v_next:='searching_rider';
      v_message:='ร้านรับออเดอร์แล้ว กำลังค้นหาไรเดอร์';
    end if;

  elsif p_action='preparing' and v_order.status='rider_assigned' and v_order.rider_id is not null then
    v_next:='preparing';
    v_message:='ร้านกำลังเตรียมสินค้า';

  elsif p_action='ready' and v_order.status='preparing' and v_order.rider_id is not null then
    v_next:='ready';
    v_message:='สินค้าเตรียมเสร็จแล้ว ไรเดอร์สามารถรับสินค้าได้';

  else
    raise exception 'order status changed; refresh';
  end if;

  v_note:=regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\\n]*\\n?','','g');
  v_note:=regexp_replace(v_note,'^__QT_CANCEL_REASON__=[^\\n]*\\n?','','g');
  v_note:='__QT_ORDER_STATUS__='||v_next||E'\n'||
    (case when v_next='cancelled'
      then '__QT_CANCEL_REASON__='||replace(trim(p_reason),E'\n',' ')||E'\n'
      else '' end)||v_note;

  perform set_config('queuego.v22_transition','rpc',true);

  update public.orders
  set status=v_next,
      note=v_note,
      updated_at=now(),
      merchant_accepted_at=case when v_next in ('searching_rider','rider_assigned') then coalesce(merchant_accepted_at,now()) else merchant_accepted_at end,
      rider_search_started_at=case when v_next='searching_rider' then coalesce(rider_search_started_at,now()) else rider_search_started_at end,
      preparing_at=case when v_next='preparing' then coalesce(preparing_at,now()) else preparing_at end,
      ready_at=case when v_next='ready' then coalesce(ready_at,now()) else ready_at end,
      cancelled_at=case when v_next='cancelled' then coalesce(cancelled_at,now()) else cancelled_at end
  where id=p_order_id;

  if v_next='searching_rider' then
    insert into public.deliveries(
      order_id,rider_id,status,
      pickup_address,pickup_latitude,pickup_longitude,
      delivery_address,delivery_latitude,delivery_longitude,
      delivery_fee,note
    ) values(
      p_order_id,null,'pending',
      v_order.pickup_address,v_order.pickup_latitude,v_order.pickup_longitude,
      v_order.delivery_address,v_order.delivery_latitude,v_order.delivery_longitude,
      v_order.delivery_fee,'Rider-first search'
    )
    on conflict(order_id) do nothing;

  elsif v_next='rider_assigned' then
    insert into public.deliveries(
      order_id,rider_id,status,
      pickup_address,pickup_latitude,pickup_longitude,
      delivery_address,delivery_latitude,delivery_longitude,
      delivery_fee,note
    ) values(
      p_order_id,v_order.rider_id,'assigned',
      v_order.pickup_address,v_order.pickup_latitude,v_order.pickup_longitude,
      v_order.delivery_address,v_order.delivery_latitude,v_order.delivery_longitude,
      v_order.delivery_fee,'Existing Market Trip rider'
    )
    on conflict(order_id) do update
      set rider_id=excluded.rider_id,status='assigned',updated_at=now(),note='Existing Market Trip rider';

  elsif v_next='cancelled' then
    update public.deliveries
    set status='cancelled',updated_at=now()
    where order_id=p_order_id and status in ('pending','assigned');
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(
    p_actor_user,'merchant_order_action','order',p_order_id,p_action,
    jsonb_build_object('from',v_order.status,'to',v_next,'reason',p_reason)
  );

  if v_order.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_order.customer_id,'อัปเดตออเดอร์',v_message,'order',p_order_id);
  end if;

  if v_order.rider_id is not null then
    select user_id into v_rider_user from public.rider_profiles where id=v_order.rider_id;
    if v_rider_user is not null then
      insert into public.notifications(user_id,title,message,type,reference_id)
      values(v_rider_user,case when v_next='cancelled' then 'ออเดอร์ถูกยกเลิกโดยร้านค้า' when v_next='ready' then 'ร้านเตรียมสินค้าเสร็จแล้ว' else 'อัปเดตจากร้านค้า' end,v_message,'order',p_order_id);
    end if;
  end if;

  return v_next;
end
$function$
;
COMMIT;

