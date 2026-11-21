-- REVIEW ONLY: null-safe cash transition role comparison in the *actual*
-- Production qg_v22_order_transition_guard() signature and body.
-- Sessionless/NULL role must not satisfy admin exemption. The existing independent
-- queuego_guard_cash_order trigger already denied the bypass in full Production
-- composition; this is essential defense-in-depth before deployment.
-- No Production writes, no migration or Cron scheduling.
BEGIN;
DO $preflight$
BEGIN
 IF md5(pg_get_functiondef('public.qg_v22_order_transition_guard()'::regprocedure)) <> 'e62b8c1fd2193ea386aedcee96cfb6dd' THEN
   RAISE EXCEPTION 'qg_v22 order transition guard has changed; stop manual review';
 END IF;
END $preflight$;
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
 and public.get_my_role() IS DISTINCT FROM 'admin'
 and (new.status is distinct from old.status or new.rider_id is distinct from old.rider_id)
 and current_setting('queuego.v22_transition',true) is distinct from 'rpc'
 then raise exception 'order transition requires server RPC'; end if;
 return new;
end $function$;
COMMIT;
