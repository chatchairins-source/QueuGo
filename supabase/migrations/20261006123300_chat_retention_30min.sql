-- QueueGo: Rider/Customer chat remains available for 30 minutes after delivery completion.
-- After the window closes, Customer/Rider can no longer read or send in that order chat,
-- and expired Rider/Customer messages are purged by pg_cron once per minute.
BEGIN;

CREATE OR REPLACE FUNCTION public.qg_chat_closed_at(p_order_id uuid)
RETURNS timestamptz
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
  SELECT coalesce(
    (SELECT max(d.delivered_at) FROM public.deliveries d WHERE d.order_id=o.id),
    o.completed_at,
    CASE WHEN o.status='completed' THEN o.updated_at END
  )
  FROM public.orders o
  WHERE o.id=p_order_id
$function$;

CREATE OR REPLACE FUNCTION public.qg_chat_window_open(p_order_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
  SELECT coalesce(
    (
      SELECT o.status <> 'cancelled'
         AND (
           o.status <> 'completed'
           OR (
             public.qg_chat_closed_at(o.id) IS NOT NULL
             AND now() < public.qg_chat_closed_at(o.id) + interval '30 minutes'
           )
         )
      FROM public.orders o
      WHERE o.id=p_order_id
    ),
    false
  )
$function$;

CREATE OR REPLACE FUNCTION public.qg_chat_postjob_allowed(p_order_id uuid, p_sender_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_actor uuid := public.get_my_user_id();
  v_role text := public.get_my_role();
  v_order record;
BEGIN
  IF v_actor IS NULL OR p_sender_id IS DISTINCT FROM v_actor THEN
    RETURN false;
  END IF;

  SELECT o.id,o.customer_id,o.shop_id,o.technician_id,o.rider_id,o.status
  INTO v_order
  FROM public.orders o
  WHERE o.id=p_order_id;

  IF NOT FOUND THEN
    RETURN false;
  END IF;

  IF v_role='admin' THEN
    RETURN true;
  END IF;

  IF v_role='rider' THEN
    IF NOT EXISTS(
      SELECT 1
      FROM public.rider_profiles r
      WHERE r.id=v_order.rider_id
        AND r.user_id=v_actor
    ) THEN
      RETURN false;
    END IF;
    RETURN public.qg_chat_window_open(p_order_id);
  END IF;

  IF v_role='customer' AND v_order.customer_id=v_actor THEN
    RETURN public.qg_chat_window_open(p_order_id);
  END IF;

  -- Preserve the existing non Rider/Customer behavior.
  IF v_role='shop' AND EXISTS(
    SELECT 1 FROM public.shop_profiles s
    WHERE s.id=v_order.shop_id AND s.user_id=v_actor
  ) THEN
    RETURN v_order.status<>'cancelled';
  END IF;

  IF v_role='technician' AND EXISTS(
    SELECT 1 FROM public.technician_profiles t
    WHERE t.id=v_order.technician_id AND t.user_id=v_actor
  ) THEN
    RETURN v_order.status<>'cancelled';
  END IF;

  RETURN false;
END
$function$;

CREATE OR REPLACE FUNCTION public.qg_chat_read_allowed(p_order_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_actor uuid := public.get_my_user_id();
  v_role text := public.get_my_role();
  v_order record;
BEGIN
  IF v_actor IS NULL THEN
    RETURN false;
  END IF;

  SELECT o.id,o.customer_id,o.shop_id,o.technician_id,o.rider_id,o.status
  INTO v_order
  FROM public.orders o
  WHERE o.id=p_order_id;

  IF NOT FOUND THEN
    RETURN false;
  END IF;

  IF v_role='admin' THEN
    RETURN true;
  END IF;

  IF v_role='rider' AND EXISTS(
    SELECT 1 FROM public.rider_profiles r
    WHERE r.id=v_order.rider_id AND r.user_id=v_actor
  ) THEN
    RETURN public.qg_chat_window_open(p_order_id);
  END IF;

  IF v_role='customer' AND v_order.customer_id=v_actor THEN
    RETURN public.qg_chat_window_open(p_order_id);
  END IF;

  IF v_role='shop' AND EXISTS(
    SELECT 1 FROM public.shop_profiles s
    WHERE s.id=v_order.shop_id AND s.user_id=v_actor
  ) THEN
    RETURN v_order.status<>'cancelled';
  END IF;

  IF v_role='technician' AND EXISTS(
    SELECT 1 FROM public.technician_profiles t
    WHERE t.id=v_order.technician_id AND t.user_id=v_actor
  ) THEN
    RETURN v_order.status<>'cancelled';
  END IF;

  RETURN false;
END
$function$;

DROP POLICY IF EXISTS order_chat_messages_insert ON public.order_chat_messages;
DROP POLICY IF EXISTS qg_chat_postjob_restrict ON public.order_chat_messages;
CREATE POLICY order_chat_messages_insert
ON public.order_chat_messages
FOR INSERT
TO authenticated
WITH CHECK (
  sender_id=public.get_my_user_id()
  AND public.qg_chat_postjob_allowed(order_id,sender_id)
);

DROP POLICY IF EXISTS order_chat_messages_select ON public.order_chat_messages;
CREATE POLICY order_chat_messages_select
ON public.order_chat_messages
FOR SELECT
TO authenticated
USING (public.qg_chat_read_allowed(order_id));

CREATE INDEX IF NOT EXISTS order_chat_messages_order_created_idx
ON public.order_chat_messages(order_id,created_at);

CREATE OR REPLACE FUNCTION public.qg_delete_expired_rider_customer_chat()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','pg_temp'
AS $function$
DECLARE
  v_deleted integer := 0;
BEGIN
  DELETE FROM public.order_chat_messages m
  USING public.orders o
  LEFT JOIN public.rider_profiles r ON r.id=o.rider_id
  WHERE m.order_id=o.id
    AND o.status='completed'
    AND o.customer_id IS NOT NULL
    AND o.rider_id IS NOT NULL
    AND public.qg_chat_closed_at(o.id) IS NOT NULL
    AND now() >= public.qg_chat_closed_at(o.id) + interval '30 minutes'
    AND m.sender_id IN (o.customer_id,r.user_id);

  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RETURN v_deleted;
END
$function$;

REVOKE ALL ON FUNCTION public.qg_delete_expired_rider_customer_chat() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.qg_delete_expired_rider_customer_chat() FROM anon,authenticated;

DO $block$
DECLARE
  v_job bigint;
BEGIN
  FOR v_job IN
    SELECT jobid FROM cron.job WHERE jobname='queuego-chat-retention-30m'
  LOOP
    PERFORM cron.unschedule(v_job);
  END LOOP;

  PERFORM cron.schedule(
    'queuego-chat-retention-30m',
    '* * * * *',
    'SELECT public.qg_delete_expired_rider_customer_chat();'
  );
END
$block$;

-- Enforce the new retention rule for already-expired chats immediately.
SELECT public.qg_delete_expired_rider_customer_chat();

COMMIT;
