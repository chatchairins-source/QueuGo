-- QueueGo Worker 2: read-only SECURITY DEFINER anonymous exposure inventory.
-- Snapshot basis: Production pg_proc catalog, 2026-11-17. No production writes.
-- Execute only with read-only DB credentials. This is NOT a runtime exploit test.
-- A callable SECURITY DEFINER routine is not necessarily vulnerable.
WITH exposed AS (
  SELECT p.oid, p.proname, p.proowner, p.proconfig,
         p.provolatile, p.prosecdef, p.proacl,
         n.nspname, pg_get_function_identity_arguments(p.oid) AS args,
         pg_get_functiondef(p.oid) AS definition
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public'
    AND p.prosecdef
    AND has_function_privilege('anon',p.oid,'EXECUTE')
)
SELECT format('%I.%I(%s)',nspname,proname,args) AS signature,
       pg_get_userbyid(proowner) AS function_owner,
       has_function_privilege('anon',oid,'EXECUTE') AS anon_effective_execute,
       has_function_privilege('authenticated',oid,'EXECUTE') AS authenticated_effective_execute,
       EXISTS (
         SELECT 1 FROM aclexplode(coalesce(proacl,acldefault('f',proowner))) acl
         WHERE acl.grantee=0 AND acl.privilege_type='EXECUTE'
       ) AS public_role_execute_grant,
       coalesce(array_to_string(proconfig,','),'') AS function_settings,
       provolatile AS volatility,
       (definition ~* '\\mexecute\\M') AS contains_execute_keyword,
       (definition ~* '\\mset_config\\s*\\(') AS contains_set_config_call,
       (definition ~* '\\mrow_security\\M') AS references_row_security,
       md5(definition) AS definition_fingerprint
FROM exposed
ORDER BY signature;

-- Check for anonymous-executable DEFINER routines with no pinned search_path.
-- This is a configuration sentinel, not a proof of authorization safety.
SELECT p.oid::regprocedure::text AS signature
FROM pg_proc p
JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prosecdef
  AND has_function_privilege('anon',p.oid,'EXECUTE')
  AND NOT EXISTS (
    SELECT 1 FROM unnest(coalesce(p.proconfig,ARRAY[]::text[])) cfg
    WHERE cfg LIKE 'search_path=%'
  )
ORDER BY 1;

-- Required isolated-database follow-up:
-- 1. Run each callable RPC with anon credentials and deliberately invalid,
--    cross-tenant and expired/revoked session fixtures.
-- 2. Assert unauthorized reads/writes are denied and authorized calls still work.
-- 3. Verify object-qualified references, search_path trust boundaries, nested
--    invocations, ownership and row-level side effects.
-- 4. Keep qg_table_checkout fix in PR #1182; do not duplicate its migration.

-- SECURITY DEFINER with search_path including public needs an explicit schema
-- CREATE privilege review: untrusted CREATE can introduce name resolution risks.
-- These are privilege checks, not a proof of exploitability.
SELECT n.nspname AS schema_name,
       has_schema_privilege('anon',n.oid,'CREATE') AS anon_can_create,
       has_schema_privilege('authenticated',n.oid,'CREATE') AS authenticated_can_create,
       EXISTS (SELECT 1 FROM aclexplode(coalesce(n.nspacl,acldefault('n',n.nspowner))) a WHERE a.grantee=0 AND a.privilege_type='CREATE') AS public_can_create
FROM pg_namespace n
WHERE n.nspname='public';

-- Find anonymous-callable DEFINER routines with unsafe owner-controlled
-- search_path entries; pg_temp is reported separately, not treated as trusted.
SELECT p.oid::regprocedure::text AS signature,
       coalesce(array_to_string(p.proconfig,','),'') AS function_settings,
       has_schema_privilege('anon','public','CREATE') AS anon_can_create_public,
       has_schema_privilege('authenticated','public','CREATE') AS authenticated_can_create_public
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.prosecdef
  AND has_function_privilege('anon',p.oid,'EXECUTE')
  AND EXISTS (SELECT 1 FROM unnest(coalesce(p.proconfig,ARRAY[]::text[])) cfg
              WHERE cfg LIKE 'search_path=%public%')
ORDER BY 1;
