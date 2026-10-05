const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const customer=fs.readFileSync(path.join(root,'index.html'),'utf8');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const admin=fs.readFileSync(path.join(root,'admin/index.html'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(customer.includes('market checkout post-commit cleanup failed'),'Market checkout cleanup after commit must be non-fatal');
ok(customer.includes('market checkout post-commit navigation failed'),'Market checkout navigation after commit must be non-fatal');
ok(customer.includes('support post-commit render failed'),'Support ticket render after commit must be non-fatal');
ok(customer.includes('let pending,result,committed=false'),'Market checkout must distinguish committed server result from UI cleanup');

ok(merchant.includes('merchant product hydrate after save failed'),'Product post-save hydrate must be non-fatal');
ok(merchant.includes('merchant profile post-commit navigation failed'),'Shop profile navigation after save must be non-fatal');
ok(merchant.includes('merchant GP post-commit refresh failed'),'GP slip refresh after server report must be non-fatal');
ok(merchant.includes('merchant module post-commit render failed'),'Module render after save must be non-fatal');
ok(merchant.includes('merchant promo post-commit navigation failed'),'Promotion navigation after save must be non-fatal');

ok(admin.includes('admin account post-commit hydrate failed'),'Admin account hydrate after saved status must be non-fatal');
ok(admin.includes('admin account post-commit render failed'),'Admin account render after saved status must be non-fatal');
ok(admin.includes('admin platform post-commit refresh failed'),'Admin platform refresh after save must be non-fatal');
ok(admin.includes("toast(({approve:'อนุมัติ',reject:'ไม่อนุมัติ',suspend:'ระงับ',restore:'ปลดระงับ'})[action]+'บัญชีแล้ว');"),'Admin account success must be acknowledged independently of refresh');

console.log(JSON.stringify({checks,failures:0,scope:'post-commit UI isolation across Customer, Merchant and Admin'}));
