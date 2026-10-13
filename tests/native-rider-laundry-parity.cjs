const assert=require('assert');
const fs=require('fs');

const models=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/Models.kt','utf8');
const api=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const app=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const profile=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt','utf8');
const ui=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderLaundryUi.kt','utf8');

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

assert(!app.includes('snapshot?.laundry?.activeJob?.let(::riderLaundryNavigationTarget)'),'Laundry work must not activate the retired in-app navigation controller');
assert(app.includes('openNavigation(context, laundryJob.asMapJob())'),'Laundry navigation must hand the real destination to Google Maps');
assert(app.includes('snapshot?.laundry?.activeJob?.asMapJob()'),'Longdo map must display active laundry endpoints');
assert(app.includes('RiderLaundryActiveCard('),'Home must render an active laundry card');
assert(app.includes('RiderLaundryPoolCard('),'Home must render claimable laundry work');
assert(app.includes('api.claimLaundryJob(auth, laundryJob.jobId)'),'Laundry claim must hit Production');
assert(app.includes('api.laundryJobAction(auth, laundryJob.jobId, action)'),'Laundry lifecycle actions must hit Production');
assert(app.includes('current.activeJob == null && current.laundry.activeJob != null'),'Normal delivery must retain priority over Laundry if a stale contradictory snapshot contains both');
assert(app.includes('fun claimLaundry(laundryJob: RiderLaundryJob)')&&
  (app.match(/onClaim = ::claimLaundry/g)||[]).length >= 2,
  'Laundry pool must remain visible beside a normal offer and reuse one claim implementation');

assert(profile.includes('"รับงานฝากซัก"'),'Profile must retain the laundry mode setting');
assert(profile.includes('api.setLaundryMode(auth, enabled)'),'Laundry mode must persist through Production RPC');
assert(profile.includes('api.respondLaundryInvite(auth, invite.inviteId, true)'),'Laundry invitations must remain actionable in Native Profile');
assert(profile.includes('snapshot.activeJob == null && snapshot.laundry.activeJob == null'),'Online toggle must stay locked during any active job');

for(const state of ['"assigned", "accepted" -> "arrive"','"arrived" -> "collect"','"collected" -> "deliver"'])
  assert(ui.includes(state),'Laundry UI must retain Production transition '+state);

console.log('Native Rider Production laundry Home parity contracts: PASS');
