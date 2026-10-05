-- Keep stale/cached Rider clients repeat-safe while the current client uses qg_rider_action_once.
-- No new order states are introduced.

create or replace function public.rider_claim_order(p_order_id uuid)
returns text
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_rider uuid;
  v_user uuid;
  v_order public.orders%rowtype;
begin
  select r.id,r.user_id into v_rider,v_user
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active'
    and coalesce((r.metadata->>'online')::boolean,false)=true
    and coalesce((r.metadata->>'available')::boolean,false)=true
  for update of r;

  if v_rider is null then raise exception 'active online rider required'; end if;

  select * into v_order
  from public.orders
  where id=p_order_id
  for update;

  if not found or v_order.order_type<>'shopping' then
    raise exception 'order no longer available';
  end if;

  -- Same Rider retrying an already-successful claim is a replay, not an error.
  if v_order.rider_id=v_rider
     and v_order.status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress','completed') then
    return v_order.status;
  end if;

  if exists(
    select 1 from public.orders
    where rider_id=v_rider
      and id<>p_order_id
      and status in ('rider_assigned','preparing','ready','assigned','picked_up','in_progress')
  ) then
    raise exception 'finish current order before claiming another';
  end if;

  if v_order.status<>'searching_rider' or v_order.rider_id is not null then
    raise exception 'order no longer available';
  end if;

  if v_order.fulfillment_vertical<>'food'
     and not exists(
       select 1
       from public.rider_profiles r
       where r.id=v_rider
         and r.status='active'
         and r.vehicle_type in ('motorcycle','car','saleng')
         and r.vehicle_status='active'
         and r.vehicle_verified_at is not null
         and r.vehicle_capacity_kg>=public.market_order_weight(p_order_id)
         and public.market_order_weight(p_order_id)>0
     ) then
    raise exception 'vehicle capacity or availability insufficient for market order';
  end if;

  perform set_config('queuego.v22_transition','rpc',true);

  update public.orders
  set rider_id=v_rider,
      status='rider_assigned',
      rider_assigned_at=coalesce(rider_assigned_at,now()),
      updated_at=now(),
      note='__QT_ORDER_STATUS__=rider_assigned'||E'\n'||
           regexp_replace(coalesce(note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g')
  where id=p_order_id;

  update public.deliveries
  set rider_id=v_rider,status='assigned',updated_at=now()
  where order_id=p_order_id and rider_id is null and status='pending';

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user,'rider_claim','order',p_order_id,'rider_assigned',
         jsonb_build_object('from','searching_rider','to','rider_assigned'));

  if v_order.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_order.customer_id,'พบไรเดอร์แล้ว','มีไรเดอร์รับออเดอร์แล้ว ร้านค้าจะเริ่มเตรียมสินค้า','order',p_order_id);
  end if;

  insert into public.notifications(user_id,title,message,type,reference_id)
  select sp.user_id,'พบไรเดอร์แล้ว','ไรเดอร์รับงานแล้ว สามารถเริ่มเตรียมสินค้าได้','order',p_order_id
  from public.shop_profiles sp
  where sp.id=v_order.shop_id;

  return 'rider_assigned';
end
$function$;

create or replace function public.rider_order_action(p_order_id uuid,p_action text)
returns text
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_rider uuid;
  v_user uuid;
  v_order public.orders%rowtype;
  v_next text;
  v_note text;
  v_action text:=lower(trim(coalesce(p_action,'')));
begin
  select r.id,r.user_id into v_rider,v_user
  from public.rider_profiles r
  join public.users u on u.id=r.user_id
  where u.auth_user_id=auth.uid()
    and u.role='rider'
    and u.status='active';

  if v_rider is null then raise exception 'active rider login required'; end if;

  select * into v_order
  from public.orders
  where id=p_order_id and rider_id=v_rider
  for update;

  if not found then raise exception 'order unavailable'; end if;

  if v_action='arrive_shop' then
    if v_order.rider_arrived_shop_at is not null then
      return v_order.status;
    end if;
    if v_order.status not in ('rider_assigned','assigned','preparing','ready') then
      raise exception 'order is not at pickup stage';
    end if;

    perform set_config('queuego.v22_transition','rpc',true);
    update public.orders
    set rider_arrived_shop_at=now(),updated_at=now()
    where id=p_order_id;

    insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
    values(v_user,'rider_arrived_shop','order',p_order_id,'rider arrived at pickup',
      jsonb_build_object('rider_id',v_rider,'shop_id',v_order.shop_id,'customer_id',v_order.customer_id,'order_status',v_order.status));

    insert into public.notifications(user_id,title,message,type,reference_id)
    select sp.user_id,'ไรเดอร์ถึงร้านแล้ว','ไรเดอร์มาถึงร้านเพื่อรับออเดอร์ '||coalesce(v_order.order_number,p_order_id::text),'order',p_order_id
    from public.shop_profiles sp
    where sp.id=v_order.shop_id;

    return v_order.status;
  end if;

  if v_order.status='completed' then return 'completed'; end if;

  -- Legacy/cached Rider replays should return the authoritative state.
  if v_action='pickup_cash' and v_order.status in ('picked_up','in_progress') then
    return v_order.status;
  elsif v_action='deliver' and v_order.status='in_progress' then
    return 'in_progress';
  elsif v_action='arrive' and v_order.status='in_progress'
        and coalesce(v_order.note,'') like '__QT_ORDER_STATUS__=arrived%' then
    return 'arrived';
  end if;

  if v_action='pickup_cash' and v_order.status='ready' then
    v_next:='picked_up';
    insert into public.rider_cash_advances(order_id,rider_id,shop_id,amount)
    values(p_order_id,v_rider,v_order.shop_id,v_order.subtotal)
    on conflict (order_id) do nothing;

    insert into public.merchant_cash_receipts(order_id,shop_id,amount,confirmed_by)
    values(p_order_id,v_order.shop_id,v_order.subtotal,v_user)
    on conflict(order_id) do nothing;

    update public.deliveries
    set status='picked_up',picked_up_at=coalesce(picked_up_at,now()),updated_at=now()
    where order_id=p_order_id;

  elsif v_action='deliver' and v_order.status='picked_up' then
    if not exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then
      raise exception 'cash advance not recorded';
    end if;
    v_next:='in_progress';

  elsif v_action='arrive' and v_order.status='in_progress'
        and coalesce(v_order.note,'') not like '__QT_ORDER_STATUS__=arrived%' then
    v_next:='in_progress';
    v_note:='__QT_ORDER_STATUS__=arrived'||chr(10)||
            regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^'||chr(10)||']*'||chr(10)||'?','');

  elsif v_action='complete' and v_order.status='in_progress' then
    if not exists(select 1 from public.rider_cash_advances where order_id=p_order_id) then
      raise exception 'cash advance not recorded';
    end if;
    v_next:='completed';

    update public.deliveries
    set status='delivered',delivered_at=coalesce(delivered_at,now()),updated_at=now()
    where order_id=p_order_id;

  else
    raise exception 'order status changed; refresh';
  end if;

  perform set_config('queuego.v22_transition','rpc',true);

  update public.orders
  set status=v_next,
      note=coalesce(
        v_note,
        case
          when v_next='picked_up' then '__QT_ORDER_STATUS__=picked_up'||E'\n'||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g')
          when v_next='in_progress' then '__QT_ORDER_STATUS__=delivering'||E'\n'||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g')
          when v_next='completed' then '__QT_ORDER_STATUS__=completed'||E'\n'||regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g')
          else v_order.note
        end
      ),
      updated_at=now(),
      picked_up_at=case when v_next='picked_up' then coalesce(picked_up_at,now()) else picked_up_at end,
      delivering_at=case when v_next='in_progress' and v_action='deliver' then coalesce(delivering_at,now()) else delivering_at end,
      completed_at=case when v_next='completed' then coalesce(completed_at,now()) else completed_at end
  where id=p_order_id;

  if v_action='complete' then
    update public.payments
    set status='paid',paid_at=coalesce(paid_at,now()),updated_at=now()
    where order_id=p_order_id and payment_method='cash' and status='pending';
  end if;

  insert into public.audit_logs(user_id,action,entity_type,entity_id,description,metadata)
  values(v_user,'rider_order_action','order',p_order_id,v_action,
    jsonb_build_object('from',v_order.status,'to',v_next,'cash_paid_to_shop',v_action='pickup_cash',
      'cash_collected',v_action='complete','source','order_action','customer_id',v_order.customer_id,
      'shop_id',v_order.shop_id,'rider_id',v_rider));

  if v_order.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
    values(v_order.customer_id,'อัปเดตออเดอร์',
      case v_action
        when 'pickup_cash' then 'ไรเดอร์ชำระเงินสดให้ร้านและรับสินค้าแล้ว'
        when 'deliver' then 'ไรเดอร์กำลังนำสินค้าไปส่ง'
        when 'arrive' then 'ไรเดอร์ถึงจุดส่งแล้ว'
        else 'จัดส่งสำเร็จ'
      end,
      'order',p_order_id);
  end if;

  return case when v_action='arrive' then 'arrived' else v_next end;
end
$function$;

revoke all on function public.rider_claim_order(uuid) from anon;
revoke all on function public.rider_order_action(uuid,text) from anon;
grant execute on function public.rider_claim_order(uuid) to authenticated;
grant execute on function public.rider_order_action(uuid,text) to authenticated;
