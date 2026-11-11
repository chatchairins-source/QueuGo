-- Align dine-in table catalog visibility with the POS availability enforced by checkout.
create or replace function public.qg_table_catalog(p_session uuid,p_device_key uuid)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare v public.qg_table_sessions%rowtype;
begin
 select * into v from public.qg_table_sessions where id=p_session and device_hash=encode(extensions.digest(p_device_key::text,'sha256'),'hex') and revoked_at is null and expires_at>now();
 if not found or not exists(select 1 from public.pos_tables where id=v.table_id and active) then raise exception 'สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง'; end if;
 return (select coalesce(jsonb_agg(jsonb_build_object('id',p.id,'name',p.name,'description',p.description,'image',p.image,'category',p.category,'price',coalesce(p.pos_price,p.price)) order by p.name),'[]'::jsonb)
 from public.products p where p.shop_id=v.shop_id and p.available and p.pos_available);
end $$;
