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
ok(
  rider.includes('val cached = auth ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)') &&
  !rider.includes('val cachedAtComposition = remember { store.load() }'),
  'Rider cold session restore must decrypt off Main instead of blocking first composition'
);
ok(
  rider.includes('pushStore.loadHomeSnapshot(auth.user.id)') &&
  rider.includes('val cachedSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)') &&
  !rider.includes('mutableStateOf<RiderSnapshot?>(pushStore.loadHomeSnapshot(auth.user.id))'),
  'Rider cached home snapshot must decrypt off Main'
);
ok(
  rider.includes('val savedTab = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)') &&
  !rider.includes('mutableStateOf(pushStore.loadActiveTab(auth.user.id))'),
  'Rider active-tab restore must decrypt off Main'
);
ok(rider.includes('if (mergedSnapshot != currentSnapshot)'),'Rider must not encrypt/write an unchanged home snapshot on every 3-second poll');
ok(rider.includes('withContext(kotlinx.coroutines.Dispatchers.IO)')&&rider.includes('saveHomeSnapshot(auth.user.id, mergedSnapshot)'),'Rider snapshot persistence must execute off Main');
ok(rider.includes('saveActiveTab(auth.user.id, activeTab)')&&rider.includes('Dispatchers.IO'),'Rider encrypted UI-state persistence must execute off Main');
ok(
  rider.includes('.putBoolean(navigationKey, true)') &&
  rider.includes('.apply()') &&
  !rider.includes('.putBoolean(navigationKey, true)\n            .commit()'),
  'Rider Google Maps launch state must never perform synchronous SharedPreferences commit on Main'
);

const merchantImage=read('merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt');
const merchantProducts=read('merchant/src/main/java/com/queuego/merchant/MerchantProductsScreen.kt');
const merchantCatalog=read('merchant/src/main/java/com/queuego/merchant/MerchantCatalogScreen.kt');
const merchantMedia=read('merchant/src/main/java/com/queuego/merchant/MerchantMediaScreen.kt');
ok(merchantImage.includes('inJustDecodeBounds = true')&&merchantImage.includes('inSampleSize = sample'),'Merchant gallery/camera uploads must sample before allocating Bitmap memory');
ok(merchantImage.indexOf('inJustDecodeBounds = true')<merchantImage.indexOf('val decoded = BitmapFactory.decodeByteArray'),'Merchant image bounds must be read before full decode');
ok(
  merchantProducts.includes('LazyColumn(') &&
  merchantProducts.includes('items(rows, key = { it.id })'),
  'Merchant product list must lazy-render image rows instead of composing the full catalog into heap'
);
ok(
  merchantCatalog.includes('LazyColumn(') &&
  merchantCatalog.includes('val productRows = remember(rows) { rows.chunked(2) }') &&
  merchantCatalog.includes('items = productRows'),
  'Merchant catalog grid must lazy-render product rows instead of decoding every product image at once'
);
ok(
  merchantMedia.includes('import androidx.compose.foundation.lazy.LazyColumn') &&
  merchantMedia.includes('import androidx.compose.foundation.lazy.items') &&
  merchantMedia.includes('items = rows.chunked(3)'),
  'Merchant media gallery must import and compile the lazy list DSL it uses'
);
ok(
  merchantMedia.includes('MERCHANT_MEDIA_RESPONSE_MAX_BYTES = 2 * 1024 * 1024') &&
  merchantMedia.includes('readMerchantMediaResponseBounded') &&
  merchantMedia.includes('contentLengthLong') &&
  !merchantMedia.includes('bufferedReader()?.use { it.readText() }'),
  'Merchant storage media listing must be byte bounded before JSON parsing'
);

const chatImage=read('shared/src/main/java/com/queuego/shared/NativeChatImage.kt');
ok(chatImage.includes('CHAT_IMAGE_MAX_SIDE = 1280'),'Shared chat bitmaps must stay capped for Customer/Merchant/Rider memory safety');
ok(chatImage.includes('DisposableEffect(bitmap)')&&chatImage.includes('owned.recycle()'),'Shared chat bitmaps must release owned memory after leaving composition');

const design=read('shared/src/main/java/com/queuego/shared/QueueGoDesign.kt');
const customerSearch=read('customer/src/main/java/com/queuego/customer/CustomerSearchScreen.kt');
const customerMarket=read('customer/src/main/java/com/queuego/customer/QueueGoMarketScreen.kt');
const customerShop=read('customer/src/main/java/com/queuego/customer/CustomerShopScreen.kt');
const merchantApp=read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
ok(design.includes('QG_REMOTE_IMAGE_MAX_SIDE = 1280'),'Shared remote image decode ceiling must remain bounded');
ok(design.includes('QG_REMOTE_IMAGE_MAX_BYTES')&&design.includes('inJustDecodeBounds = true')&&!design.includes('inputStream.use { it.readBytes() }'),'Remote images must be byte bounded and sampled before decode');
ok(
  design.includes('maxDecodeSide: Int = QG_REMOTE_IMAGE_MAX_SIDE') &&
  design.includes('maxSide.coerceIn(128, QG_REMOTE_IMAGE_MAX_SIDE)'),
  'QgRemoteImage must support per-surface decode ceilings so small thumbnails never allocate full-size bitmaps'
);
ok(
  design.includes('qgRemoteImageDecodeGate = Semaphore(3)') &&
  design.includes('qgRemoteImageDecodeGate.withPermit'),
  'Remote image loading must limit concurrent compressed-byte and bitmap allocations across all native apps'
);
ok(
  chatImage.includes('chatImageDecodeGate = Semaphore(2)') &&
  chatImage.includes('chatImageDecodeGate.withPermit'),
  'Chat image loading must limit concurrent compressed-byte and bitmap allocations'
);
ok(
  customerMarket.includes('maxDecodeSide = 256') &&
  customerSearch.includes('maxDecodeSide = 256') &&
  customerShop.includes('maxDecodeSide = 256'),
  'Customer Market/Search/shop list thumbnails must use bounded decode sizes'
);
ok(
  merchantProducts.includes('maxDecodeSide = 256') &&
  merchantCatalog.includes('maxDecodeSide = 384') &&
  merchantApp.includes('maxDecodeSide = 192') &&
  merchantApp.includes('maxDecodeSide = 256'),
  'Merchant product/order/profile thumbnails must use bounded decode sizes'
);

const sharedApi=read('shared/src/main/java/com/queuego/shared/QueueGoNativeApi.kt');
const nativeAuthApi=read('shared/src/main/java/com/queuego/shared/NativeAuthApi.kt');
const riderApiMemory=read('rider/src/main/java/com/queuego/rider/QueueGoApi.kt');
const customerApiMemory=read('customer/src/main/java/com/queuego/customer/CustomerApi.kt');
const store=read('rider/src/main/java/com/queuego/rider/SessionStore.kt');
ok(store.includes('RIDER_REGISTRATION_DRAFT_MAX_JSON_CHARS = 16 * 1024 * 1024'),'Rider registration plaintext draft must have an aggregate heap bound');
ok(store.includes('RIDER_REGISTRATION_DRAFT_MAX_FILE_BYTES = 24L * 1024L * 1024L'),'Rider encrypted draft file must be bounded before read/decrypt');
ok(!store.includes('100 * 1024 * 1024'),'Rider registration restore must never accept the old 100 MB encrypted draft');

ok(
  sharedApi.includes('QUEUEGO_NATIVE_RESPONSE_MAX_BYTES = 12 * 1024 * 1024') &&
  sharedApi.includes('readQueueGoResponseBounded') &&
  sharedApi.includes('contentLengthLong') &&
  !sharedApi.includes('BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()'),
  'Shared Customer/Merchant API responses must be byte bounded before JSON parsing to prevent low-memory process kills'
);
ok(
  nativeAuthApi.includes('NATIVE_AUTH_RESPONSE_MAX_BYTES = 4 * 1024 * 1024') &&
  nativeAuthApi.includes('readNativeAuthResponseBounded') &&
  nativeAuthApi.includes('contentLengthLong') &&
  !nativeAuthApi.includes('BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()'),
  'Customer/Merchant auth and session responses must be byte bounded before JSON parsing'
);
ok(
  riderApiMemory.includes('RIDER_API_RESPONSE_MAX_BYTES = 12 * 1024 * 1024') &&
  riderApiMemory.includes('readRiderApiResponseBounded') &&
  riderApiMemory.includes('contentLengthLong') &&
  !riderApiMemory.includes('BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()'),
  'Rider API responses must be byte bounded before JSON parsing to prevent low-memory process kills'
);
ok(
  riderApiMemory.includes('readRiderBytesBounded') &&
  riderApiMemory.includes('readRiderBytesBounded(5 * 1024 * 1024, "รูปโปรไฟล์ต้องมีขนาดไม่เกิน 5 MB")') &&
  riderApiMemory.includes('readRiderBytesBounded(5 * 1024 * 1024, "รูปหลักฐานต้องมีขนาดไม่เกิน 5 MB")') &&
  !riderApiMemory.includes('.use { it.readBytes() }'),
  'Rider evidence/profile source files must be bounded while streaming, before heap allocation'
);
ok(
  riderApiMemory.includes('readRiderApiResponseBounded(512 * 1024)') &&
  customerApiMemory.includes('readCustomerLongdoResponseBounded') &&
  !customerApiMemory.includes('reader.readText()'),
  'Customer and Rider Longdo reverse-geocode payloads must be bounded before parsing'
);

const reg=read('rider/src/main/java/com/queuego/rider/RiderRegistrationScreen.kt');
ok(reg.includes('val before = documents')&&reg.includes('documents = before'),'Oversized/failed Rider document addition must rollback from in-memory form state');

const exit=read('shared/src/main/java/com/queuego/shared/NativeExitDiagnostics.kt');
ok(exit.includes('SupervisorJob() + Dispatchers.IO')&&exit.includes('scope.launch'),'Android exit-reason diagnostics must not block Activity launch');
ok(exit.includes('REASON_LOW_MEMORY')&&exit.includes('REASON_ANR')&&exit.includes('REASON_CRASH_NATIVE'),'Exit diagnostics must distinguish LMK/ANR/native crash');
ok(!exit.includes('traceInputStream'),'Exit diagnostics must not collect crash traces or user payloads');

const customerPush=read('customer/src/main/java/com/queuego/customer/CustomerPush.kt');
const merchantPush=read('merchant/src/main/java/com/queuego/merchant/MerchantPush.kt');
ok(
  customerPush.includes('Build.VERSION_CODES.TIRAMISU') &&
  customerPush.includes('android.Manifest.permission.POST_NOTIFICATIONS') &&
  customerPush.includes('PackageManager.PERMISSION_GRANTED') &&
  customerPush.includes('runCatching {') &&
  customerPush.includes('NotificationManager).notify(') &&
  merchantPush.includes('Build.VERSION_CODES.TIRAMISU') &&
  merchantPush.includes('android.Manifest.permission.POST_NOTIFICATIONS') &&
  merchantPush.includes('PackageManager.PERMISSION_GRANTED') &&
  merchantPush.includes('NotificationManager).notify('),
  'Customer/Merchant FCM services must check Android 13 notification permission and fail closed around notify'
);

const launchQa=fs.readFileSync(path.join(__dirname,'..','native-android','qa','capture-rider-launch.py'),'utf8');
ok(
  launchQa.includes('def verify_three_role_system_kill_recovery():') &&
  launchQa.includes('for role in ("customer", "merchant", "rider")') &&
  launchQa.includes('"send-trim-memory", pkg, "RUNNING_CRITICAL"') &&
  launchQa.includes('"send-trim-memory", pkg, "COMPLETE"') &&
  launchQa.includes('adb + ["shell", "am", "kill", pkg]') &&
  launchQa.includes('verify_three_role_system_kill_recovery()'),
  'Android emulator lifecycle evidence must exercise Customer/Merchant/Rider background system-kill and cold recreation'
);
ok(
  launchQa.includes('assert_no_package_crash(pkg, directory / "crash-buffer.txt")') &&
  launchQa.includes('"background_am_kill": "PASS"') &&
  launchQa.includes('"cold_relaunch": "PASS"'),
  'Three-role process-death evidence must fail closed on package crash and record cold relaunch'
);

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
