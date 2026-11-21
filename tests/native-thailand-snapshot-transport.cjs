'use strict';
// Execute the real workflow's transport control flow with a disposable curl
// executable. This does not certify an upstream download or replace MD5 gates.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const workflow=fs.readFileSync('.github/workflows/build-native-rider-pilot.yml','utf8');
const start=workflow.indexOf('          if curl --fail --location --retry 6');
const end=workflow.indexOf('          python3 - "$source_md5"',start);
assert.ok(start>=0 && end>start,'actual country snapshot/sidecar retrieval block required');
assert.ok(workflow.includes('md5sum --check source-check.md5'),'official checksum gate must remain');
const block=workflow.slice(start,end);
const root=fs.mkdtempSync(path.join(os.tmpdir(),'qg-thailand-transport-'));
let checks=0;
try {
 const bin=path.join(root,'bin');fs.mkdirSync(bin);
 const curl=path.join(bin,'curl');
 fs.writeFileSync(curl,`#!${process.execPath}
const fs=require('node:fs');
const args=process.argv.slice(2),ipv4=args.includes('--ipv4');
const url=args.find(value=>value.startsWith('https://'));
if(!['https://download.geofabrik.de/asia/thailand-261101.osm.pbf','https://download.geofabrik.de/asia/thailand-261101.osm.pbf.md5'].includes(url))process.exit(90);
fs.appendFileSync(process.env.QG_CURL_LOG,JSON.stringify({url,ipv4})+'\\n');
const out=args[args.indexOf('-o')+1],kind=url.endsWith('.md5')?'md5':'pbf';
if(ipv4 && fs.existsSync(out))process.exit(91);
if(!ipv4 && ['both',kind].includes(process.env.QG_FAIL_KIND)) {
 fs.writeFileSync(out,'partial');process.exit(Number(process.env.QG_FAIL_CODE));
}
if(ipv4 && process.env.QG_FALLBACK_CODE)process.exit(Number(process.env.QG_FALLBACK_CODE));
fs.writeFileSync(out,'synthetic transport payload');
`,{mode:0o755});
 fs.writeFileSync(path.join(bin,'gh'),'#!/bin/sh\nexit 73\n',{mode:0o755});
 const run=(name,kind,code,expected,expectedCalls,fallbackCode='')=>{
  const directory=path.join(root,name);fs.mkdirSync(directory);
  const log=path.join(directory,'calls.jsonl');
  const result=spawnSync('bash',['-c','set -euo pipefail\nsource_pbf="$QG_MAP_DATA_DIR/source.pbf"\nsource_md5="$QG_MAP_DATA_DIR/source.md5"\n'+block],{
   encoding:'utf8',timeout:10000,env:{...process.env,PATH:bin+path.delimiter+process.env.PATH,
    QG_MAP_DATA_DIR:directory,QG_THAILAND_PBF_URL:'https://download.geofabrik.de/asia/thailand-261101.osm.pbf',
    RUNNER_TEMP:directory,GITHUB_ENV:path.join(directory,'github-env'),GITHUB_STEP_SUMMARY:path.join(directory,'summary'),GITHUB_REPOSITORY:'chatchairins-source/QueuGo',QG_CURL_LOG:log,QG_FAIL_KIND:kind,QG_FAIL_CODE:String(code),QG_FALLBACK_CODE:String(fallbackCode)}});
  if(result.error)throw result.error;
  assert.equal(result.status,expected,name+': '+result.stderr);
  const calls=fs.readFileSync(log,'utf8').trim().split('\n').map(JSON.parse);
  assert.equal(calls.length,expectedCalls,name+' bounded requests');
  if(expected===0) {
   assert.ok(fs.existsSync(path.join(directory,'source.pbf')) && fs.existsSync(path.join(directory,'source.md5')));
   assert.equal(calls.filter(call=>call.ipv4).length,kind==='none'?0:2);
  } else if(code===22 || code===23) assert.equal(calls.filter(call=>call.ipv4).length,0,'HTTP/nontransport failure must not fall back');
  checks++;
 };
 run('normal','none',0,0,2);
 for(const code of [6,7,28,35,52,56])run('transport-'+code,'both',code,0,4);
 run('pbf-404','pbf',22,22,1);run('md5-404','md5',22,22,2);
 run('write-error','pbf',23,23,1);run('recovery-artifact-unavailable','pbf',7,73,2,28);
 run('ipv4-http-failure','pbf',7,22,2,22);
} finally {fs.rmSync(root,{recursive:true,force:true});}
console.log(JSON.stringify({checks,failures:0,scope:'actual workflow shell transport fallback with disposable curl; not upstream download/country runtime certification'}));
