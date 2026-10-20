const fs=require('node:fs'),path=require('node:path');
let checks=0;
function ok(v,m){if(!v)throw new Error(m);checks++}
const root=path.join(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');

const auth=read('shared/src/main/java/com/queuego/shared/QueueGoAuthHost.kt');
const secureRoleStore=read('shared/src/main/java/com/queuego/shared/SecureRoleSessionStore.kt');
ok(
  secureRoleStore.includes('SECURE_ROLE_SESSION_MAX_PLAINTEXT_BYTES = 48 * 1024') &&
  secureRoleStore.includes('SECURE_ROLE_SESSION_MAX_ENCRYPTED_CHARS = 64 * 1024') &&
  secureRoleStore.includes('json.toByteArray(Charsets.UTF_8).size <= SECURE_ROLE_SESSION_MAX_PLAINTEXT_BYTES') &&
  secureRoleStore.includes('encrypted.length <= SECURE_ROLE_SESSION_MAX_ENCRYPTED_CHARS') &&
  secureRoleStore.includes('raw.length > SECURE_ROLE_SESSION_MAX_ENCRYPTED_CHARS') &&
  secureRoleStore.includes('prefs.edit().remove("session").apply()'),
  'Customer/Merchant session save and corrupted restore must be size-bounded before/after Keystore crypto'
);
ok(auth.includes('withContext(Dispatchers.IO) { store.load() }'),'Customer/Merchant auth restore must decrypt Keystore state off Main');
ok(auth.includes('withContext(Dispatchers.IO) { store.save(validated) }'),'Customer/Merchant periodic session persistence must stay off Main');
ok(auth.includes('repeatOnLifecycle(Lifecycle.State.STARTED)'),'Customer/Merchant auth heartbeat must stop while UI is backgrounded');
ok(!auth.includes('auth = refreshed'),'NativeAuthApi IO refresh callback must never mutate Compose auth state directly');

const roleShell=read('shared/src/main/java/com/queuego/shared/QueueGoRoleNativeApp.kt');
ok(
  roleShell.includes('withContext(Dispatchers.IO) { store.load() }') &&
  roleShell.includes('withContext(Dispatchers.IO) { store.save(validated) }') &&
  roleShell.includes('withContext(Dispatchers.IO) { store.clear() }') &&
  roleShell.includes('repeatOnLifecycle(Lifecycle.State.STARTED)') &&
  roleShell.includes('runCatching { api.revoke(current.session) }') &&
  !roleShell.includes('val cached = store.load()') &&
  !roleShell.includes('store.save(it); auth = it'),
  'Shared fallback role shell must keep secure-session crypto off Main, lifecycle-gate polling and fail closed on revoke'
);

const customerGuest=read('customer/src/main/java/com/queuego/customer/CustomerGuestShell.kt');
const customerAuthScreen=read('customer/src/main/java/com/queuego/customer/CustomerAuthenticationScreen.kt');
const customerCartStore=read('customer/src/main/java/com/queuego/customer/CustomerCartStore.kt');
const marketCartStore=read('customer/src/main/java/com/queuego/customer/MarketCartStore.kt');
ok(
  customerGuest.includes('val restored = withContext(Dispatchers.IO)') &&
  customerGuest.includes('cartStore.load() to locationStore.load()') &&
  customerGuest.includes('if (localStateLoaded) withContext(Dispatchers.IO) { cartStore.save(cart) }') &&
  !customerGuest.includes('mutableStateOf(cartStore.load())') &&
  !customerGuest.includes('remember { locationStore.load() }'),
  'Customer guest cart/location restore and cart persistence must stay off Main without empty-state overwrite'
);
ok(
  customerAuthScreen.includes('guestCartCount = withContext(Dispatchers.IO)') &&
  !customerAuthScreen.includes('CustomerCartStore(context, "guest").load().sumOf { it.quantity }\n    }'),
  'Customer auth guest-cart badge must not parse local cart JSON during composition'
);
ok(
  customerCartStore.includes('CUSTOMER_CART_MAX_JSON_CHARS = 1024 * 1024') &&
  customerCartStore.includes('CUSTOMER_PENDING_CHECKOUT_MAX_JSON_CHARS = 2 * 1024 * 1024') &&
  customerCartStore.includes('boundedPref("cart", CUSTOMER_CART_MAX_JSON_CHARS)') &&
  customerCartStore.includes('boundedPref("pending_checkout", CUSTOMER_PENDING_CHECKOUT_MAX_JSON_CHARS)'),
  'Customer Food cart and pending checkout local JSON must be bounded before parse/recovery'
);
ok(
  marketCartStore.includes('MARKET_CART_MAX_JSON_CHARS = 512 * 1024') &&
  marketCartStore.includes('MARKET_PENDING_CHECKOUT_MAX_JSON_CHARS = 2 * 1024 * 1024') &&
  marketCartStore.includes('val rows = JSONArray(cartRaw())') &&
  marketCartStore.includes('override fun read(): PendingMarketCheckout? = pendingRaw()?.let'),
  'Customer Market cart and pending checkout local JSON must be bounded before parse/recovery'
);

const customerApp=read('customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
ok(
  customerApp.includes('authRequested = withContext(Dispatchers.IO) { store.load() != null }') &&
  !customerApp.includes('mutableStateOf(store.load() != null)'),
  'Customer root secure-session presence check must never decrypt Keystore state during Compose composition'
);
ok(
  customerApp.includes('val restored = withContext(Dispatchers.IO) {') &&
  customerApp.includes('val saved = cartStore.load()') &&
  customerApp.includes('val guest = guestCartStore.load()') &&
  customerApp.includes('val savedLocation = locationStore.load()') &&
  customerApp.includes('if (localStateLoaded) {') && customerApp.includes('try { withContext(Dispatchers.IO) { cartStore.save(cart) } }') &&
  !customerApp.includes('LaunchedEffect(cart) { cartStore.save(cart) }'),
  'Customer local cart/location restore, guest merge and cart persistence must stay off Main'
);
ok(
  customerApp.includes('pendingCheckoutRecord = withContext(Dispatchers.IO)') &&
  customerApp.includes('withContext(Dispatchers.IO) { cartStore.save(submittedCart) }') &&
  customerApp.includes('create = {\n                                            withContext(Dispatchers.IO) {') &&
  !customerApp.includes('mutableStateOf(runCatching { checkoutJournal.read() }.getOrNull())'),
  'Customer checkout journal reads, submitted-cart persistence and checkout JSON snapshot creation must stay off Main'
);

const merchantMain=read('merchant/src/main/java/com/queuego/merchant/MainActivity.kt');
ok(merchantMain.includes('lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)'),'Merchant role/session watcher must be foreground lifecycle gated');
ok(merchantMain.includes('withContext(kotlinx.coroutines.Dispatchers.IO) { store.load() }'),'Merchant session watcher must decrypt off Main');
ok(merchantMain.includes('delay(5_000)')&&!merchantMain.includes('delay(350)')&&!merchantMain.includes('delay(1_000)'),'Merchant must not hot-poll encrypted session state');
ok(merchantMain.includes('sessionLoaded'),'Merchant must not flash an unauthenticated surface before initial secure-session read completes');
ok(
  merchantMain.includes('withContext(kotlinx.coroutines.Dispatchers.IO) { store.save(auth) }') &&
  merchantMain.includes('withContext(kotlinx.coroutines.Dispatchers.IO) { store.save(validated) }') &&
  merchantMain.includes('withContext(kotlinx.coroutines.Dispatchers.IO) { store.clear() }') &&
  !merchantMain.includes('auth = refreshed'),
  'Merchant login/register/POS session writes and logout clear must stay off Main, and IO refresh callbacks must not mutate Compose state'
);

ok(
  merchantMain.includes('val lifecycle = LocalLifecycleOwner.current.lifecycle') &&
  merchantMain.includes('LaunchedEffect(initialAuth.session.authUserId, lifecycle)') &&
  merchantMain.includes('lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)') &&
  merchantMain.includes('delay(25_000)'),
  'Merchant POS staff session validation must pause while the app is backgrounded'
);

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
  rider.includes('Text("นำทางด้วย QueueGo"') &&
  !rider.includes('Text("นำทางด้วย Google Maps"') &&
  !rider.includes('private fun openNavigation(') &&
  !rider.includes('RiderReturnService.start(context)'),
  'Rider navigation must stay inside QueueGo without an external-map handoff or return-overlay start on Main'
);

const merchantImage=read('merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt');
const merchantProducts=read('merchant/src/main/java/com/queuego/merchant/MerchantProductsScreen.kt');
const merchantCatalog=read('merchant/src/main/java/com/queuego/merchant/MerchantCatalogScreen.kt');
const merchantLaundry=read('merchant/src/main/java/com/queuego/merchant/MerchantLaundryScreen.kt');
ok(
  merchantLaundry.includes('fun openExternal(intent: Intent, failureMessage: String)') &&
  merchantLaundry.includes('runCatching { context.startActivity(intent) }') &&
  !merchantLaundry.includes('OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL') &&
  !merchantLaundry.includes('context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/'),
  'Merchant Laundry external dial/navigation intents must fail closed instead of crashing when Android has no handler'
);

const merchantMedia=read('merchant/src/main/java/com/queuego/merchant/MerchantMediaScreen.kt');
const merchantShopSetup=read('merchant/src/main/java/com/queuego/merchant/MerchantShopSetupScreen.kt');
const merchantPrinter=read('merchant/src/main/java/com/queuego/merchant/MerchantPrinter.kt');
const merchantPosScreen=read('merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt');
const sharedPrintQueue=read('shared/src/main/java/com/queuego/shared/NativePrintQueue.kt');
const merchantPosApi=read('merchant/src/main/java/com/queuego/merchant/MerchantPosApi.kt');
const merchantPosPending=read('merchant/src/main/java/com/queuego/merchant/MerchantPosPendingStore.kt');
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

ok(
  merchantProducts.includes('MERCHANT_PRODUCT_PREVIEW_MAX_SIDE = 512') &&
  merchantProducts.includes('inJustDecodeBounds = true') &&
  merchantProducts.includes('inSampleSize = sample') &&
  merchantProducts.includes('DisposableEffect(image)') &&
  merchantProducts.includes('owned.recycle()') &&
  !merchantProducts.includes('BitmapFactory.decodeStream(it)?.asImageBitmap()'),
  'Merchant local product preview must sample camera/gallery images and recycle owned Bitmaps instead of full-resolution decode'
);
ok(
  merchantImage.includes('MERCHANT_UPLOAD_ERROR_MAX_BYTES = 256 * 1024') &&
  merchantImage.includes('readMerchantStorageResponseBounded') &&
  !merchantImage.includes('bufferedReader()?.use { it.readText() }'),
  'Merchant storage upload error bodies must be byte bounded instead of unbounded readText heap growth'
);

ok(
  merchantShopSetup.includes('decodeMerchantProductPreview(context, uri)') &&
  merchantShopSetup.includes('DisposableEffect(image)') &&
  merchantShopSetup.includes('owned.recycle()') &&
  !merchantShopSetup.includes('BitmapFactory.decodeStream(it)?.asImageBitmap()'),
  'Merchant shop setup camera/gallery previews must reuse the sampled decoder and recycle owned Bitmaps'
);
ok(
  merchantPrinter.includes('readMerchantStorageResponseBounded(stream)') &&
  merchantPrinter.includes('require(text.length <= 256 * 1024)') &&
  merchantPrinter.includes('MERCHANT_PRINTER_URL_MAX_CHARS = 500') &&
  merchantPrinter.includes('MERCHANT_PRINTER_DEDUPE_MAX_KEYS = 300') &&
  merchantPrinter.includes('MERCHANT_PRINTER_HISTORY_MAX_CHARS = 128 * 1024') &&
  merchantPrinter.includes('check(keys.size <= MERCHANT_PRINTER_DEDUPE_MAX_KEYS)') &&
  merchantPrinter.includes('check(historyRaw.length <= MERCHANT_PRINTER_HISTORY_MAX_CHARS)') &&
  !merchantPrinter.includes('BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()'),
  'Merchant Print Bridge request/response and local dedupe/history state must stay bounded and fail closed'
);

ok(
  sharedPrintQueue.includes('wasPrinted: suspend (String) -> Boolean') &&
  merchantPosScreen.includes('printerSettings = withContext(Dispatchers.IO) { printerStore.load() }') &&
  merchantPosScreen.includes('withContext(Dispatchers.IO) { printerStore.wasPrinted(key) }') &&
  merchantPosScreen.includes('withContext(Dispatchers.IO) { printerStore.save(safe) }') &&
  merchantPosScreen.includes('lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)') &&
  !merchantPosScreen.includes('mutableStateOf(printerStore.load())') &&
  !merchantPosScreen.includes('!printerStore.wasPrinted('),
  'Merchant printer settings/dedupe storage and POS fallback polling must stay off Main/background lifecycle'
);
ok(
  merchantPosPending.includes('file.length() <= 16 * 1024') &&
  merchantPosPending.includes('it.fd.sync()') &&
  merchantPosApi.includes('suspend fun pendingEdit(auth: NativeAuth): PosEditIntent? = withContext(Dispatchers.IO)') &&
  merchantPosApi.includes('pendingStore.prepare(auth.session.authUserId, intent)') &&
  merchantPosApi.includes('withContext(Dispatchers.IO)') &&
  merchantPosApi.includes('pendingStore.clear(auth.session.authUserId, pending.requestId)'),
  'Merchant POS pending read/fsync/clear durability must execute through IO without weakening bounded replay state'
);

const chatImage=read('shared/src/main/java/com/queuego/shared/NativeChatImage.kt');
ok(chatImage.includes('CHAT_IMAGE_MAX_SIDE = 1280'),'Shared chat bitmaps must stay capped for Customer/Merchant/Rider memory safety');
ok(
  chatImage.includes('CHAT_IMAGE_MAX_BASE64_CHARS = ((CHAT_IMAGE_LIMIT + 2) / 3) * 4') &&
  chatImage.includes('encodedLength in 1..CHAT_IMAGE_MAX_BASE64_CHARS') &&
  !chatImage.includes('source.length <= 7 * 1024 * 1024'),
  'Shared chat data-URI images must reject oversized Base64 before allocating decoded bytes'
);
ok(chatImage.includes('DisposableEffect(bitmap)')&&chatImage.includes('owned.recycle()'),'Shared chat bitmaps must release owned memory after leaving composition');

const design=read('shared/src/main/java/com/queuego/shared/QueueGoDesign.kt');
const customerSearch=read('customer/src/main/java/com/queuego/customer/CustomerSearchScreen.kt');
const customerMarket=read('customer/src/main/java/com/queuego/customer/QueueGoMarketScreen.kt');
const customerShop=read('customer/src/main/java/com/queuego/customer/CustomerShopScreen.kt');
const merchantApp=read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
ok(design.includes('QG_REMOTE_IMAGE_MAX_SIDE = 1280'),'Shared remote image decode ceiling must remain bounded');
ok(design.includes('QG_REMOTE_IMAGE_MAX_BYTES')&&design.includes('inJustDecodeBounds = true')&&!design.includes('inputStream.use { it.readBytes() }'),'Remote images must be byte bounded and sampled before decode');
ok(
  design.includes('QG_REMOTE_IMAGE_MAX_BASE64_CHARS = ((QG_REMOTE_IMAGE_MAX_BYTES + 2) / 3) * 4') &&
  design.includes("val encodedLength = source.length - comma - 1") &&
  design.includes('encodedLength in 1..QG_REMOTE_IMAGE_MAX_BASE64_CHARS') &&
  !design.includes('QG_REMOTE_IMAGE_MAX_BYTES * 2'),
  'Remote data-URI images must reject oversized Base64 before allocating decoded bytes'
);
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

const orderRealtime=read('shared/src/main/java/com/queuego/shared/NativeOrderRealtime.kt');
ok(
  orderRealtime.includes('NATIVE_ORDER_REALTIME_MAX_CHARS = 256 * 1024') &&
  orderRealtime.includes('if (text.length > NATIVE_ORDER_REALTIME_MAX_CHARS)') &&
  orderRealtime.includes('.conflate()'),
  'Shared order Realtime must bound each websocket message while conflating invalidation bursts'
);

const sharedApi=read('shared/src/main/java/com/queuego/shared/QueueGoNativeApi.kt');
const nativeAuthApi=read('shared/src/main/java/com/queuego/shared/NativeAuthApi.kt');
const riderApiMemory=read('rider/src/main/java/com/queuego/rider/QueueGoApi.kt');
const customerApiMemory=read('customer/src/main/java/com/queuego/customer/CustomerApi.kt');
const store=read('rider/src/main/java/com/queuego/rider/SessionStore.kt');
ok(store.includes('RIDER_REGISTRATION_DRAFT_MAX_JSON_CHARS = 16 * 1024 * 1024'),'Rider registration plaintext draft must have an aggregate heap bound');
ok(store.includes('RIDER_REGISTRATION_DRAFT_MAX_FILE_BYTES = 24L * 1024L * 1024L'),'Rider encrypted draft file must be bounded before read/decrypt');
ok(
  store.includes('RIDER_SESSION_MAX_JSON_BYTES = 48 * 1024') &&
  store.includes('RIDER_ENCRYPTED_SMALL_PREF_MAX_CHARS = 64 * 1024') &&
  store.includes('json.toByteArray(Charsets.UTF_8).size <= RIDER_SESSION_MAX_JSON_BYTES') &&
  store.includes('encrypted.length <= RIDER_ENCRYPTED_SMALL_PREF_MAX_CHARS') &&
  store.includes('RIDER_HOME_SNAPSHOT_MAX_JSON_CHARS = 1024 * 1024') &&
  store.includes('RIDER_HOME_SNAPSHOT_MAX_ENCRYPTED_CHARS = 2 * 1024 * 1024') &&
  store.includes('boundedEncryptedPref("session", RIDER_ENCRYPTED_SMALL_PREF_MAX_CHARS)') &&
  store.includes('boundedEncryptedPref("home_snapshot", RIDER_HOME_SNAPSHOT_MAX_ENCRYPTED_CHARS)') &&
  store.includes('if (plaintext.length > RIDER_HOME_SNAPSHOT_MAX_JSON_CHARS) return false'),
  'Rider session/UI/camera ciphertext and home snapshot serialization must stay bounded before decrypt/encrypt heap allocation'
);
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

const riderAppForProofIo=read('rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
ok(
  riderAppForProofIo.includes('val pendingProofStoreMutex = remember(auth.user.id) { Mutex() }') &&
  riderAppForProofIo.includes('pendingProofStoreMutex.withLock { block() }') &&
  riderAppForProofIo.includes('Dispatchers.IO'),
  'Rider proof reservation, verification and URI durability I/O must stay serialized off Main'
);
ok(
  riderAppForProofIo.includes('scope.launch {') &&
  riderAppForProofIo.includes('pendingProofStore.promoteCapture(auth.user.id, RIDER_PROOF_SLOT_DELIVERY)') &&
  riderAppForProofIo.includes('pendingProofIo {'),
  'Rider camera result proof promotion must run through the serialized IO path'
);
ok(
  riderAppForProofIo.includes('val saved = pendingProofIo {') &&
  riderAppForProofIo.includes('pendingProofStore.loadVerification(auth.user.id)') &&
  !riderAppForProofIo.includes('verifyCollection') &&
  !riderAppForProofIo.includes('resumeCollection ='),
  'Rider verification recovery must stay serialized off Main and must not restore a removed collection sub-flow'
);
ok(
  riderAppForProofIo.includes('Server mutation already committed; stale local proof is reconciled later.') &&
  riderAppForProofIo.includes('Server return already committed; local proof cleanup is recoverable.'),
  'Rider post-commit local proof cleanup must remain non-fatal after authoritative server success'
);

const riderRoot=read('rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
const riderMessageBadge=read('rider/src/main/java/com/queuego/rider/RiderMessageBadge.kt');
ok(
  riderMessageBadge.includes('class RiderMessageSeenStore') &&
  riderMessageBadge.includes('withContext(Dispatchers.IO)') &&
  riderMessageBadge.includes('prefs().getLong(userId, 0L)') &&
  riderMessageBadge.includes('prefs().edit().putLong(userId, at).apply()') &&
  riderRoot.includes('badgeSeenStore.markSeen(auth.user.id, now)') &&
  riderRoot.includes('badgeSeenStore.seenAt(auth.user.id)') &&
  !riderRoot.includes('badgePreferences.getLong') &&
  !riderRoot.includes('badgePreferences.edit().putLong'),
  'Rider message seen SharedPreferences reads/writes must stay off Main polling and inbox callbacks'
);

const riderAcceptance=read('rider/src/main/java/com/queuego/rider/RiderAcceptanceMode.kt');
const riderStopAfterWork=read('rider/src/main/java/com/queuego/rider/RiderStopAfterWork.kt');
ok(
  riderAcceptance.includes('suspend fun autoAcceptEnabled') &&
  riderAcceptance.includes('withContext(Dispatchers.IO)') &&
  riderStopAfterWork.includes('suspend fun pending') &&
  riderStopAfterWork.includes('withContext(Dispatchers.IO)') &&
  !riderRoot.includes('mutableStateOf(acceptanceModeStore.autoAcceptEnabled(auth.user.id))') &&
  !riderRoot.includes('mutableStateOf(stopAfterWorkStore.pending(auth.user.id))'),
  'Rider Auto Accept and Stop-after-work preferences must never read SharedPreferences during Compose state creation'
);
ok(
  riderRoot.includes('dispatchPreferencesHydrated') &&
  riderRoot.includes('if (!dispatchPreferencesHydrated) return@LaunchedEffect') &&
  riderRoot.includes('dispatchPreferenceWriteMutex = remember(auth.user.id) { Mutex() }') &&
  riderRoot.includes('dispatchPreferenceWriteMutex.withLock') &&
  riderRoot.includes('enabled = dispatchPreferencesHydrated && !actionBusy'),
  'Rider dispatch automation must wait for preference restore and serialize preference writes before Auto Accept/Stop-after-work can act'
);

const riderChat=read('rider/src/main/java/com/queuego/rider/RiderChat.kt');
ok(
  riderChat.includes('fun openCommunityGuidelines()') &&
  riderChat.includes('runCatching {') &&
  riderChat.includes('message = "เปิดกติกาชุมชนไม่สำเร็จ"') &&
  !riderChat.includes('TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW'),
  'Rider chat external guideline links must fail closed instead of crashing when Android has no handler'
);
ok(
  riderChat.includes('quickMessages = withContext(Dispatchers.IO) { quickStore.load() }') &&
  riderChat.includes('scope.launch { withContext(Dispatchers.IO) { quickStore.save(next) } }') &&
  !riderChat.includes('mutableStateOf(quickStore.load())') &&
  !riderChat.includes('onSave = { quickMessages = it; quickStore.save(it) }'),
  'Rider chat quick-message SharedPreferences/JSON load and save must stay off Main'
);

ok(
  riderChat.includes('RIDER_CHAT_HISTORY_MAX_ROWS = 1_000') &&
  riderChat.includes('&order=created_at.desc&limit=" + RIDER_CHAT_HISTORY_MAX_ROWS') &&
  riderChat.includes('for (i in rows.length() - 1 downTo 0)') &&
  !riderChat.includes('// Match the web reader: retain the server history cap, not a client first-100 cutoff.'),
  'Rider chat history must keep a client-side row ceiling while rendering the newest bounded window chronologically'
);

const riderVoiceAssistant=read('rider/src/main/java/com/queuego/rider/RiderVoiceAssistant.kt');
ok(
  riderVoiceAssistant.includes('engine = runCatching { TextToSpeech(context.applicationContext, this) }.getOrNull()') &&
  riderVoiceAssistant.includes('val configured = runCatching {') &&
  riderVoiceAssistant.includes('runCatching { engine?.speak(') &&
  riderVoiceAssistant.includes('runCatching { current.shutdown() }'),
  'Rider optional TTS must fail closed on OEM TextToSpeech initialization/configuration/speak errors'
);
ok(
  riderVoiceAssistant.includes('fun state(userId: String): StateFlow<Boolean>') &&
  riderVoiceAssistant.includes('suspend fun hydrate(context: Context, userId: String)') &&
  riderVoiceAssistant.includes('suspend fun setEnabled(context: Context, userId: String, enabled: Boolean)') &&
  riderVoiceAssistant.includes('withContext(Dispatchers.IO)') &&
  rider.includes('RiderVoiceAssistantSettings.hydrate(context, auth.user.id)') &&
  !rider.includes('RiderVoiceAssistantSettings.enabled(context, auth.user.id)'),
  'Rider voice-assistant preference reads/writes must hydrate through IO instead of SharedPreferences access during composition'
);

const riderNotificationPolicy=read('rider/src/main/java/com/queuego/rider/RiderNotificationPolicy.kt');
ok(
  riderNotificationPolicy.includes('internal suspend fun consumeRiderAlert') &&
  riderNotificationPolicy.includes('withContext(Dispatchers.IO)') &&
  riderNotificationPolicy.includes('.commit()'),
  'Rider durable alert receipts must keep synchronous commit off Main without weakening duplicate-alert prevention'
);

const riderEvidencePreview=read('rider/src/main/java/com/queuego/rider/RiderEvidencePreview.kt');
const riderRegistrationImage=read('rider/src/main/java/com/queuego/rider/RiderRegistrationImage.kt');
const riderQuickMessages=read('rider/src/main/java/com/queuego/rider/RiderQuickMessages.kt');
ok(
  riderQuickMessages.includes('RIDER_QUICK_MESSAGES_MAX_JSON_CHARS = 8 * 1024') &&
  riderQuickMessages.includes('if (raw.length > RIDER_QUICK_MESSAGES_MAX_JSON_CHARS)') &&
  riderQuickMessages.includes('prefs.edit().remove(key).apply()'),
  'Rider quick-message local JSON must fail closed before parsing corrupted oversized state'
);

const riderSupport=read('rider/src/main/java/com/queuego/rider/RiderSupportScreen.kt');
ok(
  riderSupport.includes('RIDER_SUPPORT_EVIDENCE_MAX_BYTES = 5 * 1024 * 1024') &&
  riderSupport.includes('RIDER_SUPPORT_SIGNED_RESPONSE_MAX_BYTES = 256 * 1024') &&
  riderSupport.includes('readRiderSupportBounded') &&
  !riderSupport.includes('.use { it.readBytes() }') &&
  !riderSupport.includes('bufferedReader().use { it.readText() }'),
  'Rider support evidence uploads and signed-URL responses must be bounded before heap allocation'
);
ok(
  riderEvidencePreview.includes('DisposableEffect(decoded?.bitmap)') &&
  riderEvidencePreview.includes('owned.recycle()'),
  'Rider evidence preview must recycle owned Bitmaps when a proof changes or leaves composition'
);
ok(
  riderRegistrationImage.includes('RIDER_REGISTRATION_IMAGE_DECODE_MAX_SIDE = 1400') &&
  riderRegistrationImage.includes('maxOf(bounds.outWidth, bounds.outHeight) / sample > RIDER_REGISTRATION_IMAGE_DECODE_MAX_SIDE') &&
  riderRegistrationImage.includes('RIDER_REGISTRATION_IMAGE_DECODE_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)') &&
  riderRegistrationImage.includes('val transformed = android.graphics.Bitmap.createBitmap(') &&
  riderRegistrationImage.includes('if (scale < 1f) postScale(scale, scale)') &&
  !riderRegistrationImage.includes('Bitmap.createScaledBitmap(') &&
  !riderRegistrationImage.includes('val upright=') &&
  !riderRegistrationImage.includes('> 2048') &&
  !riderRegistrationImage.includes('> 2800'),
  'Rider registration image decode must cap at final output scale and avoid duplicate upright/scaled Bitmap peak memory'
);

ok(
  riderRegistrationImage.includes('RIDER_REGISTRATION_PDF_INPUT_MAX_BYTES = 5 * 1024 * 1024') &&
  riderRegistrationImage.includes('RIDER_REGISTRATION_IMAGE_INPUT_MAX_BYTES = 8 * 1024 * 1024') &&
  riderRegistrationImage.includes('RIDER_REGISTRATION_IMAGE_OUTPUT_MAX_BYTES = 4 * 1024 * 1024') &&
  riderRegistrationImage.includes('RIDER_REGISTRATION_IMAGE_MAX_BASE64_CHARS') &&
  riderRegistrationImage.includes('RIDER_REGISTRATION_PDF_MAX_BASE64_CHARS') &&
  riderRegistrationImage.includes('prefix.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))') &&
  riderRegistrationImage.includes('require(output.size() + count <= limit)') &&
  riderRegistrationImage.includes('require(output.size() <= RIDER_REGISTRATION_IMAGE_OUTPUT_MAX_BYTES)') &&
  !riderRegistrationImage.includes('output.size() <= 8 * 1024 * 1024'),
  'Rider registration must cap PDF input and resized-image output before Base64 expansion'
);

const reg=read('rider/src/main/java/com/queuego/rider/RiderRegistrationScreen.kt');
ok(reg.includes('val before = documents')&&reg.includes('documents = before'),'Oversized/failed Rider document addition must rollback from in-memory form state');
ok(
  reg.includes('RIDER_REGISTRATION_PREVIEW_MAX_BASE64_CHARS = 8 * 1024 * 1024') &&
  reg.includes('riderRegistrationPreviewDecodeGate = Semaphore(1)') &&
  reg.includes('riderRegistrationPreviewDecodeGate.withPermit') &&
  reg.includes('DisposableEffect(image)') &&
  reg.includes('owned.recycle()'),
  'Rider registration previews must keep Base64/Bitmap peak memory serialized and recycle owned Bitmaps'
);

const accountDeletion=read('shared/src/main/java/com/queuego/shared/QueueGoAccountDeletion.kt');
ok(
  accountDeletion.includes('ACCOUNT_DELETE_RESPONSE_MAX_BYTES = 256 * 1024') &&
  accountDeletion.includes('readAccountDeleteResponseBounded') &&
  !accountDeletion.includes('BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()'),
  'Shared account deletion responses must be byte bounded before JSON parsing'
);

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

const customerCheckoutRecovery=read('customer/src/main/java/com/queuego/customer/CustomerCheckoutRecovery.kt');
const customerMarketScreen=read('customer/src/main/java/com/queuego/customer/QueueGoMarketScreen.kt');
const customerLaundryScreen=read('customer/src/main/java/com/queuego/customer/QueueGoLaundryScreen.kt');
ok(
  customerCheckoutRecovery.includes('withContext(Dispatchers.IO) { journal.read() }') &&
  customerCheckoutRecovery.includes('withContext(Dispatchers.IO) { journal.write(created) }') &&
  customerCheckoutRecovery.includes('withContext(Dispatchers.IO) { journal.complete(pending) }'),
  'Customer Food/Market/Laundry durable checkout journal reads and fsync commits must stay off Main'
);
ok(
  customerMarketScreen.includes('withContext(Dispatchers.IO) { cartStore.load() }') &&
  customerMarketScreen.includes('guestStore?.revisionForMerge()') &&
  customerMarketScreen.includes('withContext(Dispatchers.IO) {') &&
  customerMarketScreen.includes('commitMarketGuestMerge('),
  'Customer Market guest/account durable cart merge must stay on IO instead of Main'
);

ok(
  customerMarketScreen.includes('checkoutPending = withContext(Dispatchers.IO) { cartStore.hasPendingCheckout() }') &&
  customerMarketScreen.includes('withContext(Dispatchers.IO) { cartStore.save(cart) }') &&
  !customerMarketScreen.includes('mutableStateOf(cartStore.hasPendingCheckout())') &&
  !customerMarketScreen.includes('if (restoredCart && !checkoutPending) cartStore.save(cart)'),
  'Customer Market pending-state reads and regular cart persistence must stay off Main'
);
ok(
  customerLaundryScreen.includes('checkoutPending = withContext(Dispatchers.IO) { journal.hasPending() }') &&
  !customerLaundryScreen.includes('mutableStateOf(journal.hasPending())'),
  'Customer Laundry pending checkout state must be read off Main during restore and reconciliation'
);

const customerPicker=read('customer/src/main/java/com/queuego/customer/CustomerLocationPicker.kt');
ok(customerPicker.includes('mapReady')&&customerPicker.includes('delay(150)'),'Customer location permission must remain delayed until Longdo renderer is ready');

const riderNavigationIntegration=read('rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
ok(
  riderNavigationIntegration.includes('withContext(Dispatchers.IO) {') &&
  riderNavigationIntegration.includes('sessionStore.loadMapCamera(stateKey)') &&
  riderNavigationIntegration.includes('sessionStore.loadMapViewMode(stateKey)') &&
  riderNavigationIntegration.includes('pendingCameraPersistence = doubleArrayOf(') &&
  riderNavigationIntegration.includes('pendingViewModePersistence = NavigationViewMode.FREE_PAN.name') &&
  riderNavigationIntegration.includes('delay(250L)') &&
  !riderNavigationIntegration.includes('val initialCamera = remember(stateKey) {\n        sessionStore.loadMapCamera(stateKey)') &&
  !riderNavigationIntegration.includes('sessionStore.saveMapCamera(\n                stateKey,'),
  'Rider navigation-map camera/view-mode Keystore restore and high-frequency persistence must stay off Main'
);

const riderNavigationRouteClient=read('rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/QueueGoNavigationRouteClient.kt');
const legacyLongdoRoutePath=path.join(root,'rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/LongdoNavigationRouteClient.kt');
ok(
  riderNavigationRouteClient.includes('QUEUEGO_ROUTE_RESPONSE_MAX_BYTES = 4 * 1024 * 1024') &&
  riderNavigationRouteClient.includes('QUEUEGO_ROUTE_MAX_STEPS = 2_048') &&
  riderNavigationRouteClient.includes('QUEUEGO_ROUTE_MAX_POINTS = 12_000') &&
  riderNavigationRouteClient.includes('readQueueGoRouteResponseBounded') &&
  !riderNavigationRouteClient.includes('response.body?.string()') &&
  !fs.existsSync(legacyLongdoRoutePath),
  'Rider QueueGo route HTTP/JSON memory must stay bounded and legacy external route client must stay removed'
);

const riderNavigationMapRuntime=read('rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');
ok(
  riderNavigationMapRuntime.includes('MapLibre.getInstance') &&
  riderNavigationMapRuntime.includes('RiderNavigationMapRegistry') &&
  riderNavigationMapRuntime.includes('holder.retainAcrossVisibilityLoss()') &&
  riderNavigationMapRuntime.includes('current?.dispose()') &&
  !riderNavigationMapRuntime.includes('PixelCopy') &&
  !riderNavigationMapRuntime.includes('Bitmap.createBitmap('),
  'Rider MapLibre continuity must retain one lifecycle holder without PixelCopy snapshot bitmap allocation'
);

ok(
  riderNavigationMapRuntime.includes('private fun resetSurface()') &&
  riderNavigationMapRuntime.includes('riderAnimator?.cancel()') &&
  riderNavigationMapRuntime.includes('routeLines.clear()') &&
  riderNavigationMapRuntime.includes('stopMarkers.clear()') &&
  riderNavigationMapRuntime.includes('demandMarkers.clear()') &&
  riderNavigationMapRuntime.includes('riderMarker = null') &&
  riderNavigationMapRuntime.includes('map = null') &&
  riderNavigationMapRuntime.includes('view?.onDestroy()') &&
  riderNavigationMapRuntime.includes('view = null') &&
  !riderNavigationMapRuntime.includes('markerBitmaps') &&
  !riderNavigationMapRuntime.includes('Bitmap.createBitmap('),
  'Rider MapLibre dispose must drop renderer, marker and view refs without legacy bitmap caches'
);

const riderLongdoMap=read('rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
ok(
  riderLongdoMap.includes('withContext(Dispatchers.IO) {') &&
  riderLongdoMap.includes('sessionStore.loadMapCamera(stateKey)') &&
  riderLongdoMap.includes('pendingCameraPersistence') &&
  riderLongdoMap.includes('sessionStore.saveMapCamera(stateKey, camera)') &&
  riderLongdoMap.includes('persistenceHydrated') &&
  !riderLongdoMap.includes('mutableStateOf(sessionStore.loadMapCamera(stateKey))'),
  'Rider MapLibre camera restore/persistence must keep SessionStore I/O off Main and map callbacks'
);
const legacyRiderLongdoMapPath=path.join(root,'rider/src/main/java/com/queuego/rider/LongdoRiderMap.kt');
ok(
  !fs.existsSync(legacyRiderLongdoMapPath) &&
  riderNavigationMapRuntime.includes('stopMarkers.clear()') &&
  riderNavigationMapRuntime.includes('demandMarkers.clear()') &&
  riderNavigationMapRuntime.includes('riderMarker = null') &&
  riderNavigationMapRuntime.includes('view?.onDestroy()') &&
  !riderNavigationMapRuntime.includes('markerBitmaps') &&
  !riderNavigationMapRuntime.includes('stopBitmaps'),
  'Rider MapLibre renderer must release marker references without restoring legacy app-owned bitmap caches'
);

const sharedManifest=read('shared/src/main/AndroidManifest.xml');
const riderManifest=read('rider/src/main/AndroidManifest.xml');
ok(sharedManifest.includes('FOREGROUND_SERVICE_MICROPHONE')&&sharedManifest.includes('foregroundServiceType="microphone"'),'Voice microphone foreground service declaration must match Android 14+ type rules');
ok(riderManifest.includes('FOREGROUND_SERVICE_LOCATION')&&riderManifest.includes('foregroundServiceType="location"'),'Rider navigation foreground service must keep location type/permission');
ok(riderManifest.includes('FOREGROUND_SERVICE_SPECIAL_USE')&&riderManifest.includes('foregroundServiceType="specialUse"'),'Rider Google Maps return foreground service must keep special-use type/permission');

const returnService=read('rider/src/main/java/com/queuego/rider/RiderReturnService.kt');
const navService=read('rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt');
const voiceService=read('shared/src/main/java/com/queuego/shared/NativeVoiceForegroundService.kt');
const voiceController=read('shared/src/main/java/com/queuego/shared/NativeVoiceCallController.kt');
const voiceRealtime=read('shared/src/main/java/com/queuego/shared/NativeVoiceRealtime.kt');
const voicePeer=read('shared/src/main/java/com/queuego/shared/NativeVoicePeer.kt');
ok(
  voiceRealtime.includes('NATIVE_VOICE_REALTIME_MAX_CHARS = 512 * 1024') &&
  voiceRealtime.includes('if (text.length > NATIVE_VOICE_REALTIME_MAX_CHARS)') &&
  voiceRealtime.includes('if (message.length > NATIVE_VOICE_REALTIME_MAX_CHARS) return false'),
  'Voice Realtime signaling must reject oversized inbound and outbound messages before JSON/downstream buffering'
);
ok(
  voicePeer.includes('NATIVE_VOICE_SDP_MAX_CHARS = 256 * 1024') &&
  voicePeer.includes('NATIVE_VOICE_ICE_CANDIDATE_MAX_CHARS = 16 * 1024') &&
  voicePeer.includes('NATIVE_VOICE_PENDING_ICE_MAX_COUNT = 256') &&
  voicePeer.includes('pendingRemoteIce.size < NATIVE_VOICE_PENDING_ICE_MAX_COUNT'),
  'WebRTC SDP/ICE payloads and pre-description ICE buffering must remain bounded'
);
ok(
  returnService.includes('runCatching { ContextCompat.startForegroundService') &&
  returnService.includes('start_foreground_failed') &&
  returnService.includes('withContext(Dispatchers.IO)') &&
  returnService.includes('SessionStore(applicationContext).load()') &&
  returnService.includes('serviceScope.cancel()') &&
  !returnService.includes('val cached = SessionStore(context.applicationContext).load()') &&
  !returnService.includes('val cached = SessionStore(applicationContext).load()'),
  'Rider Google Maps return service must start while foreground but keep encrypted session decrypt off caller/service Main threads'
);
ok(navService.includes('start_foreground_failed')||navService.includes('foreground_start_rejected'),'Rider location FGS must handle Android start rejection');
ok(
  voiceService.includes('context.startForegroundService(') &&
  voiceService.includes('catch (failure: Exception)') &&
  voiceService.includes('next.ready.completeExceptionally(failure)') &&
  voiceController.includes('try { prepareForeground() } catch (failure: Exception)') &&
  voiceController.includes('withTimeout(10_000L) { prepareForeground().await() }'),
  'Voice microphone FGS launch rejection must propagate through a caught Deferred/controller path instead of crashing the process'
);

console.log(JSON.stringify({checks,failures:0,scope:'three-app native crash/ANR/OOM/process-kill prevention contracts; static source gate, physical OEM coverage remains separate'}));
