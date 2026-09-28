-- QueueGo POS workflow refinement. Apply after QueueGo-POS-Migration.sql.
begin;
alter table public.pos_staff add column if not exists staff_role text not null default 'WAITER'
  check (staff_role in ('WAITER','CASHIER','KITCHEN'));
alter table public.pos_invites add column if not exists staff_role text not null default 'WAITER'
  check (staff_role in ('WAITER','CASHIER','KITCHEN'));
alter table public.orders add column if not exists bill_status text
  check (bill_status is null or bill_status in ('OPEN','CLOSED'));
alter table public.orders add column if not exists cash_tendered numeric
  check (cash_tendered is null or cash_tendered>=0);
alter table public.orders add column if not exists cash_change numeric
  check (cash_change is null or cash_change>=0);
alter table public.orders add column if not exists paid_at timestamptz;
update public.orders set bill_status=case when payment_status='PAID' then 'CLOSED' else 'OPEN' end
where sales_channel='POS' and bill_status is null;

create or replace function public.pos_allowed(p_permission text) returns boolean
language sql stable security definer set search_path=public,pg_temp as $$
 select public.pos_is_owner() or exists(
 select 1 from public.pos_staff s where s.user_id=(select auth.uid())
 and s.shop_id=public.pos_my_shop() and s.active
 and (case when s.permissions ? p_permission then s.permissions->>p_permission='true'
   when p_permission in ('receive_order','send_kitchen','serve_order') then s.staff_role='WAITER'
   when p_permission in ('cook_order','ready_order') then s.staff_role='KITCHEN'
   when p_permission='close_bill' then s.staff_role='CASHIER'
   else false end))
$$;

create or replace function public.pos_create_role_invite(p_secret text,p_staff_role text)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid;
begin
 if not public.pos_is_owner() then raise exception 'owner permission required'; end if;
 if p_staff_role not in ('WAITER','CASHIER','KITCHEN') or length(p_secret)<32 then raise exception 'invalid role or secret'; end if;
 insert into public.pos_invites(shop_id,secret_hash,staff_role)
 values(public.pos_my_shop(),encode(sha256(convert_to(p_secret,'utf8')),'hex'),p_staff_role)
 returning id into v_id;
 return v_id;
end $$;

create or replace function public.pos_join_shop(p_secret text,p_display_name text)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_inv public.pos_invites%rowtype;
begin
 if auth.uid() is null or length(trim(p_display_name)) not between 1 and 100 then raise exception 'invalid staff account'; end if;
 select * into v_inv from public.pos_invites where secret_hash=encode(sha256(convert_to(p_secret,'utf8')),'hex') for update;
 if not found or v_inv.used_by is not null or v_inv.expires_at<now() then raise exception 'invite invalid or expired'; end if;
 if exists(select 1 from public.pos_staff where user_id=auth.uid()) or public.pos_is_owner() then raise exception 'account already linked'; end if;
 insert into public.pos_staff(user_id,shop_id,display_name,permissions,staff_role)
 values(auth.uid(),v_inv.shop_id,trim(p_display_name),v_inv.permissions,v_inv.staff_role);
 update public.pos_invites set used_by=auth.uid() where id=v_inv.id;
 return v_inv.shop_id;
end $$;

create or replace function public.pos_set_staff_role(p_user uuid,p_role text,p_active boolean)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not public.pos_is_owner() or p_role not in ('WAITER','CASHIER','KITCHEN') then raise exception 'owner permission required or invalid role'; end if;
 update public.pos_staff set staff_role=p_role,active=p_active
 where user_id=p_user and shop_id=public.pos_my_shop();
 if not found then raise exception 'staff not found'; end if;
end $$;

-- Preserve the established bill and product RPC; add a permission gate at its entry.
do $$ declare v_sql text; begin
 select pg_get_functiondef('public.pos_edit_bill(uuid,text,uuid,uuid,integer,text)'::regprocedure) into v_sql;
 if position('v_shop:=public.pos_my_shop();' in v_sql)=0 then raise exception 'inspect changed pos_edit_bill definition'; end if;
 v_sql:=replace(v_sql,'v_shop:=public.pos_my_shop();',
 'if not public.pos_allowed(''receive_order'') then raise exception ''receive order permission denied''; end if; v_shop:=public.pos_my_shop();');
 v_sql:=replace(v_sql,'if p_type=''DINE_IN'' and not exists', 'if p_type=''DINE_IN'' and p_table is not null and not exists');
 execute v_sql;
end $$;

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
 if p_action='cancel' then update public.orders set status='cancelled',updated_at=now() where id=p_order;
 else update public.orders set kitchen_status=v_next,updated_at=now() where id=p_order; end if;
 select to_jsonb(o) into v_result from public.orders o where o.id=p_order;
 return v_result;
end $$;

create or replace function public.pos_take_payment(p_order uuid,p_method text,p_cash_received numeric default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v_order public.orders%rowtype;v_result jsonb;
begin
 if not public.pos_allowed('close_bill') then raise exception 'cashier permission denied'; end if;
 select * into v_order from public.orders where id=p_order and shop_id=public.pos_my_shop() and sales_channel='POS' for update;
 if not found or v_order.payment_status<>'UNPAID' or v_order.status='cancelled' or v_order.kitchen_status not in ('READY','SERVED') then raise exception 'bill not ready for payment'; end if;
 if p_method not in ('cash','bank_transfer','promptpay','card','other') then raise exception 'invalid payment method'; end if;
 if p_method='cash' and (p_cash_received is null or p_cash_received<v_order.total_amount) then raise exception 'cash received is less than total'; end if;
 if p_method<>'cash' and p_cash_received is not null then raise exception 'cash received only for cash payment'; end if;
 perform set_config('queuego.pos_rpc','on',true);
 insert into public.payments(order_id,amount,payment_method,status,paid_at)
 values(p_order,v_order.total_amount,p_method,'paid',now());
 update public.orders set payment_status='PAID',payment_method=p_method,
 bill_status='CLOSED',cash_tendered=p_cash_received,
 cash_change=case when p_method='cash' then p_cash_received-v_order.total_amount else null end,
 paid_at=now(),status='completed',updated_at=now() where id=p_order;
 select to_jsonb(o) into v_result from public.orders o where o.id=p_order;
 return v_result;
end $$;

create or replace function public.pos_owner_dashboard(p_days integer default 1)
returns jsonb language plpgsql stable security definer set search_path=public,pg_temp as $$
declare v_result jsonb;
begin
 if not public.pos_is_owner() then raise exception 'owner only'; end if;
 select jsonb_build_object(
 'DINE_IN',coalesce(sum(total_amount) filter(where order_type='DINE_IN'),0),
 'TAKEAWAY',coalesce(sum(total_amount) filter(where order_type='TAKEAWAY'),0),
 'DELIVERY',coalesce(sum(total_amount) filter(where sales_channel='QUEUEGO_DELIVERY' or order_type='shopping'),0),
 'GP',coalesce(sum(gp_amount) filter(where sales_channel='QUEUEGO_DELIVERY' or order_type='shopping'),0),
 'orders',count(*)) into v_result
 from public.orders where shop_id=public.pos_my_shop() and status='completed'
 and (sales_channel='POS' and payment_status='PAID' or sales_channel='QUEUEGO_DELIVERY' or order_type='shopping')
 and created_at>=(((now() at time zone 'Asia/Bangkok')::date-(least(greatest(p_days,1),30)-1))::timestamp at time zone 'Asia/Bangkok');
 return v_result;
end $$;

revoke all on function public.pos_create_role_invite(text,text),public.pos_set_staff_role(uuid,text,boolean),
 public.pos_take_payment(uuid,text,numeric),public.pos_owner_dashboard(integer) from public,anon;
grant execute on function public.pos_create_role_invite(text,text),public.pos_set_staff_role(uuid,text,boolean),
 public.pos_take_payment(uuid,text,numeric),public.pos_owner_dashboard(integer) to authenticated;
commit;
