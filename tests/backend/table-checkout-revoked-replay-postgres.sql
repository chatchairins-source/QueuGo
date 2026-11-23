\set ON_ERROR_STOP on
BEGIN;
CREATE SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;
CREATE TABLE public.qg_table_sessions (
 id uuid PRIMARY KEY, device_hash text NOT NULL, revoked_at timestamptz,
 expires_at timestamptz NOT NULL, table_id uuid, shop_id uuid
);
CREATE TABLE public.pos_tables (id uuid, shop_id uuid, active boolean, label text);
CREATE TABLE public.shop_profiles (id uuid, status text, latitude float8, longitude float8);
CREATE TABLE public.qg_table_requests (
 session_id uuid, request_id uuid, payload_hash text, order_id uuid, created_at timestamptz DEFAULT now()
);
CREATE TABLE public.products (id uuid, shop_id uuid, available boolean, pos_available boolean, name text, pos_price numeric, price numeric);
CREATE TABLE public.orders (id uuid, order_number text);
CREATE ROLE qg_checkout_test_anon NOLOGIN;
GRANT USAGE ON SCHEMA public TO qg_checkout_test_anon;
-- Explicit EXECUTE exposure reproduces the security boundary; table SELECT is intentionally absent.
\i ops/security/qg-table-checkout-revoked-replay-review-20261123.sql
GRANT EXECUTE ON FUNCTION public.qg_table_checkout(uuid,uuid,uuid,jsonb,float8,float8) TO qg_checkout_test_anon;
INSERT INTO public.qg_table_sessions(id,device_hash,revoked_at,expires_at)
VALUES
('00000000-0000-0000-0000-000000000001',encode(extensions.digest('00000000-0000-0000-0000-000000000010','sha256'),'hex'),now()-interval '1 minute',now()+interval '1 day'),
('00000000-0000-0000-0000-000000000002',encode(extensions.digest('00000000-0000-0000-0000-000000000010','sha256'),'hex'),NULL,now()-interval '1 minute');
INSERT INTO public.qg_table_requests(session_id,request_id,payload_hash,order_id)
VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000020','irrelevant','00000000-0000-0000-0000-000000000030');
-- Positive control: active sessions must still replay the same request exactly once.
INSERT INTO public.qg_table_sessions(id,device_hash,revoked_at,expires_at)
VALUES ('00000000-0000-0000-0000-000000000003',
 encode(extensions.digest('00000000-0000-0000-0000-000000000010','sha256'),'hex'),
 NULL,now()+interval '1 day');
INSERT INTO public.orders(id,order_number)
VALUES ('00000000-0000-0000-0000-000000000031','QG-TEST-001');
INSERT INTO public.qg_table_requests(session_id,request_id,payload_hash,order_id)
VALUES ('00000000-0000-0000-0000-000000000003',
 '00000000-0000-0000-0000-000000000021',
 encode(extensions.digest('[{"id": "00000000-0000-0000-0000-000000000040", "quantity": 1}]'::jsonb::text,'sha256'),'hex'),
 '00000000-0000-0000-0000-000000000031');
SET ROLE qg_checkout_test_anon;
DO $privileges$
BEGIN
 IF has_table_privilege(current_user,'public.qg_table_sessions','SELECT')
 OR has_table_privilege(current_user,'public.qg_table_requests','SELECT')
 OR has_table_privilege(current_user,'public.orders','SELECT')
 OR NOT has_function_privilege(current_user,
   'public.qg_table_checkout(uuid,uuid,uuid,jsonb,double precision,double precision)','EXECUTE')
 THEN RAISE EXCEPTION 'SECURITY FAILURE: unexpected anon privileges'; END IF;
END $privileges$;
DO $positive$
DECLARE result jsonb;
BEGIN
 result := public.qg_table_checkout(
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000010',
  '00000000-0000-0000-0000-000000000021',
  '[{"id":"00000000-0000-0000-0000-000000000040","quantity":1}]'::jsonb,13.0,100.0);
 IF result->>'order_id' <> '00000000-0000-0000-0000-000000000031'
 OR result->>'duplicate' <> 'true'
 OR result->>'reason' <> 'same_request'
 THEN RAISE EXCEPTION 'Active idempotent replay failed: %', result; END IF;
END $positive$;
DO $test$
DECLARE sid uuid; msg text; rejected integer := 0;
BEGIN
 FOR sid IN SELECT unnest(ARRAY[
 '00000000-0000-0000-0000-000000000001'::uuid,
 '00000000-0000-0000-0000-000000000002'::uuid]) LOOP
  BEGIN
   PERFORM public.qg_table_checkout(sid,
    '00000000-0000-0000-0000-000000000010'::uuid,
    '00000000-0000-0000-0000-000000000020'::uuid,
    '[{"id":"00000000-0000-0000-0000-000000000040","quantity":1}]'::jsonb,13.0,100.0);
   RAISE EXCEPTION 'SECURITY FAILURE: revoked/expired session accepted';
  EXCEPTION WHEN OTHERS THEN
   GET STACKED DIAGNOSTICS msg = MESSAGE_TEXT;
   IF msg <> 'สิทธิ์การสั่งอาหารจากโต๊ะนี้หมดอายุแล้ว กรุณาสแกน QR Code ที่โต๊ะอีกครั้ง' THEN
    RAISE EXCEPTION 'Wrong failure for %: %', sid, msg;
   END IF;
   rejected := rejected+1;
  END;
 END LOOP;
 IF rejected <> 2 THEN RAISE EXCEPTION 'Expected two rejections'; END IF;
END $test$;
RESET ROLE;
DO $test$
BEGIN
 IF (SELECT count(*) FROM public.qg_table_requests) <> 2 THEN
  RAISE EXCEPTION 'Replay altered request rows';
 END IF;
 IF (SELECT count(*) FROM public.orders) <> 1 THEN
  RAISE EXCEPTION 'Replay created order';
 END IF;
END $test$;
ROLLBACK;
\echo PASS: isolated PostgreSQL revoked/expired rejection, active idempotent replay, privilege boundary, no mutation
