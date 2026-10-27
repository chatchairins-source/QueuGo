Warning: truncated output (original token count: 36063)
Total output lines: 1441

const fs=require('fs'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

const nativePilotWorkflow=fs.readFileSync(path.join(__dirname,'../.github/workflows/build-native-rider-pilot.yml'),'utf8');
const mapEngineWorkflow=fs.readFileSync(path.join(__dirname,'../.github/workflows/queuego-map-engine-buriram.yml'),'utf8');
ok(nativePilotWorkflow.includes('group: queuego-native-${{ github.ref }}')&&nativePilotWorkflow.includes('cancel-in-progress: false')&&mapEngineWorkflow.includes('group: queuego-map-engine-${{ github.ref }}')&&mapEngineWorkflow.includes('cancel-in-progress: false'),'Native Pilot and Map Engine exact-head jobs must finish when the successor advances, while newer work waits');


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
ok(riderIcon.includes('#E6002D')&&riderIcon.includes('fillType="evenOdd"')&&!riderIcon.includes('strokeColor="#EF3340"'),
   'Rider launcher must use the simplified bold QueueGo red Q mark');
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

ok(source.includes('"cart" -> CustomerCartScreen')&&source.includes('"checkout" -> CustomerCheckoutScreen')&&customerCartCheckout.includes('"ตะกร้าสินค้า"')&&customerCartCheckout.includes('"ไปชำระเงิน"')&&customerCartCheckout.includes('"ยืนยันคำสั่งซื้อ"')&&customerCartCheckout.includes('height(115.dp)')&&customerCartCheckout.includes('QgLocationPickerMap')&&customerCartCheckout.includes('"ยืนยันสั่งซื้อ"'),'Customer native must preserve separate Production cart and checkout screens with checkout QueueGo Map and fixed action dock');
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
ok(customerNotifications.includes('LaunchedEffect(unreadIds)')&&customerNotifications.includes('if (unreadIds.isNotEmpty()) onMarkAll()'),'Opening Customer Notifications must mark currently unread owned rows through the existing action');
ok(customerNotifications.includes('OutlinedButton(onClick = onMarkAll, enabled = !busy) { Text("อ่านทั้งหมด") }')&&!customerNotifications.includes('if (notifications.any { !it.read })'),'Customer Notifications must always show the Web-parity mark-all control');
ok(customerNotifications.includes('notifications?user_id=eq." + http.enc(auth.user.id) + "&is_read=eq.false'),'Customer mark-all must stay scoped to the authenticated user unread rows');
ok(source.includes('qg_support_tickets?select=id,category,details,status,order_id,admin_note,created_at')&&source.includes('qg_create_ticket'),'Customer native support must use production ticket RPC');
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
  customerSupportScreenSource.indexOf('refresh(force = true)') <
    customerSupportScreenSource.lastIndexOf('message = "ส่งเรื่องให้ทีม QueueGo แล้ว"'),
  'Customer Support history must reuse a fresh local snapshot and preserve submit feedback after forced refresh'
);
ok(source.includes('qg_customer_order_context')&&source.includes('CustomerTrackingRider'),'Customer native tracking must use production order context');
const customerTrackingScreen=read('customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
ok((source.includes('"order" -> CustomerOrderTrackingScreen')||(source.includes('selectedOrder?.fulfillmentVer…26063 tokens truncated…')&&sharedDesign.includes('"support" ->'),'shared native icon set must preserve Merchant web vectors');
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
ok(!/PosDelivery|deliveryOrders|pos_delivery_kitchen_action|pos_enable_delivery|QUEUEGO_DELIVERY/.test(posOnlyApi+posOnlyUi),'Native POS must not read, render or mutate Delivery orders');
ok(!/GP หน้าร้าน|GP Delivery|report\.gp|report\.delivery|optDouble\("GP"/.test(posOnlyApi+posOnlyUi),'Native POS must not render Delivery GP or include its totals');
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
ok(customerRideArrivalSource.includes('text("arrived_pickup_at")')&&customerRideArrivalSource.includes('text("arrived_dropoff_at")')&&customerRideArrivalSource.includes('Text(passengerRideStatus(current))'),'Native Customer Ride must render arrival timestamps from the existing server state');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Customer Merchant Rider native Android source'}));
