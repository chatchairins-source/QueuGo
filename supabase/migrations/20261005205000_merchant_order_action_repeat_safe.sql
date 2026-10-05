-- Keep stale/cached merchant clients repeat-safe while the new client uses qg_merchant_action_once.
create or replace function public.merchant_order_action(
  p_order_id uuid,
  p_action text,
  p_reason text default null
)
returns text
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
  v_user uuid;
  v_status text;
  v_action text:=lower(trim(coalesce(p_action,'')));
begin
  select id into v_user
  from public.users
  where auth_user_id=auth.uid() and role='shop' and status='active';

  if v_user is null then raise exception 'shop login required'; end if;

  select o.status into v_status
  from public.orders o
  join public.shop_profiles p on p.id=o.shop_id
  where o.id=p_order_id and p.user_id=v_user
  for update of o;

  if not found then raise exception 'order unavailable'; end if;

  if v_action='accepted' and v_status in
    ('searching_rider','rider_assigned','preparing','ready','picked_up','in_progress','completed') then
    return v_status;
  elsif v_action='preparing' and v_status in
    ('preparing','ready','picked_up','in_progress','completed') then
    return v_status;
  elsif v_action='ready' and v_status in
    ('ready','picked_up','in_progress','completed') then
    return v_status;
  elsif v_action='cancel' and v_status='cancelled' then
    return v_status;
  end if;

  return public.queuego_shop_delivery_transition(p_order_id,v_action,p_reason,v_user);
end
$function$;

revoke all on function public.merchant_order_action(uuid,text,text) from anon;
grant execute on function public.merchant_order_action(uuid,text,text) to authenticated;
