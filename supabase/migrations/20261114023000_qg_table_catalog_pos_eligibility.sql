-- REVIEW REQUIRED: forward-only change; do not apply to Production automatically.
-- SECURITY DEFINER uses an empty search_path; all application/extension objects are schema-qualified.
-- QR table catalog must advertise the same POS-eligible products that
-- qg_table_checkout accepts. Preserve session and active-table authorization, binding the table to the session shop.
CREATE OR REPLACE FUNCTION public.qg_table_catalog(p_session uuid, p_device_key uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare v public.qg_table_sessions%rowtype;
begin
  select * into v
  from public.qg_table_sessions
  where id=p_session
    and device_hash=encode(extensions.digest(p_device_key::text,'sha256'),'hex')
    and revoked_at is null
    and expires_at>now();

  if not found or not exists(
    select 1 from public.pos_tables where id=v.table_id and shop_id=v.shop_id and active
  ) then
    raise exception 'สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง';
  end if;

  -- Match checkout's shop eligibility guard before disclosing catalog items.
  if not exists (
    select 1 from public.shop_profiles
    where id=v.shop_id and status not in ('suspended','rejected')
  ) then
    raise exception 'ร้านไม่พร้อมรับออเดอร์';
  end if;

  return (
    select coalesce(
      jsonb_agg(
        jsonb_build_object(
          'id',p.id,
          'name',p.name,
          'description',p.description,
          'image',p.image,
          'category',p.category,
          'price',coalesce(p.pos_price,p.price)
        )
        order by p.name
      ),
      '[]'::jsonb
    )
    from public.products p
    where p.shop_id=v.shop_id
      and p.available
      and p.pos_available
  );
end
$function$;
