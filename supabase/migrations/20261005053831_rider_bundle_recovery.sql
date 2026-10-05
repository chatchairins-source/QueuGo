BEGIN;
CREATE OR REPLACE FUNCTION public.qg_rider_action_once(p_request_id uuid,p_kind text,p_payload jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,pg_temp AS $$
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
 WHEN 'bundle_claim' THEN SELECT to_jsonb(public.queuego_claim_route_bundle((p_payload->>'p_order_id')::uuid)) INTO v_result;
 WHEN 'claim' THEN SELECT to_jsonb(public.rider_claim_order((p_payload->>'p_order_id')::uuid)) INTO v_result;
 WHEN 'market_claim' THEN SELECT to_jsonb(public.market_rider_claim_group((p_payload->>'p_order_id')::uuid)) INTO v_result;
 WHEN 'order' THEN SELECT to_jsonb(public.rider_order_action((p_payload->>'p_order_id')::uuid,p_payload->>'p_action')) INTO v_result;
 WHEN 'market' THEN SELECT to_jsonb(public.market_rider_group_action((p_payload->>'p_market_order_id')::uuid,p_payload->>'p_action')) INTO v_result;
 WHEN 'market_pickup' THEN SELECT to_jsonb(public.market_rider_confirm_pickup_cash((p_payload->>'p_pickup_id')::uuid,(p_payload->>'p_amount')::numeric)) INTO v_result;
 ELSE RAISE EXCEPTION 'unsupported rider action'; END CASE;
 INSERT INTO public.qg_rider_action_receipts(request_id,user_id,kind,payload,result) VALUES(p_request_id,v_user,p_kind,p_payload,coalesce(v_result,'null'::jsonb));
 RETURN v_result;
END $$;
COMMIT;
