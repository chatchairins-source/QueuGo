-- QueueGo unified push retry scheduler alignment.
-- Production migration: 20261007034201_switch_push_retry_cron_to_unified_worker

create or replace function public.qg_wake_rider_push()
returns void
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
begin
  perform public.qg_wake_push();
end
$function$;

revoke all on function public.qg_wake_rider_push() from public, anon, authenticated;
grant execute on function public.qg_wake_rider_push() to service_role;

do $$
declare
  v_jobid bigint;
begin
  for v_jobid in
    select jobid
    from cron.job
    where jobname in ('queuego-rider-push-retry','queuego-push-retry')
  loop
    perform cron.unschedule(v_jobid);
  end loop;

  perform cron.schedule(
    'queuego-push-retry',
    '* * * * *',
    'SELECT public.qg_wake_push();'
  );
end
$$;
