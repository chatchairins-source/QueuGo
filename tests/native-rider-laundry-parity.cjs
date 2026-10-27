const assert=require('assert'),fs=require('fs');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const profile=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt','utf8');
const webPreferences=fs.readFileSync('rider/work-preferences.js','utf8');
const merchant=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantLaundryScreen.kt','utf8');
const merchantApi=fs.readFileSync('native-android/merchant/src/main/java/com/queuego/merchant/MerchantLaundryApi.kt','utf8');
const shopSql=fs.readFileSync('QueueGo-Laundry-Shop-Transport.sql','utf8');

const snapshotAt=api.indexOf('suspend fun riderSnapshot');
const notificationAt=api.indexOf('/** Exact reference lookup',snapshotAt);
const snapshotSource=api.slice(snapshotAt,notificationAt);
assert(snapshotSource.includes('readLaundryState(auth.session.accessToken)'), 'Native Rider snapshot must read current dedicated Laundry RPC state');
assert(snapshotSource.includes('catch (_: Exception) {\n            RiderLaundryState()'), 'Unavailable Laundry RPC must fail closed to empty state');
assert(profile.includes('RiderLaundryModeControl(') && profile.includes('api.setLaundryMode(auth, enabled)'), 'Native Rider Profile must expose the Web Laundry mode through its dedicated mutation');
assert(profile.includes('RiderLaundryInvites(') && profile.includes('api.respondLaundryInvite(auth, invite.inviteId, accept)'), 'Native Rider Profile must expose owner-scoped Web Laundry invitations');
const serviceKeys=api.match(/riderWorkServiceKeys = setOf\(([^)]*)\)/)?.[1]||'';
assert(serviceKeys.includes('"food"') && !serviceKeys.includes('"laundry"'), 'Laundry must remain outside general Rider work-preferences services');
assert(webPreferences.includes("'food'") && !webPreferences.includes("'laundry'"), 'Web work preferences must remain distinct from the Laundry authority');
for(const rpc of ['queuego_laundry_rider_state','queuego_set_laundry_rider_mode','queuego_laundry_rider_invite_action'])
  assert(api.includes(rpc), 'Native Rider must retain the existing dedicated '+rpc+' authority');
assert(merchant.includes('ร้านจัดพนักงานรับ–ส่งเอง · GP 10% ของยอดรวม'),'Native Merchant must identify Laundry as shop-managed');
assert(merchant.includes('ชื่อพนักงานไปรับผ้า')&&merchant.includes('ชื่อพนักงานส่งคืน'),'Native Merchant must capture staff for both transport legs');
assert(merchantApi.includes('"queuego_laundry_shop_manage"'),'Native Merchant must use the authoritative shop-managed Laundry state machine');
for(const retired of ['queuego_set_laundry_rider_mode','queuego_laundry_rider_invite_action','queuego_claim_laundry_job','queuego_laundry_rider_action'])
  assert(shopSql.includes("'"+retired+"'"),'Shop-managed source must retire '+retired);
console.log('Native Rider Laundry dedicated-authority and Merchant shop-managed contracts: PASS');
