'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { PGlite } = require('@electric-sql/pglite');

const root = path.resolve(__dirname, '..');
const migrationPath = path.join(root, 'supabase/migrations/20261111073652_revoke_anon_ride_config_execute.sql');
const migration = fs.readFileSync(migrationPath, 'utf8');

async function main() {
  const db = new PGlite();
  let checks = 0;
  const check = (condition, message) => {
    assert.ok(condition, message);
    checks++;
  };

  try {
    await db.exec('CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role;');
    await db.exec(`
      CREATE FUNCTION public.queuego_ride_config()
      RETURNS jsonb
      LANGUAGE sql
      STABLE
      SECURITY DEFINER
      SET search_path = public, pg_temp
      AS $$ SELECT '{"tariff":"TH_PUBLIC_MOTORCYCLE_CEILING"}'::jsonb $$;
      GRANT EXECUTE ON FUNCTION public.queuego_ride_config() TO anon;
    `);

    const before = (await db.query(`
      SELECT has_function_privilege('anon', 'public.queuego_ride_config()', 'EXECUTE') AS anon,
             has_function_privilege('authenticated', 'public.queuego_ride_config()', 'EXECUTE') AS authenticated,
             has_function_privilege('service_role', 'public.queuego_ride_config()', 'EXECUTE') AS service_role
    `)).rows[0];
    check(before.anon && before.authenticated && before.service_role,
      'fixture must model the live anon ACL drift and the intended authenticated/service grants');

    await db.exec(migration);
    const after = (await db.query(`
      SELECT has_function_privilege('anon', 'public.queuego_ride_config()', 'EXECUTE') AS anon,
             has_function_privilege('authenticated', 'public.queuego_ride_config()', 'EXECUTE') AS authenticated,
             has_function_privilege('service_role', 'public.queuego_ride_config()', 'EXECUTE') AS service_role,
             p.prosecdef AS security_definer,
             array_to_string(p.proconfig, ',') AS settings
        FROM pg_proc p
       WHERE p.oid = 'public.queuego_ride_config()'::regprocedure
    `)).rows[0];
    check(!after.anon, 'anon must lose execution through both a direct grant and PUBLIC');
    check(after.authenticated && after.service_role, 'authenticated and service_role execution must remain');
    check(after.security_definer && after.settings.includes('search_path=public, pg_temp'),
      'the migration must preserve the function definition and pinned search_path');

    await db.exec(migration);
    const repeated = (await db.query(`
      SELECT has_function_privilege('anon', 'public.queuego_ride_config()', 'EXECUTE') AS anon,
             has_function_privilege('authenticated', 'public.queuego_ride_config()', 'EXECUTE') AS authenticated,
             has_function_privilege('service_role', 'public.queuego_ride_config()', 'EXECUTE') AS service_role
    `)).rows[0];
    check(!repeated.anon && repeated.authenticated && repeated.service_role,
      'reapplying the ACL statements must be idempotent');
  } finally {
    await db.close();
  }

  console.log(JSON.stringify({ checks, failures: 0, scope: 'isolated queuego_ride_config ACL drift remediation' }));
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
