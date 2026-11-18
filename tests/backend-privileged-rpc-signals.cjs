'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const sql = fs.readFileSync(path.join(__dirname, '../scripts/sql/audit-privileged-rpc-review-signals.sql'), 'utf8');
const statements = sql.replace(/^--.*$/gm, '');
assert.match(statements, /FROM pg_proc p JOIN pg_namespace n ON n\.oid=p\.pronamespace/);
assert.match(statements, /n\.nspname='public' AND p\.prosecdef/);
assert.match(statements, /has_function_privilege\('anon',oid,'EXECUTE'\)/);
assert.match(statements, /has_function_privilege\('authenticated',oid,'EXECUTE'\)/);
assert.match(statements, /pg_get_functiondef\(p\.oid\)/);
assert.match(statements, /search_path_configured/);
assert.match(statements, /temp_schema_first_signal/);
assert.match(statements, /\^search_path\[\[:space:\]\]\*=/);
assert.match(statements, /pg_temp/);
assert.match(statements, /dynamic_sql_signal/);
assert.match(statements, /set_config_signal/);
assert.match(statements, /auth_signal/);
assert.match(statements, /concurrency_signal/);
assert.match(statements, /WHERE anon_execute OR authenticated_execute/);
assert.doesNotMatch(statements, /^\s*(?:INSERT|UPDATE|DELETE|ALTER|DROP|TRUNCATE|GRANT|REVOKE|CALL|DO)\b/im);
// Inspect the final result projection, not internal CTE aliases used for signal detection.
const finalProjection = statements.match(/\)\s*SELECT\s+([\s\S]*?)\s+FROM\s+signals\b/i)?.[1];
assert.ok(finalProjection, 'expected a final SELECT from signals');
assert.doesNotMatch(statements, /SELECT\s+\*/i);
assert.doesNotMatch(finalProjection, /\bbody\b|pg_get_functiondef/i);
console.log('privileged RPC review signals SQL: static checks passed');
