const fs=require('node:fs'),path=require('node:path');
let checks=0;
function ok(v,m){if(!v)throw new Error(m);checks++}
const root=path.join(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');

const auth=read('shared/src/main/java/com/queuego/shared/QueueGoAuthHost.kt');
ok(auth.includes('withContext(Dispatchers.IO) { store.load() }'),'Customer/Merchant auth restore must decrypt Keystore state off Main');
ok(auth.includes('withContext(Dispatchers.IO) { store.save(validated) }'),'Customer/Merchant periodic session persistence must stay off Main');
ok(auth.includes('repeatOnLifecycle(Lifecycle.State.STARTED)'),'Customer/Merchant auth heartbeat must stop while UI is backgrounded');
ok(!auth.includes('auth = refreshed'),'NativeAuthApi IO refresh callback must never mutate Compose auth state directly');

const merchantMain=read('merchant/src/main/java/com/queuego/merchant/MainActivity.kt');
ok(merchantMain.includes('lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)'),'Merchant role/session watcher must be foreground lifecycle gated');
ok(merchantMain.includes('withContext(kotlinx.coroutines.Dispatchers.IO) { store.load() }'),'Merchant session watcher must decrypt off Main');
ok(merchantMain.includes('delay(5_000)')&&!merchantMain.includes('delay(350)')&&!merchantMain.includes('delay(1_000)'),'Merchant must not hot-poll encrypted session state');
ok(merchantMain.includes('sessionLoaded'),'Merchant must not flash an unauthenticated surface before initial secure-session read completes');

const rider=read('rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
ok(rider.includes('if (mergedSnapshot != currentSnapshot)'),'Rider must not encrypt/write an unchanged home snapshot on every 3-second poll');
ok(rider.includes('withContext(kotlinx.coroutines.Dispatchers.IO)')&&rider.includes('saveHomeSnapshot(auth.user.id, mergedSnapshot)'),'Rider snapshot persistence must execute off Main');
ok(rider.includes('saveActiveTab(auth.user.id, activeTab)')&&rider.includes('Dispatchers.IO'),'Rider encrypted UI-state persistence must execute off Main');

const merchantImage=read('merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt');
ok(merchantImage.includes('inJustDecodeBounds = true')&&merchantImage.includes('inSampleSize = sample'),'Merchant gallery/camera uploads must sample before allocating Bitmap memory');
ok(merchantImage.indexOf('inJustDecodeBounds = true')<merchantImage.indexOf('val decoded = BitmapFactory.decodeByteArray'),'Merchant image bounds must be read before full decode');

const chatImage=read('shared/src/main/java/com/queuego/shared/NativeChatImage.kt');
ok(chatImage.includes('CHAT_IMAGE_MAX_SIDE = 1280'),'Shared chat bitmaps must stay capped for Customer/Merchant/Rider memory safety');
ok(chatImage.includes('DisposableEffect(bitmap)')&&chatImage.includes('owned.recycle()'),'Shared chat bitmaps must release owned memory after leaving composition');

const design=read('shared/src/main/java/com/queuego/shared/QueueGoDesign.kt');
ok(design.includes('QG_REMOTE_IMAGE_MAX_SIDE = 1280'),'Shared remote image decode ceiling must remain bounded');
ok(design.includes('QG_REMOTE_IMAGE_MAX_BYTES')&&design.includes('inJustDecodeBounds = true')&&!design.includes('inputStream.use { it.readBytes() }'),'Remote images must be byte bounded and sampled before decode');

const store=read('rider/src/main/java/com/queuego/rider/SessionStore.kt');
ok(store.includes('RIDER_REGISTRATION_DRAFT_MAX_JSON_CHARS = 16 * 1024 * 1024'),'Rider registration plaintext draft must have an aggregate heap bound');
ok(store.includes('RIDER_REGISTRATION_DRAFT_MAX_FILE_BYTES = 24L * 1024L * 1024L'),'Rider encrypted draft file must be bounded before read/decrypt');
ok(!store.includes('100 * 1024 * 1024'),'Rider registration restore must never accept the old 100 MB encrypted draft');

const reg=read('rider/src/main/java/com/queuego/rider/RiderRegistrationScreen.kt');
ok(reg.includes('val before = documents')&&reg.includes('documents = before'),'Oversized/failed Rider document addition must rollback from in-memory form state');

const exit=read('shared/src/main/java/com/queuego/shared/NativeExitDiagnostics.kt');
ok(exit.includes('SupervisorJob() + Dispatchers.IO')&&exit.includes('scope.launch'),'Android exit-reason diagnostics must not block Activity launch');
ok(exit.includes('REASON_LOW_MEMORY')&&exit.includes('REASON_ANR')&&exit.includes('REASON_CRASH_NATIVE'),'Exit diagnostics must distinguish LMK/ANR/native crash');
ok(!exit.includes('traceInputStream'),'Exit diagnostics must not collect crash traces or user payloads');

const customerPicker=read('customer/src/main/java/com/queuego/customer/CustomerLocationPicker.kt');
ok(customerPicker.includes('mapReady')&&customerPicker.includes('delay(150)'),'Customer location permission must remain delayed until Longdo renderer is ready');

const sharedManifest=read('shared/src/main/AndroidManifest.xml');
const riderManifest=read('rider/src/main/AndroidManifest.xml');
ok(sharedManifest.includes('FOREGROUND_SERVICE_MICROPHONE')&&sharedManifest.includes('foregroundServiceType="microphone"'),'Voice microphone foreground service declaration must match Android 14+ type rules');
ok(riderManifest.includes('FOREGROUND_SERVICE_LOCATION')&&riderManifest.includes('foregroundServiceType="location"'),'Rider navigation foreground service must keep location type/permission');
ok(riderManifest.includes('FOREGROUND_SERVICE_SPECIAL_USE')&&riderManifest.includes('foregroundServiceType="specialUse"'),'Rider Google Maps return foreground service must keep special-use type/permission');

const returnService=read('rider/src/main/java/com/queuego/rider/RiderReturnService.kt');
const navService=read('rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt');
const voiceService=read('shared/src/main/java/com/queuego/shared/NativeVoiceForegroundService.kt');
ok(returnService.includes('runCatching { ContextCompat.startForegroundService')&&returnService.includes('start_foreground_failed'),'Rider return FGS must fail closed instead of crashing when Android rejects launch');
ok(navService.includes('start_foreground_failed')||navService.includes('foreground_start_rejected'),'Rider location FGS must handle Android start rejection');
ok(voiceService.includes('start_foreground_failed')||voiceService.includes('startForeground'),'Voice FGS must have guarded foreground startup');

console.log(JSON.stringify({checks,failures:0,scope:'three-app native crash/ANR/OOM/process-kill prevention contracts; static source gate, physical OEM coverage remains separate'}));
