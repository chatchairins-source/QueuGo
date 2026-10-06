const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const merchant=fs.readFileSync(path.join(root,'merchant/index.html'),'utf8');
const customer=fs.readFileSync(path.join(root,'index.html'),'utf8');
const sql=fs.readFileSync(path.join(root,'supabase/migrations/20261005213500_restricted_delivery_policy.sql'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(sql.includes("add column if not exists delivery_restriction"),'products must have an additive delivery restriction field');
ok(sql.includes("queuego_detect_delivery_restriction"),'server must classify obvious restricted products');
ok(sql.includes("new.delivery_available:=false"),'server trigger must force restricted products out of Delivery');
ok(sql.includes("products_restricted_not_delivery_check"),'database must enforce restricted products cannot be Delivery-enabled');
ok(!/set\s+pos_available\s*=|new\.pos_available\s*:=/i.test(sql),'restriction migration must not disable POS availability');
ok(sql.includes("update public.products"),'existing products must be backfilled');
ok(sql.includes("(เบียร์|เหล้า|ไวน์|วิสกี้|วอดก้า|บรั่นดี|สุราขาว|สุราพื้นบ้าน)"),'Thai alcohol names must be covered');
ok(sql.includes("(บุหรี่|ยาสูบ|ซิการ์|บุหรี่ไฟฟ้า)"),'Thai tobacco names must be covered');
ok(!/\bdrop\s+(table|schema|column)\b/i.test(sql),'migration must not destructively drop product data');

ok(merchant.includes("function qgmDeliveryRestriction(name,category,description)"),'Merchant must identify restricted products before save');
ok(merchant.includes("delivery_available:restriction==='none'&&deliveryRequested"),'Merchant save must force restricted products out of Delivery without depending on a stale checkbox DOM');
ok(merchant.includes("const posAvailable=posControl?posControl.checked:(existingProduct?existingProduct.posAvailable!==false:true);"),'Merchant save must tolerate a missing POS checkbox and preserve a safe POS value');
ok(!merchant.includes("pos_available:$('#qgm-pos-available').checked"),'Merchant save must never dereference a missing POS checkbox directly');
ok(merchant.includes("delivery_restriction:restriction"),'Merchant must persist the restriction classification');
ok(merchant.includes("ขายหน้าร้าน POS ได้ แต่ QueueGo จะไม่เปิดขายผ่าน Delivery"),'Merchant UI must explain POS-only policy');
ok(merchant.includes("qgm-delivery-available")&&merchant.includes("qgmApplyDeliveryRestriction"),'Delivery switch must be automatically locked for restricted items');

ok(customer.includes("p=>p.delivery_available!==false"),'Customer storefront must hide products disabled for Delivery');
ok(customer.includes("product.delivery_available===false"),'Checkout must reject products disabled for Delivery');
ok(customer.includes("delivery_available=eq.true"),'Customer menu search must only search Delivery-enabled products');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo POS-only restriction for alcohol/tobacco and Delivery exclusion'}));
