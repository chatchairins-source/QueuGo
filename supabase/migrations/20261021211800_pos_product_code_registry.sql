-- Shop-owned barcode/SKU lookup for the existing POS product and bill flow.
begin;

create table public.pos_product_codes (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shop_profiles(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  code_type text not null check (code_type in ('barcode','sku')),
  code text not null check (code=upper(trim(code)) and length(code) between 1 and 64),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  unique (shop_id,code)
);
create index pos_product_codes_shop_product_idx on public.pos_product_codes(shop_id,product_id);
alter table public.pos_product_codes enable row level security;
revoke all on table public.pos_product_codes from public,anon,authenticated;

comment on table public.pos_product_codes is
  'Shop-scoped POS SKU and barcode aliases for the existing products table; read and write only through guarded POS RPCs.';

create or replace function public.pos_product_codes_for_shop()
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $function$
declare v_shop uuid; v_rows jsonb;
begin
  if (select auth.uid()) is null or not public.pos_allowed('manage_staff') then
    raise exception 'product code management permission denied';
  end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS access denied'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',c.id,'product_id',c.product_id,'product_name',p.name,
    'code_type',c.code_type,'code',c.code,'created_at',c.created_at
  ) order by p.name,c.code),'[]'::jsonb)
  into v_rows
  from public.pos_product_codes c
  join public.products p on p.id=c.product_id and p.shop_id=c.shop_id
  where c.shop_id=v_shop;
  return v_rows;
end
$function$;

create or replace function public.pos_product_code_save(p_product uuid,p_code_type text,p_code text)
returns jsonb
language plpgsql
security definer
set search_path=public,pg_temp
as $function$
declare v_shop uuid; v_code text; v_type text; v_id uuid; v_product public.products%rowtype;
        v_existing public.pos_product_codes%rowtype;
begin
  if (select auth.uid()) is null or not public.pos_allowed('manage_staff') then
    raise exception 'product code management permission denied';
  end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS access denied'; end if;
  v_type:=lower(trim(coalesce(p_code_type,'')));
  v_code:=upper(trim(coalesce(p_code,'')));
  if v_type not in ('barcode','sku') then raise exception 'invalid product code type'; end if;
  if length(v_code)<1 or length(v_code)>64 then raise exception 'invalid product code'; end if;
  if v_type='barcode' and v_code !~ '^[0-9]{8,14}$' then raise exception 'barcode must contain 8 to 14 digits'; end if;
  if v_type='sku' and v_code !~ '^[A-Z0-9][A-Z0-9._/-]{0,63}$' then raise exception 'invalid SKU'; end if;
  if p_product is null then raise exception 'product is required'; end if;
  select * into v_product from public.products where id=p_product and shop_id=v_shop;
  if not found then raise exception 'product not found in this shop'; end if;

  -- Serialize concurrent attempts to claim the same shop-scoped code; the unique constraint
  -- remains the final integrity guard.
  perform pg_advisory_xact_lock(hashtextextended(v_shop::text||':'||v_code,0));
  select * into v_existing from public.pos_product_codes where shop_id=v_shop and code=v_code for update;
  if found then
    if v_existing.product_id<>p_product or v_existing.code_type<>v_type then
      raise exception 'product code already assigned';
    end if;
    return jsonb_build_object('id',v_existing.id,'product_id',v_existing.product_id,
      'code_type',v_existing.code_type,'code',v_existing.code,'created_at',v_existing.created_at);
  end if;

  insert into public.pos_product_codes(shop_id,product_id,code_type,code,created_by)
    values(v_shop,p_product,v_type,v_code,(select auth.uid()))
    returning id into v_id;
  return jsonb_build_object('id',v_id,'product_id',p_product,'code_type',v_type,'code',v_code);
exception when unique_violation then
  select * into v_existing from public.pos_product_codes where shop_id=v_shop and code=v_code;
  if found and v_existing.product_id=p_product and v_existing.code_type=v_type then
    return jsonb_build_object('id',v_existing.id,'product_id',v_existing.product_id,
      'code_type',v_existing.code_type,'code',v_existing.code,'created_at',v_existing.created_at);
  end if;
  raise exception 'product code already assigned';
end
$function$;

create or replace function public.pos_product_code_remove(p_id uuid)
returns boolean
language plpgsql
security definer
set search_path=public,pg_temp
as $function$
declare v_shop uuid;
begin
  if (select auth.uid()) is null or not public.pos_allowed('manage_staff') then
    raise exception 'product code management permission denied';
  end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS access denied'; end if;
  delete from public.pos_product_codes where id=p_id and shop_id=v_shop;
  if not found then raise exception 'product code not found'; end if;
  return true;
end
$function$;

create or replace function public.pos_product_code_lookup(p_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path=public,pg_temp
as $function$
declare v_shop uuid; v_code text; v_result jsonb;
begin
  if (select auth.uid()) is null or not public.pos_allowed('receive_order') then
    raise exception 'POS product lookup permission denied';
  end if;
  v_shop:=public.pos_my_shop();
  if v_shop is null then raise exception 'POS access denied'; end if;
  v_code:=upper(trim(coalesce(p_code,'')));
  if length(v_code)<1 or length(v_code)>64 then raise exception 'invalid product code'; end if;
  select jsonb_build_object(
    'code',c.code,'code_type',c.code_type,'product_id',p.id,'name',p.name,
    'price',coalesce(p.pos_price,p.price),'available',p.available,'pos_available',p.pos_available,
    'image',p.image,'variants',p.variants
  ) into v_result
  from public.pos_product_codes c
  join public.products p on p.id=c.product_id and p.shop_id=c.shop_id
  where c.shop_id=v_shop and c.code=v_code;
  return v_result;
end
$function$;

revoke all on function public.pos_product_codes_for_shop(),
  public.pos_product_code_save(uuid,text,text),public.pos_product_code_remove(uuid),
  public.pos_product_code_lookup(text) from public,anon;
grant execute on function public.pos_product_codes_for_shop(),
  public.pos_product_code_save(uuid,text,text),public.pos_product_code_remove(uuid),
  public.pos_product_code_lookup(text) to authenticated;

commit;
