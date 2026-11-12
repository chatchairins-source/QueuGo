# QueueGo backend security: read-only Production observations (2026-11-12)

Project: pkypiqhlrmzocysgeqew. This is NOT Production security certification.

| Observation | Result |
| --- | ---: |
| public SECURITY DEFINER functions | 280 |
| callable as anon | 9 |
| callable as authenticated | 189 |
| anonymous executable with no pinned search_path | 0 |
| public RLS tables without policies | 14 |
| those 14 granting SELECT to anon | 0 |
| those 14 granting SELECT to authenticated | 0 |

Supabase Security Advisor reports 32 RLS-enabled tables without policies across public and private schemas. No policy by itself is not necessarily a vulnerability: access may be intentionally denied.

The nine anonymous RPC function signatures exactly match docs/security-anon-definer-allowlist.json. That is an exposure inventory, not proof that each function body is safe.

Historical docs/security-auth-definer-review.json (2026-10-07) counts 123 authenticated-callable SECURITY DEFINER functions; the current number is 189. That old PASS_CODE_DATABASE_REVIEW verdict MUST NOT be presented as current Production certification.

## Generate safe metadata-only snapshot

Execute this SELECT as a read-only Supabase catalog query, then save rows in a local JSON object under the functions key (do not commit sensitive DB data):

    SELECT p.proname || '(' || regexp_replace(oidvectortypes(p.proargtypes), '\s*,\s*', ',', 'g') || ')' AS signature,
           p.prosecdef AS security_definer,
           has_function_privilege('anon',p.oid,'EXECUTE') AS anon_execute,
           has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_execute,
           EXISTS (SELECT 1 FROM unnest(coalesce(p.proconfig,ARRAY[]::text[])) x
                   WHERE x LIKE 'search_path=%') AS search_path_pinned
    FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.prosecdef
    ORDER BY signature;

Then run:

    node scripts/audit-supabase-definer-exposure.cjs /local/snapshot.json docs/security-anon-definer-allowlist.json docs/security-auth-definer-review.json

Exit 0 means the snapshot matches the allowlist and historical count; NOT certified. Exit 2 means review required. Exit 1 means invalid input.

Never automatically issue GRANT, REVOKE, or schema changes based solely on this report. Inspect privileged function bodies, per-role permissions, RPC call chains, payment/order state transition guards, and independent backup/restore proof first.
