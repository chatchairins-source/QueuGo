-- QueueGo Worker 2: read-only catalog signals for privileged public RPCs.
-- No user rows, function bodies, secrets or payment data are returned.
-- Heuristics are review prompts, not proof of safety or vulnerability.
WITH funcs AS (
 SELECT p.oid, p.proname, pg_get_function_identity_arguments(p.oid) AS arguments,
        p.proconfig, p.provolatile, p.prosecdef,
        pg_get_functiondef(p.oid) AS body
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef
), signals AS (
 SELECT proname, arguments,
        has_function_privilege('anon',oid,'EXECUTE') AS anon_execute,
        has_function_privilege('authenticated',oid,'EXECUTE') AS authenticated_execute,
        EXISTS (SELECT 1 FROM unnest(COALESCE(proconfig,ARRAY[]::text[])) AS cfg(v)
                WHERE cfg.v LIKE 'search_path=%') AS search_path_configured,
        body ~* '\\mEXECUTE\\M' AS dynamic_sql_signal,
        body ~* 'set_config[[:space:]]*\\(' AS set_config_signal,
        body ~* 'auth[.]uid|auth[.]jwt|current_setting|session_id' AS auth_signal,
        body ~* 'FOR[[:space:]]+UPDATE|pg_advisory_xact_lock|ON[[:space:]]+CONFLICT' AS concurrency_signal
 FROM funcs
)
SELECT proname AS function_name, arguments, anon_execute, authenticated_execute,
       search_path_configured, dynamic_sql_signal, set_config_signal,
       auth_signal, concurrency_signal
FROM signals
WHERE anon_execute OR authenticated_execute
ORDER BY anon_execute DESC, function_name, arguments;
