-- Disposable PostgreSQL ONLY. Tests the actual rider-search timestamp gate used by
-- ops/p0/dispatch-terminalization-preflight-readonly-20261120.sql.
-- psql -v ON_ERROR_STOP=1 -f tests/backend/dispatch-search-time-negative.sql
BEGIN;
CREATE TEMP TABLE cases (name text PRIMARY KEY, created_at timestamptz NOT NULL,
 merchant_accepted_at timestamptz, rider_search_started_at timestamptz,
 expected boolean NOT NULL) ON COMMIT DROP;
INSERT INTO cases VALUES
 ('valid', now()-interval '45 minutes',now()-interval '40 minutes',now()-interval '35 minutes',true),
 ('missing',now()-interval '45 minutes',now()-interval '40 minutes',NULL,false),
 ('before_created',now()-interval '30 minutes',now()-interval '40 minutes',now()-interval '35 minutes',false),
 ('before_merchant_accept',now()-interval '45 minutes',now()-interval '30 minutes',now()-interval '35 minutes',false),
 ('future',now()-interval '45 minutes',now()-interval '40 minutes',now()+interval '1 minute',false),
 ('not_overdue',now()-interval '45 minutes',now()-interval '40 minutes',now()-interval '29 minutes',false),
 ('no_merchant_accept',now()-interval '45 minutes',NULL,now()-interval '35 minutes',true);
DO $test$
DECLARE failures text;
BEGIN
 SELECT string_agg(name, ', ' ORDER BY name) INTO failures FROM (
  SELECT name, expected,
   (rider_search_started_at IS NOT NULL
    AND rider_search_started_at >= created_at
    AND (merchant_accepted_at IS NULL OR rider_search_started_at >= merchant_accepted_at)
    AND rider_search_started_at <= now()
    AND now() >= rider_search_started_at + interval '30 minutes') IS TRUE AS actual
  FROM cases
 ) t WHERE actual IS DISTINCT FROM expected;
 IF failures IS NOT NULL THEN RAISE EXCEPTION 'P0 timestamp gate failed: %', failures; END IF;
END $test$;
ROLLBACK;
