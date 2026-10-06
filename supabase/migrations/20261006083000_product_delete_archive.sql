-- QueueGo Merchant product delete/archive path.
-- Unused products are hard-deleted; products referenced by historical order items
-- are archived so historical bills remain intact.

create or replace function public.queuego_delete_or_archive_product(p_product_id uuid)
returns text
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_user uuid;
  v_shop uuid;
begin
  v_user := public.get_my_user_id();
  if v_user is null or p_product_id is null then
    raise exception 'shop login required';
  end if;

  select p.shop_id into v_shop
  from public.products p
  join public.shop_profiles sp on sp.id=p.shop_id
  where p.id=p_product_id
    and sp.user_id=v_user
  for update of p;

  if v_shop is null then
    return 'missing';
  end if;

  delete from public.market_products
  where product_id=p_product_id
    and shop_id=v_shop;

  if exists(select 1 from public.order_items oi where oi.product_id=p_product_id) then
    update public.products
    set available=false,
        delivery_available=false,
        pos_available=false,
        metadata=coalesce(metadata,'{}'::jsonb)
          || jsonb_build_object(
               'archived_at',to_char(now() at time zone 'utc','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
               'archived_by',v_user::text
             ),
        updated_at=now()
    where id=p_product_id
      and shop_id=v_shop;
    return 'archived';
  end if;

  delete from public.products
  where id=p_product_id
    and shop_id=v_shop;

  return 'deleted';
end
$function$;

revoke all on function public.queuego_delete_or_archive_product(uuid) from public,anon;
grant execute on function public.queuego_delete_or_archive_product(uuid) to authenticated;
