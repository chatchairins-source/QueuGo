const assert=require('assert'),fs=require('fs');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const profile=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt','utf8');
const merchant=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantLaundryScreen.kt','utf8');
const merchantApi=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantLaundryApi.kt','utf8');
const shopSql=fs.readFileSync('QueueGo-Laundry-Shop-Transport.sql','utf8');

const snapshotAt=api.indexOf('suspend fun riderSnapshot');
const notificationAt=api.indexOf('/** Exact reference lookup',snapshotAt);
const snapshotSource=api.slice(snapshotAt,notificationAt);
assert(snapshotSource.includes('val laundry = RiderLaundryState()'),'General Rider snapshot must keep Laundry state empty under shop-managed transport');
assert(!snapshotSource.includes('readLaundryState('),'General Rider snapshot must not call platform Laundry Rider state');
assert(!profile.includes('รับงานฝากซัก'),'General Rider profile must not expose a Laundry work toggle');
assert(!profile.includes('respondLaundryInvite('),'General Rider profile must not expose shop Laundry invitations');
assert(merchant.includes('ร้านจัดพนักงานรับ–ส่งเอง · GP 10% ของยอดรวม'),'Native Merchant must identify Laundry as shop-managed');
assert(merchant.includes('ชื่อพนักงานไปรับผ้า')&&merchant.includes('ชื่อพนักงานส่งคืน'),'Native Merchant must capture staff for both transport legs');
assert(merchantApi.includes('"queuego_laundry_shop_manage"'),'Native Merchant must use the authoritative shop-managed Laundry state machine');
for(const retired of ['queuego_set_laundry_rider_mode','queuego_laundry_rider_invite_action','queuego_claim_laundry_job','queuego_laundry_rider_action'])
  assert(shopSql.includes("'"+retired+"'"),'Shop-managed source must retire '+retired);
console.log('Native Laundry shop-managed reconciliation contracts: PASS');