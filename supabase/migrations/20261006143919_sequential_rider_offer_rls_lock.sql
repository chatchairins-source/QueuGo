CREATE OR REPLACE FUNCTION public.qg_has_live_rider_offer(p_order_id uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
 select exists(
  select 1 from public.qg_rider_order_offers f
  join public.rider_profiles r on r.id=f.rider_id
  join public.users u on u.id=r.user_id
  where f.order_id=p_order_id and f.expires_at>now()
    and u.auth_user_id=auth.uid() and u.role='rider' and u.status='active' and r.status='active'
 )
$function$

revoke all on function public.qg_has_live_rider_offer(uuid) from public,anon;
grant execute on function public.qg_has_live_rider_offer(uuid) to authenticated;
drop policy if exists rider_orders_available_select on public.orders;
drop policy if exists rider_orders_offered_select on public.orders;
create policy rider_orders_offered_select on public.orders for select to authenticated using(get_my_role()='rider' and order_type='shopping' and status='searching_rider' and rider_id is null and (select public.qg_has_live_rider_offer(id)));
