-- Read-only. Export independent hashes of stored Production SQL, never SQL bodies.
-- Exactly one non-null SQL body per history version is REQUIRED; otherwise stop.
SELECT version, name,
       encode(sha256(convert_to(statements[1], 'UTF8')), 'hex') AS sha256,
       octet_length(statements[1]) AS sql_bytes,
       cardinality(statements) AS statement_count,
       now() AS checked_at
FROM supabase_migrations.schema_migrations
ORDER BY version;
