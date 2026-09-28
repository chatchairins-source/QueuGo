-- Add kitchen state to each POS line so later additions do not re-enter the kitchen as old items.
begin;
alter table public.order_items add column if not exists pos_kitchen_status text
 check (pos_kitchen_status is null or pos_kitchen_status in ('NEW','SENT_TO_KITCHEN','COOKING','READY','SERVED'));
update public.order_items i set pos_kitchen_status=coalesce(o.kitchen_status,'NEW')
from public.orders o where i.order_id=o.id and o.sales_channel='POS' and i.pos_kitchen_status is null;

create or replace function public.pos_set_new_item_state() returns trigger
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if exists(select 1 from public.orders o where o.id=new.order_id and o.sales_channel='POS') then
   new.pos_kitchen_status:='NEW';
 end if;
 return new;
end $$;
create trigger pos_new_item_state before insert on public.order_items
for each row execute function public.pos_set_new_item_state();
revoke all on function public.pos_set_new_item_state() from public,anon;

create or replace function public.pos_bill_action(p_order uuid,p_action text,p_method text default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;v_next text;v_permission text;v_result jsonb;
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
   update public.order_items set pos_kitchen_status=v_next where order_id=p_order
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
