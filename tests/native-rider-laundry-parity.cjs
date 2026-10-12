const assert=require('assert');
const fs=require('fs');

const models=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const profile=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderLaundryUi.kt','utf8');
const nav=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderNavigation.kt','utf8');

assert(models.includes('val featureEnabled: Boolean = false'),'Laundry feature switch must remain in Rider snapshot state');
assert(models.includes('internal fun RiderLaundryJob.asMapJob()'),'Laundry active jobs must render on the real Native map');

for(const rpc of [
  'queuego_laundry_rider_state',
  'queuego_set_laundry_rider_mode',
  'queuego_laundry_rider_invite_action',
  'queuego_claim_laundry_job',
  'queuego_laundry_rider_action'
]) assert(api.includes('"'+rpc+'"'),'Native Rider must use Production '+rpc+' RPC');

assert(api.includes('laundry = laundry'),'Rider snapshot must carry Production laundry state');
assert(api.includes('fallbackStatus = if (name == "pool") "waiting" else "assigned"'),'Laundry pool/active state must preserve Production transition defaults');

assert(nav.includes('internal fun riderLaundryNavigationTarget(job: RiderLaundryJob)'),'Laundry navigation must use the in-app route controller');
assert(app.includes('snapshot?.laundry?.activeJob?.let(::riderLaundryNavigationTarget)'),'Home route target must follow active laundry work');
assert(app.includes('snapshot?.laundry?.activeJob?.asMapJob()'),'Longdo map must display active laundry endpoints');
assert(app.includes('RiderLaundryActiveCard('),'Home must render an active laundry card');
assert(app.includes('RiderLaundryPoolCard('),'Home must render claimable laundry work');
assert(app.includes('api.claimLaundryJob(auth, laundryJob.jobId)'),'Laundry claim must hit Production');
assert(app.includes('api.laundryJobAction(auth, laundryJob.jobId, action)'),'Laundry lifecycle actions must hit Production');

assert(profile.includes('"รับงานฝากซัก"'),'Profile must retain the laundry mode setting');
assert(profile.includes('api.setLaundryMode(auth, enabled)'),'Laundry mode must persist through Production RPC');
assert(profile.includes('api.respondLaundryInvite(auth, invite.inviteId, true)'),'Laundry invitations must remain actionable in Native Profile');
assert(profile.includes('snapshot.activeJob == null && snapshot.laundry.activeJob == null'),'Online toggle must stay locked during any active job');

for(const state of ['"assigned", "accepted" -> "arrive"','"arrived" -> "collect"','"collected" -> "deliver"'])
  assert(ui.includes(state),'Laundry UI must retain Production transition '+state);

console.log('Native Rider Production laundry Home parity contracts: PASS');
