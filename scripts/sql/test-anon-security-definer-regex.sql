-- Worker 2: read-only PostgreSQL regex regression for PR #1234.
-- Synthetic strings only; no schema changes or customer/payment data.
-- These patterns must match audit-anon-security-definer-exposure.sql.
-- A successful match does NOT establish a SECURITY DEFINER exploit.
WITH cases(label, body, expect_execute, expect_set_config, expect_row_security) AS (
 VALUES
 ('dynamic_sql', 'BEGIN EXECUTE v_sql; END', true, false, false),
 ('set_config', 'PERFORM set_config(''row_security'', ''off'', true);', false, true, true),
 ('row_security', 'SET row_security = off;', false, false, true),
 ('case_insensitive', 'execute format(''select 1'');', true, false, false),
 ('negative_identifiers', 'execute_helper set_configured row_security_enabled', false, false, false),
 ('negative_plain', 'SELECT 1;', false, false, false)
), results AS (
 SELECT label,
        (body ~* '\mexecute\M') = expect_execute AS execute_ok,
        (body ~* '\mset_config\s*\(') = expect_set_config AS set_config_ok,
        (body ~* '\mrow_security\M') = expect_row_security AS row_security_ok
 FROM cases
)
SELECT label, execute_ok, set_config_ok, row_security_ok,
       (execute_ok AND set_config_ok AND row_security_ok) AS passed
FROM results ORDER BY label;
