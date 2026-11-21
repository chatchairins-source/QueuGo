'use strict';
const assert = require('node:assert/strict');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const moduleUrl = pathToFileURL(path.resolve(__dirname,'../ops/restore-storage-target.mjs')).href;
const script = path.resolve(__dirname,'../ops/restore-storage.mjs');
const rejected = [
 'https://pkypiqhlrmzocysgeqew.supabase.co', 'https://other-project.supabase.co',
 'https://127.0.0.1.example.com', 'https://localhost.example.com', 'http://192.168.1.10:54321',
 'file:///tmp/restore', 'ftp://127.0.0.1', 'not a URL',
 'http://user:password@127.0.0.1:54321', 'http://127.0.0.1:54321/storage/v1',
 'http://127.0.0.1:54321?target=remote', 'http://127.0.0.1:54321#remote'
];
// Run the real entrypoint with no SDK or manifest available. A forbidden URL
// must be rejected before either file reads, client imports or network calls.
for(const url of rejected) {
 const run = spawnSync(process.execPath,[script],{encoding:'utf8',timeout:5000,
  env:{PATH:process.env.PATH,QG_RESTORE_SUPABASE_URL:url,QG_RESTORE_SERVICE_ROLE_KEY:'fixture-only',
    QG_STORAGE_MANIFEST:'/nonexistent-queuego-restore-fixture/manifest.json'}});
 if(run.error) throw run.error;
 assert.notEqual(run.status,0,'forbidden restore URL must fail');
 assert.match(run.stderr,/local loopback target/);
 assert.doesNotMatch(run.stderr,/ENOENT|ERR_MODULE_NOT_FOUND|fetch failed/);
}
const unit = spawnSync(process.execPath,['--input-type=module','-e',`
 import assert from 'node:assert/strict';
 import {localRestoreStorageTarget as check} from ${JSON.stringify(moduleUrl)};
 for(const value of ['http://127.0.0.1:54321','http://localhost:54321/','http://[::1]:54321','https://127.0.0.1:54321']) {
  assert.equal(check(value),new URL(value).origin);
 }
 for(const value of [undefined,'','https://pkypiqhlrmzocysgeqew.supabase.co']) {
  assert.throws(()=>check(value),/local loopback target/);
 }
`],{encoding:'utf8',timeout:5000});
if(unit.error) throw unit.error;
assert.equal(unit.status,0,unit.stderr);
console.log(JSON.stringify({checks:rejected.length+7,failures:0,scope:'local-only Storage restore target + real entrypoint remote rejection; no backup restore or Production write'}));
