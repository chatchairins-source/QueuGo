-- Separate later additions into kitchen batches within the same order.
begin;
alter table public.order_items add column if not exists pos_batch integer check(pos_batch is null or pos_batch>0);
update public.order_items i set pos_batch=1 from public.orders o
where i.order_id=o.id and o.sales_channel='POS' and i.pos_batch is null;

create or replace function public.pos_set_new_item_state() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
declare v_batch integer;
begin
 if exists(select 1 from public.orders o where o.id=new.order_id and o.sales_channel='POS') then
   select max(pos_batch) into v_batch from public.order_items where order_id=new.order_id and pos_kitchen_status='NEW';
   if v_batch is null then select coalesce(max(pos_batch),0)+1 into v_batch from public.order_items where order_id=new.order_id; end if;
   new.pos_kitchen_status:='NEW';new.pos_batch:=v_batch;
 end if;
 return new;
end $$;

create or replace function public.pos_bill_action(p_order uuid,p_action text,p_method text default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;v_next text;v_permission text;v_result jsonb;v_batch integer;
begin
 select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
 if not found or v_order.status='cancelled' or v_order.payment_status<>'UNPAID' then raise exception 'open bill not found'; end if;
 if p_action='send' and v_order.kitchen_status='NEW' and v_order.subtotal>0 then v_next:='SENT_TO_KITCHEN';v_permission:='send_kitchen';
 elsif p_action='cook' and v_order.kitchen_status='SENT_TO_KITCHEN' then v_next:='COOKING';v_permission:='cook_order';
 elsif p_action='ready' and v_order.kitchen_status in ('SENT_TO_KITCHEN','COOKING') then v_next:='READY';v_permission:='ready_order';
 elsif p_action='serve' and v_order.kitchen_status='READY' then v_next:='SERVED';v_permission:='serve_order';
 elsif p_action='cancel' then v_permission:='cancel_bill';
 else raise exception 'invalid POS transition'; end if;
 if not public.pos_allowed(v_permission) then raise exception 'permission denied'; end if;
 perform set_config('queuego.pos_rpc','on',true);
 if p_action='cancel' then
   update public.orders set status='cancelled',updated_at=now() where id=p_order;
 else
   select max(pos_batch) into v_batch from public.order_items where order_id=p_order;
   update public.order_items set pos_kitchen_status=v_next where order_id=p_order and pos_batch=v_batch
   and (case p_action when 'send' then pos_kitchen_status='NEW'
    when 'cook' then pos_kitchen_status in ('NEW','SENT_TO_KITCHEN')
    when 'ready' then pos_kitchen_status in ('SENT_TO_KITCHEN','COOKING')
    when 'serve' then pos_kitchen_status='READY' else false end);
   update public.orders set kitchen_status=v_next,updated_at=now() where id=p_order;
 end if;
 select to_jsonb(o) into v_result from public.orders o where o.id=p_order;
 return v_result;
end $$;
commit;
