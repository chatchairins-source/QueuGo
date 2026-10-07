create or replace function public.qg_lease_rider_push()
returns setof public.qg_rider_push_outbox
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
begin
  raise exception 'LEGACY_PUSH_RETIRED_USE_QUEUEGO_PUSH' using errcode='P0001';
  return;
end
$function$;

create or replace function public.qg_finish_rider_push(p_id uuid, p_lease uuid, p_http integer)
returns void
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
begin
  raise exception 'LEGACY_PUSH_RETIRED_USE_QUEUEGO_PUSH' using errcode='P0001';
end
$function$;

revoke all on function public.qg_lease_rider_push() from public, anon, authenticated, service_role;
revoke all on function public.qg_finish_rider_push(uuid,uuid,integer) from public, anon, authenticated, service_role;
