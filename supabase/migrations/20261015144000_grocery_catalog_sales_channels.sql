-- Grocery catalog Merchant controls: one store price, explicit POS/Delivery channels, no promo-price editor.
-- Additive successor to the existing Grocery Master Catalog. Preserve the private receipt/idempotency model.

create or replace function public.qg_grocery_product_guard()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  m public.grocery_master_products;
begin
  if tg_op='UPDATE'
     and old.grocery_master_id is not null
     and new.grocery_master_id is distinct from old.grocery_master_id
     and not public.is_active_admin()
  then
    raise exception 'master identity is locked';
  end if;

  if new.grocery_master_id is null then
    return new;
  end if;

  if not exists(
    select 1
    from public.shop_profiles
    where id=new.shop_id
      and public_category='grocery'
      and archived_at is null
  ) then
    raise exception 'grocery shop required';
  end if;

  select * into strict m
  from public.grocery_master_products
  where id=new.grocery_master_id;

  if m.merged_into is not null then
    raise exception 'master is merged';
  end if;
  if new.stock < 0 then
    raise exception 'negative stock';
  end if;

  -- Grocery catalog promotions are retired. The merchant has one storefront
  -- price; QueueGo derives the Delivery price through the existing GP trigger.
  new.grocery_promo_price:=null;

  new.name:=
    case
      when left(public.qg_grocery_fold(m.product_name_th),length(public.qg_grocery_fold(m.brand)))=public.qg_grocery_fold(m.brand)
      then ''
      else m.brand||' '
    end
    ||m.product_name_th
    ||case when m.variant='' then '' else ' '||m.variant end
    ||' '||m.size_value::text||' '||m.size_unit
    ||case when m.pack_size=1 then '' else ' x '||m.pack_size end;
  new.category:=m.category;
  new.image:=case when m.image_status='approved' then m.image else null end;
  new.price:=coalesce(new.grocery_selling_price,0);
  new.pos_price:=new.price;
  new.available:=
    m.active
    and new.grocery_enabled
    and new.grocery_selling_price>0
    and (not new.grocery_stock_tracking or new.stock>0);
  new.available:=coalesce(new.available,false);
  new.updated_at:=now();
  return new;
end
$$;

revoke all on function public.qg_grocery_product_guard() from public,anon,authenticated;

create or replace function grocery_private.store_save(
  p_shop_id uuid,
  p_rows jsonb,
  p_request_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $$
declare
  r jsonb;
  v_n integer:=0;
  receipt grocery_private.store_save_receipts;
  current_stamp timestamptz;
  result jsonb;
begin
  if auth.uid() is null
     or coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1
       from public.shop_profiles
       where id=p_shop_id
         and user_id=public.get_my_user_id()
         and public_category='grocery'
         and archived_at is null
     )
  then
    raise exception 'own grocery shop required' using errcode='42501';
  end if;

  if jsonb_typeof(p_rows)<>'array'
     or jsonb_array_length(p_rows) not between 1 and 100
  then
    raise exception 'batch must contain 1..100 rows';
  end if;
  if p_request_id is null then
    raise exception 'request ID required';
  end if;

  perform pg_advisory_xact_lock(hashtext(p_shop_id::text||p_request_id::text));
  select * into receipt
  from grocery_private.store_save_receipts
  where shop_id=p_shop_id and request_id=p_request_id;

  if found then
    if receipt.request_body is distinct from p_rows then
      raise exception 'request ID body mismatch';
    end if;
    return receipt.result;
  end if;

  for r in
    select value
    from jsonb_array_elements(p_rows)
    order by value->>'master_product_id'
  loop
    if not exists(
      select 1
      from public.grocery_master_products
      where id=(r->>'master_product_id')::uuid
        and active
        and merged_into is null
    ) then
      raise exception 'master unavailable';
    end if;

    select updated_at into current_stamp
    from public.products
    where shop_id=p_shop_id
      and grocery_master_id=(r->>'master_product_id')::uuid
    for update;

    if current_stamp is distinct from nullif(r->>'expected_updated_at','')::timestamptz then
      raise exception 'grocery listing changed; refresh before saving';
    end if;

    insert into public.products(
      shop_id,
      grocery_master_id,
      grocery_enabled,
      grocery_selling_price,
      grocery_promo_price,
      stock,
      grocery_stock_tracking,
      pos_available,
      delivery_available
    )
    values(
      p_shop_id,
      (r->>'master_product_id')::uuid,
      coalesce((r->>'enabled')::boolean,false),
      nullif(r->>'selling_price','')::numeric,
      null,
      coalesce((r->>'stock_qty')::integer,0),
      coalesce((r->>'stock_tracking')::boolean,false),
      coalesce(nullif(r->>'pos_available','')::boolean,true),
      coalesce(nullif(r->>'delivery_available','')::boolean,true)
    )
    on conflict(shop_id,grocery_master_id)
      where grocery_master_id is not null
    do update set
      price=excluded.price,
      pos_price=excluded.pos_price,
      grocery_enabled=excluded.grocery_enabled,
      grocery_selling_price=excluded.grocery_selling_price,
      grocery_promo_price=null,
      stock=excluded.stock,
      grocery_stock_tracking=excluded.grocery_stock_tracking,
      pos_available=case
        when r ? 'pos_available' then excluded.pos_available
        else products.pos_available
      end,
      delivery_available=case
        when r ? 'delivery_available' then excluded.delivery_available
        else products.delivery_available
      end;

    v_n:=v_n+1;
  end loop;

  result:=jsonb_build_object('saved',v_n);
  insert into grocery_private.store_save_receipts(
    shop_id,request_id,request_body,result
  )
  values(p_shop_id,p_request_id,p_rows,result);
  return result;
end
$$;

revoke all on function grocery_private.store_save(uuid,jsonb,uuid) from public,anon;
grant execute on function grocery_private.store_save(uuid,jsonb,uuid) to authenticated;

create or replace function public.qg_grocery_merchant_page(
  p_shop_id uuid,
  p_query text default '',
  p_category text default '',
  p_mode text default 'all',
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security invoker
set search_path=public,pg_temp
as $$
declare
  result jsonb;
begin
  if coalesce(public.get_my_role(),'')<>'shop'
     or not exists(
       select 1
       from public.shop_profiles
       where id=p_shop_id
         and user_id=public.get_my_user_id()
         and public_category='grocery'
     )
  then
    raise exception 'own grocery shop required';
  end if;

  if p_offset<0
     or length(p_query)>100
     or p_mode not in ('all','on','off','unpriced')
  then
    raise exception 'invalid query';
  end if;

  select coalesce(
    jsonb_agg(
      to_jsonb(x)
      order by
        (x.image_status='approved' and nullif(btrim(x.image),'') is not null) desc,
        x.product_name_th,
        x.id
    ),
    '[]'
  )
  into result
  from (
    select
      m.id,
      m.brand,
      m.product_name_th,
      m.variant,
      m.size_value,
      m.size_unit,
      m.pack_size,
      m.sales_unit,
      m.category,
      m.image,
      m.image_status,
      p.id product_id,
      p.updated_at product_updated_at,
      coalesce(p.grocery_enabled,false) enabled,
      p.grocery_selling_price selling_price,
      coalesce(p.stock,0) stock_qty,
      coalesce(p.grocery_stock_tracking,false) stock_tracking,
      coalesce(p.pos_available,true) pos_available,
      coalesce(p.delivery_available,true) delivery_available,
      p.delivery_price
    from public.grocery_master_products m
    left join public.products p
      on p.grocery_master_id=m.id
     and p.shop_id=p_shop_id
    where m.active
      and m.merged_into is null
      and (p_category='' or m.category=p_category)
      and (
        p_query=''
        or to_tsvector(
          'simple',
          m.brand||' '||m.product_name_th||' '||coalesce(m.product_name_en,'')||' '||m.variant
        ) @@ plainto_tsquery('simple',p_query)
        or m.product_name_th ilike '%'||p_query||'%'
        or m.brand ilike '%'||p_query||'%'
      )
      and (
        p_mode='all'
        or p_mode='on' and p.grocery_enabled
        or p_mode='off' and not coalesce(p.grocery_enabled,false)
        or p_mode='unpriced' and p.grocery_selling_price is null
      )
    order by
      (m.image_status='approved' and nullif(btrim(m.image),'') is not null) desc,
      m.product_name_th,
      m.id
    limit 51
    offset p_offset
  ) x;

  return result;
end
$$;

revoke all on function public.qg_grocery_merchant_page(uuid,text,text,text,integer) from public,anon;
grant execute on function public.qg_grocery_merchant_page(uuid,text,text,text,integer) to authenticated;

-- Normalize any legacy hidden Grocery promo value back to the one-price rule.
-- Including price/pos_price in the SET list deliberately runs the existing GP lock
-- so delivery_price is recalculated from the storefront price.
update public.products
set
  grocery_promo_price=null,
  price=coalesce(grocery_selling_price,price),
  pos_price=coalesce(grocery_selling_price,pos_price,price)
where grocery_master_id is not null
  and grocery_promo_price is not null;
