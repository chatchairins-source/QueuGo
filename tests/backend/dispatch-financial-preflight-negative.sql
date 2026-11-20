-- P0 #1268 financial preflight regression; execute ONLY in disposable PostgreSQL.
-- psql -v ON_ERROR_STOP=1 -f tests/backend/dispatch-financial-preflight-negative.sql
BEGIN;
CREATE TEMP TABLE p0_preflight_cases (
  case_name text PRIMARY KEY, payment_status text, payment_method text,
  paid_at timestamptz, rider_assigned_at timestamptz, cancelled_at timestamptz,
  cash_lock boolean NOT NULL DEFAULT false, payment_ledger boolean NOT NULL DEFAULT false,
  merchant_receipt boolean NOT NULL DEFAULT false, rider_advance boolean NOT NULL DEFAULT false,
  rider_refund boolean NOT NULL DEFAULT false, grocery_reservation boolean NOT NULL DEFAULT false,
  pos_reservation boolean NOT NULL DEFAULT false, offer_row boolean NOT NULL DEFAULT false,
  market_group boolean NOT NULL DEFAULT false, active_siblings integer NOT NULL DEFAULT 0,
  search_start timestamptz, expected boolean NOT NULL
) ON COMMIT DROP;
INSERT INTO p0_preflight_cases(case_name,payment_status,payment_method,search_start,expected) VALUES
 ('clean_unpaid','unpaid','cash',now()-interval '31 minutes',true),
 ('unknown_status',NULL,'cash',now()-interval '31 minutes',false),
 ('unknown_method','unpaid',NULL,now()-interval '31 minutes',false),
 ('blank_method','unpaid','   ',now()-interval '31 minutes',false),
 ('not_expired','unpaid','cash',now()-interval '29 minutes',false),
 ('future_start','unpaid','cash',now()+interval '1 minute',false),
 ('missing_start','unpaid','cash',NULL,false);
INSERT INTO p0_preflight_cases(case_name,payment_status,payment_method,search_start,expected,cash_lock,payment_ledger,merchant_receipt,rider_advance,rider_refund,grocery_reservation,pos_reservation,offer_row,market_group,active_siblings) VALUES
 ('cash_lock','unpaid','cash',now()-interval '31 minutes',false,true,false,false,false,false,false,false,false,false,0),
 ('payment_ledger','unpaid','cash',now()-interval '31 minutes',false,false,true,false,false,false,false,false,false,false,0),
 ('merchant_receipt','unpaid','cash',now()-interval '31 minutes',false,false,false,true,false,false,false,false,false,false,0),
 ('rider_advance','unpaid','cash',now()-interval '31 minutes',false,false,false,false,true,false,false,false,false,false,0),
 ('rider_refund','unpaid','cash',now()-interval '31 minutes',false,false,false,false,false,true,false,false,false,false,0),
 ('grocery_stock','unpaid','cash',now()-interval '31 minutes',false,false,false,false,false,false,true,false,false,false,0),
 ('pos_stock','unpaid','cash',now()-interval '31 minutes',false,false,false,false,false,false,false,true,false,false,0),
 ('existing_offer','unpaid','cash',now()-interval '31 minutes',false,false,false,false,false,false,false,false,true,false,0),
 ('market_group','unpaid','cash',now()-interval '31 minutes',false,false,false,false,false,false,false,false,false,true,0),
 ('active_sibling','unpaid','cash',now()-interval '31 minutes',false,false,false,false,false,false,false,false,false,false,1);
INSERT INTO p0_preflight_cases(case_name,payment_status,payment_method,search_start,expected) VALUES
 ('paid_at','unpaid','cash',now()-interval '31 minutes',false);
UPDATE p0_preflight_cases SET paid_at=now() WHERE case_name='paid_at';
INSERT INTO p0_preflight_cases(case_name,payment_status,payment_method,search_start,expected) VALUES
 ('previously_assigned','unpaid','cash',now()-interval '31 minutes',false),
 ('contradictory_cancelled','unpaid','cash',now()-interval '31 minutes',false);
UPDATE p0_preflight_cases SET rider_assigned_at=now() WHERE case_name='previously_assigned';
UPDATE p0_preflight_cases SET cancelled_at=now() WHERE case_name='contradictory_cancelled';
-- Additional clean control ensures positive cases are not all blocked.
INSERT INTO p0_preflight_cases(case_name,payment_status,payment_method,search_start,expected) VALUES
 ('clean_control','unpaid','cash',now()-interval '31 minutes',true);
DO $assert$
DECLARE failures text;
BEGIN
 SELECT string_agg(case_name, ', ' ORDER BY case_name) INTO failures
 FROM (
 SELECT case_name, expected,
  (search_start < now()-interval '30 minutes' AND search_start IS NOT NULL AND search_start <= now()
   AND payment_status='unpaid' AND payment_method IS NOT NULL AND btrim(payment_method)<>''
   AND paid_at IS NULL AND rider_assigned_at IS NULL AND cancelled_at IS NULL
   AND NOT cash_lock AND NOT payment_ledger AND NOT merchant_receipt AND NOT rider_advance
   AND NOT rider_refund AND NOT grocery_reservation AND NOT pos_reservation AND NOT offer_row
   AND NOT market_group AND active_siblings=0) IS TRUE AS actual
 FROM p0_preflight_cases
 ) checks WHERE actual IS DISTINCT FROM expected;
 IF failures IS NOT NULL THEN RAISE EXCEPTION 'P0 financial fail-closed regression failed: %', failures; END IF;
END $assert$;
ROLLBACK;
