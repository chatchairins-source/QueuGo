const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const customer=fs.readFileSync(path.join(root,'index.html'),'utf8');
const customerFeatures=fs.readFileSync(path.join(root,'customer-features.js'),'utf8');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const admin=fs.readFileSync(path.join(root,'admin/index.html'),'utf8');
const rider=fs.readFileSync(path.join(root,'rider/index.html'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(customer.includes('market checkout post-commit cleanup failed'),'Market checkout cleanup after commit must be non-fatal');
ok(customer.includes('market checkout post-commit navigation failed'),'Market checkout navigation after commit must be non-fatal');
ok(customer.includes('support post-commit render failed'),'Support ticket render after commit must be non-fatal');
ok(customer.includes('let pending,result,committed=false'),'Market checkout must distinguish committed server result from UI cleanup');
ok(customer.includes('customer location post-commit navigation failed'),'Customer saved location navigation must be non-fatal');
ok(customerFeatures.includes('customer chat post-send refresh failed'),'Customer chat refresh after sent message must be non-fatal');

ok(merchant.includes('merchant product hydrate after save failed'),'Product post-save hydrate must be non-fatal');
ok(merchant.includes('merchant profile post-commit navigation failed'),'Shop profile navigation after save must be non-fatal');
ok(merchant.includes('merchant GP post-commit refresh failed'),'GP slip refresh after server report must be non-fatal');
ok(merchant.includes('merchant module post-commit render failed'),'Module render after save must be non-fatal');
ok(merchant.includes('merchant promo post-commit navigation failed'),'Promotion navigation after save must be non-fatal');
ok(merchant.includes('merchant support post-send refresh failed'),'Merchant support refresh after sent message must be non-fatal');
ok(merchant.includes('merchant shop-open post-commit UI failed'),'Merchant shop open UI update after commit must be non-fatal');

ok(admin.includes('admin account post-commit hydrate failed'),'Admin account hydrate after saved status must be non-fatal');
ok(admin.includes('admin account post-commit render failed'),'Admin account render after saved status must be non-fatal');
ok(admin.includes('admin platform post-commit refresh failed'),'Admin platform refresh after save must be non-fatal');
ok(admin.includes("toast(({approve:'อนุมัติ',reject:'ไม่อนุมัติ',suspend:'ระงับ',restore:'ปลดระงับ'})[action]+'บัญชีแล้ว');"),'Admin account success must be acknowledged independently of refresh');
ok(admin.includes('admin support post-send refresh failed'),'Admin support refresh after sent message must be non-fatal');
ok(admin.includes('admin shop-open post-commit render failed'),'Admin shop-state render after commit must be non-fatal');
ok(rider.includes('rider chat post-send refresh failed'),'Rider chat refresh after sent message must be non-fatal');

console.log(JSON.stringify({checks,failures:0,scope:'post-commit UI isolation across Customer, Merchant, Rider and Admin'}));
