begin;

-- Immutable manager adjustments for the opt-in generic POS inventory owner.
-- Order reservations stay in pos_inventory_reservations; Grocery Master keeps its own ledger.
create table public.pos_inventory_adjustments (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null,
  product_id uuid not null,
  order_id uuid,
  actor_user_id uuid references auth.users(id) on delete set null,
  event_type text not null check (event_type in (
    'opening_balance','manual_adjustment','sale_reservation','reservation_release','cancellation_release'
  )),
  delta integer not null check (delta <> 0),
  stock_before integer not null check (stock_before >= 0),
  stock_after integer not null check (stock_after >= 0 and stock_after=stock_before+delta),
  reason text not null,
  note text,
  request_key uuid,
  created_at timestamptz not null default now(),
  unique (shop_id,actor_user_id,request_key)
);
create index pos_inventory_adjustments_product_history
  on public.pos_inventory_adjustments(shop_id,product_id,created_at desc,id desc);
-- Keep auth.users deletion / actor SET NULL from scanning the immutable audit ledger.
create index pos_inventory_adjustments_actor_user_id_idx
  on public.pos_inventory_adjustments(actor_user_id);
alter table public.pos_inventory_adjustments enable row level security;
revoke all on table public.pos_inventory_adjustments from public,anon,authenticated;

comment on table public.pos_inventory_adjustments is
  'Append-only audit of generic POS stock adjustments and automatic stock movements; read/write through manager RPCs only.';

create function public.qg_pos_inventory_stock_guard() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_adjustment_id uuid; v_reason text;
begin
  if old.grocery_master_id is not null or not old.inventory_stock_tracking or new.stock is not distinct from old.stock then
    return new;
  end if;
  -- Order reservation/cancellation triggers update the sellable count from a nested, checked path.
  if pg_trigger_depth()>1 then return new; end if;
  v_reason:=coalesce(nullif(current_setting('queuego.pos_inventory_adjustment_id',true),''),'');
  if v_reason !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    raise exception 'tracked stock must be changed through the inventory adjustment RPC';
  end if;
  v_adjustment_id:=v_reason::uuid;
  if not exists (
    select 1 from public.pos_inventory_adjustments a
    where a.id=v_adjustment_id and a.shop_id=old.shop_id and a.product_id=old.id
      and a.actor_user_id=(select auth.uid()) and a.event_type='manual_adjustment'
      and a.stock_before=old.stock and a.stock_after=new.stock and a.delta=new.stock-old.stock
  ) then raise exception 'stock adjustment audit receipt is invalid'; end if;
  return new;
end $$;
create trigger qg_pos_inventory_stock_guard
before update of stock on public.products
for each row execute function public.qg_pos_inventory_stock_guard();

create function public.qg_pos_inventory_stock_audit() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare
  v_before integer; v_delta integer; v_type text; v_request text; v_order text;
  v_request_id uuid; v_order_id uuid;
begin
  if new.grocery_master_id is not null or not new.inventory_stock_tracking then return new; end if;
  if tg_op='UPDATE' then
    if not old.inventory_stock_tracking then return new; end if;
  end if;
  v_request:=coalesce(nullif(current_setting('queuego.pos_inventory_adjustment_id',true),''),'');
  if v_request ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     and exists(select 1 from public.pos_inventory_adjustments where id=v_request::uuid and product_id=new.id) then
    return new; -- RPC inserted the immutable event before applying the validated stock delta.
  end if;
  if tg_op='INSERT' then
    v_before:=0; v_delta:=new.stock; v_type:='opening_balance';
  else
    if new.stock is not distinct from old.stock then return new; end if;
    v_before:=old.stock; v_delta:=new.stock-old.stock;
    v_type:=coalesce(nullif(current_setting('queuego.pos_inventory_event_type',true),''),'sale_reservation');
    if v_type not in ('sale_reservation','reservation_release','cancellation_release') then v_type:='sale_reservation'; end if;
  end if;
  if v_delta=0 then return new; end if;
  v_order:=coalesce(nullif(current_setting('queuego.pos_inventory_order_id',true),''),'');
  if v_order ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then v_order_id:=v_order::uuid; end if;
  insert into public.pos_inventory_adjustments(
    shop_id,product_id,order_id,actor_user_id,event_type,delta,stock_before,stock_after,reason
  ) values(
    new.shop_id,new.id,v_order_id,(select auth.uid()),v_type,v_delta,v_before,new.stock,
    case v_type when 'opening_balance' then 'opening_balance' when 'sale_reservation' then 'order_reservation'
      when 'reservation_release' then 'order_quantity_reduced' else 'order_cancelled' end
  );
  return new;
end $$;
create trigger qg_pos_inventory_stock_audit
after insert or update of stock on public.products
for each row execute function public.qg_pos_inventory_stock_audit();

create function public.qg_pos_inventory_tracking_audit() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if not old.inventory_stock_tracking and new.inventory_stock_tracking
     and new.grocery_master_id is null and new.stock>0 then
    insert into public.pos_inventory_adjustments(
      shop_id,product_id,actor_user_id,event_type,delta,stock_before,stock_after,reason
    ) values(new.shop_id,new.id,(select auth.uid()),'opening_balance',new.stock,0,new.stock,'tracking_enabled_opening_balance');
  end if;
  return new;
end $$;
create trigger qg_pos_inventory_tracking_audit
after update of inventory_stock_tracking on public.products
for each row execute function public.qg_pos_inventory_tracking_audit();

-- Add explicit source metadata around the existing generic order-item stock movement.
create or replace function public.qg_pos_generic_item_stock() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare
  item uuid; order_key uuid; product uuid; p public.products%rowtype;
  o public.orders%rowtype; r public.pos_inventory_reservations%rowtype;
  qty integer; delta integer; v_source text; v_order_context text;
begin
  item:=case when tg_op='DELETE' then old.id else new.id end;
  order_key:=case when tg_op='DELETE' then old.order_id else new.order_id end;
  product:=case when tg_op='DELETE' then old.product_id else new.product_id end;
  if tg_op='UPDATE' and (new.product_id is distinct from old.product_id or new.order_id is distinct from old.order_id)
     and (exists(select 1 from public.pos_inventory_reservations where item_id=item)
       or exists(select 1 from public.products where id in (new.product_id,old.product_id) and inventory_stock_tracking)) then
    raise exception 'tracked inventory order item identity is locked';
  end if;
  if exists(select 1 from public.products where id=product and grocery_master_id is not null) then
    return case when tg_op='DELETE' then old else new end;
  end if;
  select * into o from public.orders where id=order_key for update;
  if not found then raise exception 'inventory order not found'; end if;
  select * into p from public.products where id=product for update;
  if not found or p.grocery_master_id is not null then return case when tg_op='DELETE' then old else new end; end if;
  select * into r from public.pos_inventory_reservations where item_id=item for update;
  if not p.inventory_stock_tracking and r.item_id is null then return case when tg_op='DELETE' then old else new end; end if;
  if o.status='completed' then raise exception 'completed inventory item is immutable'; end if;
  if p.shop_id is distinct from o.shop_id then raise exception 'inventory product/shop mismatch'; end if;
  if tg_op='INSERT' and not p.available then raise exception 'inventory product unavailable'; end if;
  if o.status in ('cancelled','no_rider_available') then
    if tg_op='INSERT' then raise exception 'closed order cannot reserve inventory'; end if;
    return case when tg_op='DELETE' then old else new end;
  end if;
  if tg_op<>'DELETE' and (new.quantity<1 or new.quantity<>trunc(new.quantity)) then raise exception 'inventory quantity must be a positive integer'; end if;
  qty:=case when tg_op='DELETE' then 0 else new.quantity::integer end;
  delta:=qty-case when coalesce(r.released,false) then 0 else coalesce(r.quantity,0) end;
  if delta<>0 then
    v_source:=current_setting('queuego.pos_inventory_event_type',true);
    v_order_context:=current_setting('queuego.pos_inventory_order_id',true);
    perform set_config('queuego.pos_inventory_event_type',case when delta>0 then 'sale_reservation' else 'reservation_release' end,true);
    perform set_config('queuego.pos_inventory_order_id',order_key::text,true);
    update public.products set stock=stock-delta where id=product and stock>=delta;
    if not found then raise exception 'insufficient stock'; end if;
    perform set_config('queuego.pos_inventory_event_type',coalesce(v_source,''),true);
    perform set_config('queuego.pos_inventory_order_id',coalesce(v_order_context,''),true);
  end if;
  if tg_op='DELETE' then delete from public.pos_inventory_reservations where item_id=item;
  else insert into public.pos_inventory_reservations(item_id,order_id,product_id,quantity,released)
    values(item,order_key,product,qty,false)
    on conflict(item_id) do update set quantity=excluded.quantity,released=false,updated_at=now(); end if;
  return case when tg_op='DELETE' then old else new end;
end $$;

create or replace function public.qg_pos_generic_cancel_stock() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare r record; v_source text; v_order_context text;
begin
  if new.status not in ('cancelled','no_rider_available') or old.status in ('cancelled','no_rider_available') then return new; end if;
  for r in select * from public.pos_inventory_reservations where order_id=new.id and not released order by product_id,item_id for update loop
    if old.status<>'completed' then
      v_source:=current_setting('queuego.pos_inventory_event_type',true);
      v_order_context:=current_setting('queuego.pos_inventory_order_id',true);
      perform set_config('queuego.pos_inventory_event_type','cancellation_release',true);
      perform set_config('queuego.pos_inventory_order_id',new.id::text,true);
      update public.products set stock=stock+r.quantity where id=r.product_id;
      perform set_config('queuego.pos_inventory_event_type',coalesce(v_source,''),true);
      perform set_config('queuego.pos_inventory_order_id',coalesce(v_order_context,''),true);
    end if;
    update public.pos_inventory_reservations set released=true,updated_at=now() where item_id=r.item_id;
  end loop;
  return new;
end $$;

create function public.pos_inventory_stock_adjust(
  p_product_id uuid,p_delta integer,p_reason text,p_note text,p_request_key uuid
) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_shop uuid; v_actor uuid; v_product public.products%rowtype; v_existing public.pos_inventory_adjustments%rowtype; v_row public.pos_inventory_adjustments%rowtype;
begin
  v_actor:=(select auth.uid());
  if v_actor is null or not public.pos_allowed('manage_staff') then raise exception 'inventory adjustment permission denied'; end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS shop required'; end if;
  if p_product_id is null or p_delta is null or p_delta=0 or abs(p_delta)>100000 then raise exception 'invalid stock delta'; end if;
  if p_request_key is null then raise exception 'stock adjustment request key required'; end if;
  if p_reason not in ('receiving','count_correction','damage','loss','return_restock','other') then raise exception 'invalid stock adjustment reason'; end if;
  if length(btrim(coalesce(p_note,'')))<3 or length(p_note)>300 then raise exception 'stock adjustment note required'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_shop::text||':'||v_actor::text||':'||p_request_key::text,0));
  select * into v_existing from public.pos_inventory_adjustments
    where shop_id=v_shop and actor_user_id=v_actor and request_key=p_request_key for update;
  if found then
    if v_existing.product_id=p_product_id and v_existing.delta=p_delta and v_existing.reason=p_reason and v_existing.note=btrim(p_note)
      then return to_jsonb(v_existing)||jsonb_build_object('replayed',true); end if;
    raise exception 'stock adjustment request key was already used';
  end if;
  select * into v_product from public.products where id=p_product_id and shop_id=v_shop for update;
  if not found or not v_product.inventory_stock_tracking or v_product.grocery_master_id is not null then raise exception 'tracked generic product not found'; end if;
  if v_product.stock+p_delta<0 then raise exception 'stock adjustment would make inventory negative'; end if;
  insert into public.pos_inventory_adjustments(shop_id,product_id,actor_user_id,event_type,delta,stock_before,stock_after,reason,note,request_key)
  values(v_shop,p_product_id,v_actor,'manual_adjustment',p_delta,v_product.stock,v_product.stock+p_delta,p_reason,btrim(p_note),p_request_key)
  returning * into v_row;
  perform set_config('queuego.pos_inventory_adjustment_id',v_row.id::text,true);
  update public.products set stock=v_row.stock_after where id=v_product.id;
  perform set_config('queuego.pos_inventory_adjustment_id','',true);
  return to_jsonb(v_row)||jsonb_build_object('replayed',false);
end $$;

create function public.pos_inventory_adjustment_history(
  p_product_id uuid,p_limit integer default 40,p_before_at timestamptz default null,p_before_id uuid default null
) returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare v_shop uuid; v_limit integer; v_rows jsonb; v_count integer;
begin
  if (select auth.uid()) is null or not public.pos_allowed('manage_staff') then raise exception 'inventory history permission denied'; end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null or not exists(select 1 from public.products where id=p_product_id and shop_id=v_shop and inventory_stock_tracking and grocery_master_id is null) then
    raise exception 'tracked generic product not found';
  end if;
  if (p_before_at is null)<>(p_before_id is null) then raise exception 'complete history cursor required'; end if;
  v_limit:=least(greatest(coalesce(p_limit,40),1),100);
  select count(*)::integer into v_count from (
    select a.id from public.pos_inventory_adjustments a where a.shop_id=v_shop and a.product_id=p_product_id
      and (p_before_at is null or (a.created_at,a.id)<(p_before_at,p_before_id))
    order by a.created_at desc,a.id desc limit v_limit+1
  ) q;
  select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc,q.id desc),'[]'::jsonb) into v_rows from (
    select a.id,a.event_type,a.delta,a.stock_before,a.stock_after,a.reason,a.note,a.created_at,
      coalesce(s.display_name,'เจ้าของร้าน/ระบบ') actor_name
    from public.pos_inventory_adjustments a
    left join public.pos_staff s on s.user_id=a.actor_user_id and s.shop_id=a.shop_id and s.active
    where a.shop_id=v_shop and a.product_id=p_product_id
      and (p_before_at is null or (a.created_at,a.id)<(p_before_at,p_before_id))
    order by a.created_at desc,a.id desc limit v_limit
  ) q;
  return jsonb_build_object('entries',v_rows,'has_more',v_count>v_limit);
end $$;

revoke all on function public.qg_pos_inventory_stock_guard(),public.qg_pos_inventory_stock_audit(),public.qg_pos_inventory_tracking_audit(),
  public.qg_pos_generic_item_stock(),public.qg_pos_generic_cancel_stock() from public,anon,authenticated;
revoke all on function public.pos_inventory_stock_adjust(uuid,integer,text,text,uuid),
  public.pos_inventory_adjustment_history(uuid,integer,timestamptz,uuid) from public,anon,authenticated;
grant execute on function public.pos_inventory_stock_adjust(uuid,integer,text,text,uuid),
  public.pos_inventory_adjustment_history(uuid,integer,timestamptz,uuid) to authenticated;

commit;
