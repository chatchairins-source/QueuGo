-- Read-only regression signal: duplicate checkout replay must not bypass session validity.
-- Returns only booleans; never returns function bodies or customer rows.
WITH target AS (
 SELECT pg_get_functiondef(p.oid) AS body
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='qg_table_checkout'
   AND p.prosecdef AND pg_get_function_identity_arguments(p.oid) LIKE 'p_session uuid%'
), checks AS (
 SELECT
   position('if v.revoked_at is not null' in body) AS validity_guard,
   position('if v_old.payload_hash<>v_hash' in body) AS replay_guard,
   position('return jsonb_build_object(' in body) AS first_return,
   position('for update' in lower(body)) > 0 AS locks_session,
   position('set_config(' in lower(body)) > 0 AS privileged_context
 FROM target
)
SELECT count(*) AS matched_functions,
       coalesce(bool_and(validity_guard > 0 AND replay_guard > 0 AND validity_guard < replay_guard),false) AS validity_before_replay,
       coalesce(bool_and(validity_guard > 0 AND first_return > 0 AND validity_guard < first_return),false) AS validity_before_first_return,
       coalesce(bool_and(locks_session),false) AS locks_session,
       coalesce(bool_and(privileged_context),false) AS privileged_context
FROM checks;
