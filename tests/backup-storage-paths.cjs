'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const crypto=require('node:crypto');
const repo=path.resolve(__dirname,'..');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'queuego-backup-boundary-'));
let checks=0;
try {
  // Replace only the remote SDK boundary; execute both real CLI entrypoints.
  // No server, credentials or Production data are used.
  const sdk=`export function createClient(){return {storage:{
    async listBuckets(){return {data:[{id:process.env.FIXTURE_BUCKET,name:'fixture'}]}},
    from(){return {
      async list(){return {data:[{id:'fixture-object',name:process.env.FIXTURE_OBJECT}]}},
      async download(){return {data:new Blob(['fixture'])}},
      async upload(){return {data:{}}}
    }}, async createBucket(){return {data:{}}}
  }}}`;
  const loader=path.join(tmp,'sdk-loader.mjs');
  fs.writeFileSync(loader,`export async function resolve(specifier,context,next){
    if(specifier==='@supabase/supabase-js')return {url:${JSON.stringify('data:text/javascript,'+encodeURIComponent(sdk))},shortCircuit:true};
    return next(specifier,context);
  }`);
  function run(mode,bucket,object){
    const caseRoot=fs.mkdtempSync(path.join(tmp,'case-'));
    const root=path.join(caseRoot,'storage');
    const outside=path.join(caseRoot,'escaped.txt');
    fs.writeFileSync(outside,'fixture');
    const manifest=path.join(caseRoot,'storage-manifest.json');
    fs.writeFileSync(manifest,JSON.stringify({version:1,buckets:[{id:bucket}],objects:[{
      bucket,path:object,bytes:7,sha256:crypto.createHash('sha256').update('fixture').digest('hex')
    }]}));
    const result=spawnSync(process.execPath,['--experimental-loader',pathToFileURL(loader).href,
      path.join(repo,'ops',mode+'-storage.mjs')],{encoding:'utf8',timeout:5000,env:{
      PATH:process.env.PATH,QG_STORAGE_BACKUP_DIR:root,QG_STORAGE_MANIFEST:manifest,
      QG_SUPABASE_URL:'http://127.0.0.1:54321',QG_SUPABASE_SERVICE_ROLE_KEY:'fixture-only',
      QG_RESTORE_SUPABASE_URL:'http://127.0.0.1:54321',QG_RESTORE_SERVICE_ROLE_KEY:'fixture-only',
      FIXTURE_BUCKET:bucket,FIXTURE_OBJECT:object
    }});
    if(result.error)throw result.error;
    return {result,root,outside,manifest};
  }
  const bad=[['photos','../../escaped.txt'],['..','escaped.txt'],['/absolute','escaped.txt'],
    ['photos','../escaped.txt'],['photos','folder/../escaped.txt'],['photos','/escaped.txt'],
    ['photos','folder//escaped.txt'],['photos','folder\\escaped.txt'],['photos',''],['.','escaped.txt']];
  for(const mode of ['backup','restore'])for(const [bucket,object] of bad){
    const {result,outside}=run(mode,bucket,object);
    assert.notEqual(result.status,0,mode+' must reject '+JSON.stringify([bucket,object]));
    assert.match(result.stderr,/Unsafe Storage backup path/);
    assert.equal(fs.readFileSync(outside,'utf8'),'fixture');
    checks+=3;
  }
  // Preserve real nested, Unicode and literal percent-encoded object names.
  for(const object of ['orders/receipt.jpg','ร้านค้า/รูปภาพ.jpg','literal/%2e%2e.txt']){
    const backed=run('backup','photos',object);
    assert.equal(backed.result.status,0,backed.result.stderr);
    assert.equal(fs.readFileSync(path.join(backed.root,'photos',...object.split('/')),'utf8'),'fixture');
    const restored=spawnSync(process.execPath,['--experimental-loader',pathToFileURL(loader).href,
      path.join(repo,'ops/restore-storage.mjs')],{encoding:'utf8',timeout:5000,env:{
      PATH:process.env.PATH,QG_STORAGE_BACKUP_DIR:backed.root,QG_STORAGE_MANIFEST:backed.manifest,
      QG_RESTORE_SUPABASE_URL:'http://127.0.0.1:54321',QG_RESTORE_SERVICE_ROLE_KEY:'fixture-only',
      FIXTURE_BUCKET:'photos',FIXTURE_OBJECT:object
    }});
    if(restored.error)throw restored.error;
    assert.equal(restored.status,0,restored.stderr);
    assert.match(restored.stdout,/"verifiedObjects":1/);
    checks+=4;
  }
  console.log(JSON.stringify({checks,failures:0,scope:'actual backup/restore CLI path rejection with isolated SDK fixtures; no live backup acceptance'}));
}finally{fs.rmSync(tmp,{recursive:true,force:true});}
