const fs=require('fs'),assert=require('assert');
const policy=require('../queuego-password-policy.js');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};
ok(policy.MIN_LENGTH===12,'Password minimum must be 12');
ok(policy.validate('QueueGo#2026Secure'),'Strong password must pass');
for(const weak of ['12345678','queuegoqueuego1!','QUEUEGOQUEUEGO1!','QueueGoQueueGo!!','QueueGoQueueGo12']){
  ok(!policy.validate(weak),'Weak password must fail: '+weak);
}
for(const p of ['index.html','merchant/index.html','rider/index.html']){
  const s=fs.readFileSync(p,'utf8');
  ok(s.includes('queuego-password-policy.js'),'Shared password policy must be loaded by '+p);
  ok(s.includes('QueueGoPasswordPolicy'),'Signup flow must use shared password policy in '+p);
}
const customer=fs.readFileSync('index.html','utf8');
const merchant=fs.readFileSync('merchant/index.html','utf8');
const rider=fs.readFileSync('rider/index.html','utf8');
ok(/id="rw"[^>]*minlength="12"/.test(customer),'Customer registration must require 12 chars');
ok(/id="reg-password"[^>]*minlength="12"/.test(merchant),'Merchant registration must require 12 chars');
ok(/id="rr-password"[^>]*minlength="12"/.test(rider),'Rider registration must require 12 chars');
for(const wf of ['.github/workflows/build-queuego-pilot-apks.yml','.github/workflows/build-queuego-apks.yml']){
  const s=fs.readFileSync(wf,'utf8');
  ok(s.includes('queuego-password-policy.js'),'Android workflow must bundle password policy: '+wf);
}
console.log(JSON.stringify({checks,failures:0,scope:'shared 12-character password policy across Customer, Merchant, Rider and Android bundles'}));
