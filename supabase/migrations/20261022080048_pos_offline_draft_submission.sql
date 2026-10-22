-- Submit a reviewed device-local cart as one atomic, repeat-safe POS transaction.
begin;

create schema if not exists queuego_private;

create table queuego_private.pos_offline_draft_submissions (
  request_id uuid primary key,
  shop_id uuid not null references public.shop_profiles(id) on delete cascade,
  actor_id uuid not null references auth.users(id),
  order_id uuid not null,
  payload_hash text not null check (length(payload_hash)=32),
  created_at timestamptz not null default now()
);
create index pos_offline_draft_submissions_shop_created_idx
  on queuego_private.pos_offline_draft_submissions(shop_id,created_at desc);
alter table queuego_private.pos_offline_draft_submissions enable row level security;
revoke all on schema queuego_private from public,anon,authenticated;
revoke all on table queuego_private.pos_offline_draft_submissions from public,anon,authenticated;

create or replace function public.pos_submit_offline_draft(
  p_request uuid,
  p_type text,
  p_table uuid,
  p_items jsonb
)
returns jsonb
language plpgsql
security definer
set search_path=pg_catalog,public,pg_temp
as $function$
declare
  v_shop uuid;
  v_actor uuid;
  v_receipt queuego_private.pos_offline_draft_submissions%rowtype;
  v_hash text;
  v_order uuid;
  v_item jsonb;
  v_product uuid;
  v_options jsonb;
  v_quantity integer;
  v_unit integer;
  v_request uuid;
  v_seen_requests uuid[]:='{}'::uuid[];
  v_result jsonb;
  v_total integer:=0;
begin
  v_actor:=auth.uid();
  if v_actor is null or not public.pos_allowed('receive_order') then raise exception 'POS product permission denied'; end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS access denied'; end if;
  if p_request is null or p_type is null or p_type not in ('DINE_IN','TAKEAWAY') or (p_type='TAKEAWAY' and p_table is not null)
     or coalesce(jsonb_typeof(p_items),'')<>'array' or jsonb_array_length(p_items)<1 or jsonb_array_length(p_items)>100 then
    raise exception 'invalid POS offline draft';
  end if;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    if jsonb_typeof(v_item)<>'object' or jsonb_typeof(v_item->'options')<>'object'
       or jsonb_typeof(v_item->'request_ids')<>'array' then raise exception 'invalid POS offline draft item'; end if;
    v_quantity:=(v_item->>'quantity')::integer;
    if v_quantity<1 or v_quantity>99 or jsonb_array_length(v_item->'request_ids')<>v_quantity
       or coalesce(v_item->>'product_id','')='' then raise exception 'invalid POS offline draft quantity'; end if;
    v_total:=v_total+v_quantity;
    if v_total>500 then raise exception 'POS offline draft exceeds 500 units'; end if;
    for v_request in select value::uuid from jsonb_array_elements_text(v_item->'request_ids')
    loop
      if v_request is null or v_request=any(v_seen_requests) then raise exception 'duplicate or invalid POS offline draft request id'; end if;
      v_seen_requests:=array_append(v_seen_requests,v_request);
    end loop;
  end loop;

  v_hash:=md5(jsonb_build_object('type',p_type,'table',p_table,'items',p_items)::text);
  perform pg_advisory_xact_lock(hashtextextended(v_shop::text||':'||v_actor::text||':'||p_request::text,0));
  select * into v_receipt from queuego_private.pos_offline_draft_submissions where request_id=p_request;
  if found then
    if v_receipt.shop_id<>v_shop or v_receipt.actor_id<>v_actor or v_receipt.payload_hash<>v_hash then
      raise exception 'draft request id already used with different data';
    end if;
    return jsonb_build_object('order_id',v_receipt.order_id,'replayed',true);
  end if;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_product:=(v_item->>'product_id')::uuid;
    v_options:=v_item->'options';
    v_quantity:=(v_item->>'quantity')::integer;
    for v_unit in 1..v_quantity
    loop
      v_request:=(v_item->'request_ids'->>(v_unit-1))::uuid;
      v_result:=public.pos_add_product_with_options(v_order,p_type,p_table,v_product,'',v_options,v_request);
      v_order:=(v_result->>'order_id')::uuid;
    end loop;
  end loop;

  insert into queuego_private.pos_offline_draft_submissions(request_id,shop_id,actor_id,order_id,payload_hash)
    values(p_request,v_shop,v_actor,v_order,v_hash);
  return jsonb_build_object('order_id',v_order,'replayed',false);
end
$function$;

revoke all on function public.pos_submit_offline_draft(uuid,text,uuid,jsonb) from public,anon;
grant execute on function public.pos_submit_offline_draft(uuid,text,uuid,jsonb) to authenticated;
commit;
