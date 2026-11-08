Warning: truncated output (original token count: 46595)
Total output lines: 1987

const fs=require('fs'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const nativePilotWorkflow=fs.readFileSync(path.join(__dirname,'../.github/workflows/build-native-rider-pilot.yml'),'utf8');
const mapEngineWorkflow=fs.readFileSync(path.join(__dirname,'../.github/workflows/queuego-map-engine-buriram.yml'),'utf8');
const rideReleaseCandidateBranch = 'work-native-rider-nav-unified-offline-20261030';
const requiredFullNativeGates = [
  'continuous-dispatch-races', 'route-bundle-stack-races',
  'passenger-ride-races', 'credit-races', 'express-races',
  'map-display-only', 'navigation-map-runtime', 'maplibre-osm-runtime'
];
for (const gate of requiredFullNativeGates) {
  const section = nativePilotWorkflow.match(new RegExp('^  '+gate+':\\n    if: ([^\\n]+)', 'm'));
  ok(
    !!section && section[1].includes("github.ref == 'refs/heads/"+rideReleaseCandidateBranch+"'") &&
    section[1].includes("github.head_ref == '"+rideReleaseCandidateBranch+"'"),
    'Native launch candidate must execute '+gate+' on both direct push and pull-request checks, not skip it'
  );
}
const roadTestPublish = nativePilotWorkflow.match(/^  publish-rider-road-test:\n    if: ([^\n]+)/m);
ok(!!roadTestPublish && !roadTestPublish[1].includes(rideReleaseCandidateBranch),
   'Native launch candidate must not publish an APK automatically before real-device release acceptance');

ok(nativePilotWorkflow.includes('group: queuego-native-${{ github.ref }}')&&nativePilotWorkflow.includes('cancel-in-progress: false')&&mapEngineWorkflow.includes('group: queuego-map-engine-${{ github.ref }}')&&mapEngineWorkflow.includes('cancel-in-progress: false'),'Native Pilot and Map Engine exact-head jobs must finish when the successor advances, while newer work waits');


const parallelRoleStart=nativePilotWorkflow.indexOf('  parallel-role-smoke:\n');
const parallelRoleEnd=nativePilotWorkflow.indexOf('\n  native-pilot:',parallelRoleStart);
const parallelRoleJob=parallelRoleStart >= 0 && parallelRoleEnd > parallelRoleStart
  ? nativePilotWorkflow.slice(parallelRoleStart,parallelRoleEnd) : '';
ok(
  parallelRoleJob.includes('fail-fast: false') &&
  parallelRoleJob.includes('max-parallel: 3') &&
  parallelRoleJob.includes('role: [customer, merchant, rider]') &&
  parallelRoleJob.includes(':testDebugUnitTest') &&
  parallelRoleJob.includes(':assembleDebug') &&
  parallelRoleJob.includes('verify-16kb-page-size.py') &&
  parallelRoleJob.includes('github.event.pull_request.head.sha || github.sha') &&
  parallelRoleJob.includes('if: always()'),
  'Native Pilot must run independent Customer/Merchant/Rider JVM+APK jobs concurrently from exact HEAD, retaining evidence even when one role fails'
);

for(const p of [
  'settings.gradle.kts',
  'build.gradle.kts',
  'shared/build.gradle.kts',
  'shared/src/main/java/com/queuego/shared/NativeExitDiagnostics.kt',
  'shared/src/main/java/com/queuego/shared/QueueGoDesign.kt',
  'shared/src/main/java/com/queuego/shared/NativeChatImage.kt',
  'shared/src/main/java/com/queuego/shared/QueueGoAccountDeletion.kt',
  'shared/src/main/java/com/queuego/shared/QueueGoPlaceSearch.kt',
  'shared/src/main/java/com/queuego/shared/NativeSupportTickets.kt',
  'customer/build.gradle.kts',
  'customer/src/main/AndroidManifest.xml',
  'merchant/build.gradle.kts',
  'rider/build.gradle.kts',
  'rider/src/main/AndroidManifest.xml',
  'rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt',
  'rider/src/main/java/com/queuego/rider/QueueGoApi.kt',
  'rider/src/main/java/com/queuego/rider/RiderPush.kt',
  'rider/src/main/res/drawable/qg_notification.xml',
  'rider/src/main/java/com/queuego/rider/RiderOrderDetailsSheet.kt',
  'rider/src/main/java/com/queuego/rider/RiderChat.kt',
  'rider/src/main/java/com/queuego/rider/RiderMessagesScreen.kt',
  'rider/src/main/java/com/queuego/rider/RiderEarningsScreen.kt',
  'rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt',
  'rider/src/main/java/com/queuego/rider/RiderSupportScreen.kt',
  'qa/capture-rider-launch.py',
  'customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt',
  'customer/src/main/java/com/queuego/customer/CustomerApi.kt',
  'customer/src/main/java/com/queuego/customer/CustomerGuestShell.kt',
  'customer/src/main/java/com/queuego/customer/CustomerPromotionScreen.kt',
  'customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt',
  'customer/src/main/java/com/queuego/customer/CustomerCartCheckout.kt',
  'customer/src/main/java/com/queuego/customer/CustomerMarketApi.kt',
  'customer/src/main/java/com/queuego/customer/CustomerLaundryApi.kt',
  'customer/src/main/java/com/queuego/customer/CustomerExtras.kt',
  'customer/src/main/java/com/queuego/customer/CustomerSupportScreen.kt',
  'customer/src/main/java/com/queuego/customer/CustomerChat.kt',
  'customer/src/main/java/com/queuego/customer/CustomerReview.kt',
  'merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantSupportTicketsScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantApi.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantContentCache.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantShopSetupScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantHoursScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantRevenueScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantPromotionScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantNotificationsScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantProductModel.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantPosApi.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantPosOfflineDraftStore.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantProductsScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantCatalogScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantMediaScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantModulesScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantMarketStockScreen.kt',
  'merchant/src/main/AndroidManifest.xml',
  'merchant/src/main/java/com/queuego/merchant/MerchantLaundryApi.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantSupportScreen.kt'
]) ok(fs.existsSync(path.join(root,p)),'missing '+p);

const customerGradle=read('customer/build.gradle.kts');
const merchantGradle=read('merchant/build.gradle.kts');
ok(customerGradle.includes('applicationId = "com.queuego.customer"'),'Customer package id');
ok(merchantGradle.includes('applicationId = "com.queuego.merchant"'),'Merchant package id');
ok(/targetSdk\s*=\s*36/.test(customerGradle)&&/targetSdk\s*=\s*36/.test(merchantGradle),'Customer/Merchant targetSdk 36');
const gradle=read('rider/build.gradle.kts');
ok(gradle.includes('applicationId = "com.queuego.rider"'),'Rider package id');
ok(/compileSdk\s*=\s*36/.test(gradle),'compileSdk 36');
ok(/targetSdk\s*=\s*36/.test(gradle),'targetSdk 36');

const riderIcon=read('rider/src/main/res/drawable/ic_queuego_foreground.xml');
const riderAdaptive33=read('rider/src/main/res/mipmap-anydpi-v33/ic_queuego_launcher.xml');
ok(riderIcon.includes('#FFFFFFFF')&&riderIcon.includes('fillType="evenOdd"')&&read('rider/src/main/res/drawable/ic_queuego_background.xml').includes('#EF1717'),
   'Rider launcher must use the approved white circular Q on red');
ok(riderAdaptive33.includes('<monochrome android:drawable="@drawable/ic_queuego_monochrome"'),
   'Rider launcher must expose Android 13+ monochrome icon support');
for(const density of ['mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi']){
  ok(fs.existsSync(path.join(root,'rider/src/main/res/mipmap-'+density+'/ic_queuego_launcher.png')),
     'Rider launcher PNG missing for '+density);
  ok(!fs.existsSync(path.join(root,'rider/src/main/res/mipmap-'+density+'/ic_queuego_launcher.xml')),
     'Rider density must keep one canonical launcher resource for '+density);
}
const playStoreIcon=fs.readFileSync(path.join(root,'branding/rider-play-store-512.png'));
ok(playStoreIcon.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])),'Rider Play Store icon must be PNG');
ok(playStoreIcon.readUInt32BE(16)===512&&playStoreIcon.readUInt32BE(20)===512,'Rider Play Store icon must be exactly 512x512');

const all=[];
(function walk(dir){
  for(const name of fs.readdirSync(dir)){
    const p=path.join(dir,name),st=fs.statSync(p);
    if(st.isDirectory())walk(p); else if(/\.(kt|kts|xml)$/.test(name))all.push(fs.readFileSync(p,'utf8'));
  }
})(root);
const source=all.join('\n');

const sharedDesign=read('shared/src/main/java/com/queuego/shared/QueueGoDesign.kt');
const sharedChatImage=read('shared/src/main/java/com/queuego/shared/NativeChatImage.kt');
const exitDiagnostics=read('shared/src/main/java/com/queuego/shared/NativeExitDiagnostics.kt');
const customerMainForExit=read('customer/src/main/java/com/queuego/customer/MainActivity.kt');
const merchantMainForExit=read('merchant/src/main/java/com/queuego/merchant/MainActivity.kt');
const riderMainForExit=read('rider/src/main/java/com/queuego/rider/MainActivity.kt');
const merchantImageUploadForMemory=read('merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt');
ok(
  sharedDesign.includes('QG_REMOTE_IMAGE_MAX_BYTES') &&
  sharedDesign.includes('readQueueGoImageLimited') &&
  sharedDesign.includes('inJustDecodeBounds = true') &&
  sharedDesign.includes('inSampleSize = sample') &&
  sharedDesign.includes('DisposableEffect(image)') &&
  !sharedDesign.includes('inputStream.use { it.readBytes() }'),
  'Shared remote images must be bounded, sampled before decode and recycled instead of risking all-role OOM'
);
ok(
  sharedDesign.includes('val attempts = if (source.startsWith("https://")) 2 else 1') &&
  sharedDesign.includes('for (attempt in 0 until attempts)') &&
  sharedDesign.includes('if (attempt + 1 < attempts) delay(600)'),
  'Shared HTTPS images may retry one transient failure without unbounded polling'
);
ok(
  sharedDesign.includes('context.noBackupFilesDir, "queuego_remote_images_v2"') &&
  sharedDesign.includes('legacyQueueGoRemoteImageCacheDir') &&
  !sharedDesign.includes('QG_REMOTE_IMAGE_DISK_CACHE_TTL_MS'),
  'Shared downloaded images must survive normal cache cleanup and must not expire every 24 hours'
);
ok(
  merchantMainForExit.includes('Restore Merchant session silently.') &&
  merchantMainForExit.includes('Box(Modifier.fillMaxSize())') &&
  !merchantMainForExit.includes('Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n                CircularProgressIndicator()'),
  'Merchant entry must restore the saved session without showing loading chrome'
);
ok(
  sharedChatImage.includes('DisposableEffect(bitmap)') &&
  sharedChatImage.includes('owned.recycle()'),
  'Shared chat images must release Bitmap memory when messages leave composition'
);
ok(
  sharedChatImage.includes('context.noBackupFilesDir, "queuego_chat_images_v1"') &&
  sharedChatImage.includes('readNativeChatImageCache(context, source) ?: run') &&
  sharedChatImage.includes('writeNativeChatImageCache(context, source, it)') &&
  !sharedChatImage.includes('else CircularProgressIndicator()'),
  'Shared chat HTTPS images must reuse durable private cache and load without spinner chrome'
);
ok(
  merchantImageUploadForMemory.includes('inJustDecodeBounds = true') &&
  merchantImageUploadForMemory.includes('inSampleSize = sample') &&
  merchantImageUploadForMemory.indexOf('inJustDecodeBounds = true') <
    merchantImageUploadForMemory.indexOf('val decoded = BitmapFactory.decodeByteArray'),
  'Merchant uploads must sample camera/gallery images before allocating the full Bitmap'
);
ok(
  exitDiagnostics.includes('getHistoricalProcessExitReasons') &&
  exitDiagnostics.includes('REASON_LOW_MEMORY') &&
  exitDiagnostics.includes('REASON_ANR') &&
  !exitDiagnostics.includes('traceInputStream') &&
  customerMainForExit.includes('NativeExitDiagnostics.recordPreviousProcessExit(this, "customer")') &&
  merchantMainForExit.includes('NativeExitDiagnostics.recordPreviousProcessExit(this, "merchant")') &&
  riderMainForExit.includes('NativeExitDiagnostics.recordPreviousProcessExit(this, "rider")'),
  'All three native apps must record coarse Android exit reasons without collecting exit traces'
);

ok(!/android\.webkit\.WebView|<WebView\b|loadUrl\(/.test(source),'native apps must not use WebView UI');
ok(source.includes('expectedRole = "customer"')&&source.includes('expectedRole = "shop"'),'Customer/Merchant must validate real QueueGo roles');
const customerMain=read('customer/src/main/java/com/queuego/customer/MainActivity.kt');
const merchantMain=read('merchant/src/main/java/com/queuego/merchant/MainActivity.kt');
ok(customerMain.includes('QueueGoCustomerApp(')&&customerMain.includes('setContent')&&!customerMain.includes('QueueGoRoleNativeApp'),'Customer must launch full native app, not role shell');
ok(merchantMain.includes('QueueGoMerchantApp(')&&merchantMain.includes('setContent')&&!merchantMain.includes('QueueGoRoleNativeApp'),'Merchant must launch full native app, not role shell');
ok(source.includes('queuego_place_cash_order'),'Customer native checkout must use production cash-order RPC');
const customerCartCheckout=read('customer/src/main/java/com/queuego/customer/CustomerCartCheckout.kt');
ok(
  !customerCartCheckout.includes('if (!mapReady) {\n                            CircularProgressIndicator('),
  'Customer checkout QueueGo Map readiness must stay visually silent'
);

ok(source.includes('"cart" -> CustomerCartScreen')&&source.includes('"checkout" -> CustomerCheckoutScreen')&&customerCartCheckout.includes('"ตะกร้าสินค้า"')&&customerCartCheckout.includes('"ไปชำระเงิน"')&&customerCartCheckout.includes('"ยืนยันคำสั่งซื้อ"')&&customerCartCheckout.includes('height(checkoutMapHeight)')&&customerCartCheckout.includes('.coerceIn(200.dp, 300.dp)')&&customerCartCheckout.includes('QgLocationPickerMap')&&customerCartCheckout.includes('"ยืนยันสั่งซื้อ"'),'Customer native must preserve separate Production cart and checkout screens with checkout QueueGo Map and fixed action dock');
ok(
  !customerCartCheckout.includes('QgPlaceSearchPanel(') &&
  !customerCartCheckout.includes('searchPlaces: suspend') &&
  customerCartCheckout.includes('QgLocationPickerMap(') &&
  customerCartCheckout.includes('recenterPoint = selected?.takeIf') &&
  source.includes('"checkout" -> CustomerCheckoutScreen(') &&
  source.includes('resolveAddress = { latitude, longitude -> api.reverseGeocode(latitude, longitude) },') &&
  read('customer/src/main/java/com/queuego/customer/CustomerLocationPicker.kt').includes('QgPlaceSearchPanel(') &&
  source.includes('"location" -> CustomerLocationPickerScreen(\n                    location = location,\n                    address = address,\n                    busy = busy,\n                    searchPlaces = { query -> api.searchPlaces(query) },') &&
  source.includes('"checkout" -> CustomerCheckoutScreen(\n                    pendingCheckout = pendingCheckoutRecord,\n                    cart = cart,') &&
  source.includes('note = note,\n                    busy = busy,\n                    resolveAddress = { latitude, longitude -> api.reverseGeocode(latitude, longitude) }'),
  'Customer checkout matches Web controls while full location picker retains QueueGo Places search'
);
// Keep Native Ride aligned with the merged Web Production #737 layout. These
// contracts are structural: real location/map and backend flows stay reusable.
const rideScreenWebParity=read('customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt');
const rideStopPickerWebParity=read('customer/src/main/java/com/queuego/customer/CustomerLocationPicker.kt');
const sharedRideMapWebParity=read('shared/src/main/java/com/queuego/shared/QueueGoMap.kt');
ok(
  rideScreenWebParity.includes('rideStop = editing') &&
  rideScreenWebParity.includes('cameraReference = if (editing == "destination") pickup else null') &&
  rideStopPickerWebParity.includes('initialCameraPoint = if (rideDestination && selectedPoint == null)') &&
  sharedRideMapWebParity.includes('holder.renderSelectedPoint(initialPoint?.takeIf { it.valid })'),
  'Ride destination must start over the real pickup without preselecting it as the destination'
);
ok(
  rideStopPickerWebParity.includes('if (rideStop != null) 245.dp') &&
  rideStopPickerWebParity.includes('"pickup" -> "เลือกจุดรับ"') &&
  rideStopPickerWebParity.includes('"destination" -> "เลือกจุดหมาย"') &&
  rideStopPickerWebParity.includes('"pickup" -> "ที่อยู่จุดรับ"') &&
  rideStopPickerWebParity.includes('"destination" -> "ที่อยู่จุดส่ง"') &&
  rideStopPickerWebParity.includes('else Text(if (rideStop != null) "ใช้จุดนี้"') &&
  rideStopPickerWebParity.includes('if (rideDestination || ridePickup && location != null) return@LaunchedEffect') &&
  rideScreenWebParity.includes('height(245.dp)') &&
  rideScreenWebParity.includes('showWebMapControls = true'),
  'Ride pickup/dropoff picker must match the web header, 245dp maps, GPS and save button'
);
ok(
  rideScreenWebParity.includes('heightIn(min = 70.dp)') &&
  rideScreenWebParity.includes('Box(Modifier.size(12.dp).background(markerColor, CircleShape))') &&
  rideScreenWebParity.includes('Text("ค่าโดยสารเงินสด"') &&
  rideScreenWebParity.includes('price.optDouble("distance_km")'),
  'Ride root must use the web location markers and real fare/distance summary'
);

ok(
  rideScreenWebParity.includes('Text("ใช้ตำแหน่งปัจจุบันเป็นจุดรับ"') &&
  rideScreenWebParity.includes('val currentPickupPermission = rememberLauncherForActivityResult(') &&
  !rideScreenWebParity.includes('lastKnownLocation(context)') &&
  rideScreenWebParity.includes('customerRegistrationLocation(context)') &&
  rideScreenWebParity.includes('customerApi.reverseGeocode(coordinates.first, coordinates.second)') &&
  rideScreenWebParity.includes('if (hasLocation(context))'),
  'Web Ride quick-pickup must use the native GPS permission, real coordinates and reverse geocoder'
);

// Current pickup/delivery locations must not silently reuse old cached GPS
// fixes; approximate-only permission must never request the fine GPS provider.
const customerGpsSource=read('customer/src/main/java/com/queuego/customer/CustomerAuthenticationScreen.kt');
ok(
  !rideStopPickerWebParity.includes('lastKnownLocation(context)') &&
  rideStopPickerWebParity.includes('val position = customerRegistrationLocation(context)') &&
  !rideScreenWebParity.includes('lastKnownLocation(context)') &&
  rideScreenWebParity.includes('customerRegistrationLocation(context)'),
  'Customer picker and Ride quick pickup must wait for a fresh GPS fix or allow manual selection'
);
ok(
  customerGpsSource.includes('val fineGranted = ContextCompat.checkSelfPermission(') &&
  customerGpsSource.includes('val coarseGranted = ContextCompat.checkSelfPermission(') &&
  customerGpsSource.includes('if (fineGranted && manager.isProviderEnabled(LocationManager.GPS_PROVIDER))') &&
  customerGpsSource.includes('if ((fineGranted || coarseGranted) &&') &&
  customerGpsSource.includes('manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)'),
  'Approximate-only Android location must not request the fine-only GPS provider'
);

const customerAppGps=read('customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
ok(
  !customerAppGps.includes('lastKnownLocation(context)') &&
  customerAppGps.includes('val p = customerRegistrationLocation(context)') &&
  (customerAppGps.match(/onGps = ::requestGps,/g) || []).length === 3 &&
  customerAppGps.includes('if (grants.values.any { it }) readFreshGps()') &&
  customerAppGps.includes('catch (failure: CancellationException)'),
  'Laundry, Market and checkout GPS must use one fresh bounded provider read and retain manual fallback'
);

ok(!/private fun CartScreen\(/.test(source),'legacy combined Customer cart-checkout implementation must stay removed');
ok(customerCartCheckout.includes('checkoutPending: Boolean')&&customerCartCheckout.includes('enabled = !checkoutPending')&&customerCartCheckout.includes('pendingCheckout: PendingCustomerCheckout?')&&customerCartCheckout.includes('p_expected_delivery_fee'),'Customer pending checkout must lock cart edits and render the persisted Production quote');
ok(source.includes('loadActiveOrder(auth)')&&source.includes('&status=in.(pending,accepted,searching_rider,rider_assigned,preparing,ready,assigned,picked_up,in_progress)'),'Customer checkout must preserve Production active-order guard before creating another order');
ok(source.includes('if (checkoutPending) {')&&source.includes('กรุณาตรวจผลคำสั่งซื้อเดิมก่อนแก้ตะกร้า')&&source.includes('if (!checkoutPending) cart = cart.mapNotNull')&&source.includes('if (!checkoutPending) cart = cart.filterNot'),'Customer pending checkout must block every native cart mutation path');
ok(source.includes('market_public_catalog_v2')&&source.includes('queuego_place_market_order'),'Customer native Market must use production market RPCs');
ok(source.includes('queuego_add_market_order_shops'),'Customer native Market must preserve add-shop flow');
const customerMarketScreen=read('customer/src/main/java/com/queuego/customer/QueueGoMarketScreen.kt');
ok(customerMarketScreen.includes('Text("ตลาดสด", fontSize = 20.sp')&&customerMarketScreen.includes('QgIcon("back"'),'Customer Native Market header must match the Production web page-title/back pattern');
ok(customerMarketScreen.includes('CustomerServiceBanner(')&&customerMarketScreen.includes('R.drawable.qg_market_banner')&&customerMarketScreen.includes('fallbackSubtitle = "ตลาดสดใกล้คุณ สดใหม่ทุกวัน"'),'Customer Native Market must reuse the managed Production service-banner renderer and exact fallback asset');
ok(source.includes('serviceBanner = serviceBanners["market"]')&&source.includes('onBannerLink = ::openBannerLink')&&source.includes('onBannerLink = ::openHomeBannerLink'),'Customer Native Market banner must stay managed and link-aware for guest and authenticated flows');
ok(!customerMarketScreen.includes('height(142.dp)')&&!customerMarketScreen.includes('background(QgRed, RoundedCornerShape(20.dp))'),'Customer Native Market must not regress to the legacy red placeholder banner');
ok(customerMarketScreen.includes('if (!cartPage) {')&&customerMarketScreen.includes('if (cartPage && (cart.isNotEmpty() || checkoutPending))')&&customerMarketScreen.includes('onClick = onOpenCart'),'Market Browse and Cart must separate checkout fields and expose a floating cart route');
ok(customerMarketScreen.includes('cart.groupBy { it.product.shopId }')&&customerMarketScreen.includes('line.quantity < minOf(99, line.product.availablePacks)'),'Market cart must group shops and cap quantities by available packs');
ok(!customerMarketScreen.includes('cart.first().product.marketId != m.id) cart = emptyList()'),'Browsing a different market must never destroy a persisted cart');
ok(
  source.includes('"market", "market-cart" -> MarketNativeScreen(') &&
  source.includes('onRequireLogin = { onLogin(customerGuestPostLoginDestination("market-submit")) }') &&
  !source.includes('onLogin("market-cart")'),
  'Guest Market submit must authenticate then return Home like Web; public Market browse/cart remain available before login'
);

ok(source.includes('queuego_place_laundry_order_v2'),'Customer native Laundry must use production laundry order RPC');
ok(source.includes('notifications?select=id,title,message,type,reference_id,is_read,created_at'),'Customer native notifications must use production notifications table');
const customerNotifications=read('customer/src/main/java/com/queuego/customer/CustomerExtras.kt');
ok(!customerNotifications.includes('LaunchedEffect(unreadIds)')&&!customerNotifications.includes('if (unreadIds.isNotEmpty()) onMarkAll()'),'Customer Notifications must preserve unread state until the owner taps อ่านทั้งหมด or opens an item');
ok(customerNotifications.includes('TextButton(onClick = onMarkAll, enabled = !busy)')&&customerNotifications.includes('Text("แจ้งเตือน", modifier = Modifier.weight(1f), fontSize = 26.sp')&&source.includes('message = null'),'Customer Notifications must match the Web header/read action and suppress sticky read-success banner');
ok(customerNotifications.includes('notifications?user_id=eq." + http.enc(auth.user.id) + "&is_read=eq.false'),'Customer mark-all must stay scoped to the authenticated user unread rows');
ok(source.includes('qg_support_tickets?select=id,category,details,status,order_id,admin_note,created_at,evidence_path')&&source.includes('qg_create_ticket'),'Customer native support must use production ticket RPC and load evidence path');
const customerSupportCacheSource=read('customer/src/main/java/com/queuego/customer/CustomerSupportCache.kt');
const customerSupportScreenSource=read('customer/src/main/java/com/queuego/customer/CustomerSupportScreen.kt');
ok(
  customerSupportCacheSource.includes('context.noBackupFilesDir, "customer_support_cache_v1"') &&
  customerSupportCacheSource.includes('FRESH_MS = 60L * 1000L') &&
  customerSupportScreenSource.includes('cache.read(auth.user.id)') &&
  customerSupportScreenSource.includes('if (cached.fresh)') &&
  customerSupportScreenSource.includes('cache.invalidate(auth.user.id)') &&
  customerSupportScreenSource.includes('refresh(force = true)') &&
  customerSupportScreenSource.includes('cache.write(auth.user.id, fresh)') &&
  customerSupportCacheSource.includes('evidencePath') &&
  customerSupportScreenSource.includes('NativeSupportTicketApi()') &&
  customerSupportScreenSource.includes('customerEvidencePathBelongsTo(path, auth.session.authUserId)') &&
  customerSupportScreenSource.includes('"ดูรูปหลักฐาน"') &&
  customerSupportScreenSource.includes('"/storage/v1/object/sign/qg-evidence/"') &&
  customerSupportScreenSource.indexOf('refresh(force = true)') <
    customerSupportScreenSource.lastIndexOf('message = "ส่งเรื่องให้ทีม QueueGo แล้ว"'),
  'Customer Support history must cache and open account-owned evidence with a signed URL while preserving submit feedback after forced refresh'
);
ok(
  customerSupportScreenSource.includes('ActivityResultContracts.OpenDocument()') &&
  customerSupportScreenSource.includes('evidenceApi.uploadEvidence(') &&
  customerSupportScreenSource.includes('pendingTicketId ?: UUID.randomUUID().toString()') &&
  customerSupportScreenSource.includes('orders.take(30)') &&
  read('customer/src/main/java/com/queuego/customer/CustomerExtras.kt').includes('"p_evidence_path", evidencePath ?: JSONObject.NULL') &&
  read('customer/src/main/java/com/queuego/customer/CustomerExtras.kt').includes('qg_support_tickets?select=id&user_id=eq.') &&
  customerSupportScreenSource.includes('"rider" to "ติดต่อเกี่ยวกับไรเดอร์"') &&
  customerSupportScreenSource.includes('"merchant" to "ติดต่อเกี่ยวกับร้าน"'),
  'Customer Support submit must match Web optional evidence upload, 30-order selector and same-ticket retry check'
);
ok(source.includes('qg_customer_order_context')&&source.includes('CustomerTrackingRider'),'Customer native tracking must use production order context');
const customerTrackingScreen=read('customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
ok((source.includes('"order" -> CustomerOrderTrackingScreen')||(source.includes('selectedOrder?.fulfillmentVertical == "ride"')&&source.includes('else CustomerOrderTrackingScreen(')))&&customerTrackingScreen.includes('"สถานะล่าสุด"')&&customerTrackingScreen.includes('"ส่งไปที่"')&&customerTrackingScreen.includes('CustomerTrackingProgress(stage)')&&customerTrackingScreen.includes('"Rider ของคุณ"')&&customerTrackingScreen.includes('"รายการสินค้า"')&&customerTrackingScreen.includes('"ยอดรวม"'),'Customer native tracking must preserve Production order-detail hierarchy');
ok(customerTrackingScreen.includes('context?.shop?.phone?.any')&&customerTrackingScreen.includes('it.isDigit()'),'Customer native shop-contact action must match the Web phone-availability guard');
ok(customerTrackingScreen.includes('d/M/yyyy HH:mm:ss')&&customerTrackingScreen.includes('ThaiBuddhistChronology.INSTANCE'),'Customer native tracking status time must match Web Thai-locale date and time precision');
ok(read('shared/src/main/java/com/queuego/shared/NativeOrderNumber.kt').includes('nativeOrderNumberInText')&&read('customer/src/main/java/com/queuego/customer/CustomerExtras.kt').includes('nativeOrderNumberInText(n.message)'),'Customer notification title and message must normalize order numbers like Web');
ok(read('customer/src/main/java/com/queuego/customer/CustomerExtras.kt').includes('d/M/yyyy HH:mm:ss')&&read('customer/src/main/java/com/queuego/customer/CustomerExtras.kt').includes('customerThaiDateTime(n.createdAt!!)')&&read('customer/src/main/java/com/queuego/customer/CustomerSupportScreen.kt').includes('customerThaiDateTime(createdAt)'),'Customer notifications and support tickets must format timestamps like Web');
const nativeAuthApi=read('shared/src/main/java/com/queuego/shared/NativeAuthApi.kt');
const nativeProfile=read('customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
ok(nativeAuthApi.includes('profileImage')&&nativeAuthApi.includes('profile_image')&&nativeAuthApi.includes('avatarUrl')&&nativeProfile.includes('auth.user.customerPhoto')&&nativeProfile.includes('QgRemoteImage(safePhoto'),'Customer profile must display Web metadata profile images with a text fallback');
ok((nativeAuthApi.match(/customerProfilePhoto\(\)/g)||[]).length>=2&&nativeAuthApi.includes('status,phone,metadata&auth_user_id=eq.'),'Customer profile image metadata must refresh for existing authenticated sessions');
ok(customerTrackingScreen.includes('size(46.dp)')&&customerTrackingScreen.includes('RoundedCornerShape(20.dp)')&&customerTrackingScreen.includes('fontSize = 22.sp')&&customerTrackingScreen.includes('"แจ้งปัญหา"'),'Customer native tracking must preserve Production rider, hero, total and support proportions');
const customerTrackingMap=read('customer/src/main/java/com/queuego/customer/CustomerTrackingMap.kt');
const customerTrackingPolicy=read('customer/src/main/java/com/queuego/customer/CustomerTrackingPolicy.kt');
ok(
  customerTrackingPolicy.includes('"ร้านค้า · รับ"') &&
  customerTrackingPolicy.includes('"ลูกค้า · ส่ง"') &&
  customerTrackingPolicy.includes('"ไรเดอร์"') &&
  customerTrackingMap.includes('CustomerTrackingMarkerLegend("ร้าน · รับ"') &&
  customerTrackingMap.includes('CustomerTrackingMarkerLegend("ไรเดอร์"') &&
  customerTrackingMap.includes('CustomerTrackingMarkerLegend("ลูกค้า · ส่ง"') &&
  customerTrackingMap.includes('else -> 260.dp'),
  'Customer order tracking map must identify Merchant pickup, Rider position and Customer dropoff with distinct labeled markers and visible legend'
);

ok(customerTrackingMap.includes('RoundedCornerShape(18.dp)')&&customerTrackingMap.includes('.padding(10.dp)')&&customerTrackingMap.includes('Color(0xFF20B46D)')&&customerTrackingMap.includes('fontSize = 9.sp'),'Customer native tracking map card must preserve Production radius, padding and live-location indicator');
ok(!/private fun OrderTrackingScreen\(/.test(source),'legacy generic Customer tracking screen must stay removed');
ok(source.includes('qg_customer_cancel_order'),'Customer native cancellation must use production RPC');
ok(source.includes('qg_chat_moderation_state')&&source.includes('qg_accept_ugc_terms')&&source.includes('qg_block_chat_counterpart')&&source.includes('qg_report_chat'),'Customer native chat must preserve UGC safety RPCs');
ok(source.includes('order_chat_messages?select=id,sender_id,message,created_at'),'Customer native chat must use production chat table');
ok(source.includes('qg_customer_save_review')&&source.includes('reviews?select=id,rating,food_rating,rider_rating,comment'),'Customer native completed orders must preserve review RPC');
ok(source.includes('\"search\" -> CustomerSearchScreen')&&source.includes('CustomerBottomNavigation(screen, cart.sumOf')&&source.includes('Triple(\"search\", \"ค้นหา\", R.drawable.qg_nav_search)')&&source.includes('onSelect(key)'),'Customer native must preserve global search navigation');
const customerAuthScreen=read('customer/src/main/java/com/queuego/customer/CustomerAuthenticationScreen.kt');
const customerGuestShell=read('customer/src/main/java/com/queuego/customer/CustomerGuestShell.kt');
const customerApp=read('customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
const customerLocationPicker=read('customer/src/main/java/com/queuego/customer/CustomerLocationPicker.kt');
const merchantShopSetup=read('merchant/src/main/java/com/queuego/merchant/MerchantShopSetupScreen.kt');
const sharedQueueGoMap=read('shared/src/main/java/com/queuego/shared/QueueGoMap.kt');
ok(
  customerLocationPicker.includes('LaunchedEffect(mapReady)') &&
  customerLocationPicker.includes('autoGpsRequested') &&
  customerLocationPicker.includes('if (!mapReady || autoGpsRequested) return@LaunchedEffect') &&
  !customerLocationPicker.includes('delay(80)\n        onGps()'),
  'Customer location picker must wait for QueueGo Map readiness before automatic GPS permission handoff'
);
ok(
  merchantShopSetup.includes('QgLocationPickerMap(') &&
  sharedQueueGoMap.includes('MapLibre.getInstance') &&
  sharedQueueGoMap.includes('fun QgLocationPickerMap(') &&
  sharedQueueGoMap.includes('Lifecycle.Event.ON_RESUME -> holder.resume()') &&
  sharedQueueGoMap.includes('Lifecycle.Event.ON_PAUSE -> holder.pause()') &&
  sharedQueueGoMap.includes('Lifecycle.Event.ON_DESTROY -> holder.destroy()') &&
  sharedQueueGoMap.includes('view?.onDestroy()') &&
  sharedQueueGoMap.includes('holder.destroy()'),
  'Customer/Merchant shared QueueGo MapLibre picker lifecycle must follow lifecycle ownership and destroy the MapView'
);
ok(
  sharedQueueGoMap.includes('map.addOnMapClickListener { target ->') &&
  sharedQueueGoMap.includes('holder.renderSelectedPoint(point)') &&
  sharedQueueGoMap.includes('holder.onPointSelected(point)') &&
  sharedQueueGoMap.includes('CircleLayer(QUEUEGO_LOCATION_PICKER_LAYER, QUEUEGO_LOCATION_PICKER_SOURCE)') &&
  !sharedQueueGoMap.includes('map.addOnCameraIdleListener') &&
  !sharedQueueGoMap.includes('.align(Alignment.Center)\n                    .size(12.dp)') &&
  customerCartCheckout.includes('onPointSelected = { point ->') &&
  customerLocationPicker.includes('onPointSelected = { point ->') &&
  merchantShopSetup.includes('onPointSelected = {') &&
  merchantShopSetup.includes('แตะบนแผนที่ตรงหน้าร้านเพื่อปักหมุด แล้วกดบันทึก'),
  'Shared QueueGo location picker must select the tapped coordinate and move the red marker there instead of selecting the camera center'
);
ok(customerAuthScreen.includes('CustomerBottomNavigation(screen = "login", cartCount = guestCartCount)')&&customerAuthScreen.includes('Spacer(Modifier.height(92.dp))'),'Customer native auth must preserve the Production web fixed four-tab navigation and 92dp content clearance');
ok(customerAuthScreen.includes('.windowInsetsPadding(WindowInsets.statusBars)')&&customerAuthScreen.includes('padding(horizontal = 12.dp, vertical = 6.dp)')&&customerAuthScreen.includes('.widthIn(max = 720.dp)'),'Customer native auth top bar must preserve Production web safe-area, compact spacing and 720px content width');
ok(customerAuthScreen.includes('.onFocusChanged { focused = it.isFocused }')&&customerAuthScreen.includes('if (focused) QgRed else QgLine')&&customerAuthScreen.includes('cursorBrush = SolidColor(QgRed)'),'Customer native auth inputs must preserve Production web red focus treatment');
ok(customerAuthScreen.includes('onGuestNavigate: ((String) -> Unit)? = null')&&customerAuthScreen.includes('target != "orders"'),'Customer auth navigation must return public tabs to the real guest shell while keeping Orders protected');
ok(customerApp.includes('guestStartDestination')&&customerApp.includes('initialScreen = guestStartDestination')&&customerGuestShell.includes('rememberSaveable(initialScreen, stateSaver = pickerRouteSaver)'),'Customer login bottom navigation must reopen the selected real guest destination instead of rendering a decorative auth-only nav');
ok(source.includes('GuestCustomerShell(')&&source.includes('loadShopsPublic()')&&source.includes('loadProductsPublic(')&&source.includes('เข้าสู่ระบบเพื่อสั่งซื้อ'),'Customer native must preserve Production guest Home/catalog/cart/Market browsing before authentication');
ok(source.includes('CustomerCartStore(context, "guest")')&&source.includes('destinationAfterLogin')&&source.includes('onCheckout = { onLogin(customerGuestPostLoginDestination("checkout")) }'),'Customer native guest cart must persist locally and checkout must remain the sole preserved post-login destination');
ok(source.includes('CustomerPromotionScreen(')&&source.includes('qg_public_promotions'),'Customer native guest routing must preserve public promotion browsing');
ok(source.includes('auth: NativeAuth?')&&source.includes('onRequireLogin'),'Customer public shop/Market renderers must share native implementations without fake authenticated actors');
ok(source.includes('ServiceCategoryScreen')&&source.includes('"category" -> ServiceCategoryScreen'),'Food Drink Grocery Shopping must use dedicated native category pages');

ok(
  source.includes('CustomerCategoryButton') &&
  source.includes('.width(58.dp)') &&
  source.includes('.size(48.dp)') &&
  source.includes('QgIcon(icon, Modifier.size(22.dp), QgRed)') &&
  source.includes('if (compact) Modifier.size(72.dp) else Modifier.size(78.dp)') &&
  source.includes('val radius = RoundedCornerShape(16.dp)') &&
  source.includes('top = 13.dp, bottom = 10.dp') &&
  source.includes('fontSize = 18.sp'),
  'Customer native Home must preserve current Web location, category, heading and shop-card proportions'
);
ok(source.includes('CustomerTopAction(')&&source.includes('R.drawable.qg_nav_bag')&&source.includes('R.drawable.qg_top_bell')&&source.includes('R.drawable.qg_top_user'),'Customer top actions must remain exact icon-first web vectors');
const merchantMainSource=read('merchant/src/main/java/com/queuego/merchant/MainActivity.kt');
ok(
  !merchantMainSource.includes('if (findingMarkets) CircularProgressIndicator') &&
  merchantMainSource.includes('enabled = !busy && !findingMarkets') &&
  merchantMainSource.includes('Text("ใช้พิกัดร้านนี้และค้นหาตลาดใกล้ฉัน")'),
  'Merchant registration market lookup must stay visually silent while preventing duplicate requests'
);

ok(
  merchantMainSource.includes('withContext(kotlinx.coroutines.Dispatchers.IO) { store.load() }') &&
  merchantMainSource.includes('lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)') &&
  merchantMainSource.includes('delay(5_000)') &&
  m…26595 tokens truncated…__"') &&
  merchantPosScreen.includes('LaunchedEffect(snap?.shopId) {') &&
  merchantPosScreen.includes('selectedPosCategory = "all"') &&
  merchantPosScreen.includes('if (!kitchenEnabled)') &&
  merchantPosScreen.includes('บาร์โค้ด / SKU') &&
  merchantPosScreen.includes('api.lookupProductCode(auth, code)') &&
  merchantPosScreen.includes('selectedCategory == "__best_seller__"') &&
  merchantPosScreen.includes('api.ownerItemSales(auth, 7)') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosOfflineDraftStore.kt').includes('.put("category", p.category ?: JSONObject.NULL)') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosOfflineDraftStore.kt').includes('!p.has("category") || p.isNull("category")'),
  'Native FULL POS must preserve Production product categories across filtering, identity switches and offline cache'
);
ok(
  merchantPosApi.includes('http.rpc(\n            "pos_register_shift_history"') &&
  merchantPosScreen.includes('add("shifts" to "กะรับเงิน")') &&
  merchantPosScreen.includes('"shifts" -> MerchantPosShiftHistoryScreen(') &&
  merchantPosScreen.includes('cashierEnabled = can("close_bill") || can("view_sales")') &&
  require('node:fs').readFileSync(require('node:path').join(root, 'merchant/src/main/java/com/queuego/merchant/MerchantPosShiftHistory.kt'), 'utf8').includes('api.shiftHistory(auth, 20)'),
  'Merchant POS must expose read-only Production register shift history to authorized users'
);

ok(
  merchantPosApi.includes('"pos_record_cash_movement"') &&
  merchantPosApi.includes('receipt.optString("event_type") == type') &&
  merchantPosScreen.includes('shopId = snap.shopId') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosShiftHistory.kt').includes('ตรวจรายการเดิมด้วยรหัสเดิม') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosCashMovementStore.kt').includes('request_id') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosCashMovementStore.kt').includes('.commit()') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosCashMovementStore.kt').includes('prefs.contains(key(pending.shopId, pending.actorId))'),
  'Merchant POS cash-in/out must persist exact retry intent and validate server receipt'
);

ok(
  merchantPosApi.includes('"pos_owner_item_sales"') &&
  merchantPosApi.includes('PosItemSalesReport(') &&
  merchantPosScreen.includes('api.ownerItemSales(auth, reportDays)') &&
  merchantPosScreen.includes('QgSectionTitle("สินค้าขายดี"') &&
  merchantPosScreen.includes('itemSalesReport.items.forEach { item ->'),
  'Merchant Native FULL POS must preserve current Web Production per-item sales report'
);

ok(
  merchantPosApi.includes('"pos_product_codes_for_shop"') &&
  merchantPosApi.includes('"pos_product_code_lookup"') &&
  merchantPosApi.includes('suspend fun lookupProductCode(') &&
  merchantPosApi.includes('"pos_product_code_save"') &&
  merchantPosApi.includes('"pos_product_code_remove"') &&
  merchantPosScreen.includes('add("codes" to "บาร์โค้ด/SKU")') &&
  merchantPosScreen.includes('codesEnabled = can("manage_staff")') &&
  merchantPosScreen.includes('"codes" -> MerchantPosCodesScreen(') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosCodesScreen.kt').includes('api.saveProductCode(auth, product.id, codeType, codeText)') &&
  read('merchant/src/main/java/com/queuego/merchant/MerchantPosCodesScreen.kt').includes('api.removeProductCode(auth, target.id)'),
  merchantPosScreen.includes('onBarcodeLookup') && merchantPosScreen.includes('if (!kitchenEnabled)') &&
  merchantPosApi.includes('suspend fun lookupProductCode('),
  'Native POS keeps barcode/SKU management and exposes the real scanner only for retail counters'
);

ok(
  merchantPosApi.includes('posPaymentReceiptMatches(objectValue(raw), billId, method, cashReceived)') &&
  merchantPosApi.includes('suspend fun paymentConfirmed(') &&
  merchantPosApi.includes('payment_status') && merchantPosApi.includes('bill_status') &&
  merchantPosApi.includes('actual.isFinite()') &&
  !merchantPosApi.includes('return scalarId(raw) ?: billId') &&
  merchantPosScreen.includes('api.paymentConfirmed(auth, bill.id, method, cash)') &&
  merchantPosScreen.includes('suspend fun showConfirmedPayment()') &&
  merchantPosScreen.includes('val verified = snapshot?.bills?.firstOrNull') &&
  merchantPosScreen.includes('receiptDialogBillId = verified.id') &&
  merchantPosScreen.includes('ก่อนกดยืนยันซ้ำ'),
  'Native POS must validate the paid receipt and reconcile timeout without duplicate payment'
);

ok(
  merchantPosScreen.includes('var receiptDialogBillId by rememberSaveable') &&
  merchantPosScreen.includes('val verified = snapshot?.bills?.firstOrNull') &&
  merchantPosScreen.includes('receiptDialogBillId = verified.id') &&
  merchantPosScreen.includes('message = "ปิดบิลแล้ว" + change +') &&
  merchantPosScreen.includes('it.paymentStatus == "PAID"') &&
  merchantPosScreen.includes('it.billStatus == "CLOSED"') &&
  merchantPosScreen.includes('merchantReceiptTicket(snap, paid)') &&
  merchantPosScreen.includes('merchantReceiptPrintKey(snap, paid)') &&
  merchantPosScreen.includes('type = "receipt"') &&
  merchantPosScreen.includes('force = true'),
  'Merchant native POS must preserve confirmed cash closure across refresh failure and show server-paid receipt via existing printer'
);

const merchantPosPending=read('merchant/src/main/java/com/queuego/merchant/MerchantPosPendingStore.kt');
const merchantPosDraftStore=read('merchant/src/main/java/com/queuego/merchant/MerchantPosOfflineDraftStore.kt');
ok(merchantPosApi.includes('inventory_stock_tracking,grocery_master_id,grocery_stock_tracking')&&merchantPosApi.includes('stock = r.optInt("stock", 0)')&&merchantPosApi.includes('inventoryStockTracking = r.optBoolean("inventory_stock_tracking", false)')&&merchantPosApi.includes('groceryMasterId = nullableString(r, "grocery_master_id")')&&merchantPosApi.includes('groceryStockTracking = r.optBoolean("grocery_stock_tracking", false)'),'Merchant native POS must load Web Production stock and generic/Grocery tracking inputs');
ok(merchantPosScreen.includes('!posProductSoldOut(product)')&&merchantPosScreen.includes('สินค้าหมดสต็อก · เหลือ 0')&&merchantPosScreen.includes('เหลือ ${product.stock}'),'Merchant native POS must keep tracked products visible, disable sold-out adds and show stock remaining');
ok(merchantPosApi.includes('variants,stock')&&merchantPosApi.includes('optionGroups = parsePosProductOptionGroups')&&merchantPosApi.includes('"pos_add_product_with_options"')&&merchantPosScreen.includes('optionsJson = optionsJson'),'Merchant native POS must load variant groups and call the Production option-pricing RPC');
ok(merchantPosOptions.includes('queuego.menu-options.v1')&&merchantPosOptions.includes('posProductOptionPayload')&&merchantPosOptions.includes('เลือกตัวเลือก'),'Merchant native POS must validate structured and legacy product options before adding');
ok(merchantPosPending.includes('optionsJson')&&merchantPosPending.includes('canonicalPosOptionJson')&&merchantPosPending.includes('optionBody()'),'Merchant native POS must persist selected option payload with the idempotent pending request');
ok(merchantPosDraftStore.includes('basePriceSnapshot')&&merchantPosDraftStore.includes('canonicalPosOptionJson')&&merchantPosDraftStore.includes('optionLabel')&&merchantPosApi.includes('put("options", JSONObject(item.optionsJson))'),'Merchant offline drafts must persist exact option and price snapshots and submit saved options with stable retry IDs');
const merchantMarketStock=read('merchant/src/main/java/com/queuego/merchant/MerchantMarketStockScreen.kt');
ok(source.includes('rpc/market_save_product')===false && source.includes('"market_save_product"')&&source.includes('"market_adjust_stock"'),'Merchant native market stock must call Production market RPCs through shared RPC client');
ok(source.includes('market_products?select=product_id,unit,pack_size,item_weight_kg,stock_quantity,min_stock,cost_price')&&source.includes('market_stock_movements?select=type,quantity,note,created_at'),'Merchant native market stock must load authoritative market inventory and movements');
ok(merchantMarketStock.includes('สินค้าและสต๊อกตลาด')&&merchantMarketStock.includes('รับเข้า / ปรับยอด')&&merchantMarketStock.includes('สต๊อกเริ่มต้น')&&merchantMarketStock.includes('แจ้งเตือนใกล้หมด'),'Merchant native market stock must preserve Production stock workflow');
ok(source.includes('"market-stock" ->')&&source.includes('MerchantMenuTile("ตลาดและสต๊อก"'),'Merchant native market shops must expose dedicated Market stock route');

ok(sharedDesign.includes('"tag" ->')&&sharedDesign.includes('"chart" ->')&&sharedDesign.includes('"clock" ->')&&sharedDesign.includes('"support" ->'),'shared native icon set must preserve Merchant web vectors');
ok(source.includes('queuego_laundry_merchant_state')&&source.includes('queuego_laundry_shop_manage'),'Merchant native Laundry must use production laundry state/actions');
ok(source.includes('พร้อมส่ง · เหลือ'),'Merchant preparation countdown must remain on ready button');
ok(source.includes('SecureRoleSessionStore')&&source.includes('NativeAuthApi'),'Customer/Merchant must share native auth/session implementation');
ok(source.includes('functions/v1/account-delete')&&source.includes('DELETE_ACCOUNT'),'native apps must expose account deletion through production edge function');
ok((source.match(/QgAccountDeletionSection\(/g)||[]).length>=4,'Customer Merchant Rider must wire account deletion UI');
ok(source.includes('claim_active_session'),'reuse active-session claim');
ok(source.includes('check_active_session')&&source.includes('touch_active_session'),'reuse session guard');
ok(riderApi.includes('qg_get_my_rider_offer')&&riderApi.includes('qg_rider_action_once')&&riderApi.includes('qg_rider_decline_offer'),'reuse guarded sequential server-selected Rider dispatch');
ok(source.includes('status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)'),'reuse active order states');
ok(source.includes('AndroidKeyStore')&&source.includes('AES/GCM/NoPadding'),'encrypted session storage');
ok(source.includes('qg_rider_mark_arrival'),'native Rider must reuse arrival RPC');
ok(source.includes('qg_pickup_with_photo')&&source.includes('qg_complete_with_photo'),'native Rider must reuse photo proof RPCs');
ok(source.includes('/storage/v1/object/qg-evidence/'),'native Rider must upload proof to existing evidence bucket');
ok(riderApi.includes('qg_rider_decline_offer'),'native Rider must preserve sequential offer decline');
ok(source.includes('status=eq.completed&order=updated_at.desc'),'Rider native must expose completed history');
ok((source.match(/qg_chat_moderation_state/g)||[]).length>=2&&(source.match(/qg_accept_ugc_terms/g)||[]).length>=2,'Rider native chat must preserve UGC safety');
ok((source.match(/order_chat_messages\?select=id,sender_id,message,created_at/g)||[]).length>=2,'Customer and Rider native chat must use production chat table');
ok(source.includes('market_pickup_route_summary')&&source.includes('qg_market_pickup_with_photo'),'native Rider must preserve market multi-stop pickup flow');
ok(source.includes('market_claim')&&source.includes('qg_complete_market_with_photo'),'native Rider must claim and complete market groups through existing RPCs');
ok(source.includes('FileProvider')&&source.includes('TakePicture'),'native Rider must use Android camera flow');
ok(source.includes('SYSTEM_ALERT_WINDOW')&&source.includes('TYPE_APPLICATION_OVERLAY'),'native Rider must provide Android cross-app Q return overlay');
ok(source.includes('FOREGROUND_SERVICE_SPECIAL_USE')&&source.includes('RiderReturnService'),'navigation return overlay must run as explicit foreground special-use service');
ok(source.includes('ACTION_MANAGE_OVERLAY_PERMISSION'),'overlay permission must be explicitly user-controlled');
ok(source.includes('RiderReturnService.stop'),'overlay service must have cleanup path');
ok(riderMap.includes('MapLibre.getInstance')&&riderMap.includes('if (disposed || view !== mapView) return@getMapAsync')&&riderMap.includes('if (disposed) return@setStyle')&&riderMap.includes('if (disposed || resumed) return')&&riderMap.includes('view?.onPause()')&&riderMap.includes('view?.onDestroy()'),'Rider MapLibre host must guard renderer callbacks and lifecycle after disposal');
const missingRiderMapSourceBlock=riderMap.slice(riderMap.indexOf('if (sourceUri == null)'),riderMap.indexOf('} else {',riderMap.indexOf('if (sourceUri == null)')));
ok(missingRiderMapSourceBlock.includes('currentReady.value(false)')&&missingRiderMapSourceBlock.includes('View(context)')&&!missingRiderMapSourceBlock.includes('MapView(context)'),'Rider map must fail closed without constructing MapView when QueueGo runtime hosting is not configured');
const riderReturnService=read('rider/src/main/java/com/queuego/rider/RiderReturnService.kt');
ok(riderReturnService.includes('foreground_start_rejected')&&riderReturnService.includes('start_foreground_failed')&&riderReturnService.includes('foregroundStarted'),'Rider navigation return service must tolerate Android foreground-service rejection without crashing');
ok(!/cash-confirm|ยืนยันชำระเงินให้ร้าน|ยืนยันเก็บเงินจากลูกค้า/i.test(source),'native Rider must not reintroduce manual cash confirmation screens');
ok(!/service_role|sb_secret_/i.test(source),'no privileged Supabase secret');
ok(runtimeCapture.includes('def package_pids(pkg: str)')&&runtimeCapture.includes('def android_runtime_for_pids(pids)'),'Android runtime evidence must resolve the tested QueueGo process IDs');
ok(runtimeCapture.includes('"--pid=" + pid')&&(runtimeCapture.match(/android_runtime_for_pids\(/g)||[]).length>=6,'runtime crash gate must read AndroidRuntime by QueueGo process PID instead of failing on unrelated system-process crashes');
ok(runtimeCapture.includes('def full_crash_buffer()')&&runtimeCapture.includes('["logcat", "-b", "crash", "-d"]'),'full emulator crash buffer must remain preserved as diagnostic evidence');
ok(runtimeCapture.includes('def package_crash_blocks(crash_text: str, pkg: str)')&&runtimeCapture.includes('if f"Process: {pkg}," in window'),'crash-buffer gate must attribute FATAL EXCEPTION blocks to the tested QueueGo package');
ok((runtimeCapture.match(/assert_no_package_crash\(/g)||[]).length>=3,'runtime gate must pair PID-scoped checks with QueueGo package-scoped crash-buffer checks');


const marketStore=read('customer/src/main/java/com/queuego/customer/MarketCartStore.kt');
const marketApi=read('customer/src/main/java/com/queuego/customer/CustomerMarketApi.kt');
ok(marketStore.includes('pending_checkout')&&marketStore.includes('pending.encode()).putString("cart", pending.cartSnapshot).commit()')&&customerMarketScreen.includes('CustomerCheckoutRecovery<PendingMarketCheckout'),'Market checkout must reuse durable recovery and existing cart storage');
ok(marketApi.includes('http.rpc(pending.rpc, auth.session.accessToken, pending.body)')&&!marketApi.slice(marketApi.indexOf('internal suspend fun sendCheckout'),marketApi.indexOf('suspend fun trips')).includes('UUID.randomUUID'),'Market retries must send the frozen RPC/body, never generate a new order or add-shop ID');
ok(customerMarketScreen.includes('!busy && !checkoutPending')&&customerMarketScreen.includes('restoredCart && !checkoutPending')&&customerMarketScreen.includes('catch (cancelled: CancellationException)'),'Pending Market checkout must freeze cart mutations and survive navigation cancellation');



ok(marketApi.includes('queuego_platform_rules?select=id,rule_key,value,effective_from,created_at')&&customerMarketScreen.includes('pricing?.deliveryEstimate(api.distanceKm('),'Market cart estimates must hydrate current Production rules and actual shop/customer coordinates');
ok(customerMarketScreen.includes('คำนวณจากราคาที่ล็อกไว้ใน Trip เดิม')&&customerMarketScreen.includes('catalog.find { it.id == line.product.id && it.marketId == line.product.marketId && it.availablePacks > 0 }'),'Market add mode must preserve locked-trip pricing and refresh available packs before new checkout');


ok(customerMarketScreen.includes('LifecycleResumeEffect(actorId)')&&customerMarketScreen.includes('if (hasResumed) reload++')&&customerMarketScreen.includes('old.quantity < minOf(99, p.availablePacks)'), 'Market foreground recovery must refresh authoritative data and additions must obey the Production RPC 99-pack cap');
ok(customerMarketScreen.includes('LaunchedEffect(actorId, reload) {')&&!customerMarketScreen.includes('LaunchedEffect(actorId, location?.latitude, location?.longitude, reload)'), 'Customer Market location updates must not refetch full market, shop, and catalog reference sets');
ok(!customerMarketScreen.includes('LaunchedEffect(cartPage) { if (cartPage) reload++ }'), 'Opening the Market cart must not refetch the full market, shop, and catalog reference sets');
const specialTracking=fs.readFileSync(path.join(root,'customer/src/main/java/com/queuego/customer/QueueGoSpecialTracking.kt'),'utf8');
ok(specialTracking.includes('api.trip(auth, trip.id)')&&specialTracking.includes('api.order(auth, order.id)')&&specialTracking.includes('repeatOnLifecycle(Lifecycle.State.STARTED)')&&specialTracking.includes('catch (cancelled: CancellationException) { throw cancelled }'),'Market/Laundry detail must read authoritative owner/id snapshots and suspend polling in background');
ok(
  specialTracking.includes('var shopsLoaded by remember(auth.user.id, trip.id)') &&
  specialTracking.includes('if (!shopsLoaded)') &&
  specialTracking.includes('var eventsLoaded by remember(auth.user.id, order.id)') &&
  specialTracking.includes('if (!eventsLoaded || fresh.summary.status != previousStatus)') &&
  !specialTracking.includes('"กำลังโหลดการติดตาม..."') &&
  !specialTracking.includes('"กำลังโหลดร้านในทริป..."') &&
  !specialTracking.includes('?: "รอโหลด"'),
  'Market/Laundry tracking must avoid repeated reference/history downloads and must not show data-loading placeholders'
);
ok(specialTracking.includes('d.actualQuantity')&&specialTracking.includes('d.pickupFee')&&specialTracking.includes('d.returnFee')&&specialTracking.includes('งานนี้ถูกยกเลิก'),'Laundry details must preserve snapshot pricing/quantity/fees and explicit cancellation');
ok(marketApi.includes('qg_customer_cancel_market_order')&&specialTracking.includes('ยกเลิก Market Trip')&&specialTracking.includes('current.multiShopFee')&&specialTracking.includes('onTrackChild(child.id)'),'Market Trip must preserve current Web cancellation/tracking actions and server fee snapshots');
ok(source.includes('if (authRequested == false) {\n        QueueGoTheme {\n        GuestCustomerShell('),'Customer guest screens must use the existing Production-red QueueGoTheme');

ok(/queuego_(?:set_laundry_rider_mode|laundry_rider_|claim_laundry_job)/.test(riderApi)&&riderApi.includes('queuego_laundry_rider_state'),'Rider Native must load and mutate Production Laundry jobs through authoritative RPCs');
ok(riderApp.includes('RiderLaundryActiveCard(')&&riderApp.includes('RiderLaundryPoolCard(')&&!riderApp.includes('riderLaundryNavigationTarget')&&!riderMap.includes('RiderLaundryJob'),'Rider Native must render Production Laundry work while navigation hands off to Google Maps without a duplicate in-app route engine');
ok(!nativeRealtimeSubscriptions.includes('laundry_rider_'),'Rider must not subscribe to shop-managed Laundry dispatch');
ok(riderApi.includes('"queuego_route_bundle_active_orders"')&&riderApi.includes('"queuego_route_bundle_candidates"')&&riderApi.includes('"bundle_claim"'),'Rider Native must use Production Route Bundle reads and idempotent claim');
ok(riderApp.includes('RiderRouteBundlePanel(')&&riderApp.includes('bundleOrders = snapshot?.bundle?.activeOrders.orEmpty()'),'Rider Native Home must render Route Bundle state and pass every stop to Longdo');
const riderNavigationIntegration=read('rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
ok(riderNavigationIntegration.includes('bundleOrders: List<RiderBundleOrder>')&&
   riderNavigationIntegration.includes('val pickups = usable.sortedWith')&&
   riderNavigationIntegration.includes('compareBy<RiderBundleOrder> { it.bundleSequence }')&&
   riderNavigationIntegration.includes('compareBy<RiderBundleOrder> { it.dropRank }')&&
   riderNavigationIntegration.includes('val offset = pickups.size')&&
   riderNavigationIntegration.includes('sequence = offset + index + 1'),
   'Rider Native QueueGo navigation must render every live Route Bundle stop with stable pickup-then-drop numbering');

const posOnlyApi=read('merchant/src/main/java/com/queuego/merchant/MerchantPosApi.kt'),posOnlyUi=read('merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt');
ok(!/deliveryOrders|pos_delivery_kitchen_action|QUEUEGO_DELIVERY/.test(posOnlyApi+posOnlyUi),
  'Native POS must not read, render or mutate Delivery orders as POS bills');
ok(posOnlyUi.includes('"delivery" -> PosDeliveryView(')&&
  posOnlyUi.includes('merchantApi.enableDelivery(auth)')&&
  posOnlyUi.includes('ใช้หน้าร้าน POS ต่อได้เสมอ · ตรวจความพร้อมก่อนเปิดรับ Delivery'),
  'Native POS may expose the Web Delivery readiness/configuration surface without mixing Delivery orders into POS bills');
ok(posOnlyApi.includes('delivery = raw.optDouble("DELIVERY"')&&posOnlyApi.includes('gp = raw.optDouble("GP"')&&
  posOnlyApi.includes('averageTicket = raw.optDouble("average_ticket"')&&
  posOnlyApi.includes('cashSales = raw.optDouble("cash_sales"')&&
  posOnlyApi.includes('hourlySales = if (hourlyRows == null)')&&
  posOnlyUi.includes('PosReportLine("ยอดขายรวม", report.dineIn + report.takeaway, true)')&&
  posOnlyUi.includes('PosReportLine("รับเงินสด", report.cashSales)')&&
  posOnlyUi.includes('PosReportLine("ส่วนลด", report.discounts)')&&
  posOnlyUi.includes('PosReportLine("คืนเงิน", report.refunds)')&&
  posOnlyUi.includes('report.hourlySales.forEach')&&
  posOnlyUi.includes('buildPosReportCsv(report, itemSalesReport)')&&
  posOnlyUi.includes('ActivityResultContracts.CreateDocument("text/csv")')&&
  posOnlyUi.includes('Text("ส่งออก CSV"')&&
  !posOnlyUi.includes('PosReportLine("Delivery", report.delivery)')&&
  !posOnlyUi.includes('PosReportLine("GP Delivery", report.gp)'),
  'Native POS report must match Web POS metrics/hourly/CSV and exclude Delivery/GP from POS totals');
ok(posOnlyApi.includes('&sales_channel=eq.POS&created_at=gte.'),'Native POS history must be scoped to POS at the server');

const sharedNativeApi=read('shared/src/main/java/com/queuego/shared/QueueGoNativeApi.kt');
const sharedPlaceSearch=read('shared/src/main/java/com/queuego/shared/QueueGoPlaceSearch.kt');
const customerApi=read('customer/src/main/java/com/queuego/customer/CustomerApi.kt');
const riderDestinationDialog=read('rider/src/main/java/com/queuego/rider/RiderMyDestinationDialog.kt');
ok(sharedNativeApi.includes('"qg_search_places"')&&sharedNativeApi.includes('reversePlaceAddress')&&sharedNativeApi.includes('"administrative_area"'),'Native reverse labels must use shared QueueGo Places authority');
ok(!customerApi.includes('api.longdo.com')&&!riderApi.includes('api.longdo.com'),'Customer and Rider Native must not call Longdo reverse geocoding directly');
ok(customerApi.includes('http.reversePlaceAddress(latitude, longitude)')&&riderApi.includes('placesApi.reversePlaceAddress(latitude, longitude)'),'Customer and Rider reverse labels must converge on the shared QueueGo Places client');
ok(
  sharedNativeApi.includes('suspend fun searchPlaces(') &&
  sharedNativeApi.includes('.put("p_query", normalized)') &&
  sharedNativeApi.includes('QueueGoPlaceSearchResult') &&
  sharedPlaceSearch.includes('fun QgPlaceSearchPanel(') &&
  sharedPlaceSearch.includes('delay(300)'),
  'Native text place search must use one shared debounced QueueGo Places authority'
);
ok(
  customerLocationPicker.includes('QgPlaceSearchPanel(') &&
  merchantSetup.includes('QgPlaceSearchPanel(') &&
  riderDestinationDialog.includes('QgPlaceSearchPanel(') &&
  customerLocationPicker.indexOf('QgPlaceSearchPanel(') < customerLocationPicker.indexOf('QgLocationPickerMap(') &&
  merchantSetup.indexOf('QgPlaceSearchPanel(') < merchantSetup.indexOf('QgLocationPickerMap(') &&
  riderDestinationDialog.indexOf('QgPlaceSearchPanel(') < riderDestinationDialog.indexOf('QgLocationPickerMap('),
  'Customer Merchant Rider place search must stay outside the map canvas'
);
ok(
  customerApi.includes('http.searchPlaces(query)') &&
  merchantApiSource.includes('http.searchPlaces(query)') &&
  riderApi.includes('placesApi.searchPlaces(query)'),
  'Customer Merchant Rider must converge on the shared QueueGo Places text search client'
);


const groceryCustomerApi=read('customer/src/main/java/com/queuego/customer/CustomerApi.kt');
const groceryCustomerCatalog=read('customer/src/main/java/com/queuego/customer/CustomerShopCatalog.kt');
const groceryCustomerContentCache=read('customer/src/main/java/com/queuego/customer/CustomerContentCache.kt');
const groceryCustomerGuest=read('customer/src/main/java/com/queuego/customer/CustomerGuestShell.kt');
const groceryCustomerApp=read('customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
ok(groceryCustomerApi.includes('"qg_grocery_customer_page"')&&groceryCustomerApi.includes('"qg_grocery_customer_categories"')&&groceryCustomerApi.includes('put("p_offset", offset)')&&groceryCustomerCatalog.includes('rows.take(50)')&&groceryCustomerCatalog.includes('rows.size > 50')&&groceryCustomerGuest.includes('api.loadGroceryPage(null, shop.id, category, offset)')&&groceryCustomerApp.includes('api.loadGroceryPage(auth, shop.id, category, offset)'),'Native guest and signed-in Grocery must use the Production category/page RPCs and 51-row sentinel');
ok(groceryCustomerContentCache.includes('readGroceryCategories(shopId: String)')&&groceryCustomerContentCache.includes('writeGroceryCategories(shopId: String')&&groceryCustomerContentCache.includes('grocery_categories_" + sha256(shopId)')&&groceryCustomerCatalog.includes('loadCachedCategories()')&&groceryCustomerCatalog.includes('persistCategories(categories)')&&groceryCustomerGuest.includes('contentCache.readGroceryCategories(shop.id)')&&groceryCustomerApp.includes('contentCache.readGroceryCategories(shop.id)'),'Customer Grocery category references must be shop-scoped, bounded, cached locally and rendered before server refresh');

const groceryMerchantCache=read('merchant/src/main/java/com/queuego/merchant/MerchantContentCache.kt');
const groceryMerchantApp=read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
ok(groceryMerchantCache.includes('readGroceryCategories(ownerId: String, shopId: String)')&&groceryMerchantCache.includes('writeGroceryCategories(ownerId: String, shopId: String')&&groceryMerchantCache.includes('groceryCategoriesFresh(ownerId: String, shopId: String)')&&groceryMerchantCache.includes('CATEGORY_FRESH_MS = 10L * 60L * 1000L')&&groceryMerchantCache.includes('groceryCategoriesUpdatedAt')&&groceryMerchantCache.includes('optJSONObject("shop")?.optString("id") != shopId')&&groceryMerchantApp.includes('contentCache.readGroceryCategories(owner, activeShop.id)')&&groceryMerchantApp.includes('contentCache.groceryCategoriesFresh(owner, activeShop.id)')&&groceryMerchantApp.includes('if (!cachedState.second)')&&groceryMerchantApp.includes('contentCache.writeGroceryCategories(owner, activeShop.id, categories)'),'Merchant Grocery category references must reuse fresh account/shop cache before server refresh');
const marketCatalogMerchant=read('merchant/src/main/java/com/queuego/merchant/MerchantMarketCatalogScreen.kt');
ok(groceryMerchantCache.includes('readMarketCatalogCategories(ownerId: String, shopId: String)')&&groceryMerchantCache.includes('writeMarketCatalogCategories(ownerId: String, shopId: String')&&groceryMerchantCache.includes('marketCatalogCategoriesFresh(ownerId: String, shopId: String)')&&groceryMerchantCache.includes('marketCatalogCategoriesUpdatedAt')&&marketCatalogMerchant.includes('readMarketCatalogCategories(auth.user.id, shop.id)')&&marketCatalogMerchant.includes('contentCache.marketCatalogCategoriesFresh(auth.user.id, shop.id)')&&marketCatalogMerchant.includes('if (!cachedState.second)')&&marketCatalogMerchant.includes('writeMarketCatalogCategories(auth.user.id, shop.id, fresh)')&&!marketCatalogMerchant.includes('val categoryRead = async')&&marketCatalogMerchant.includes('api.marketCatalogCategories(auth)'),'Merchant Market catalog categories must reuse fresh local cache and avoid repeated category RPCs');


const customerLaundryCatalogApi=read('customer/src/main/java/com/queuego/customer/CustomerLaundryApi.kt');
ok(customerLaundryCatalogApi.includes('"queuego_laundry_customer_catalog"')&&customerLaundryCatalogApi.includes('catalog(auth).servicesForCustomerHub(hubId)')&&!customerLaundryCatalogApi.includes('laundry_hubs?select=')&&!customerLaundryCatalogApi.includes('laundry_shop_settings?select=')&&!customerLaundryCatalogApi.includes('laundry_services?select='),'Native Customer Laundry discovery must use the same eligible catalog authority as Production Web, without raw-table fallback');


const customerRideArrivalSource=read('customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt');
ok(customerRideArrivalSource.includes('text("arrived_pickup_at")')&&customerRideArrivalSource.includes('text("arrived_dropoff_at")')&&customerRideArrivalSource.includes('Text(passengerRideStatus(current)'),'Native Customer Ride must render arrival-derived status from the existing server state');

ok(
  sharedQueueGoMap.includes('if (!hasSetInitialViewport && lastPoints.isNotEmpty())') &&
  sharedQueueGoMap.includes('map.uiSettings.isZoomGesturesEnabled = true') &&
  sharedQueueGoMap.includes('map.uiSettings.isRotateGesturesEnabled = true') &&
  sharedQueueGoMap.includes('map.uiSettings.isTiltGesturesEnabled = true') &&
  sharedQueueGoMap.includes('map.uiSettings.setDisableRotateWhenScaling(false)') &&
  read('../customer-ride.js').includes('dragRotate:true,pitchWithRotate:true') &&
  !read('../customer-ride.js').includes('trackingMap.touchZoomRotate.disableRotation()'),
  'Customer Ride map must preserve user zoom/rotate/tilt after marker updates and match Web gesture controls'
);


// POS tax calculation foundation must use integer satang and distinguish tax-inclusive/exclusive prices.
const merchantPosApiSource=read('merchant/src/main/java/com/queuego/merchant/MerchantPosApi.kt');
ok(merchantPosApiSource.includes('data class PosTaxBreakdown('),'POS tax breakdown model must exist');
ok(merchantPosApiSource.includes('internal fun calculatePosTaxBreakdown('),'POS tax calculator must exist');
ok(merchantPosApiSource.includes('amountSatang: Long') && merchantPosApiSource.includes('taxableBaseSatang: Long') && merchantPosApiSource.includes('taxSatang: Long'),'POS tax math must use integer satang');
ok(merchantPosApiSource.includes('pricesIncludeTax: Boolean'),'POS tax math must distinguish tax-inclusive and tax-exclusive prices');
ok(merchantPosApiSource.includes('ratePercent.isFinite() && ratePercent in 0.0..100.0'),'POS tax math must reject invalid tax rates');
ok(merchantPosApiSource.includes('Math.addExact(amountSatang, tax)'),'POS tax-exclusive total must detect integer overflow');
ok(merchantPosApiSource.includes('suspend fun loadTaxSettings(auth: NativeAuth): PosTaxSettings'),'Merchant POS must load server-owned tax settings');
ok(merchantPosApiSource.includes('suspend fun saveTaxSettings(auth: NativeAuth, settings: PosTaxSettings)'),'Merchant POS must save tax settings through the owner RPC');
ok(merchantPosApiSource.includes('"pos_save_tax_settings"'),'Merchant POS tax settings must use the protected server RPC');

const merchantPosTaxScreen=read('merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt');
ok(merchantPosTaxScreen.includes('"tax" -> PosTaxSettingsView(') && merchantPosTaxScreen.includes('add("tax" to "ภาษี POS")'),'Owner must have a native POS tax settings screen');
ok(merchantPosTaxScreen.includes('ราคารวมภาษีแล้ว') && merchantPosTaxScreen.includes('ราคายังไม่รวมภาษี'),'POS tax screen must explain inclusive and exclusive pricing');
ok(merchantPosApiSource.includes('taxEnabledSnapshot = r.optBoolean("pos_tax_enabled_snapshot", false)'),'POS bills must parse the stored tax-enabled snapshot');
ok(merchantPosApiSource.includes('taxAmount = nullableDouble(r, "pos_tax_amount")'),'POS bills must parse the stored tax amount');
const merchantPrinterSource=read('merchant/src/main/java/com/queuego/merchant/MerchantPrinter.kt');
ok(merchantPrinterSource.includes('if (receipt && bill.taxEnabledSnapshot)'),'Receipts must use the immutable tax snapshot, not current shop settings');
ok(merchantPrinterSource.includes('"มูลค่าก่อนภาษี "') && merchantPrinterSource.includes('"ภาษี "'),'POS receipts must print taxable base and tax breakdown');

ok(merchantPosApiSource.includes('pos_tax_enabled_snapshot,pos_tax_rate_snapshot,pos_prices_include_tax_snapshot,pos_taxable_base,pos_tax_amount'),'POS bills must load immutable tax snapshots for receipts');
ok(merchantPrinterSource.includes('มูลค่าก่อนภาษี') && merchantPrinterSource.includes('bill.taxAmount'),'POS receipts must print tax breakdown from saved bill snapshots');
ok(merchantPrinterSource.includes('bill.taxRegistrationSnapshot'),'POS receipts must use tax registration data snapshotted on the bill');



// POS tax schema remains additive and does not silently change live bill totals.
const posTaxMigration=fs.readFileSync(path.join(__dirname,'../QueueGo-POS-Tax-Foundation-Migration.sql'),'utf8');
ok(posTaxMigration.includes('create table if not exists public.pos_tax_settings'),'POS tax settings table must be defined');
ok(posTaxMigration.includes('tax_enabled boolean not null default false'),'Tax must default off for existing shops');
ok(posTaxMigration.includes('prices_include_tax boolean not null default true'),'Tax-inclusive pricing must be configurable');
ok(posTaxMigration.includes('pos_tax_amount numeric(12,2)'),'POS bill must have a tax snapshot field');
ok(posTaxMigration.includes('revoke insert, update, delete on public.pos_tax_settings from anon, authenticated'),'Tax settings must not permit direct client writes');
ok(posTaxMigration.includes('does not change live checkout totals'),'Migration must not claim live checkout integration before server RPC support');


// POS tax checkout integration must calculate tax on the server and preserve per-bill snapshots.
const posTaxCheckoutMigration=fs.readFileSync(path.join(__dirname,'../QueueGo-POS-Tax-Checkout-Integration-Migration.sql'),'utf8');
ok(posTaxCheckoutMigration.includes('create or replace function public.pos_save_tax_settings('),'POS tax settings must be saved through an owner-authorized RPC');
ok(posTaxCheckoutMigration.includes('not public.pos_is_owner()'), 'Only the POS shop owner may change tax settings');
ok(posTaxCheckoutMigration.includes('create or replace function public.pos_apply_tax_snapshot()'),'POS tax must be calculated by a server-side trigger');
ok(posTaxCheckoutMigration.includes('new.pos_tax_registration_snapshot := v_settings.tax_registration_number'),'Tax registration details must be snapshotted on each new POS bill');
ok(posTaxCheckoutMigration.includes('round(v_net * v_rate / (1 + v_rate),2)'),'Tax-inclusive prices must extract tax from the customer total');
ok(posTaxCheckoutMigration.includes('new.total_amount := v_net + v_tax'),'Tax-exclusive prices must add tax to the customer total');
ok(posTaxCheckoutMigration.includes('alter table public.orders drop constraint if exists pos_channel_integrity_check'),'Tax checkout must replace the legacy POS total constraint');
ok(posTaxCheckoutMigration.includes('total_amount = greatest(subtotal-discount_amount,0) + coalesce(pos_tax_amount,0)'),'Tax-exclusive totals must remain valid under the POS integrity constraint');
ok(posTaxCheckoutMigration.includes('else total_amount = subtotal-discount_amount'),'Tax-disabled and tax-inclusive POS bills must preserve existing total integrity');
ok(posTaxCheckoutMigration.includes('before insert or update of subtotal,discount_amount,sales_channel'),'Tax must recalculate when bill subtotal or discount changes');
ok(posTaxCheckoutMigration.includes('revoke all on function public.pos_apply_tax_snapshot() from public,anon,authenticated'),'Clients must not call the internal tax trigger function');

// Both historical and open bill reads must survive only the missing optional tax schema.
ok((merchantPosApiSource.match(/async \{ readPosBillsWithTaxCompatibility\(/g) || []).length === 2,
  'POS history and open bills must share the narrowly scoped tax read compatibility path');
ok(merchantPosApiSource.includes('error.statusCode != 400 || !missingTaxColumn') &&
  merchantPosApiSource.includes('read("orders?$baseColumns$scope")'),
  'Tax schema fallback must preserve scope and propagate auth/network/unrelated failures');
ok(merchantPosTaxScreen.includes('if (view == "tax" && current?.owner == true)') &&
  merchantPosTaxScreen.includes('.onFailure {\n                    taxSettingsLoaded = false'),
  'Unavailable tax settings must not interrupt POS entry or enable saving unloaded defaults');

ok(!merchantPosScreen.includes('it.copy(requestIds = emptyList())') &&
  !merchantPosScreen.includes('ไม่มีบิลถูกสร้าง แก้ร่างแล้วลองใหม่ได้'),
  'Uncertain offline draft retries must preserve durable per-unit IDs even after HTTP 4xx');

ok(merchantPosScreen.indexOf('amount > 999999999.0') >= 0 &&
  merchantPosScreen.indexOf('amount > 999999999.0') < merchantPosScreen.indexOf('!cashMovementStore.save(request)'),
  'Cashier must reject amounts outside RPC bounds before persisting an unreplayable cash intent');

const authenticatedReviewWorkflow=fs.readFileSync(path.join(__dirname,'../.github/workflows/rider-android-authenticated-review.yml'),'utf8');
ok(authenticatedReviewWorkflow.includes("- name: Fetch the exact offline-tested Rider candidate\n        if: ${{ needs.credential-check.outputs.merchant_only != 'true' }}") &&
  authenticatedReviewWorkflow.includes('REVIEW_PR_HEAD_SHA: ${{ github.event.pull_request.head.sha }}') &&
  authenticatedReviewWorkflow.includes('urllib.request.urlopen(request, timeout=20)') &&
  authenticatedReviewWorkflow.includes('merchant_only: ${{ steps.check.outputs.merchant_only }}') &&
  authenticatedReviewWorkflow.includes('      - fix-rider-invalid-step-metrics-20261106') &&
  authenticatedReviewWorkflow.includes("github.ref_name == 'fix-rider-invalid-step-metrics-20261106') && contains(github.event.head_commit.message, '[merchant-visual-review]')") &&
  authenticatedReviewWorkflow.includes('Capture authenticated Merchant views on Android Emulator'),
  'Merchant-only visual review must trigger on the POS successor and skip the unrelated nationwide Rider artifact');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Customer Merchant Rider native Android source'}));
