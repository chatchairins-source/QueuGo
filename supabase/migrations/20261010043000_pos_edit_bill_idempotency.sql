-- Add replay protection around the existing Production POS edit function.
-- No existing table, policy, order state, payment flow, or RPC is replaced.
create table if not exists public.pos_edit_request_keys (
  request_id uuid primary key,
  shop_id uuid not null references public.shop_profiles(id) on delete cascade,
  actor_id uuid not null,
  order_id uuid not null references public.orders(id) on delete cascade,
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  created_at timestamptz not null default now()
);
create index if not exists pos_edit_request_keys_shop_created_idx
  on public.pos_edit_request_keys(shop_id, created_at);
create index if not exists pos_edit_request_keys_order_fk_idx
  on public.pos_edit_request_keys(order_id);
alter table public.pos_edit_request_keys enable row level security;
revoke all on public.pos_edit_request_keys from public, anon, authenticated;
do $$ begin
  if not exists (select 1 from pg_policies where schemaname='public'
    and tablename='pos_edit_request_keys' and policyname='qg_server_only_no_client_access') then
    create policy qg_server_only_no_client_access on public.pos_edit_request_keys
      for all to anon, authenticated using (false) with check (false);
  end if;
end $$;

create or replace function public.pos_edit_bill_once(
  p_request uuid, p_order uuid, p_type text, p_table uuid,
  p_product uuid, p_quantity integer, p_note text default ''
) returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_shop uuid;
  v_actor uuid := auth.uid();
  v_existing public.pos_edit_request_keys%rowtype;
  v_payload jsonb;
  v_order uuid;
begin
  v_shop := public.pos_my_shop();
  if v_actor is null or v_shop is null or not coalesce(public.pos_allowed('receive_order'),false) then
    raise exception 'POS access denied';
  end if;
  if p_request is null or p_order is null or p_product is null
    or p_type is null or p_type not in ('DINE_IN','TAKEAWAY')
    or p_quantity is null or p_quantity = 0 or p_quantity not between -99 and 99
    or length(coalesce(p_note,'')) > 500 then
    raise exception 'invalid bill edit request';
  end if;
  -- Ownership is checked even when returning a previously committed result.
  if not exists (select 1 from public.orders where id=p_order
    and shop_id=v_shop and sales_channel='POS') then
    raise exception 'bill not found';
  end if;
  v_payload := jsonb_build_object('order',p_order,'type',p_type,'table',p_table,
    'product',p_product,'quantity',p_quantity,'note',trim(coalesce(p_note,'')));
  perform pg_advisory_xact_lock(hashtextextended('pos_edit:' || p_request::text,0));
  select * into v_existing from public.pos_edit_request_keys where request_id=p_request;
  if found then
    if v_existing.shop_id<>v_shop or v_existing.actor_id<>v_actor
      or v_existing.order_id<>p_order or v_existing.payload<>v_payload then
      raise exception 'request id already in use';
    end if;
    return v_existing.order_id;
  end if;
  -- Retain every validation and row lock in the deployed POS implementation.
  v_order := public.pos_edit_bill(p_order,p_type,p_table,p_product,p_quantity,p_note);
  if v_order is distinct from p_order then raise exception 'unexpected bill edit result'; end if;
  insert into public.pos_edit_request_keys(request_id,shop_id,actor_id,order_id,payload)
    values(p_request,v_shop,v_actor,v_order,v_payload);
  return v_order;
end $$;
revoke all on function public.pos_edit_bill_once(uuid,uuid,text,uuid,uuid,integer,text) from public,anon;
grant execute on function public.pos_edit_bill_once(uuid,uuid,text,uuid,uuid,integer,text) to authenticated;
