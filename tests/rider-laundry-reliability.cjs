const fs=require('fs'),assert=require('assert');
const rider=fs.readFileSync('rider/laundry.js','utf8');
const merchant=fs.readFileSync('merchant/laundry.js','utf8');
const shopSql=fs.readFileSync('QueueGo-Laundry-Shop-Transport.sql','utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

for(const rpc of [
 'queuego_laundry_rider_state','queuego_set_laundry_rider_mode',
 'queuego_laundry_rider_invite_action','queuego_claim_laundry_job','queuego_laundry_rider_action'
]) ok(!rider.includes("rpc('"+rpc+"'")&&!rider.includes('"'+rpc+'"'),rpc+' must not be callable from general Rider Web');
ok(rider.includes('งานฝากซักรับ–ส่งโดยพนักงานของร้าน'),'stale Rider Laundry entry must fail closed with shop-managed copy');
ok(merchant.includes("rpc('queuego_laundry_shop_manage'"),'Merchant Web must use authoritative shop-managed Laundry RPC');
ok(merchant.includes("p_expected_status:o.status"),'Merchant Web must guard each Laundry transition against stale status');
ok(merchant.includes("ชื่อพนักงานไปรับผ้า")&&merchant.includes("ชื่อพนักงานส่งคืน"),'Merchant Web must capture staff for pickup and return');
ok(!merchant.includes("rpc('queuego_laundry_invite_rider'"),'Merchant Web must not invite general QueueGo Riders');
ok(!merchant.includes('Rider ของบริการฝากซัก'),'Merchant Web must not advertise a platform Rider pool');
ok(shopSql.includes("revoke all on function %s from public,anon,authenticated"),'shop-managed SQL must revoke retired Rider endpoints');
ok(shopSql.includes("queuego_laundry_shop_manage"),'shop-managed SQL must keep one authoritative shop state machine');
console.log(JSON.stringify({checks,failures:0,scope:'Laundry shop-managed transport; no general Rider pool or invitation UI'}));