'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const repo=path.resolve(__dirname,'../..');
const container=process.env.QG_BACKUP_TEST_CONTAINER;
// No network URL is accepted. Only the workflow's pinned disposable PG service.
assert(/^[a-f0-9]{64}$/.test(container||''),'Only an isolated PostgreSQL service container id is allowed');
const image='postgres:17.6@sha256:00bc86618629af00d2937fdc5a5d63db3ff8450acf52f0636ec813c7f4902929';
const sourceDb='qg_backup_source_ci',restoredDb='qg_backup_restore_ci';
function command(binary,args,input){
 const r=spawnSync(binary,args,{input,encoding:'utf8',timeout:60000,maxBuffer:8*1024*1024});
 if(r.error)throw r.error;assert.equal(r.status,0,r.stderr);return r.stdout;
}
const config=JSON.parse(command('docker',['inspect',container]))[0];
assert.equal(config.Config.Image,image,'Refuse an unpinned or different database container');
assert(config.Config.Env.includes('POSTGRES_DB='+sourceDb),'Refuse a different database service');
function sql(db,text){return command('docker',['exec','-i',container,'psql','-U','postgres','-d',db,'-X','-At','-v','ON_ERROR_STOP=1'],text);}
function dump(option){return command('docker',['exec',container,'pg_dump','-U','postgres','-d',sourceDb,'--no-owner','--no-privileges',option,...(option==='--schema-only'?['--clean','--if-exists']:[])]);}
const tables=['auth.users','public.users','public.shop_profiles','public.rider_profiles',
 'public.orders','public.order_items','public.deliveries','public.notifications',
 'public.products','public.markets','public.market_orders','public.laundry_orders',
 'public.qg_pickup_proofs','public.qg_delivery_proofs','public.qg_ugc_terms_acceptances',
 'public.qg_user_blocks','public.qg_ugc_reports','storage.buckets','storage.objects'];
const directory=fs.mkdtempSync(path.join(os.tmpdir(),'queuego-pg-copy-'));
let checks=0;
try{
 sql(sourceDb,'CREATE SCHEMA auth; CREATE SCHEMA storage;');
 for(const table of tables)sql(sourceDb,`CREATE TABLE ${table}(id integer PRIMARY KEY,body text,amount numeric); INSERT INTO ${table} VALUES(1,E'line\\nwith\\ttab-บุรีรัมย์',12.34);`);
 sql(sourceDb,"INSERT INTO public.orders VALUES(2,E'backslash\\\\and-null',NULL)");
 const schema=dump('--schema-only'),data=dump('--data-only');
 const dataPath=path.join(directory,'data.sql');fs.writeFileSync(dataPath,data,{mode:0o600});
 const sourcePath=path.join(directory,'source.json'),targetPath=path.join(directory,'restored.json');
 const expected=JSON.parse(command(process.execPath,[path.join(repo,'ops/inventory-from-copy-dump.mjs'),dataPath]));
 assert.equal(expected['public.orders'],2);assert.equal(expected.inventory_basis,'pg_dump_text_copy_v1');checks+=2;
 sql(sourceDb,'CREATE DATABASE '+restoredDb);
 sql(restoredDb,schema);sql(restoredDb,data);
 assert.equal(JSON.parse(sql(restoredDb,'SELECT to_json(body) FROM public.orders WHERE id=1')),'line\nwith\ttab-บุรีรัมย์');checks++;
 const inventory=fs.readFileSync(path.join(repo,'ops/backup-inventory.sql'),'utf8');
 fs.writeFileSync(sourcePath,JSON.stringify(expected));
 fs.writeFileSync(targetPath,sql(restoredDb,inventory));
 const args=[path.join(repo,'ops/compare-inventory.mjs'),sourcePath,targetPath];
 command(process.execPath,args);checks++;
 // Simulate concurrent live growth after dump without changing the dump snapshot.
 sql(sourceDb,"INSERT INTO public.orders VALUES(3,'after-snapshot',99)");
 assert.equal(JSON.parse(sql(sourceDb,inventory))['public.orders'],3);checks++;
 command(process.execPath,args);checks++;
 // Removing just one restored order must fail. The former tolerance accepted it.
 sql(restoredDb,'DELETE FROM public.orders WHERE id=2');
 fs.writeFileSync(targetPath,sql(restoredDb,inventory));
 const loss=spawnSync(process.execPath,args,{encoding:'utf8',timeout:5000});
 assert.notEqual(loss.status,0);assert.match(loss.stderr,/public.orders:restored count differs/);checks+=2;
 console.log(JSON.stringify({checks,failures:0,source_commit:command('git',['rev-parse','HEAD']).trim(),engine:'PostgreSQL17.6',scope:'real pg_dump COPY to independent restored database; escaped data roundtrip, post-snapshot live growth and single restored-order loss; synthetic rows, NOT Production backup/restore certification'}));
}finally{fs.rmSync(directory,{recursive:true,force:true});}
