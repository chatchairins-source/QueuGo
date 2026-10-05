BEGIN;
-- Action receipts supplement existing order/audit transactions; no order states are added.
CREATE TABLE public.qg_rider_action_receipts(request_id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES public.users(id),kind text NOT NULL,payload jsonb NOT NULL,result jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX qg_rider_receipt_owner ON public.qg_rider_action_receipts(user_id);
ALTER TABLE public.qg_rider_action_receipts ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON public.qg_rider_action_receipts TO authenticated;
CREATE POLICY rider_receipt_owner ON public.qg_rider_action_receipts FOR SELECT TO authenticated USING(user_id=public.get_my_user_id());
CREATE FUNCTION public.qg_rider_action_once(p_request_id uuid,p_kind text,p_payload jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE v_user uuid;v_existing public.qg_rider_action_receipts%rowtype;v_result jsonb;
BEGIN
 SELECT u.id INTO v_user FROM public.users u JOIN public.rider_profiles r ON r.user_id=u.id WHERE u.auth_user_id=auth.uid() AND u.role='rider' AND u.status='active';
 IF v_user IS NULL OR p_request_id IS NULL OR p_payload IS NULL OR jsonb_typeof(p_payload)<>'object' THEN RAISE EXCEPTION 'active rider request required'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(p_request_id::text,0));
 SELECT * INTO v_existing FROM public.qg_rider_action_receipts WHERE request_id=p_request_id;
 IF FOUND THEN
  IF v_existing.user_id<>v_user OR v_existing.kind<>p_kind OR v_existing.payload<>p_payload THEN RAISE EXCEPTION 'request id already in use';END IF;
  RETURN v_existing.result;
 END IF;
 CASE p_kind
 WHEN 'claim' THEN SELECT to_jsonb(public.rider_claim_order((p_payload->>'p_order_id')::uuid)) INTO v_result;
 WHEN 'market_claim' THEN SELECT to_jsonb(public.market_rider_claim_group((p_payload->>'p_order_id')::uuid)) INTO v_result;
 WHEN 'order' THEN SELECT to_jsonb(public.rider_order_action((p_payload->>'p_order_id')::uuid,p_payload->>'p_action')) INTO v_result;
 WHEN 'market' THEN SELECT to_jsonb(public.market_rider_group_action((p_payload->>'p_market_order_id')::uuid,p_payload->>'p_action')) INTO v_result;
 WHEN 'market_pickup' THEN SELECT to_jsonb(public.market_rider_confirm_pickup_cash((p_payload->>'p_pickup_id')::uuid,(p_payload->>'p_amount')::numeric)) INTO v_result;
 ELSE RAISE EXCEPTION 'unsupported rider action'; END CASE;
 INSERT INTO public.qg_rider_action_receipts(request_id,user_id,kind,payload,result) VALUES(p_request_id,v_user,p_kind,p_payload,coalesce(v_result,'null'::jsonb));
 RETURN v_result;
END $$;
REVOKE ALL ON FUNCTION public.qg_rider_action_once(uuid,text,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.qg_rider_action_once(uuid,text,jsonb) TO authenticated;

-- Secrets are server-only, protected by RLS and grants. No browser may read them.
CREATE TABLE public.qg_rider_push_config(id boolean PRIMARY KEY DEFAULT true CHECK(id),public_key text NOT NULL,private_key text NOT NULL,worker_token text NOT NULL DEFAULT encode(extensions.gen_random_bytes(32),'hex'));
CREATE TABLE public.qg_rider_push_subscriptions(id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES public.users(id),endpoint text UNIQUE NOT NULL,p256dh text NOT NULL,auth_key text NOT NULL,enabled boolean NOT NULL DEFAULT true,updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX qg_rider_push_owner ON public.qg_rider_push_subscriptions(user_id) WHERE enabled;
CREATE TABLE public.qg_rider_push_outbox(id uuid PRIMARY KEY DEFAULT gen_random_uuid(),notification_id uuid NOT NULL REFERENCES public.notifications(id) ON DELETE CASCADE,subscription_id uuid NOT NULL REFERENCES public.qg_rider_push_subscriptions(id) ON DELETE CASCADE,status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','leased','sent','dead')),attempts integer NOT NULL DEFAULT 0,retry_at timestamptz NOT NULL DEFAULT now(),lease_id uuid,last_http_status integer,created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(notification_id,subscription_id));
CREATE INDEX qg_rider_push_due ON public.qg_rider_push_outbox(retry_at) WHERE status IN ('pending','leased');
CREATE INDEX qg_rider_push_subscription_fk ON public.qg_rider_push_outbox(subscription_id);
ALTER TABLE public.qg_rider_push_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.qg_rider_push_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.qg_rider_push_outbox ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.qg_rider_push_config,public.qg_rider_push_subscriptions,public.qg_rider_push_outbox FROM PUBLIC,anon,authenticated;
GRANT ALL ON public.qg_rider_push_config,public.qg_rider_push_subscriptions,public.qg_rider_push_outbox TO service_role;
GRANT ALL ON public.qg_rider_action_receipts TO service_role;

CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE FUNCTION public.qg_wake_rider_push() RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE v_token text;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.qg_rider_push_outbox WHERE status IN ('pending','leased') AND retry_at<=now()) THEN RETURN; END IF;
 SELECT worker_token INTO v_token FROM public.qg_rider_push_config WHERE id;
 IF v_token IS NULL THEN RETURN;END IF;
 PERFORM net.http_post(url:='https://pkypiqhlrmzocysgeqew.supabase.co/functions/v1/rider-web-push',headers:=jsonb_build_object('Content-Type','application/json','x-queuego-worker',v_token),body:='{"action":"dispatch"}'::jsonb,timeout_milliseconds:=5000);
END $$;
REVOKE ALL ON FUNCTION public.qg_wake_rider_push() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.qg_wake_rider_push() TO service_role;
CREATE FUNCTION public.qg_enqueue_rider_push() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
BEGIN
 INSERT INTO public.qg_rider_push_outbox(notification_id,subscription_id)
 SELECT NEW.id,s.id FROM public.qg_rider_push_subscriptions s JOIN public.users u ON u.id=s.user_id WHERE s.user_id=NEW.user_id AND s.enabled AND u.role='rider' AND u.status='active' ON CONFLICT DO NOTHING;
 BEGIN PERFORM public.qg_wake_rider_push();EXCEPTION WHEN OTHERS THEN RAISE WARNING 'QueueGo push dispatch deferred';END;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.qg_enqueue_rider_push() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER qg_notifications_rider_push AFTER INSERT ON public.notifications FOR EACH ROW EXECUTE FUNCTION public.qg_enqueue_rider_push();

CREATE FUNCTION public.qg_lease_rider_push() RETURNS SETOF public.qg_rider_push_outbox LANGUAGE sql SECURITY DEFINER SET search_path=public,pg_temp AS $$
 UPDATE public.qg_rider_push_outbox SET status='leased',attempts=attempts+1,retry_at=now()+interval '2 minutes',lease_id=gen_random_uuid()
 WHERE id IN(SELECT id FROM public.qg_rider_push_outbox WHERE status IN ('pending','leased') AND retry_at<=now() ORDER BY created_at LIMIT 25 FOR UPDATE SKIP LOCKED) RETURNING *;
$$;
CREATE FUNCTION public.qg_finish_rider_push(p_id uuid,p_lease uuid,p_http integer) RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE v_sub uuid;
BEGIN
 UPDATE public.qg_rider_push_outbox SET status=CASE WHEN p_http BETWEEN 200 AND 299 THEN 'sent' WHEN p_http IN(404,410) OR attempts>=6 THEN 'dead' ELSE 'pending' END,last_http_status=p_http,retry_at=now()+make_interval(secs=>least(3600,30*power(2,attempts)::int)) WHERE id=p_id AND status='leased' AND lease_id=p_lease RETURNING subscription_id INTO v_sub;
 IF p_http IN(404,410) AND v_sub IS NOT NULL THEN UPDATE public.qg_rider_push_subscriptions SET enabled=false WHERE id=v_sub;END IF;
END $$;
REVOKE ALL ON FUNCTION public.qg_lease_rider_push(),public.qg_finish_rider_push(uuid,uuid,integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.qg_lease_rider_push(),public.qg_finish_rider_push(uuid,uuid,integer) TO service_role;
SELECT cron.schedule('queuego-rider-push-retry','* * * * *','SELECT public.qg_wake_rider_push();');

-- Customer/shop chat messages feed the same notifications -> push pipeline.
CREATE FUNCTION public.qg_notify_rider_chat() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
DECLARE v_rider_user uuid;
BEGIN
 SELECT r.user_id INTO v_rider_user FROM public.orders o JOIN public.rider_profiles r ON r.id=o.rider_id WHERE o.id=NEW.order_id AND o.status<>'cancelled';
 IF v_rider_user IS NOT NULL AND NEW.sender_id<>v_rider_user THEN
 INSERT INTO public.notifications(user_id,title,message,type,reference_id) VALUES(v_rider_user,'ข้อความใหม่ในออเดอร์',CASE WHEN NEW.message LIKE '__IMG__%' THEN 'มีรูปภาพใหม่ แตะเพื่อเปิดแชต' ELSE left(NEW.message,180) END,'order',NEW.order_id);
 END IF;RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION public.qg_notify_rider_chat() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER qg_chat_rider_notification AFTER INSERT ON public.order_chat_messages FOR EACH ROW EXECUTE FUNCTION public.qg_notify_rider_chat();
COMMIT;
