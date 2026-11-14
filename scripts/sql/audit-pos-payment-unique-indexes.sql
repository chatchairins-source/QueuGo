-- Read-only inventory of uniqueness guarantees for POS payment/refund tables.
-- Signals inspect indexed key expressions, not index names (which can cause false positives).
-- A partial unique index or nullable key is not proof of payment idempotency.
-- No payment rows, amounts, tokens, or function bodies are returned.
WITH candidate_tables AS (
  SELECT c.oid AS table_oid, n.nspname AS schema_name, c.relname AS table_name
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public'
    AND c.relkind IN ('r', 'p')
    AND (c.relname ILIKE '%payment%' OR c.relname ILIKE '%refund%')
), unique_indexes AS (
  SELECT t.schema_name, t.table_name, i.indexrelid,
         i.indisvalid, i.indisprimary,
         i.indpred IS NOT NULL AS is_partial,
         EXISTS (
           SELECT 1
           FROM generate_series(1, i.indnkeyatts) AS key_pos(n)
           WHERE pg_get_indexdef(i.indexrelid, key_pos.n, true)
                 ~* '(^|[^a-z_])(idempotency|request_id|external_ref|provider_ref)([^a-z_]|$)'
         ) AS request_reference_key_signal
  FROM candidate_tables t
  JOIN pg_index i ON i.indrelid = t.table_oid
  WHERE i.indisunique
)
SELECT t.schema_name, t.table_name,
       count(u.indexrelid) AS unique_index_count,
       count(u.indexrelid) FILTER (WHERE u.indisvalid) AS valid_unique_index_count,
       count(u.indexrelid) FILTER (WHERE u.indisprimary) AS primary_key_index_count,
       count(u.indexrelid) FILTER (WHERE u.is_partial) AS partial_unique_index_count,
       coalesce(bool_or(u.request_reference_key_signal AND u.indisvalid AND NOT u.is_partial), false)
         AS unique_request_reference_signal
FROM candidate_tables t
LEFT JOIN unique_indexes u ON u.schema_name = t.schema_name AND u.table_name = t.table_name
GROUP BY t.schema_name, t.table_name
ORDER BY t.schema_name, t.table_name;
