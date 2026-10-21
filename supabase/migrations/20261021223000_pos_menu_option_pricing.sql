-- POS menu-option selection is validated against the shop's saved product options.
-- The server owns option snapshots, price deltas and replay protection.
begin;

create table public.pos_option_add_requests (
  request_id uuid primary key,
  shop_id uuid not null references public.shop_profiles(id) on delete cascade,
  actor_id uuid not null references auth.users(id),
  order_id uuid not null references public.orders(id) on delete cascade,
  -- Keep the replay receipt if the cashier later removes this line from an open bill.
  order_item_id uuid references public.order_items(id) on delete set null,
  payload_hash text not null check (length(payload_hash)=32),
  created_at timestamptz not null default now()
);
create index pos_option_add_requests_shop_created_idx on public.pos_option_add_requests(shop_id,created_at desc);
alter table public.pos_option_add_requests enable row level security;
revoke all on table public.pos_option_add_requests from public,anon,authenticated;

create or replace function public.pos_add_product_with_options(
  p_order uuid,
  p_type text,
  p_table uuid,
  p_product uuid,
  p_note text,
  p_options jsonb,
  p_request uuid
)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $function$
declare
  v_shop uuid;
  v_actor uuid;
  v_product public.products%rowtype;
  v_request public.pos_option_add_requests%rowtype;
  v_item public.order_items%rowtype;
  v_options jsonb:=coalesce(p_options,'{}'::jsonb);
  v_group jsonb;
  v_option jsonb;
  v_selected jsonb;
  v_key text;
  v_selected_key text;
  v_delta numeric:=0;
  v_option_delta numeric;
  v_price numeric;
  v_note text:=trim(coalesce(p_note,''));
  v_snapshot text:='';
  v_description text;
  v_order uuid;
  v_hash text;
  v_group_count integer:=0;
  v_legacy_count integer:=0;
  v_min_count integer;
  v_max_count integer;
  v_selected_count integer;
begin
  v_actor:=auth.uid();
  if v_actor is null or not public.pos_allowed('receive_order') then
    raise exception 'POS product permission denied';
  end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS access denied'; end if;
  if p_request is null or p_product is null then raise exception 'product request id and product are required'; end if;
  if p_type not in ('DINE_IN','TAKEAWAY') or (p_type='TAKEAWAY' and p_table is not null) then
    raise exception 'invalid POS bill type';
  end if;
  if length(v_note)>500 or jsonb_typeof(v_options)<>'object' then raise exception 'invalid product note or options'; end if;

  select * into v_product from public.products
    where id=p_product and shop_id=v_shop and available and pos_available;
  if not found then raise exception 'product unavailable in this shop'; end if;

  -- A request UUID is one immutable add action, including its selected options.
  v_hash:=md5(jsonb_build_object('order',p_order,'type',p_type,'table',p_table,
    'product',p_product,'note',v_note,'options',v_options)::text);
  perform pg_advisory_xact_lock(hashtextextended(v_shop::text||':'||v_actor::text||':'||p_request::text,0));
  select * into v_request from public.pos_option_add_requests where request_id=p_request;
  if found then
    if v_request.shop_id<>v_shop or v_request.actor_id<>v_actor or v_request.payload_hash<>v_hash then
      raise exception 'product request id already used with different data';
    end if;
    return jsonb_build_object('order_id',v_request.order_id,'order_item_id',v_request.order_item_id,'replayed',true);
  end if;

  select count(*) into v_group_count from jsonb_array_elements(coalesce(v_product.variants,'[]'::jsonb))
    where value->>'schema'='queuego.menu-options.v1';
  if v_group_count>0 then
    for v_group in select value from jsonb_array_elements(v_product.variants) where value->>'schema'='queuego.menu-options.v1'
    loop
      v_key:=v_group->>'key';
      if v_key is null or not (v_group ? 'options') or jsonb_typeof(v_group->'options')<>'array' then
        raise exception 'product option configuration is invalid';
      end if;
      if v_group->>'type'='single' then
        v_selected_key:=v_options->>v_key;
        if coalesce((v_group->>'required')::boolean,false) and coalesce(v_selected_key,'')='' then
          raise exception 'เลือก % ก่อนเพิ่มสินค้า',coalesce(v_group->>'name','ตัวเลือกสินค้า');
        end if;
        if v_selected_key is not null then
          select value into v_option from jsonb_array_elements(v_group->'options') where value->>'key'=v_selected_key;
          if not found then raise exception 'ตัวเลือกสินค้าไม่ถูกต้อง'; end if;
          v_option_delta:=coalesce((v_option->>'price_delta')::numeric,(v_option->>'price')::numeric,0);
          if not (v_option_delta between 0 and 999999) then raise exception 'product option price is invalid'; end if;
          v_delta:=v_delta+v_option_delta;
          v_snapshot:=concat_ws(' · ',nullif(v_snapshot,''),coalesce(v_group->>'name','ตัวเลือก')||': '||coalesce(v_option->>'name',v_selected_key));
        end if;
      elsif v_group->>'type'='multi' then
        v_selected:=coalesce(v_options->v_key,'[]'::jsonb);
        if jsonb_typeof(v_selected)<>'array' or jsonb_array_length(v_selected)>30 then raise exception 'รายการตัวเลือกไม่ถูกต้อง'; end if;
        v_min_count:=coalesce(nullif(v_group->>'min','')::integer,
          case when coalesce((v_group->>'required')::boolean,false) then 1 else 0 end);
        v_max_count:=coalesce(nullif(v_group->>'max','')::integer,jsonb_array_length(v_group->'options'));
        if v_min_count<0 or v_max_count<v_min_count or v_max_count>jsonb_array_length(v_group->'options') then
          raise exception 'product option configuration is invalid';
        end if;
        v_selected_count:=jsonb_array_length(v_selected);
        if v_selected_count<v_min_count then raise exception 'กรุณาเลือกอย่างน้อย % รายการใน %',v_min_count,coalesce(v_group->>'name','ตัวเลือกสินค้า'); end if;
        if v_selected_count>v_max_count then raise exception 'เลือกได้ไม่เกิน % รายการใน %',v_max_count,coalesce(v_group->>'name','ตัวเลือกสินค้า'); end if;
        if (select count(*) from jsonb_array_elements(v_selected))<>(select count(distinct value) from jsonb_array_elements_text(v_selected)) then
          raise exception 'มีตัวเลือกสินค้าซ้ำ';
        end if;
        for v_selected_key in select value from jsonb_array_elements_text(v_selected)
        loop
          select value into v_option from jsonb_array_elements(v_group->'options') where value->>'key'=v_selected_key;
          if not found then raise exception 'ตัวเลือกสินค้าไม่ถูกต้อง'; end if;
          v_option_delta:=coalesce((v_option->>'price_delta')::numeric,(v_option->>'price')::numeric,0);
          if not (v_option_delta between 0 and 999999) then raise exception 'product option price is invalid'; end if;
          v_delta:=v_delta+v_option_delta;
          v_snapshot:=concat_ws(' · ',nullif(v_snapshot,''),coalesce(v_group->>'name','ตัวเลือก')||': '||coalesce(v_option->>'name',v_selected_key));
        end loop;
      else
        raise exception 'product option type is unsupported';
      end if;
    end loop;
  else
    select count(*) into v_legacy_count from jsonb_array_elements(coalesce(v_product.variants,'[]'::jsonb));
    if v_legacy_count>0 then
      v_selected_key:=v_options->>'variant';
      if v_selected_key is null then raise exception 'เลือกตัวเลือกสินค้าก่อนเพิ่ม'; end if;
      if not exists(select 1 from jsonb_array_elements(v_product.variants) x where jsonb_typeof(x)='string' and x #>> '{}'=v_selected_key) then
        raise exception 'ตัวเลือกสินค้าไม่ถูกต้อง';
      end if;
      v_snapshot:='ตัวเลือก: '||v_selected_key;
    end if;
  end if;

  for v_key in select jsonb_object_keys(v_options)
  loop
    if not exists(select 1 from jsonb_array_elements(coalesce(v_product.variants,'[]'::jsonb)) g where g->>'schema'='queuego.menu-options.v1' and g->>'key'=v_key)
       and not (v_key='variant' and v_legacy_count>0) then
      raise exception 'ตัวเลือกสินค้าไม่ถูกต้อง';
    end if;
  end loop;
  if v_delta<0 or v_delta>999999 then raise exception 'product option price is invalid'; end if;
  if v_snapshot<>'' then v_note:=concat_ws(' · ',nullif(v_note,''),v_snapshot); end if;
  if length(v_note)>500 then raise exception 'product note and option summary exceed 500 characters'; end if;

  v_order:=public.pos_edit_bill(p_order,p_type,p_table,p_product,1,v_note);
  select * into v_item from public.order_items
    where order_id=v_order and product_id=p_product and coalesce(description,'')=v_note
    order by created_at desc,id desc limit 1 for update;
  if not found then raise exception 'POS item line was not created'; end if;
  v_price:=coalesce(v_product.pos_price,v_product.price)+v_delta;
  update public.order_items set unit_price=v_price,total_price=v_price*quantity where id=v_item.id;
  update public.orders o set subtotal=x.subtotal,
    discount_amount=least(o.discount_amount,x.subtotal),
    total_amount=x.subtotal-least(o.discount_amount,x.subtotal),updated_at=now()
  from (select coalesce(sum(total_price),0) as subtotal from public.order_items where order_id=v_order) x
  where o.id=v_order and o.shop_id=v_shop and o.sales_channel='POS';
  insert into public.pos_option_add_requests(request_id,shop_id,actor_id,order_id,order_item_id,payload_hash)
    values(p_request,v_shop,v_actor,v_order,v_item.id,v_hash);
  return jsonb_build_object('order_id',v_order,'order_item_id',v_item.id,'unit_price',v_price,'description',v_note,'replayed',false);
end
$function$;

revoke all on function public.pos_add_product_with_options(uuid,text,uuid,uuid,text,jsonb,uuid) from public,anon;
grant execute on function public.pos_add_product_with_options(uuid,text,uuid,uuid,text,jsonb,uuid) to authenticated;
commit;
