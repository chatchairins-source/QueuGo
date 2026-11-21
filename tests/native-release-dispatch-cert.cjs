'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const yaml = fs.readFileSync(path.join(root, '.github/workflows/native-release-certification.yml'), 'utf8');
const target = '      - name: Require deployed Rider dispatch P0 prevention and live health';
const start = yaml.indexOf(target);
assert.ok(start >= 0, 'Production P0 validation must be an actual certification step');
const runMarker = '        run: |\n';
const runAt = yaml.indexOf(runMarker, start);
assert.ok(runAt > start, 'P0 certification must have executable shell');
const end = yaml.indexOf('\n      - ', runAt + runMarker.length);
assert.ok(end > runAt, 'P0 certification step must be bounded');
const script = yaml.slice(runAt + runMarker.length, end)
  .split('\n').map(line => line.startsWith('          ') ? line.slice(10) : line).join('\n');
assert.match(script, /set -euo pipefail/, 'abort on errors and failed pipes');
assert.match(script, /default_transaction_read_only=on/, 'database must enforce read-only');
assert.match(script, /ops\/p0\/rider-dispatch-deployment-readonly\.sql/, 'check deployed function and trigger');
assert.match(script, /ops\/p0\/rider-dispatch-highwater-readonly\.sql/, 'check current dispatch health');
assert.match(script, /t\|t\|t\|OPERATOR_ATTESTATION_REQUIRED/, 'no implicit certification from absent guards');
assert.match(script, /PASS_DISPATCH_OVERTIME/, 'no implicit certification from overdue or invalid clocks');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'queuego-cert-dispatch-'));
let checks = 6;
try {
  const fake = String.raw`#!/bin/sh
case "$PGOPTIONS" in *default_transaction_read_only=on*) ;; *) exit 27;; esac
if [ "$QG_TEST_DBFAIL" = '1' ]; then exit 42; fi
case "$*" in
  *rider-dispatch-deployment-readonly.sql*) printf '%s\n' "$QG_TEST_COMPONENTS";;
  *rider-dispatch-highwater-readonly.sql*) printf '%s\n' "$QG_TEST_HEALTH";;
  *) exit 43;;
esac
`;
  const executable = path.join(tmp, 'psql');
  fs.writeFileSync(executable, fake, {mode:0o700});
  function invoke(opts={}) {
    const env = {
      ...process.env, PATH:tmp+path.delimiter+process.env.PATH,
      QG_SUPABASE_DB_URL: 'postgresql://fixture-only',
      QG_TEST_COMPONENTS: 't|t|t|OPERATOR_ATTESTATION_REQUIRED',
      QG_TEST_HEALTH: '30|0|0|0|0|0|0|0|0|0|PASS_DISPATCH_OVERTIME',
      QG_TEST_DBFAIL: '0',
      ...opts,
    };
    const result = spawnSync('bash',['-c',script],{
      cwd:root,encoding:'utf8',timeout:5000,env,
    });
    if(result.error)throw result.error;
    return result;
  }
  const scenarios = [
    [{}, true, 'installed components and clean live health permit proceeding to remaining release gates'],
    [{QG_TEST_COMPONENTS:'f|f|f|NO_GO_DISPATCH_P0'}, false, 'no Production preventive installation'],
    [{QG_TEST_COMPONENTS:'t|f|t|NO_GO_DISPATCH_P0'}, false, 'disabled or absent trigger'],
    [{QG_TEST_COMPONENTS:'t|t|f|NO_GO_DISPATCH_P0'}, false, 'cash timeout authority missing'],
    [{QG_TEST_COMPONENTS:''}, false, 'empty SQL result'],
    [{QG_TEST_COMPONENTS:'t|t|t|PASS'}, false, 'wrong status cannot pass'],
    [{QG_TEST_HEALTH:'30|1|1|NO_GO_DISPATCH_P0'}, false, 'overdue searching order'],
    [{QG_TEST_HEALTH:'30|0|1|NO_GO_DISPATCH_P0'}, false, 'invalid persisted search clock'],
    [{QG_TEST_HEALTH:''}, false, 'empty health output'],
    [{QG_SUPABASE_DB_URL:''}, false, 'missing Production DB credential'],
    [{QG_TEST_DBFAIL:'1'}, false, 'psql connection or query failure'],
  ];
  for(const [opts,shouldPass,label] of scenarios) {
    const result=invoke(opts);
    assert.equal(result.status===0,shouldPass,label+' stdout='+result.stdout+' stderr='+result.stderr);
    checks++;
  }
  console.log(JSON.stringify({checks,failures:0,
    scope:'certification P0 SQL checks are read-only and fail closed; fake psql only, no Production access'}));
}finally{
  fs.rmSync(tmp,{recursive:true,force:true});
}
