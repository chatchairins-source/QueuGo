const fs=require('node:fs'),assert=require('node:assert/strict'),vm=require('node:vm');
const html=fs.readFileSync('index.html','utf8'),express=fs.readFileSync('customer-express.js','utf8');
let checks=0;const ok=(v,m)=>{assert(v,m);checks++;};
ok(html.includes("go('express')")&&html.includes('>ส่งด่วน</b>')&&html.includes('customer-express.js?v=20261018-web2'),'Customer Home must expose Web Express as a category');
for(const token of ['queuego_express_config','queuego_express_state','queuego_request_express','queuego-express-quote','qg_customer_cancel_order'])ok(express.includes(token),'Web Express missing '+token);
for(const token of ['senderName','senderPhone','receiverName','receiverPhone','itemType','weight','size','vehicleType','instructions'])ok(express.includes(token),'Web Express form missing '+token);
ok(express.includes("qg_customer_express_pending_v1:")&&express.includes('localStorage.setItem(key(),JSON.stringify(p))'),'Web Express must persist the exact request before mutation');
ok(express.includes("o.status!=='searching_rider'")&&express.includes('o.rider_id'),'Web Express cancellation must fail closed after Rider assignment');
ok(express.includes("fulfillment_vertical==='express'")&&express.includes("go('express/'+id)"),'Express order history must route to the dedicated state screen');
ok(express.includes('express-live-map')&&express.includes('rider_latitude')&&express.includes('rider_longitude'),'Assigned Web Express must show Rider tracking from owner-scoped state');
ok(express.includes("payment")===false&&express.includes('ค่าส่งเงินสด'),'Web Express stays cash-first and does not add e-payment');
const phoneStart=express.indexOf(' function validExpressContactPhone(');
const phoneEnd=express.indexOf(' function formPayload(){',phoneStart);
ok(phoneStart>=0&&phoneEnd>phoneStart,'Web must provide a shared Express phone validator');
const validPhone=vm.runInNewContext(express.slice(phoneStart,phoneEnd)+'; validExpressContactPhone');
for(const phone of ['0812345678','021234567','+66 81-234-5678','(02) 123-4567']){
  ok(validPhone(phone),'Web must accept dialable phone '+phone);
}
for(const phone of ['', 'abcde','12345','0812345678 ext 9','0812345678901234','++66812345678','0812345678\n123']){
  ok(!validPhone(phone),'Web must reject invalid phone '+phone);
}
ok(express.includes('!validExpressContactPhone(form.senderPhone)')&&express.includes('!validExpressContactPhone(form.receiverPhone)'),
   'Web sender and receiver must both be validated before quoting');
ok(express.includes('weight<=0||weight>1000'),'Web parcel weight must match the 1,000 kg Native and backend boundary');

console.log(JSON.stringify({checks,failures:0,scope:'Customer Web Express category/form/trusted quote/request/recovery/state/tracking; no Production mutation'}));