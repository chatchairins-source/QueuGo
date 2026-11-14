-- Read-only SECURITY DEFINER search_path trust-boundary audit.
-- A pinned search_path is not sufficient if a referenced schema is writable
-- by untrusted roles. No function bodies or application rows are returned.
WITH definer AS (
  SELECT p.oid, p.proname, pg_get_function_identity_arguments(p.oid) AS args,
         coalesce((SELECT substring(setting FROM '^search_path=(.*)$')
                   FROM unnest(coalesce(p.proconfig, ARRAY[]::text[])) AS setting
                   WHERE setting LIKE 'search_path=%' LIMIT 1), '') AS configured_path
  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.prosecdef
    AND (has_function_privilege('anon', p.oid, 'EXECUTE')
      OR has_function_privilege('authenticated', p.oid, 'EXECUTE'))
), paths AS (
  SELECT d.*, trim(both ' "' FROM s.schema_name) AS path_schema
  FROM definer d
  CROSS JOIN LATERAL regexp_split_to_table(d.configured_path, ',') AS s(schema_name)
)
SELECT proname, args, path_schema,
       has_schema_privilege('anon', path_schema, 'CREATE') AS anon_can_create,
       has_schema_privilege('authenticated', path_schema, 'CREATE') AS authenticated_can_create,
       has_schema_privilege('service_role', path_schema, 'CREATE') AS service_can_create
FROM paths
WHERE path_schema <> '' AND path_schema NOT IN ('pg_temp', 'pg_catalog', '$user')
  AND to_regnamespace(path_schema) IS NOT NULL
ORDER BY proname, args, path_schema;

-- Separately surface callable SECURITY DEFINER functions without a pinned
-- search_path. They otherwise disappear from the schema-privilege result above.
SELECT p.proname, pg_get_function_identity_arguments(p.oid) AS args,
       'missing_search_path' AS audit_finding
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.prosecdef
  AND (has_function_privilege('anon', p.oid, 'EXECUTE')
    OR has_function_privilege('authenticated', p.oid, 'EXECUTE'))
  AND NOT EXISTS (
    SELECT 1 FROM unnest(coalesce(p.proconfig, ARRAY[]::text[])) AS setting
    WHERE setting LIKE 'search_path=%'
  )
ORDER BY p.proname, args;


-- Surface configured non-system schemas that do not resolve. The primary
-- privilege report excludes them; silently omitting them could mislead audits.
-- No function bodies or application data are returned.
WITH definer AS (
  SELECT p.oid, p.proname, pg_get_function_identity_arguments(p.oid) AS args,
         coalesce((SELECT substring(setting FROM '^search_path=(.*)$')
                   FROM unnest(coalesce(p.proconfig, ARRAY[]::text[])) AS setting
                   WHERE setting LIKE 'search_path=%' LIMIT 1), '') AS configured_path
  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.prosecdef
    AND (has_function_privilege('anon', p.oid, 'EXECUTE')
      OR has_function_privilege('authenticated', p.oid, 'EXECUTE'))
), paths AS (
  SELECT d.proname, d.args, trim(both ' "' FROM s.schema_name) AS path_schema
  FROM definer d
  CROSS JOIN LATERAL regexp_split_to_table(d.configured_path, ',') AS s(schema_name)
)
SELECT proname, args, path_schema, 'unresolved_search_path_schema' AS audit_finding
FROM paths
WHERE path_schema <> '' AND path_schema NOT IN ('pg_temp', 'pg_catalog', '$user')
  AND to_regnamespace(path_schema) IS NULL
ORDER BY proname, args, path_schema;
