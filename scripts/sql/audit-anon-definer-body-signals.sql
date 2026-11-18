-- QueueGo Worker 2: metadata-only audit of anonymously executable public SECURITY DEFINER RPCs.
-- READ ONLY. Review findings manually; heuristic flags do NOT prove a vulnerability or safety.
-- No function bodies, user records, secrets or payment rows are returned.
WITH exposed AS (
  SELECT p.oid, p.proname, pg_get_function_identity_arguments(p.oid) AS arguments,
         pg_get_functiondef(p.oid) AS definition,
         COALESCE(p.proconfig, ARRAY[]::text[]) AS configuration
  FROM pg_proc AS p
  JOIN pg_namespace AS n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public'
    AND p.prosecdef
    AND has_function_privilege('anon', p.oid, 'EXECUTE')
)
SELECT proname AS function_name, arguments,
       EXISTS (SELECT 1 FROM unnest(configuration) AS c(value)
               WHERE c.value LIKE 'search_path=%') AS search_path_pinned,
       definition ~* 'auth[.]uid|auth[.]jwt|current_setting|session_id' AS auth_reference_detected,
       definition ~* 'FOR[[:space:]]+UPDATE|pg_advisory_xact_lock|ON[[:space:]]+CONFLICT'
         AS locking_or_conflict_reference_detected
FROM exposed
ORDER BY function_name, arguments;
