-- Captured READ ONLY from Supabase Production 2026-11-21:
-- pg_get_functiondef MD5 e62b8c1fd2193ea386aedcee96cfb6dd
-- Isolated PostgreSQL regression fixture ONLY. Not a migration and NOT deployed.
CREATE OR REPLACE FUNCTION public.qg_v22_order_transition_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
 if new.rider_arrived_shop_at is distinct from old.rider_arrived_shop_at
 and public.get_my_role() is distinct from 'admin' then
  if old.rider_arrived_shop_at is not null
   or current_setting('queuego.v22_transition',true) is distinct from 'rpc'
   or new.rider_id is distinct from old.rider_id
   or not exists(select 1 from public.rider_profiles r join public.users u on u.id=r.user_id
      where r.id=old.rider_id and u.auth_user_id=auth.uid() and u.role='rider' and u.status='active')
  then raise exception 'shop arrival requires assigned rider RPC'; end if;
 end if;
 if exists(select 1 from public.queuego_cash_order_locks where order_id=old.id)
 and public.get_my_role()<>'admin'
 and (new.status is distinct from old.status or new.rider_id is distinct from old.rider_id)
 and current_setting('queuego.v22_transition',true) is distinct from 'rpc'
 then raise exception 'order transition requires server RPC'; end if;
 return new;
end $function$;
