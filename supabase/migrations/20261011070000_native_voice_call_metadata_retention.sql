-- QueueGo native voice-call metadata retention.
-- Audio, SDP and ICE candidates are never stored. Keep only call/session/order
-- metadata for a bounded support/security window, then purge automatically.

CREATE INDEX IF NOT EXISTS qg_call_sessions_created_idx
  ON public.qg_call_sessions(created_at);

CREATE INDEX IF NOT EXISTS notifications_voice_call_retention_idx
  ON public.notifications(created_at)
  WHERE type='voice_call';

CREATE OR REPLACE FUNCTION qg_private.qg_purge_call_sessions()
RETURNS bigint
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path=''
AS $function$
DECLARE
  v_deleted bigint;
  v_notification_deleted bigint;
BEGIN
  DELETE FROM public.qg_call_sessions
  WHERE created_at < now() - interval '30 days';

  GET DIAGNOSTICS v_deleted = ROW_COUNT;

  DELETE FROM public.notifications
  WHERE type='voice_call'
    AND created_at < now() - interval '30 days';

  GET DIAGNOSTICS v_notification_deleted = ROW_COUNT;
  RETURN v_deleted + v_notification_deleted;
END
$function$;

REVOKE ALL ON FUNCTION qg_private.qg_purge_call_sessions() FROM PUBLIC;
REVOKE ALL ON FUNCTION qg_private.qg_purge_call_sessions() FROM anon;
REVOKE ALL ON FUNCTION qg_private.qg_purge_call_sessions() FROM authenticated;
GRANT EXECUTE ON FUNCTION qg_private.qg_purge_call_sessions() TO service_role;

DO $
DECLARE
  v_jobid bigint;
BEGIN
  FOR v_jobid IN
    SELECT jobid FROM cron.job WHERE jobname='queuego-voice-call-retention'
  LOOP
    PERFORM cron.unschedule(v_jobid);
  END LOOP;

  PERFORM cron.schedule(
    'queuego-voice-call-retention',
    '17 3 * * *',
    'select qg_private.qg_purge_call_sessions();'
  );
END
$;

-- Apply the retention cutoff immediately on deployment as well as daily.
SELECT qg_private.qg_purge_call_sessions();
