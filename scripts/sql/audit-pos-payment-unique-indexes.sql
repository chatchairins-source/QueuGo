-- Read-only inventory of uniqueness guarantees for POS payment/refund tables.
-- Review evidence only: unique indexes do not prove end-to-end idempotency.
-- No customer rows, payment amounts, tokens, or function bodies are returned.
WITH candidate_tables AS (
  SELECT c.oid AS table_oid, n.nspname AS schema_name, c.relname AS table_name
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public'
    AND c.relkind IN ('r', 'p')
    AND (c.relname ILIKE '%payment%' OR c.relname ILIKE '%refund%')
), unique_indexes AS (
  SELECT t.schema_name, t.table_name, i.indexrelid, ic.relname AS index_name,
         i.indisunique, i.indisvalid, i.indisprimary,
         pg_get_indexdef(i.indexrelid) AS index_definition
  FROM candidate_tables t
  JOIN pg_index i ON i.indrelid = t.table_oid
  JOIN pg_class ic ON ic.oid = i.indexrelid
  WHERE i.indisunique
)
SELECT t.schema_name, t.table_name,
       count(u.indexrelid) AS unique_index_count,
       count(u.indexrelid) FILTER (WHERE u.indisvalid) AS valid_unique_index_count,
       count(u.indexrelid) FILTER (WHERE u.indisprimary) AS primary_key_index_count,
       coalesce(bool_or(u.index_definition ~* '(idempotency|request_id|external_ref|provider_ref)'), false)
         AS unique_request_reference_signal
FROM candidate_tables t
LEFT JOIN unique_indexes u ON u.schema_name = t.schema_name AND u.table_name = t.table_name
GROUP BY t.schema_name, t.table_name
ORDER BY t.schema_name, t.table_name;
