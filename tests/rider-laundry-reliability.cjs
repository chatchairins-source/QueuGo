const fs=require('fs'),assert=require('assert');
const rider=fs.readFileSync('rider/laundry.js','utf8');
const riderIndex=fs.readFileSync('rider/index.html','utf8');
const workPreferences=fs.readFileSync('rider/work-preferences.js','utf8');
const merchant=fs.readFileSync('merchant/laundry.js','utf8');
const shopSql=fs.readFileSync('QueueGo-Laundry-Shop-Transport.sql','utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

for(const rpc of [
 'queuego_laundry_rider_state','queuego_set_laundry_rider_mode',
 'queuego_laundry_rider_invite_action','queuego_claim_laundry_job','queuego_laundry_rider_action'
]) ok(!rider.includes("rpc('"+rpc+"'")&&!rider.includes('"'+rpc+'"'),rpc+' must not be callable from general Rider Web');
for(const retired of [
 'queuego_set_laundry_rider_mode','queuego_laundry_rider_invite_action',
 'queuego_claim_laundry_job','queuego_laundry_rider_action',
 'queuego_laundry_rider_invites','queuego_laundry_rider_active_job','queuego_laundry_rider_pool'
]) ok(!riderIndex.includes(retired),retired+' must not remain in integrated Rider Web');
for(const stale of ['qgLoadLaundryRiderState','qgLaundryPoolHTML','qgLaundryActiveHTML','qg-laundry-mode','S.laundryJob','S.laundryInvites','S.laundryPool','S.laundryMode'])
 ok(!riderIndex.includes(stale),stale+' must not restore retired general Rider Laundry state/UI');
ok(!workPreferences.includes('S.laundryJob'),'Web work preferences must not depend on retired general Rider Laundry state');
for(const retired of ['queuego_set_laundry_rider_mode','queuego_laundry_rider_invite_action','queuego_claim_laundry_job','queuego_laundry_rider_action'])
 ok(!workPreferences.includes(retired),retired+' must not be callable from Web work preferences');
ok(riderIndex.includes('work-preferences.js')&&riderIndex.includes('id="qg-work-preferences"')&&
   riderIndex.includes('QueueGoRiderWork?.mount')&&riderIndex.includes('QueueGoRiderWork?.maybeAccept'),
   'Integrated Rider Web must use current server-authoritative work preferences after Laundry retirement');
ok(rider.includes('งานฝากซักรับ–ส่งโดยพนักงานของร้าน'),'stale Rider Laundry entry must fail closed with shop-managed copy');
ok(merchant.includes("rpc('queuego_laundry_shop_manage'"),'Merchant Web must use authoritative shop-managed Laundry RPC');
ok(merchant.includes("p_expected_status:o.status"),'Merchant Web must guard each Laundry transition against stale status');
ok(merchant.includes("ชื่อพนักงานไปรับผ้า")&&merchant.includes("ชื่อพนักงานส่งคืน"),'Merchant Web must capture staff for pickup and return');
ok(!merchant.includes("rpc('queuego_laundry_invite_rider'"),'Merchant Web must not invite general QueueGo Riders');
ok(!merchant.includes('Rider ของบริการฝากซัก'),'Merchant Web must not advertise a platform Rider pool');
ok(shopSql.includes("revoke all on function %s from public,anon,authenticated"),'shop-managed SQL must revoke retired Rider endpoints');
ok(shopSql.includes("queuego_laundry_shop_manage"),'shop-managed SQL must keep one authoritative shop state machine');
ok(shopSql.includes("p_action in ('start_pickup','start_return') and v_staff is null"),'shop-managed pickup/return must require a named shop staff member');
console.log(JSON.stringify({checks,failures:0,scope:'Laundry shop-managed transport; no general Rider pool or invitation UI'}));