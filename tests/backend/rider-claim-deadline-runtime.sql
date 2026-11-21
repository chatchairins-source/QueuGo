\set ON_ERROR_STOP on
-- Executes on a disposable PostgreSQL database only. Not live trigger/RLS certification.
CREATE SCHEMA auth; CREATE SCHEMA qg_private;
CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$
  SELECT '00000000-0000-4000-8000-000000000901'::uuid
$$;
CREATE FUNCTION qg_private.ride_owner_authorized(uuid) RETURNS boolean LANGUAGE sql AS $$ SELECT true $$;
CREATE TABLE public.users(id uuid PRIMARY KEY,auth_user_id uuid,role text,status text);
CREATE TABLE public.rider_profiles(
 id uuid PRIMARY KEY,user_id uuid,status text,metadata jsonb,
 vehicle_type text,vehicle_status text,vehicle_verified_at timestamptz,
 vehicle_capacity_kg numeric
);
CREATE TABLE public.shop_profiles(id uuid PRIMARY KEY,user_id uuid);
CREATE TABLE public.orders(
 id uuid PRIMARY KEY,order_type text,fulfillment_vertical text,status text,
 rider_id uuid,created_at timestamptz,rider_search_started_at timestamptz,
 merchant_accepted_at timestamptz,rider_assigned_at timestamptz,market_order_id uuid,
 updated_at timestamptz,note text,customer_id uuid,shop_id uuid
);
CREATE TABLE public.system_settings(key text PRIMARY KEY,value jsonb);
CREATE TABLE public.deliveries(order_id uuid,rider_id uuid,status text,updated_at timestamptz);
CREATE TABLE public.notifications(user_id uuid,title text,message text,type text,reference_id uuid);
CREATE TABLE public.audit_logs(user_id uuid,action text,entity_type text,entity_id uuid,description text,metadata jsonb);
CREATE FUNCTION public.market_order_weight(uuid) RETURNS numeric LANGUAGE sql AS $$ SELECT 1::numeric $$;
INSERT INTO public.users VALUES('00000000-0000-4000-8000-000000000902','00000000-0000-4000-8000-000000000901','rider','active');
INSERT INTO public.rider_profiles VALUES(
 '00000000-0000-4000-8000-000000000903','00000000-0000-4000-8000-000000000902',
 'active','{"online":true,"available":true}'::jsonb,'motorcycle','active',now()-interval '1 day',50
);
INSERT INTO public.shop_profiles VALUES('00000000-0000-4000-8000-000000000904','00000000-0000-4000-8000-000000000902');
INSERT INTO public.system_settings VALUES('app','{"orderTimeout":"30"}'::jsonb);
\ir ../fixtures/rider-claims-production-20261121.sql
\ir ../../ops/p0/rider-claim-deadline-review-only.sql
-- 30-minute deadline: a 31-minute-old search cannot be claimed.
INSERT INTO public.orders(id,order_type,fulfillment_vertical,status,created_at,rider_search_started_at,customer_id,shop_id)
VALUES('00000000-0000-4000-8000-000000000001','shopping','food','searching_rider',
 now()-interval '40 minutes',now()-interval '31 minutes',
 '00000000-0000-4000-8000-000000000905','00000000-0000-4000-8000-000000000904');
DO $test$
BEGIN
 BEGIN
  PERFORM public.rider_claim_order('00000000-0000-4000-8000-000000000001');
  RAISE EXCEPTION 'expected expired claim rejection';
 EXCEPTION WHEN OTHERS THEN
  IF SQLERRM NOT LIKE '%rider search deadline elapsed%' THEN RAISE; END IF;
 END;
 IF EXISTS(SELECT 1 FROM public.orders WHERE id='00000000-0000-4000-8000-000000000001' AND status<>'searching_rider')
  THEN RAISE EXCEPTION 'expired order must not be assigned'; END IF;
END $test$;
-- Before the deadline: claim must still work, including existing notifications.
INSERT INTO public.orders(id,order_type,fulfillment_vertical,status,created_at,rider_search_started_at,customer_id,shop_id)
VALUES('00000000-0000-4000-8000-000000000002','shopping','food','searching_rider',
 now()-interval '3 minutes',now()-interval '2 minutes',
 '00000000-0000-4000-8000-000000000905','00000000-0000-4000-8000-000000000904');
INSERT INTO public.deliveries VALUES('00000000-0000-4000-8000-000000000002',NULL,'pending',NULL);
DO $test$
BEGIN
 IF public.rider_claim_order('00000000-0000-4000-8000-000000000002') <> 'rider_assigned'
 THEN RAISE EXCEPTION 'valid claim must succeed'; END IF;
 IF (SELECT status FROM public.deliveries WHERE order_id='00000000-0000-4000-8000-000000000002') <> 'assigned'
 THEN RAISE EXCEPTION 'delivery assignment missing'; END IF;
 IF (SELECT count(*) FROM public.notifications WHERE reference_id='00000000-0000-4000-8000-000000000002')<>2
 THEN RAISE EXCEPTION 'successful claim must notify customer and merchant'; END IF;
 IF public.rider_claim_order('00000000-0000-4000-8000-000000000002')<>'rider_assigned'
 THEN RAISE EXCEPTION 'claimed order idempotent retry must still work'; END IF;
END $test$;
UPDATE public.orders SET status='completed' WHERE id='00000000-0000-4000-8000-000000000002';
-- Admin switch: 15-minute deadline, no code recompile.
UPDATE public.system_settings SET value='{"orderTimeout":"15"}'::jsonb WHERE key='app';
INSERT INTO public.orders(id,order_type,fulfillment_vertical,status,created_at,rider_search_started_at)
VALUES('00000000-0000-4000-8000-000000000003','shopping','food','searching_rider',
 now()-interval '20 minutes',now()-interval '16 minutes');
DO $test$
BEGIN
 BEGIN
  PERFORM public.rider_claim_order('00000000-0000-4000-8000-000000000003');
  RAISE EXCEPTION 'expected 15-minute expiry';
 EXCEPTION WHEN OTHERS THEN
  IF SQLERRM NOT LIKE '%deadline elapsed%' THEN RAISE; END IF;
 END;
END $test$;
-- Future and missing clocks fail closed.
INSERT INTO public.orders(id,order_type,fulfillment_vertical,status,created_at,rider_search_started_at)
VALUES ('00000000-0000-4000-8000-000000000004','shopping','food','searching_rider',now()-interval '5 minutes',now()+interval '10 minutes'),
       ('00000000-0000-4000-8000-000000000005','shopping','food','searching_rider',now()-interval '5 minutes',NULL);
DO $test$
DECLARE oid uuid;
BEGIN
 FOREACH oid IN ARRAY ARRAY['00000000-0000-4000-8000-000000000004'::uuid,'00000000-0000-4000-8000-000000000005'::uuid] LOOP
   BEGIN
     PERFORM public.rider_claim_order(oid);
     RAISE EXCEPTION 'expected bad search clock';
   EXCEPTION WHEN OTHERS THEN
     IF SQLERRM NOT LIKE '%clock invalid%' THEN RAISE; END IF;
   END;
 END LOOP;
END $test$;
-- One expired group member must prevent the entire group from being claimed.
INSERT INTO public.orders(id,order_type,fulfillment_vertical,status,created_at,rider_search_started_at,market_order_id)
VALUES ('00000000-0000-4000-8000-000000000006','shopping','market','searching_rider',
 now()-interval '20 minutes',now()-interval '16 minutes','00000000-0000-4000-8000-000000000099'),
 ('00000000-0000-4000-8000-000000000007','shopping','market','searching_rider',
 now()-interval '5 minutes',now()-interval '4 minutes','00000000-0000-4000-8000-000000000099');
DO $test$
BEGIN
 BEGIN
   PERFORM public.market_rider_claim_group('00000000-0000-4000-8000-000000000007');
   RAISE EXCEPTION 'expected group deadline rejection';
 EXCEPTION WHEN OTHERS THEN
   IF SQLERRM NOT LIKE '%market rider search deadline%' THEN RAISE; END IF;
 END;
 IF EXISTS(SELECT 1 FROM public.orders WHERE market_order_id='00000000-0000-4000-8000-000000000099' AND rider_id IS NOT NULL)
 THEN RAISE EXCEPTION 'expired group must remain unassigned'; END IF;
END $test$;
-- Replace the old group member with a fresh search; claim succeeds.
UPDATE public.orders SET created_at=now()-interval '4 minutes',rider_search_started_at=now()-interval '3 minutes'
 WHERE id='00000000-0000-4000-8000-000000000006';
DO $test$
BEGIN
 IF public.market_rider_claim_group('00000000-0000-4000-8000-000000000007')
 <> '00000000-0000-4000-8000-000000000099'::uuid
 THEN RAISE EXCEPTION 'fresh market group should claim'; END IF;
 IF (SELECT count(*) FROM public.orders WHERE market_order_id='00000000-0000-4000-8000-000000000099' AND status='rider_assigned')<>2
 THEN RAISE EXCEPTION 'both group orders should be assigned'; END IF;
END $test$;
SELECT 'RIDER_CLAIM_DEADLINE_POSTGRES_30M_15M_GROUP_PASS' AS evidence;
