-- Record every material Rider order transition in the existing audit ledger.
-- No new public table/API surface: qg_private trigger writes server-time audit rows only.

create or replace function qg_private.qg_audit_rider_order_transition()
returns trigger
language plpgsql
security definer
set search_path = 'pg_catalog'
as $function$
declare
  v_user_id uuid;
  v_lat double precision;
  v_lng double precision;
  v_position_updated_at timestamptz;
  v_event text;
begin
  if new.rider_id is null then
    return new;
  end if;

  if old.rider_id is distinct from new.rider_id then
    v_event := 'rider_assigned';
  elsif old.rider_arrived_shop_at is null and new.rider_arrived_shop_at is not null then
    v_event := 'at_merchant';
  elsif old.rider_arrived_customer_at is null and new.rider_arrived_customer_at is not null then
    v_event := 'at_customer';
  elsif old.status is distinct from new.status then
    v_event := case lower(coalesce(new.status,''))
      when 'rider_assigned' then 'to_merchant'
      when 'assigned' then 'to_merchant'
      when 'preparing' then 'at_merchant'
      when 'ready' then 'at_merchant'
      when 'picked_up' then 'picked_up'
      when 'in_progress' then 'to_customer'
      when 'completed' then 'completed'
      when 'cancelled' then 'cancelled'
      when 'no_rider_available' then 'no_rider_available'
      else 'status_changed'
    end;
  else
    return new;
  end if;

  select rp.user_id, rp.latitude, rp.longitude, rp.updated_at
    into v_user_id, v_lat, v_lng, v_position_updated_at
  from public.rider_profiles rp
  where rp.id = new.rider_id;

  if v_user_id is null then
    return new;
  end if;

  insert into public.audit_logs(
    user_id,
    action,
    entity_type,
    entity_id,
    description,
    metadata
  )
  values(
    v_user_id,
    'rider_state_transition',
    'order',
    new.id,
    v_event,
    jsonb_strip_nulls(jsonb_build_object(
      'event', v_event,
      'from_status', old.status,
      'to_status', new.status,
      'rider_id', new.rider_id,
      'gps_lat', v_lat,
      'gps_lng', v_lng,
      'position_record_updated_at', v_position_updated_at,
      'rider_changed', old.rider_id is distinct from new.rider_id,
      'arrived_shop_at', new.rider_arrived_shop_at,
      'arrived_customer_at', new.rider_arrived_customer_at
    ))
  );

  return new;
end
$function$;

revoke all on function qg_private.qg_audit_rider_order_transition() from public, anon, authenticated;

drop trigger if exists qg_audit_rider_order_transition_trg on public.orders;
create trigger qg_audit_rider_order_transition_trg
after update of rider_id, status, rider_arrived_shop_at, rider_arrived_customer_at
on public.orders
for each row
execute function qg_private.qg_audit_rider_order_transition();
