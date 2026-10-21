const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync('supabase/functions/queuego-credit/index.ts','utf8').replace(/^import[^\n]*\n/,'');
let checks=0;for(const [secret,kind] of [['skey_test_fixture123','test'],['skey_live_fixture123','live'],['skey_fixture123','legacy_live'],['pkey_live_fixture123','public_key'],['','absent'],[' skey_live_fixture123 ','live']]){
 const logs=[];vm.runInNewContext(script,{Deno:{env:{get:k=>k==='OMISE_SECRET_KEY'?secret:undefined},serve:()=>{}},console:{info:v=>logs.push(v)},creditReadiness:()=>({}),createCreditHandler:()=>()=>{}});
 const diagnostic=JSON.parse(logs[1].slice(logs[1].indexOf('{')));assert.equal(diagnostic.omise_secret_kind,kind);assert.equal(diagnostic.outer_whitespace,secret!==secret.trim());if(secret)assert.equal(logs.join(' ').includes(secret.trim()),false);checks+=3;
}console.log('Credit config diagnostics: '+checks+' checks PASS; no key value logged');
