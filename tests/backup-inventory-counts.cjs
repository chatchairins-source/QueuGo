'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const repo=path.resolve(__dirname,'..');
const keys=['auth.users','public.users','public.shop_profiles','public.rider_profiles',
  'public.orders','public.order_items','public.deliveries','public.notifications',
  'public.products','public.markets','public.market_orders','public.laundry_orders',
  'public.qg_pickup_proofs','public.qg_delivery_proofs','public.qg_ugc_terms_acceptances',
  'public.qg_user_blocks','public.qg_ugc_reports','storage.buckets','storage.objects'];
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'queuego-backup-counts-'));
let checks=0;
try {
  const valid=Object.fromEntries(keys.map(k=>[k,10]));
  function run(a,b){
    fs.writeFileSync(path.join(dir,'source.json'),JSON.stringify(a));
    fs.writeFileSync(path.join(dir,'restored.json'),JSON.stringify(b));
    const result=spawnSync(process.execPath,[path.join(repo,'ops/compare-inventory.mjs'),
      path.join(dir,'source.json'),path.join(dir,'restored.json')],{encoding:'utf8',timeout:5000});
    if(result.error)throw result.error;
    return result;
  }
  for(const key of keys)for(const value of ['not-a-count','10','',true,[],{},null,-1,0.5,Number.MAX_SAFE_INTEGER+1]){
    for(const side of ['source','restored']){
      const malformed={...valid,[key]:value};
      const r=side==='source'?run(malformed,valid):run(valid,malformed);
      assert.notEqual(r.status,0,key+' '+side+' must reject '+JSON.stringify(value));
      assert.match(r.stderr,/Restore inventory mismatch/);
      checks++;
    }
  }
  for(const [a,b] of [[valid,valid],[Object.fromEntries(keys.map(k=>[k,0])),Object.fromEntries(keys.map(k=>[k,0]))]]){
    const r=run(a,b);assert.equal(r.status,0,r.stderr);checks++;
  }
  // Preserve existing bounded snapshot-drift behavior; do not loosen it.
  for(const [count,passed] of [[8,true],[7,false],[11,false]]){
    const r=run(valid,{...valid,'public.orders':count});
    assert.equal(r.status===0,passed);checks++;
  }
  console.log(JSON.stringify({checks,failures:0,scope:'actual inventory comparison CLI rejects malformed counts; no live restore certification'}));
}finally{fs.rmSync(dir,{recursive:true,force:true});}
