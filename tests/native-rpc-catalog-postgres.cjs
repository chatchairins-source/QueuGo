'use strict';
// Disposable loopback PostgreSQL only. Never run fixture DDL against Supabase.
const { Client } = require('pg');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const url = process.env.QG_TEST_PG_URL;
if (!url) throw new Error('QG_TEST_PG_URL is required; no skipped catalog acceptance');
const parsed = new URL(url);
if (!['127.0.0.1', 'localhost', '[::1]'].includes(parsed.hostname) ||
    parsed.pathname !== '/queuego_rpc_catalog_gate') {
  throw new Error('Catalog fixtures require the isolated loopback test database');
}
const names = ['qg_test_callable', 'qg_test_denied', 'qg_test_private',
  'qg_test_procedure', 'qg_test_aggregate', 'qg_test_window', 'qg_test_absent'];
const sql = execFileSync('python3', ['-c', `
import importlib.util,json,sys
spec=importlib.util.spec_from_file_location('preflight','native-android/qa/native-rpc-production-preflight.py')
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
print(m.sql_query(json.loads(sys.stdin.read())))
`], { input: JSON.stringify(names), encoding: 'utf8' });
(async () => {
  const db = new Client({ connectionString: url });
  await db.connect();
  try {
    await db.query('BEGIN');
    await db.query(`
      create role authenticated nologin;
      create role anon nologin;
      grant usage on schema public to authenticated,anon;
      create schema qg_shadow;
      create function public.qg_test_callable() returns integer language sql as 'select 1';
      revoke all on function public.qg_test_callable() from public;
      grant execute on function public.qg_test_callable() to authenticated;
      create function public.qg_test_denied() returns integer language sql as 'select 1';
      revoke all on function public.qg_test_denied() from public;
      create function qg_shadow.qg_test_private() returns integer language sql as 'select 1';
      create procedure public.qg_test_procedure() language plpgsql as 'begin null; end';
      create aggregate public.qg_test_aggregate(integer) (sfunc=int4pl, stype=integer, initcond='0');
      create function public.qg_test_window() returns bigint language internal window as 'window_row_number';
    `);
    const rows = (await db.query(sql)).rows;
    const byName = Object.fromEntries(rows.map(row => [row.name, row]));
    assert.equal(rows.length, names.length, 'missing names must retain their rows');
    for (const name of names.slice(2)) {
      assert.equal(byName[name].overloads, 0, `${name} is not an ordinary public RPC`);
      assert.equal(byName[name].authenticated_execute, false);
    }
    assert.equal(byName.qg_test_callable.overloads, 1);
    assert.equal(byName.qg_test_callable.authenticated_execute, true);
    assert.equal(byName.qg_test_callable.anonymous_execute, false);
    assert.equal(byName.qg_test_denied.overloads, 1);
    assert.equal(byName.qg_test_denied.authenticated_execute, false);
    await db.query('revoke usage on schema public from public,authenticated');
    const noSchema = (await db.query(sql)).rows.find(row => row.name === 'qg_test_callable');
    assert.equal(noSchema.overloads, 1, 'schema denial must not disguise function presence');
    assert.equal(noSchema.authenticated_execute, false, 'EXECUTE without USAGE must fail');
    console.log('PASS: actual PostgreSQL catalog rejects private/procedure/aggregate/window/missing RPCs, denied EXECUTE and denied schema USAGE');
  } finally {
    await db.query('ROLLBACK');
    await db.end();
  }
})().catch(() => { console.error('FAIL: isolated PostgreSQL RPC catalog regression'); process.exitCode = 1; });
