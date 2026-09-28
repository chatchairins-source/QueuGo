-- QueueGo POS MVP integration. Additive changes only; keep Delivery's existing order lifecycle.
begin;

create table if not exists public.pos_request_keys (
  request_id uuid primary key,
  shop_id uuid not null references public.shop_profiles(id),
  actor_id uuid not null,
  order_id uuid not null references public.orders(id),
  created_at timestamptz not null default now()
);
create index if not exists pos_request_keys_shop_created_idx on public.pos_request_keys(shop_id,created_at);
alter table public.pos_request_keys enable row level security;
revoke all on public.pos_request_keys from public,anon,authenticated;

create or replace function public.pos_create_bill_once(
  p_request uuid,p_type text,p_table uuid,p_product uuid,p_note text default ''
) returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare v_shop uuid; v_existing public.pos_request_keys%rowtype; v_order uuid;
begin
  v_shop:=public.pos_my_shop();
  if auth.uid() is null or v_shop is null or not public.pos_allowed('receive_order') or p_request is null then
    raise exception 'POS access denied';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_request::text,0));
  select * into v_existing from public.pos_request_keys where request_id=p_request;
  if found then
    if v_existing.shop_id<>v_shop or v_existing.actor_id<>auth.uid() then raise exception 'request key belongs to another user'; end if;
    return v_existing.order_id;
  end if;
  v_order:=public.pos_edit_bill(null,p_type,p_table,p_product,1,p_note);
  insert into public.pos_request_keys(request_id,shop_id,actor_id,order_id)
    values(p_request,v_shop,auth.uid(),v_order);
  return v_order;
end $$;
revoke all on function public.pos_create_bill_once(uuid,text,uuid,uuid,text) from public,anon;
grant execute on function public.pos_create_bill_once(uuid,text,uuid,uuid,text) to authenticated;

create policy pos_delivery_orders_read on public.orders for select to authenticated
  using (shop_id = public.pos_my_shop() and
    (sales_channel = 'QUEUEGO_DELIVERY' or (sales_channel is null and order_type = 'shopping')));

create policy pos_delivery_items_read on public.order_items for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id
    and o.shop_id = public.pos_my_shop()
    and (o.sales_channel = 'QUEUEGO_DELIVERY' or (o.sales_channel is null and o.order_type = 'shopping'))));

create or replace function public.pos_cancel_bill(p_order uuid, p_reason text)
returns jsonb language plpgsql security definer set search_path = public, pg_temp as $$
declare v_order public.orders%rowtype;
begin
  if not public.pos_allowed('cancel_bill') then raise exception 'cancel permission denied'; end if;
  if length(trim(coalesce(p_reason,''))) < 3 or length(p_reason) > 500 then
    raise exception 'cancellation reason required (3-500 characters)';
  end if;
  select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop()
    and sales_channel='POS' for update;
  if not found or v_order.status='cancelled' or v_order.payment_status<>'UNPAID' then
    raise exception 'open unpaid bill not found';
  end if;
  perform set_config('queuego.pos_rpc','on',true);
  update public.orders set status='cancelled',updated_at=now() where id=p_order;
  insert into public.pos_events(shop_id,order_id,actor_id,entity,action,before_state,after_state)
    values(v_order.shop_id,p_order,auth.uid(),'orders','CANCEL_REASON',
      jsonb_build_object('status',v_order.status),jsonb_build_object('reason',trim(p_reason)));
  return (select to_jsonb(o) from public.orders o where o.id=p_order);
end $$;
revoke all on function public.pos_cancel_bill(uuid,text) from public,anon;
grant execute on function public.pos_cancel_bill(uuid,text) to authenticated;

create or replace function public.pos_delivery_kitchen_action(p_order uuid,p_action text)
returns text language plpgsql security definer set search_path = public, pg_temp as $$
declare v_order public.orders%rowtype; v_next text; v_permission text; v_note text;
begin
  select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop()
    and (sales_channel='QUEUEGO_DELIVERY' or (sales_channel is null and order_type='shopping')) for update;
  if not found then raise exception 'delivery order not found in this shop'; end if;
  if v_order.status='pending' and p_action='accepted' then v_next:='accepted'; v_permission:='receive_order';
  elsif v_order.status='accepted' and p_action='preparing' then v_next:='preparing'; v_permission:='cook_order';
  elsif v_order.status='preparing' and p_action='ready' then v_next:='ready'; v_permission:='ready_order';
  else raise exception 'order status changed; reload kitchen'; end if;
  if not public.pos_allowed(v_permission) then raise exception 'kitchen permission denied'; end if;
  v_note:=regexp_replace(coalesce(v_order.note,''),'^__QT_ORDER_STATUS__=[^\n]*\n?','','g');
  v_note:='__QT_ORDER_STATUS__='||(case v_next when 'accepted' then 'shop_accepted' when 'ready' then 'waiting_rider' else v_next end)||E'\n'||v_note;
  perform set_config('queuego.v22_transition','rpc',true);
  update public.orders set status=v_next,note=v_note,updated_at=now() where id=p_order;
  insert into public.pos_events(shop_id,order_id,actor_id,entity,action,before_state,after_state)
    values(v_order.shop_id,p_order,auth.uid(),'orders','DELIVERY_KITCHEN',
      jsonb_build_object('status',v_order.status),jsonb_build_object('status',v_next));
  if v_order.customer_id is not null then
    insert into public.notifications(user_id,title,message,type,reference_id)
      values(v_order.customer_id,'อัปเดตออเดอร์',
        case v_next when 'accepted' then 'ร้านรับออเดอร์แล้ว' when 'preparing' then 'ร้านกำลังเตรียมสินค้า' else 'สินค้าเตรียมเสร็จแล้ว' end,
        'order',p_order);
  end if;
  return v_next;
end $$;
revoke all on function public.pos_delivery_kitchen_action(uuid,text) from public,anon;
grant execute on function public.pos_delivery_kitchen_action(uuid,text) to authenticated;
commit;
