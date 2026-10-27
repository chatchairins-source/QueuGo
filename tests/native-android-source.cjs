const fs=require('fs'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

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
  customerCartCheckout.includes('searchPlaces: suspend (String) -> List<QueueGoPlaceSearchResult>') &&
  customerCartCheckout.includes('QgPlaceSearchPanel(') &&
  customerCartCheckout.indexOf('QgPlaceSearchPanel(') < customerCartCheckout.indexOf('QgLocationPickerMap(') &&
  customerCartCheckout.includes('lastAutoAddress = label') &&
  customerCartCheckout.includes('recenterPoint = selected?.takeIf') &&
  source.includes('"checkout" -> CustomerCheckoutScreen(') &&
  source.includes('busy = busy,\n                    searchPlaces = { query -> api.searchPlaces(query) },\n                    resolveAddress = { latitude, longitude -> api.reverseGeocode(latitude, longitude) },'),
  'Customer checkout place search must use shared QueueGo Places outside the map canvas'
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
ok(source.includes('qg_customer_order_context')&&source.includes('CustomerTrackingRider'),'Customer native tracking must use production order context');
const customerTrackingScreen=read('customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
ok((source.includes('"order" -> CustomerOrderTrackingScreen')||(source.includes('selectedOrder?.fulfillmentVertical == "ride"')&&source.includes('else CustomerOrderTrackingScreen(')))&&customerTrackingScreen.includes('"สถานะล่าสุด"')&&customerTrackingScreen.includes('"ส่งไปที่"')&&customerTrackingScreen.includes('CustomerTrackingProgress(stage)')&&customerTrackingScreen.includes('"Rider ของคุณ"')&&customerTrackingScreen.includes('"รายการสินค้า"')&&customerTrackingScreen.includes('"ยอดรวม"'),'Customer native tracking must preserve Production order-detail hierarchy');
ok(customerTrackingScreen.includes('size(46.dp)')&&customerTrackingScreen.includes('RoundedCornerShape(20.dp)')&&customerTrackingScreen.includes('fontSize = 22.sp')&&customerTrackingScreen.includes('"แจ้งปัญหา"'),'Customer native tracking must preserve Production rider, hero, total and support proportions');
const customerTrackingMap=read('customer/src/main/java/com/queuego/customer/CustomerTrackingMap.kt');
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
  merchantMainSource.includes('sessionLoaded') &&
  !merchantMainSource.includes('delay(350)') &&
  !merchantMainSource.includes('delay(1_000)') &&
  !merchantMainSource.includes('val saved = store.load()\n            if (saved?.session'),
  'Merchant secure-session polling must stay lifecycle-gated, off Main and outside the former hot polling loop'
);
const customerShopForMemory=read('customer/src/main/java/com/queuego/customer/CustomerShopScreen.kt');
const customerSearchForMemory=read('customer/src/main/java/com/queuego/customer/CustomerSearchScreen.kt');
const customerAppForMemory=read('customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt');
ok(
  customerAppForMemory.includes('"favorites" -> CustomerFavoritesScreen(') &&
  customerAppForMemory.includes('customerSystemBackTarget(') &&
  read('customer/src/main/java/com/queuego/customer/CustomerBackNavigation.kt').includes('"favorites" -> "profile"') &&
  customerShopForMemory.includes('qg_customer_favorites?select=id,shop_id,product_id') &&
  customerShopForMemory.includes('available=eq.true&id=in.($selected)') &&
  customerShopForMemory.includes('"รายการนี้ยังไม่พร้อมให้บริการ"') &&
  customerShopForMemory.includes('api.toggle(auth, if (favorite.shop.isNotBlank()) "shop" else "product"'),
  'Customer Favorites must read the existing Production authority, retain unavailable entries for removal, and return to Profile'
);
const customerBackNavigation=read('customer/src/main/java/com/queuego/customer/CustomerBackNavigation.kt');
ok(
  customerAppForMemory.includes('shopReturnScreen = shopReturnScreen') &&
  customerAppForMemory.includes('cartReturnScreen = cartReturnScreen') &&
  customerAppForMemory.includes('ordersReturnScreen = ordersReturnScreen') &&
  customerAppForMemory.includes('locationReturnScreen = locationReturnScreen') &&
  customerAppForMemory.includes('promotionReturnScreen = promotionReturnScreen') &&
  customerBackNavigation.includes('"market-cart" -> "market"') &&
  customerBackNavigation.includes('"checkout" -> "cart"') &&
  customerBackNavigation.includes('"orders" -> ordersReturnScreen') &&
  customerBackNavigation.includes('"market-trip", "laundry-order" -> "orders"') &&
  customerAppForMemory.includes('onBack = { screen = cartReturnScreen }') &&
  customerAppForMemory.includes('onBack = { screen = locationReturnScreen }') &&
  customerAppForMemory.includes('onBack = { screen = promotionReturnScreen }'),
  'Customer system Back and page controls must use Web-aligned parent routes for nested journeys'
);
ok(
  ['ที่อยู่จัดส่ง', 'ออเดอร์ของฉัน', 'การแจ้งเตือน', 'รายการโปรด', 'โปรโมชั่นจากร้าน', 'ติดต่อฝ่ายช่วยเหลือ']
    .every(label => customerAppForMemory.includes(`ProfileDestination("${label}"`)),
  'Customer Profile must expose the same six destinations as the current Web Profile'
);
ok(
  !customerAppForMemory.includes('Text("QueueGo Production"') &&
  !customerAppForMemory.includes('บัญชีนี้ใช้ Session และ RLS ของระบบจริง') &&
  !customerAppForMemory.includes('if (BuildConfig.DEBUG) com.queuego.shared.QgTestBuildInfo('),
  'Customer Profile must not expose internal Production/session/RLS implementation copy'
);
ok(
  customerAppForMemory.includes('customerProfileContact(auth.user.customerPhone, auth.user.customerEmail)') &&
  customerAppForMemory.includes('private fun CustomerProfileHero(name: String, contact: String?)') &&
  customerAppForMemory.includes('if (!contact.isNullOrBlank())') &&
  customerAppForMemory.includes('.size(60.dp)') &&
  customerAppForMemory.includes('RoundedCornerShape(20.dp)') &&
  customerAppForMemory.includes('"บัญชีลูกค้า"') &&
  customerAppForMemory.includes('private fun CustomerProfileSection(') &&
  customerAppForMemory.includes('fontSize = 10.sp') &&
  customerAppForMemory.includes('RoundedCornerShape(16.dp)') &&
  customerAppForMemory.includes('fontSize = 13.sp') &&
  customerAppForMemory.includes('fontSize = 9.5.sp') &&
  /ProfileDestination\(\s*"ลบบัญชีถาวร"/.test(customerAppForMemory) &&
  /ProfileDestination\(\s*"ออกจากระบบ",\s*logout/.test(customerAppForMemory) &&
  customerAppForMemory.includes('QgAccountDeletionSection('),
  'Customer Profile must keep the current Web compact hero/grouped-row hierarchy while preserving account deletion authority'
);
const nativeAuthForCustomerContact=read('shared/src/main/java/com/queuego/shared/NativeAuthApi.kt');
const nativeSessionStoreForCustomerContact=read('shared/src/main/java/com/queuego/shared/SecureRoleSessionStore.kt');
const customerProfileContactForParity=read('customer/src/main/java/com/queuego/customer/CustomerProfileContact.kt');
ok(
  nativeAuthForCustomerContact.includes('select=id,name,role,status,auth_user_id,phone') &&
  nativeAuthForCustomerContact.includes('!it.endsWith("@auth.queuetech.local", ignoreCase = true)') &&
  nativeSessionStoreForCustomerContact.includes('.put("customerPhone", auth.user.customerPhone)') &&
  nativeSessionStoreForCustomerContact.includes('.put("customerEmail", auth.user.customerEmail)') &&
  customerProfileContactForParity.includes('phone?.trim()?.takeIf { it.isNotBlank() }') &&
  customerProfileContactForParity.includes('email?.trim()?.takeIf {') &&
  customerProfileContactForParity.includes('!it.endsWith("@auth.queuetech.local", ignoreCase = true)'),
  'Customer Profile contact must reuse RLS-protected user phone/Auth email and hide synthetic phone-login addresses'
);
const customerPushForMemory=read('customer/src/main/java/com/queuego/customer/CustomerPush.kt');
const customerWebFeaturesForMemory=fs.readFileSync(path.resolve(__dirname,'../customer-features.js'),'utf8');
ok(
  customerAppForMemory.includes('pushEnabled = pushEnabled') &&
  customerAppForMemory.includes('onTogglePush = ::toggleCustomerPush') &&
  customerAppForMemory.includes('onTestPush = ::testCustomerPush') &&
  customerAppForMemory.includes('onPrivacy = {') &&
  customerAppForMemory.includes('CUSTOMER_PRIVACY_POLICY_URL') &&
  customerAppForMemory.includes('setCustomerPushEnabled(context, auth.user.id, true)') &&
  customerAppForMemory.includes('setCustomerPushEnabled(context, auth.user.id, false)') &&
  customerAppForMemory.includes('if (!customerPushEnabled(context, auth.user.id)) {\n            // Remove registrations created by versions that subscribed at login.\n            disableCustomerNativePush(context, auth)') &&
  customerAppForMemory.includes('pushApi.test(auth)'),
  'Customer Profile notification controls must use the real permission, subscribe/unsubscribe and test-push actions and expose the production privacy policy'
);
ok(
  customerPushForMemory.includes('if (!customerPushEnabled(applicationContext, cached.user.id)) return') &&
  customerPushForMemory.includes('if (!customerPushEnabled(this, cached.user.id)) return') &&
  customerPushForMemory.includes('CUSTOMER_ARRIVAL_SOUND_CHANNEL') &&
  customerPushForMemory.includes('CUSTOMER_ARRIVAL_SILENT_CHANNEL') &&
  customerPushForMemory.includes('customerArrivalNotification(title, body)'),
  'Customer push must respect the explicit background preference and arrival sound preference across token refresh and process recreation'
);
ok(
  customerPushForMemory.includes('.getBoolean("arrival_sound_$userId", false)') &&
  customerWebFeaturesForMemory.includes("soundOn=localStorage.getItem('qg_customer_sound_'+u.userId)==='on'"),
  'Customer arrival sound must default off on Native exactly as it does on the current Web Profile'
);
ok(
  customerAppForMemory.includes('"เสียงแจ้งเตือนเมื่อ Rider ถึง"') &&
  customerAppForMemory.includes('"การแจ้งเตือนเบื้องหลัง"') &&
  customerAppForMemory.includes('"ทดสอบการแจ้งเตือน"') &&
  customerAppForMemory.includes('"นโยบายความเป็นส่วนตัว"'),
  'Customer Profile must match current Web notification and privacy controls'
);
const merchantMediaForMemory=read('merchant/src/main/java/com/queuego/merchant/MerchantMediaScreen.kt');
ok(
  sharedDesign.includes('QG_REMOTE_IMAGE_MAX_SIDE = 1280'),
  'Shared remote images must keep the low-memory 1280px decode ceiling'
);
ok(
  customerShopForMemory.includes('LazyColumn(Modifier.fillMaxSize())') &&
  customerShopForMemory.includes('items(products, key = { it.id })') &&
  !customerShopForMemory.includes('products.forEach { product ->'),
  'Customer shop product images must lazy-render instead of allocating the entire catalog at once'
);

const silentCustomerMarket=read('customer/src/main/java/com/queuego/customer/QueueGoMarketScreen.kt');
const silentCustomerLaundry=read('customer/src/main/java/com/queuego/customer/QueueGoLaundryScreen.kt');
const silentMerchantApp=read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
const silentMerchantGrocery=read('merchant/src/main/java/com/queuego/merchant/MerchantGroceryCatalogScreen.kt');
const silentMerchantMarket=read('merchant/src/main/java/com/queuego/merchant/MerchantMarketCatalogScreen.kt');
const silentMerchantPos=read('merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt');
const silentRiderEarnings=read('rider/src/main/java/com/queuego/rider/RiderEarningsScreen.kt');
const silentRiderMessages=read('rider/src/main/java/com/queuego/rider/RiderMessagesScreen.kt');
const silentRiderNotifications=read('rider/src/main/java/com/queuego/rider/RiderNotificationCenter.kt');
const silentRiderSupport=read('rider/src/main/java/com/queuego/rider/RiderSupportScreen.kt');
const silentRiderSafety=read('rider/src/main/java/com/queuego/rider/RiderSafetyScreen.kt');
const silentRiderBenefits=read('rider/src/main/java/com/queuego/rider/RiderBenefitsCard.kt');
const silentRiderDocuments=read('rider/src/main/java/com/queuego/rider/RiderDocumentReadinessCard.kt');
const silentRiderInsurance=read('rider/src/main/java/com/queuego/rider/RiderInsuranceCard.kt');
const silentRiderVehicles=read('rider/src/main/java/com/queuego/rider/RiderVehiclesCard.kt');
const silentRiderDestination=read('rider/src/main/java/com/queuego/rider/RiderMyDestinationDialog.kt');
const silentRiderPassengerLicense=read('rider/src/main/java/com/queuego/rider/RiderPassengerLicenseCard.kt');
const silentCustomerShopping=read('customer/src/main/java/com/queuego/customer/CustomerShoppingScreen.kt');
const silentCustomerPromotion=read('customer/src/main/java/com/queuego/customer/CustomerPromotionScreen.kt');
const silentCustomerOrderTracking=read('customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
const silentCustomerRide=read('customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt');
const silentCustomerExpress=read('customer/src/main/java/com/queuego/customer/CustomerExpressScreen.kt');
const silentCustomerLocation=read('customer/src/main/java/com/queuego/customer/CustomerLocationPicker.kt');
const silentPlaceSearch=read('shared/src/main/java/com/queuego/shared/QueueGoPlaceSearch.kt');
const silentMerchantRevenue=read('merchant/src/main/java/com/queuego/merchant/MerchantRevenueScreen.kt');
const silentMerchantSupport=read('merchant/src/main/java/com/queuego/merchant/MerchantSupportScreen.kt');
const silentMerchantStock=read('merchant/src/main/java/com/queuego/merchant/MerchantMarketStockScreen.kt');
const silentMerchantMedia=read('merchant/src/main/java/com/queuego/merchant/MerchantMediaScreen.kt');
const silentMerchantModules=read('merchant/src/main/java/com/queuego/merchant/MerchantModulesScreen.kt');
const silentMerchantTickets=read('merchant/src/main/java/com/queuego/merchant/MerchantSupportTicketsScreen.kt');
const silentMerchantLaundry=read('merchant/src/main/java/com/queuego/merchant/MerchantLaundryScreen.kt');
const silentMerchantPromotion=read('merchant/src/main/java/com/queuego/merchant/MerchantPromotionScreen.kt');
const silentMerchantHours=read('merchant/src/main/java/com/queuego/merchant/MerchantHoursScreen.kt');
const silentMerchantSetup=read('merchant/src/main/java/com/queuego/merchant/MerchantShopSetupScreen.kt');
const silentRoleShell=read('shared/src/main/java/com/queuego/shared/QueueGoRoleNativeApp.kt');
const silentAuthHost=read('shared/src/main/java/com/queuego/shared/QueueGoAuthHost.kt');
ok(
  !customerAppForMemory.includes('"กำลังโหลดข้อมูล…"') &&
  !customerAppForMemory.includes('if (loading) {\n                CircularProgressIndicator()') &&
  !customerShopForMemory.includes('LinearProgressIndicator') &&
  !customerShopForMemory.includes('"กำลังโหลดสินค้า…"') &&
  !silentCustomerMarket.includes('loading -> CircularProgressIndicator()') &&
  !silentCustomerMarket.includes('cartPage && loading) CircularProgressIndicator()') &&
  !silentCustomerLaundry.includes('"กำลังโหลดร้านซัก..."') &&
  !silentCustomerLaundry.includes('servicesLoading) {\n                    CircularProgressIndicator()') &&
  !silentMerchantApp.includes('"กำลังโหลดข้อมูล…"') &&
  !silentMerchantApp.includes('if (loading && orders.isEmpty()) CircularProgressIndicator()') &&
  !silentMerchantApp.includes('"กำลังโหลดรายการ..."') &&
  !silentMerchantGrocery.includes('กำลังโหลดแคตตาล็อก') &&
  !silentMerchantGrocery.includes('CircularProgressIndicator') &&
  !silentMerchantMarket.includes('LinearProgressIndicator') &&
  !silentMerchantPos.includes('if (report == null) {\n            CircularProgressIndicator()') &&
  !silentRiderEarnings.includes('กำลังโหลด') &&
  !silentRiderMessages.includes('กำลังโหลดข้อความ') &&
  !silentRiderNotifications.includes('กำลังโหลดแจ้งเตือน') &&
  !silentRiderSupport.includes('Text("กำลังโหลด…", color = QgMuted') &&
  !silentRiderSafety.includes('!emergencyLoaded -> CircularProgressIndicator') &&
  !silentRiderSafety.includes('!fatigueLoaded -> CircularProgressIndicator') &&
  !silentRiderBenefits.includes('LinearProgressIndicator') &&
  !silentRiderDocuments.includes('LinearProgressIndicator') &&
  !silentRiderInsurance.includes('LinearProgressIndicator') &&
  !silentRiderVehicles.includes('LinearProgressIndicator') &&
  !silentRiderDestination.includes('CircularProgressIndicator') &&
  !silentRiderPassengerLicense.includes('กำลังตรวจสิทธิ์') &&
  !silentMerchantApp.includes('if (orderCountsLoading) "…"') &&
  !customerTrackingMap.includes('กำลังโหลดตำแหน่งล่าสุด') &&
  !silentCustomerShopping.includes('"กำลังโหลดข้อมูล…"') &&
  !silentCustomerPromotion.includes('loading -> CircularProgressIndicator()') &&
  !silentCustomerOrderTracking.includes('"กำลังโหลดรายการ..."') &&
  !silentCustomerRide.includes('กำลังตรวจสถานะเที่ยวล่าสุด') &&
  !silentCustomerExpress.includes('กำลังตรวจสถานะ Express ล่าสุด') &&
  !silentCustomerLocation.includes('modifier = Modifier.align(Alignment.Center).size(28.dp)') &&
  !silentPlaceSearch.includes('if (busy) CircularProgressIndicator(Modifier.size(18.dp)') &&
  !silentMerchantRevenue.includes('CircularProgressIndicator') &&
  !silentMerchantSupport.includes('"กำลังโหลดข้อความ..."') &&
  !silentMerchantStock.includes('loading -> Box(') &&
  !silentMerchantMedia.includes('loading -> item(key = "media-loading")') &&
  !silentMerchantModules.includes('if (loading) {') &&
  !silentMerchantTickets.includes('loading -> Text("กำลังโหลด..."') &&
  !silentMerchantLaundry.includes('Text("กำลังโหลด..."') &&
  !silentMerchantHours.includes('if (loading) {\n            Box(') &&
  !silentMerchantSetup.includes('if (!mapReady) {\n                    CircularProgressIndicator(') &&
  !silentRoleShell.includes('CircularProgressIndicator(Modifier.padding(24.dp))') &&
  !silentAuthHost.includes('CircularProgressIndicator(Modifier.padding(24.dp))') &&
  customerAppForMemory.includes('Restore local session silently.'),
  'Native data refresh must stay visually silent while cached/current content remains on screen'
);
ok(
  customerSearchForMemory.includes('LazyColumn(') &&
  customerSearchForMemory.includes('items(rows, key = { "search-shop:" + it.id })') &&
  !customerSearchForMemory.includes('rows.forEach { shop ->'),
  'Customer search image results must lazy-render'
);
ok(
  customerAppForMemory.includes('items(nearbyShops, key = { "home-shop:" + it.id })') &&
  !customerAppForMemory.includes('else -> nearbyShops.forEach { shop ->'),
  'Customer Home nearby-shop images must lazy-render'
);
ok(
  merchantMediaForMemory.includes('LazyColumn(') &&
  merchantMediaForMemory.includes('items = rows.chunked(3)') &&
  !merchantMediaForMemory.includes('rows.chunked(3).forEach'),
  'Merchant media gallery must lazy-render rows instead of decoding the full image library at once'
);
const placeSearchCacheSource=read('shared/src/main/java/com/queuego/shared/QueueGoPlaceSearchCache.kt');
ok(
  placeSearchCacheSource.includes('context.noBackupFilesDir, "queuego_place_search_cache_v1"') &&
  placeSearchCacheSource.includes('FRESH_MS = 7L * 24L * 60L * 60L * 1000L') &&
  silentPlaceSearch.includes('cache.read(normalized)') &&
  silentPlaceSearch.includes('if (cached.fresh)') &&
  silentPlaceSearch.includes('cache.write(normalized, fresh)') &&
  silentPlaceSearch.includes('if (results.isEmpty() && searched && !busy)'),
  'QueueGo Places search must render durable cached results first and avoid repeated fresh downloads'
);
const riderBenefitsCacheSource=read('rider/src/main/java/com/queuego/rider/RiderBenefitsCache.kt');
const riderBenefitsCardSource=read('rider/src/main/java/com/queuego/rider/RiderBenefitsCard.kt');
ok(
  riderBenefitsCacheSource.includes('context.noBackupFilesDir, "rider_benefits_cache_v1"') &&
  riderBenefitsCardSource.includes('cache.read(auth.user.id)') &&
  riderBenefitsCardSource.includes('cache.write(auth.user.id, fresh)') &&
  riderBenefitsCardSource.includes('Keep the cached/current rows visible on transient refresh failure') &&
  !riderBenefitsCardSource.includes('benefits = emptyList()'),
  'Rider Benefits must render durable user-scoped cached rows first and refresh silently'
);
const customerLaundryContentCacheSource=read('customer/src/main/java/com/queuego/customer/CustomerLaundryContentCache.kt');
const customerPromotionCacheSource=read('customer/src/main/java/com/queuego/customer/CustomerPromotionCache.kt');
const customerContentCacheSource=read('customer/src/main/java/com/queuego/customer/CustomerContentCache.kt');
ok(
  customerPromotionCacheSource.includes('context.noBackupFilesDir, "customer_promotion_cache_v1"') &&
  silentCustomerPromotion.includes('cache.read()') &&
  silentCustomerPromotion.includes('cache.write(fresh)') &&
  silentCustomerPromotion.includes('if (rows.isEmpty()) error = failure.message'),
  'Customer Promotions must render durable cached rows first and refresh in the background'
);
ok(
  !customerContentCacheSource.includes('readPromotions()') &&
  !customerContentCacheSource.includes('writePromotions('),
  'Customer Promotions must use one durable cache path only'
);
ok(
  customerLaundryContentCacheSource.includes('context.noBackupFilesDir, "customer_laundry_cache_v1"') &&
  silentCustomerLaundry.includes('contentCache.read()') &&
  silentCustomerLaundry.includes('contentCache.write(fresh)') &&
  silentCustomerLaundry.includes('catalog?.servicesByHub?.get(hub.id).orEmpty()') &&
  !silentCustomerLaundry.includes('api.services(auth, hub.id)'),
  'Customer Laundry catalog must render local-first and must not redownload the full catalog when choosing a shop'
);
const merchantAppSource=read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
const merchantPromotionCacheSource=read('merchant/src/main/java/com/queuego/merchant/MerchantPromotionCache.kt');
ok(
  merchantPromotionCacheSource.includes('context.noBackupFilesDir, "merchant_promotion_cache_v1"') &&
  silentMerchantPromotion.includes('cache.read(auth.user.id, shop.id)') &&
  silentMerchantPromotion.includes('cache.write(auth.user.id, shop.id, fresh)') &&
  silentMerchantPromotion.includes('if (rows.isEmpty()) message = failure.message'),
  'Merchant Promotions must render durable cached metadata first and refresh in the background'
);
const merchantContentCacheSource=read('merchant/src/main/java/com/queuego/merchant/MerchantContentCache.kt');
ok(
  silentMerchantApp.includes('delay(8_000)\n                    refreshOrders()') &&
  !silentMerchantApp.includes('refreshOrders()\n                    delay(3_000)'),
  'Merchant foreground fallback must not redownload the full order snapshot every three seconds'
);
ok(
  merchantContentCacheSource.includes('context.noBackupFilesDir, "merchant_content_cache_v1"') &&
  merchantContentCacheSource.includes('CachedMerchantContent') &&
  merchantAppSource.includes('contentCache.read(auth.user.id)') &&
  merchantAppSource.includes('contentCache.write(auth.user.id, s, freshProducts)') &&
  merchantAppSource.includes('refreshAll(showLoading = cached == null)'),
  'Merchant shop and menu content must render from durable local cache before authoritative refresh'
);
const merchantHoursCacheSource=read('merchant/src/main/java/com/queuego/merchant/MerchantHoursCache.kt');
ok(
  merchantHoursCacheSource.includes('context.noBackupFilesDir, "merchant_hours_cache_v1"') &&
  silentMerchantHours.includes('cache.read(shop.id, today)') &&
  silentMerchantHours.includes('cache.write(shop.id, state)') &&
  silentMerchantHours.includes('cache.write(shop.id, MerchantHoursState(days, special))') &&
  silentMerchantHours.includes('if (cached == null) message = it.message'),
  'Merchant business hours must render durable cached settings first and refresh silently'
);
const merchantMediaCacheSource=read('merchant/src/main/java/com/queuego/merchant/MerchantMediaCache.kt');
ok(
  merchantMediaCacheSource.includes('context.noBackupFilesDir, "merchant_media_cache_v1"') &&
  merchantMediaForMemory.includes('cache.read(auth.session.authUserId)') &&
  merchantMediaForMemory.includes('cache.write(auth.session.authUserId, fresh)') &&
  merchantMediaForMemory.includes('if (rows.isEmpty()) message = it.message'),
  'Merchant media rows must render durable cached content before silent Storage refresh'
);
const merchantModuleCacheSource=read('merchant/src/main/java/com/queuego/merchant/MerchantModuleCache.kt');
ok(
  merchantModuleCacheSource.includes('context.noBackupFilesDir, "merchant_module_cache_v1"') &&
  silentMerchantModules.includes('cache.read(auth.user.id)') &&
  silentMerchantModules.includes('cache.write(auth.user.id, fresh)') &&
  silentMerchantModules.includes('enabled = busyKey == null && authoritativeReady') &&
  silentMerchantModules.includes('if (busyKey == null && authoritativeReady)'),
  'Merchant module settings may render cached values but actions must wait for authoritative refresh'
);
ok(
  merchantAppSource.includes('coroutineScope {') &&
  merchantAppSource.includes('val ordersDeferred = async { api.loadOrders(auth) }') &&
  merchantAppSource.includes('val productsDeferred = async { api.loadProducts(auth, s.id) }') &&
  merchantAppSource.includes('val revenueDeferred = async {') &&
  merchantAppSource.includes('val gpDeferred = async {'),
  'Merchant initial refresh must parallelize independent server reads instead of serializing the whole dashboard'
);
ok(
  customerAppForMemory.includes('coroutineScope {') &&
  customerAppForMemory.includes('val shopsDeferred = async { api.loadShops(auth) }') &&
  customerAppForMemory.includes('val ordersDeferred = async { api.loadOrders(auth) }') &&
  customerAppForMemory.includes('val bannersDeferred = async { api.loadHomeBanners(auth) }') &&
  customerGuestShell.includes('val shopsDeferred = async { api.loadShopsPublic() }') &&
  customerGuestShell.includes('val serviceBannersDeferred = async { api.loadServiceBannersPublic() }'),
  'Customer initial refresh must parallelize independent authenticated and guest reads'
);
const sharedMapForStability=read('shared/src/main/java/com/queuego/shared/QueueGoMap.kt');
ok(
  merchantAppSource.includes('runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }') &&
  /val started = runCatching \{[\s\S]*?tone\.startTone\(ToneGenerator\.TONE_PROP_BEEP2, 650\)[\s\S]*?\}\.getOrDefault\(false\)/.test(merchantAppSource) &&
  merchantAppSource.includes('if (!started) {') &&
  merchantAppSource.includes('runCatching { tone.release() }'),
  'Merchant notification audio must fail closed if the Android audio service cannot create or play a tone'
);
ok(
  sharedMapForStability.includes('private class QueueGoTrackingMapHolder') &&
  sharedMapForStability.includes('view?.onDestroy()') &&
  sharedMapForStability.includes('labelImages.clear()') &&
  sharedMapForStability.includes('lastPoints = emptyList()') &&
  sharedMapForStability.includes('map = null') &&
  sharedMapForStability.includes('view = null') &&
  sharedMapForStability.includes('holder.destroy()') &&
  !sharedMapForStability.includes('mutableMapOf<String, Bitmap>') &&
  !sharedMapForStability.includes('mutableMapOf<String, android.graphics.Bitmap>'),
  'Shared QueueGo MapLibre tracking maps must destroy MapView ownership and clear label signatures without retaining bitmap cache objects'
);
ok(merchantAppSource.includes('LaunchedEffect(screen, auth.session.accessToken, lifecycle)')&&
   merchantAppSource.includes('lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)')&&
   merchantAppSource.includes('.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }'),
   'Merchant order and notification polling must stop outside foreground lifecycle and preserve cancellation');
ok(merchantAppSource.includes('runMerchantPushProfileAction')&&
   merchantAppSource.includes('pushApi.test(auth)')&&
   merchantAppSource.includes('title = "การแจ้งเตือนเบื้องหลัง"')&&
   merchantAppSource.includes('title = "ทดสอบการแจ้งเตือน"'),
   'Merchant profile must use the real native push subscription and server test path rather than a local-only sound check');
const merchantDashboardStart=source.indexOf('private fun DashboardScreen(');
const merchantDashboardEnd=source.indexOf('@Composable\nprivate fun MerchantPauseButton',merchantDashboardStart);
const merchantDashboardSource=source.slice(merchantDashboardStart,merchantDashboardEnd);
const merchantDashboardSummarySource=read('merchant/src/main/java/com/queuego/merchant/MerchantDashboardSummary.kt');
ok(merchantDashboardSource.includes('merchantDashboardSummary(orders)')&&
   merchantDashboardSource.includes('"สรุปออเดอร์วันนี้"')&&
   ['"ออเดอร์ทั้งหมด"','"เสร็จแล้ว"','"กำลังเตรียม"','"ยกเลิก"','"เมนูหลัก"'].every(label=>merchantDashboardSource.includes(label))&&
   !merchantDashboardSource.includes('"ออเดอร์ที่ต้องจัดการ"')&&
   merchantDashboardSummarySource.includes('status in setOf("completed", "delivered")')&&
   merchantDashboardSummarySource.includes('status in setOf("shop_accepted", "preparing")'),
   'Merchant home must show Web status counts and main menu from the authoritative native order list');
const merchantOrdersStart=source.indexOf('private fun MerchantOrdersScreen(');
const merchantOrdersEnd=source.indexOf('@Composable\nprivate fun MerchantOrderCard',merchantOrdersStart);
const merchantOrdersSource=source.slice(merchantOrdersStart,merchantOrdersEnd);
ok(merchantOrdersSource.includes('merchantOrderFilterBucket')&&
   merchantOrdersSource.includes('merchantOrderSearchMatches')&&
   ['"ทั้งหมด"','"รอรับ"','"กำลังเตรียม"','"กำลังหาไรเดอร์"','"กำลังส่ง"','"สำเร็จ"'].every(label=>merchantOrdersSource.includes(label))&&
   merchantOrdersSource.includes('"ค้นหาด้วยเลขออเดอร์ หรือชื่อลูกค้า"')&&
   merchantOrdersSource.includes('"ไม่พบคำสั่งซื้อ"')&&
   merchantAppSource.includes('"pending_shop" -> "รอรับ"')&&
   merchantAppSource.includes('"delivered" -> "จัดส่งสำเร็จ"'),
   'Merchant order list must preserve Web status filters, order/customer search, empty results and status labels');
const merchantOrderDetailStart=source.indexOf('private fun MerchantOrderDetail(');
const merchantOrderDetailEnd=source.indexOf('private data class MerchantOrderEditDraftRow',merchantOrderDetailStart);
const merchantOrderDetailSource=source.slice(merchantOrderDetailStart,merchantOrderDetailEnd);
const merchantOrderDetailApiSource=read('merchant/src/main/java/com/queuego/merchant/MerchantApi.kt');
ok(merchantOrderDetailApiSource.includes('select=id,delivery_address,rider_arrived_customer_at,merchant_ready_estimate_at,merchant_ready_revision')&&
   merchantOrderDetailApiSource.includes('deliveryAddress = deliveryAddresses[order.id]')&&
   merchantOrderDetailSource.includes('order.deliveryAddress')&&
   merchantOrderDetailSource.includes('order.customerName')&&
   merchantOrderDetailSource.includes('merchantOrderProgressStage(order.status)')&&
   ['"ออเดอร์เข้า"','"ร้านรับ"','"เตรียมสินค้า"','"กำลังจัดส่ง"','"สำเร็จ"'].every(label=>merchantOrderDetailSource.includes(label))&&
   merchantOrderDetailSource.includes('items.sumOf { it.quantity.coerceAtLeast(0) }')&&
   merchantOrderDetailSource.includes('order.status !in setOf("pending", "pending_shop", "cancelled")')&&
   merchantOrderDetailSource.includes('"เงินสดที่ Rider จ่ายร้านเมื่อรับสินค้า"'),
   'Merchant order detail must show the Web customer/address/progress/item-count and only show Rider cash when Web does');
ok(merchantAppSource.includes('voiceController.startOutgoing(auth, orderId, "customer")')&&
   merchantAppSource.includes('pendingVoiceOrderId')&&
   merchantAppSource.includes('voicePermission.launch(Manifest.permission.RECORD_AUDIO)')&&
   merchantAppSource.includes('voiceController.state.value.phase in setOf(')&&
   merchantOrderDetailSource.includes('onCallCustomer')&&
   merchantOrderDetailSource.includes('"โทรหาลูกค้าผ่าน QueueGo"')&&
   merchantOrderDetailSource.includes('"completed", "delivered", "cancelled", "no_rider_available"'),
   'Merchant order detail must start the existing authenticated QueueGo customer voice call only in active order states');
const merchantTicketSource=read('merchant/src/main/java/com/queuego/merchant/MerchantSupportTicketsScreen.kt');
const sharedTicketSource=read('shared/src/main/java/com/queuego/shared/NativeSupportTickets.kt');
ok(merchantOrderDetailSource.includes('merchantOrderCanReportIssue(order.status)')&&
   merchantOrderDetailSource.includes('onReportIssue')&&merchantOrderDetailSource.includes('"แจ้งปัญหาออเดอร์นี้"')&&
   merchantAppSource.includes('screen = "order-report"')&&merchantAppSource.includes('MerchantSupportTicketsScreen(auth, orders, order.id, order.number)'),
   'Merchant order detail must expose Web report action only for completed orders and bind the selected order');
ok(sharedTicketSource.includes('"qg_support_tickets?select=id,category,details,status,order_id,created_at,admin_note,evidence_path"')&&
   sharedTicketSource.includes('http.rpc("qg_create_ticket"')&&sharedTicketSource.includes('/storage/v1/object/qg-evidence/')&&
   sharedTicketSource.includes('10..2000')&&sharedTicketSource.includes('5 * 1024 * 1024')&&
   merchantTicketSource.includes('"ออเดอร์ที่เกี่ยวข้อง (ถ้ามี)"')&&merchantTicketSource.includes('"ประเภทปัญหา"')&&
   merchantTicketSource.includes('"รายละเอียด"')&&merchantTicketSource.includes('"ส่งเรื่อง"')&&
   merchantTicketSource.includes('"เรื่องที่แจ้งไว้"')&&merchantTicketSource.includes('"ดูหลักฐาน"'),
   'Merchant issue reporting must reuse QueueGo ticket RPC/evidence authority and match Web form/list states');
ok(source.includes('RiderReadyCard')&&source.includes('RoundedCornerShape(21.dp)')&&source.includes('"รับงาน · " + secondsLeft + " วิ"'),'Rider native must preserve one Ready card and the active job accept countdown');
const riderApi=read('rider/src/main/java/com/queuego/rider/QueueGoApi.kt');
const riderApp=read('rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
const riderAcceptanceMode=read('rider/src/main/java/com/queuego/rider/RiderAcceptanceMode.kt');
const riderPush=read('rider/src/main/java/com/queuego/rider/RiderPush.kt');
const riderSessionStore=read('rider/src/main/java/com/queuego/rider/SessionStore.kt');
const sharedManifest=read('shared/src/main/AndroidManifest.xml');
for(const role of ['customer','merchant','rider']){
  const appManifest=read(role+'/src/main/AndroidManifest.xml');
  ok(!/longdo\.map\.|api\.longdo\.com/i.test(sharedManifest+'\n'+appManifest),
     role+' merged app manifest must not inherit retired Longdo key/checker metadata');
  ok(appManifest.includes('queuego.map.service.base_url')&&appManifest.includes('${queuegoMapServiceBaseUrl}'),
     role+' must retain its current QueueGo Map service configuration');
}
const riderManifest=read('rider/src/main/AndroidManifest.xml');
const nativeRootGradle=read('build.gradle.kts');
const runtimeCapture=read('qa/capture-rider-launch.py');
const authenticatedReviewCapture=read('qa/capture-rider-authenticated-review.py');
const pilotWorkflow=fs.readFileSync(path.resolve(root,'..','.github','workflows','build-native-rider-pilot.yml'),'utf8');
ok(
  authenticatedReviewCapture.includes('def node_exact(n,marker):') &&
  authenticatedReviewCapture.includes('any(node_exact(n,title) and rect(n) for n in xs)') &&
  authenticatedReviewCapture.includes('not any(node_exact(n,title) and rect(n) for n in xs)') &&
  authenticatedReviewCapture.includes('laundry_profile=scroll_to_text("รับงานฝากซัก")') &&
  authenticatedReviewCapture.includes('expanded=scroll_to_text("แนะนำบริการและวิธีใช้งาน")') &&
  authenticatedReviewCapture.includes('"profile_laundry","08-learning-center"') &&
  authenticatedReviewCapture.includes('shot("03-profile-laundry")') &&
  !authenticatedReviewCapture.includes('node_contains(n,title)'),
  'Rider authenticated review must capture the Web-backed Laundry Profile control and use exact dialog titles so underlying subtitles cannot fake open/close state'
);
ok(riderApi.includes('qg_get_my_rider_offer')&&!riderApi.includes('rpcArray("get_rider_delivery_pool"'),'Rider native must show only the server-selected live offer, never a shared delivery pool');
ok(riderApi.includes('qg_rider_decline_offer')&&riderApi.includes('qg_rider_action_once'),'Rider native offer accept/decline must stay on guarded Production RPCs');
ok(
  riderApi.includes('"qg_rider_work_preferences"') &&
  riderApi.includes('suspend fun workPreferences(auth: QueueGoAuth)') &&
  riderApi.includes('suspend fun saveWorkPreferences(') &&
  riderApp.includes('val restored = api.workPreferences(auth)') &&
  riderApp.includes('val acknowledged = api.saveWorkPreferences(') &&
  riderApp.includes('acceptanceModeStore.setAutoAcceptEnabled(auth.user.id, acknowledged.autoAccept)') &&
  riderAcceptanceMode.includes('RiderWorkPreferencesControl(') &&
  riderAcceptanceMode.includes('"รับงานพ่วง"') &&
  riderAcceptanceMode.includes('"ประเภทบริการที่รับ"') &&
  riderAcceptanceMode.includes('"ส่งพัสดุ Express"') &&
  riderAcceptanceMode.includes('"รับส่งผู้โดยสาร"'),
  'Rider Native work preferences must reuse the Web server authority and expose bundle/service choices without local-only toggles'
);
ok(riderApp.includes('พร้อมรับงานแล้ว')&&riderApp.includes('คุณจะได้รับงานในพื้นที่ใกล้เคียง'),'Rider idle card must use the approved Ready title and nearby-work subtitle');
ok(riderApp.includes('snapshot?.laundry?.activeJob?.jobId')&&!riderApp.includes('snapshot?.laundry?.activeJob?.id,'),'Rider Auto Accept lifecycle key must use Laundry jobId, never a nonexistent id field');
ok(riderApp.includes('onExpired =')&&riderApp.includes('ข้อเสนอนี้หมดเวลา ระบบกำลังส่งงานให้ Rider คนถัดไป'),'Rider expired offers must immediately leave the card and continue sequential dispatch');
ok(riderApp.includes('ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)')&&riderApp.includes('มีงานใหม่ที่ระบบจัดให้ กรุณาตอบรับภายใน 30 วินาที'),'Rider foreground must alert once for a newly issued server offer');
ok(riderApi.includes('if (!online)')&&riderApi.includes('qg_get_my_rider_offer')&&riderApi.includes('qg_rider_decline_offer'),'Rider going offline must release any live offer immediately');
ok(riderApp.includes('api.setOnline(current, false, null, null)')&&riderApp.includes('api.revoke(current.session)'),'Rider logout must take the Rider offline before session revocation');
ok(
  riderApp.includes('รับงานสำเร็จ · นำทางด้วยแผนที่ QueueGo')&&
  riderApp.includes('activeJob = acceptedFallback')&&
  !riderApp.includes('openNavigation(context, updated?.activeJob ?: acceptedFallback)')&&
  (
    riderApp.includes('val acceptedFallback = current.offeredJob.copy(')||
    (
      riderApp.includes('fun acceptTargetedOffer(job: RiderJob, automatic: Boolean)')&&
      riderApp.includes('val liveOffer = current.offeredJob?.takeIf')&&
      riderApp.includes('val acceptedFallback = liveOffer.copy(')
    )
  ),
  'Rider native must promote an accepted offer locally and retain QueueGo navigation even if optional post-claim refresh fails'
);
ok(riderApi.includes('riderNullableUuid')&&riderApi.includes('marketOrderId = riderNullableUuid(o.optString("market_order_id"))')&&riderApi.includes('val id = riderNullableUuid(rows.optJSONObject(i)?.optString("order_id"))'),'Rider native must reject JSON null/null-like UUID values before Market or offer RPC routing');
ok(riderApi.includes('arrivedShopAt = riderNullableText(o.optString("rider_arrived_shop_at"))')&&
   riderApi.includes('arrivedCustomerAt = riderNullableText(o.optString("rider_arrived_customer_at"))'),
   'Rider arrival timestamps must reject JSON null literals so the UI never shows false arrived checkmarks');
ok(riderApp.includes('completedMode == "pickup" && pickupCommittedStatus == "in_progress"')&&
   riderApp.includes('snapshot = committedRead')&&
   riderApp.includes('snapshot = localAfterSuccess')&&
   !riderApp.includes('openNavigation(context, nextStop)'),
  'Rider native must preserve the authoritative next bundle stop after pickup and stay in QueueGo');
ok(riderApp.includes('val localAfterSuccess = when (completedMode)')&&
   riderApp.includes('riderApplyPickupCommit(')&&
   riderApp.includes('pickupCommittedStatus ?: "picked_up"')&&
   riderApp.includes('pickup.copy(status = "PICKED_UP")')&&
   riderApp.includes('riderAfterDeliveryCommit(previous, job.id)')&&
   read('rider/src/main/java/com/queuego/rider/RiderContinuousDispatch.kt').includes('bundled.copy(job = bundled.job.copy(status = "completed"))')&&
   riderApp.includes('persistCommittedSnapshot(localAfterSuccess)')&&
   riderApp.includes('?: localAfterSuccess?.activeJob'),
   'Rider proof success must advance local workflow immediately, including bundle-aware pickup handoff, so verification cannot repeat when refresh is slow or fails');
ok(riderApp.includes('if (current == null) {')&&
   riderApp.includes('if (loadError != null) {')&&
   riderApp.includes('if (snapshot == null) loadError = "ยังโหลดสถานะ Rider ไม่สำเร็จ: "') &&
   riderApp.includes('Text("ลองเชื่อมต่ออีกครั้ง")')&&
   !riderApp.includes('"กำลังเชื่อมต่อ Longdo Map…"'),
   'Rider refresh errors must keep an existing safe snapshot visible and initial loading must stay silent unless there is a real first-load failure');
const riderMap=read('rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/RiderNavigationMap.kt');
const nativeRealtimeSubscriptions=read('shared/src/main/java/com/queuego/shared/NativeRealtimeSubscription.kt');
const riderDispatchLaundryMigration=fs.readFileSync(path.resolve(root,'..','supabase','migrations','20261009044500_rider_dispatch_excludes_active_laundry.sql'),'utf8');
ok(riderDispatchLaundryMigration.includes("from public.laundry_rider_jobs lj")&&riderDispatchLaundryMigration.includes("lj.status in('assigned','accepted','arrived','collected')"),'automatic Rider dispatch must not offer normal delivery work during an active Laundry leg');
const riderMessages=read('rider/src/main/java/com/queuego/rider/RiderMessagesScreen.kt');
const riderEarnings=read('rider/src/main/java/com/queuego/rider/RiderEarningsScreen.kt');
const riderProfile=read('rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt');
ok(riderApp.includes('RiderBottomNavigation(')&&riderApp.includes('Triple("home", "หน้าแรก", "home")')&&riderApp.includes('Triple("chat", "ข้อความ", "support")')&&riderApp.includes('Triple("earn", "รายได้", "chart")')&&riderApp.includes('Triple("profile", "โปรไฟล์", "user")'),'Rider native must preserve Production four-tab bottom navigation');
ok(riderApp.includes('BackHandler(enabled = activeTab != "home"')&&riderApp.includes('activeTab = "home"'),'Rider native Android back must return non-home tabs to Home');
ok(riderApp.includes('chatJob = null')&&riderApp.includes('activeTab = "home"')&&riderApp.includes('มีงานใหม่ที่ระบบจัดให้ กรุณาตอบรับภายใน 30 วินาที'),'new selected Rider offer must immediately surface on Home from any tab');
ok(riderMessages.includes('order_chat_messages?select=order_id,sender_id,message,created_at')&&riderMessages.includes('deliveries?select=order_id,delivered_at')&&riderMessages.includes('30 * 60_000L'),'Rider native message inbox must use Production chat and 30-minute completed-order window');
ok(
  riderMessages.includes('Text("ข้อความ"') &&
  !riderMessages.includes('"แชต"') &&
  !riderMessages.includes('"แจ้งเตือน"') &&
  !riderMessages.includes('RiderNotificationCenter(') &&
  !riderApp.includes('messageSection') &&
  !riderApp.includes('unreadNotifications') &&
  riderApp.includes('unreadMessages = unreadMessages.coerceAtMost(99)') &&
  riderApp.includes('syncRiderNativePush(context, auth, api, pushStore)') &&
  riderProfile.includes('api.testNativePush(auth)'),
  'Rider Messages must match current Web single inbox while native push remains owned by background/profile controls'
);
ok(riderEarnings.includes('api.periodSummary(auth, days)')&&riderEarnings.includes('api.cashLedger(auth, from.toString(), to.toString())')&&riderEarnings.includes('"วันนี้"')&&riderEarnings.includes('"7 วัน"')&&riderEarnings.includes('"30 วัน"'),'Rider native earnings must preserve Production periods and ledger source');
ok(riderProfile.includes('api.updateAccount(auth, name, phone)')&&riderProfile.includes('api.testNativePush(auth)')&&riderProfile.includes('QgAccountDeletionSection')&&riderProfile.includes('RiderLaundryModeControl(')&&riderProfile.includes('api.setLaundryMode(auth, enabled)')&&riderProfile.includes('RiderLaundryInvites(')&&riderProfile.includes('api.respondLaundryInvite(auth, invite.inviteId, accept)'),'Rider native Profile must preserve account, push and deletion controls while matching current Web Laundry mode and invitations through their existing dedicated RPCs');
ok(
  riderProfile.includes('profilePhotoPicker.launch("image/*")') &&
  riderProfile.includes('riderVehicleLabel(profile?.vehicleType)') &&
  riderProfile.includes('RiderProfilePill(snapshot?.online == true)') &&
  !riderProfile.includes('"ยืนยันข้อมูลรถแล้ว"') &&
  !riderProfile.includes('"ยังไม่มีคะแนนจากลูกค้า"') &&
  !riderProfile.includes('api.ratingSummary(auth)') &&
  !riderProfile.includes('api.complimentSummary(auth)'),
  'Rider Profile hero must match Web compact identity/vehicle/status surface without extra reputation or verification rows'
);
const riderSupport=read('rider/src/main/java/com/queuego/rider/RiderSupportScreen.kt');
ok(riderProfile.includes('RiderSupportScreen(')&&riderProfile.includes('แจ้งปัญหา / ติดตามเรื่อง'),'Rider native profile must expose Production support flow');
const riderProfilePreferencesSection = riderProfile.indexOf('RiderProfileSection("รูปแบบและประเภทงาน")');
const riderProfileWorkSection = riderProfile.indexOf('RiderProfileSection("การรับงาน")');
const riderProfileAccountSection = riderProfile.indexOf('RiderProfileSection("บัญชีและช่วยเหลือ")');
const riderProfileNotificationSection = riderProfile.indexOf('RiderProfileSection("การแจ้งเตือน")');
const riderProfilePrivacySection = riderProfile.indexOf('RiderProfileSection("ความเป็นส่วนตัวและบัญชี")');
const riderProfileExtraSection = riderProfile.indexOf('RiderProfileSection("เพิ่มเติม")');
ok(
  riderProfilePreferencesSection >= 0 &&
  riderProfileWorkSection > riderProfilePreferencesSection &&
  riderProfileAccountSection > riderProfileWorkSection &&
  riderProfileNotificationSection > riderProfileAccountSection &&
  riderProfilePrivacySection > riderProfileNotificationSection &&
  riderProfileExtraSection > riderProfilePrivacySection &&
  riderProfile.includes('"สถานะรับงาน"') &&
  riderProfile.includes('"จัดการสถานะที่หน้าแรก"') &&
  riderProfile.includes('onClick = onOpenHome') &&
  riderProfile.includes('routeSettings()') &&
  riderApp.includes('onOpenHome = { activeTab = "home" }') &&
  riderApp.includes('routeSettings = {') &&
  riderProfile.includes('var showExtraTools by remember { mutableStateOf(false) }') &&
  riderProfile.includes('"เครื่องมือ Rider เพิ่มเติม"') &&
  riderProfile.includes('if (showExtraTools && showAccountDetails)') &&
  riderProfile.includes('fontSize = 9.sp') &&
  riderProfile.includes('shape = RoundedCornerShape(16.dp)') &&
  riderProfile.includes('heightIn(min = 52.dp)') &&
  riderProfile.includes('.size(34.dp)') &&
  riderProfile.includes('RoundedCornerShape(11.dp)') &&
  riderProfile.includes('fontSize = 12.sp') &&
  riderProfile.includes('fontSize = 8.5.sp') &&
  !riderProfile.includes('RiderProfileSection("บัญชีและเอกสาร")') &&
  !riderProfile.includes('RiderProfileSection("การนำทางและแผนที่")') &&
  !riderProfile.includes('RiderProfileSection("ขั้นสูง")'),
  'Rider Profile must keep the current Web core section order and collapse Native-only tools below it'
);
ok(riderSupport.includes('qg_support_tickets?select=id,category,details,status,order_id,created_at,admin_note,evidence_path')&&riderSupport.includes('"qg_create_ticket"')&&riderSupport.includes('/storage/v1/object/qg-evidence/')&&riderSupport.includes('/storage/v1/object/sign/qg-evidence/')&&riderSupport.includes('"expiresIn", 300')&&riderSupport.includes('"ดูหลักฐาน"'),'Rider native support must reuse Production ticket RPC/table, private evidence bucket and signed evidence viewer');
ok(riderApi.includes('qg_rider_cash_ledger')&&riderApi.includes('/rest/v1/users?auth_user_id=eq.')&&riderApi.includes('/rest/v1/rider_profiles?user_id=eq.'),'Rider native profile and earnings must reuse Production ownership APIs');
ok(nativeRootGradle.includes('com.google.gms.google-services')&&gradle.includes('com.google.firebase:firebase-bom:35.0.0')&&gradle.includes('com.google.firebase:firebase-messaging'),'Rider native must compile with current Firebase Messaging transport');
ok(riderManifest.includes('.QueueGoRiderMessagingService')&&riderManifest.includes('com.google.firebase.MESSAGING_EVENT')&&riderManifest.includes('queuego_orders'),'Rider native manifest must register FCM service and order channel');
ok(riderPush.includes('FirebaseMessagingService')&&riderPush.includes('NotificationManager.IMPORTANCE_HIGH')&&riderPush.includes('syncRiderNativePush'),'Rider native must receive high-priority background job notifications and sync FCM tokens');
ok(riderApi.includes('/functions/v1/queuego-push')&&riderApi.includes('"subscribe-native"')&&riderApi.includes('"unsubscribe-native"'),'Rider native must reuse QueueGo Production push backend');
ok(riderSessionStore.includes('fun pushDeviceId()')&&riderSessionStore.includes('UUID.randomUUID().toString()'),'Rider push subscription must use a stable UUID device id');
ok(riderApp.includes('syncRiderNativePush(context, auth, api, pushStore)')&&riderApp.includes('disableRiderNativePush(current, api, store)'),'Rider login/logout must synchronize native push subscription lifecycle');
ok(pilotWorkflow.includes('QG_FIREBASE_GOOGLE_SERVICES_JSON_B64')&&pilotWorkflow.includes('native-android/rider/google-services.json')&&pilotWorkflow.includes('com.queuego.rider'),'native Rider CI must inject and validate Firebase config without committing it');
ok(source.includes('QueueGoRiderNavigationMap(')&&gradle.includes('implementation(project(\":rider-navigation-map\"))')&&read('rider-navigation-map/build.gradle.kts').includes('org.maplibre.gl:android-sdk-opengl:13.5.3'),'native Rider must include QueueGo MapLibre navigation host');
const riderJobDock=read('rider/src/main/java/com/queuego/rider/RiderJobDock.kt');
ok(riderApp.includes('QueueGoRiderNavigationMap(')&&
   riderApp.includes('RiderJobDock(')&&riderApp.includes('val dockKey = when {')&&
   riderApp.includes('activeSheetJob != null -> activeSheetJob.id')&&
   riderApp.includes('snapshot?.offeredJob != null -> null')&&
   riderApp.includes('snapshot?.offeredJob != null -> null\n            else -> null')&&
   riderApp.includes('initiallyExpanded = activeSheetJob == null && dockKey != null')&&
   riderApp.includes('(maxHeight * 0.62f)')&&
   riderApp.includes('navigationBottomInsetPx = coordinates.size.height + with(navigationDensity) { 68.dp.roundToPx() }')&&
   riderJobDock.includes('rememberSaveable(jobKey)')&&riderJobDock.includes('.draggable(')&&
   riderJobDock.includes('onClick = ::toggle')&&
   riderJobDock.includes('mapOverviewActive: Boolean = false')&&
   riderJobDock.includes('if (mapOverviewActive) "ไปที่" else "ภาพรวม"')&&
   riderApp.includes('mapOverviewActive = activeSheetJob != null && routeOverviewActive')&&
   riderApp.includes('routeOverviewActive = true')&&riderApp.includes('overviewSignal += 1')&&
   riderApp.includes('routeOverviewActive = false')&&riderApp.includes('recenterSignal += 1')&&
   riderJobDock.includes('Modifier.weight(1f).fillMaxWidth().clipToBounds().verticalScroll(scroll)')&&
   !riderJobDock.includes('AndroidView')&&riderApp.includes('recenterSignal'),
   'Rider job dock must expand/collapse over retained map, preserve targeted offer visibility, leave the idle Ready card without a duplicate collapse header and measure camera inset including bottom navigation');
ok(
   riderApp.includes('QgIcon("route", Modifier.size(23.dp), QgRed)') &&
   riderApp.includes('actionMessage = "ยังไม่มีเส้นทางให้ดูทั้งหมด"') &&
   riderApp.includes('.padding(top = 168.dp, end = 10.dp)') &&
   riderApp.includes('routeOverviewActive = true') &&
   riderApp.includes('overviewSignal += 1'),
   'Rider map right rail must expose Web-parity overview between recenter and the lower compass');
ok(
   riderApp.includes('private fun RiderMapWordmark(') &&
   riderApp.includes('if (!activeNavigationWork) {') &&
   riderApp.includes('RiderMapWordmark(') &&
   riderApp.includes('Color(0xFF17171B).copy(alpha = 0.46f)') &&
   riderApp.includes('QgRed.copy(alpha = 0.46f)') &&
   riderApp.includes('private fun RiderBlueprintSpeedPill(speedKph: Int?)') &&
   riderApp.includes('modifier = Modifier.size(52.dp)') &&
   riderApp.includes('shape = androidx.compose.foundation.shape.CircleShape') &&
   riderApp.includes('fontSize = 20.sp') &&
   riderApp.includes('fontSize = 7.sp'),
   'Rider idle map chrome must match current Web wordmark and circular 52dp speed control');
ok(riderApp.includes('var auth by remember { mutableStateOf<QueueGoAuth?>(null) }')&&
   riderApp.includes('var restoring by remember { mutableStateOf(true) }')&&
   riderApp.includes('val cached = auth ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)')&&
   riderApp.includes('if (auth == null) auth = cached')&&
   riderApp.includes('restoring -> LoadingScreen')&&
   riderApp.includes('auth == null -> LoginScreen')&&
   riderApp.includes('val cachedSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)')&&
   riderApp.includes('if (snapshot == null && cachedSnapshot != null) snapshot = cachedSnapshot')&&
   riderApp.includes('riderResolveMissingActiveSnapshot(')&&
   riderApp.includes('lookup?.getOrNull()')&&
   riderApp.includes('lookup?.isSuccess == true')&&
   riderApp.includes('pushStore.saveHomeSnapshot(auth.user.id, mergedSnapshot)')&&
   riderApp.includes('val savedTab = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)')&&
   riderApp.includes('if (!activeTabHydrated) return@LaunchedEffect')&&
   riderApp.includes('pushStore.saveActiveTab(auth.user.id, activeTab)'),
   'Rider must restore encrypted session, monotonic active work snapshot and persisted tab off Main without flashing login after activity recreation');
ok(riderSessionStore.includes('saveHomeSnapshot(')&&riderSessionStore.includes('loadHomeSnapshot(')&&riderSessionStore.includes('home_snapshot'),'Rider session store must persist encrypted home work state for background/process recovery');
ok(riderManifest.includes('android:launchMode="singleTask"')&&riderManifest.includes('android:alwaysRetainTaskState="true"'),'Rider launcher must reuse its existing task instead of creating a fresh work activity after app switching');
ok(!source.includes('longdo.map.key')&&!source.includes('longdo.map.apikeychecker'),'native apps must not package retired Longdo credential metadata');
const customerManifest=read('customer/src/main/AndroidManifest.xml');
ok(customerManifest.includes('queuego.map.service.base_url')&&customerManifest.includes('${queuegoMapServiceBaseUrl}')&&customerManifest.includes('ACCESS_FINE_LOCATION'),'native Customer QueueGo Map picker must declare map service config and location permission');
const customerMapPicker=read('customer/src/main/java/com/queuego/customer/CustomerLocationPicker.kt');
ok(customerMapPicker.includes('screenHeightDp.dp * 0.34f')&&customerMapPicker.includes('mapFailed')&&customerMapPicker.includes('ลองโหลดแผนที่อีกครั้ง'),'Customer native map picker must use responsive map height and expose a retry state instead of spinning forever');
const customerRide=read('customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt');
ok(customerRide.includes('QgTrackingMap(')&&customerRide.includes('.height(245.dp)'),'Customer native Ride request must show the QueueGo route map preview like Web');
const sharedMap=read('shared/src/main/java/com/queuego/shared/QueueGoMap.kt');
ok(sharedMap.includes('newLatLngBounds(bounds, 48)')&&sharedMap.includes('LatLng(14.9918756, 103.0994795)'),'Native tracking preview must frame both selected Ride stops and use the Buriram pilot viewport when empty');
ok(
  sharedMap.includes('https://tile.openstreetmap.org/{z}/{x}/{y}.png') &&
  sharedMap.includes('RasterSource(QUEUEGO_WEB_BASEMAP_SOURCE') &&
  sharedMap.includes('RasterLayer(QUEUEGO_WEB_BASEMAP_LAYER') &&
  sharedMap.includes('map.uiSettings.isLogoEnabled = false'),
  'Native QueueGo basemap presentation must stay locked to the same raster tiles as Web'
);
ok(pilotWorkflow.includes("vars.QG_MAP_SERVICE_BASE_URL || 'https://queuego-map-buriram.onrender.com'"),'pilot APKs must compile the deployed QueueGo Map endpoint when no override is configured');
const riderNavigationLocationService=read('rider/src/main/java/com/queuego/rider/RiderNavigationLocationService.kt');
const riderPuckArrow=read('rider-navigation-map/src/main/res/drawable/qg_rider_puck_arrow.xml');
ok(
  riderNavigationLocationService.includes('LocationServices.getFusedLocationProviderClient') &&
  riderNavigationLocationService.includes('NavigationMapFix(') &&
  riderMap.includes('GeoJsonSource(') &&
  riderMap.includes('CircleLayer(RIDER_BODY_LAYER_ID, RIDER_SOURCE_ID)') &&
  riderMap.includes('SymbolLayer(RIDER_ARROW_LAYER_ID, RIDER_SOURCE_ID)') &&
  riderMap.includes('PropertyFactory.circleColor(android.graphics.Color.rgb(230, 0, 45))') &&
  riderMap.includes('PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)') &&
  riderMap.includes('PropertyFactory.iconRotate(Expression.get("bearing"))') &&
  riderMap.includes('Property.ICON_ROTATION_ALIGNMENT_MAP') &&
  riderMap.includes('smoothRiderPuckHeading(') &&
  riderMap.includes('resolvedRiderPuckHeading(state.rider)') &&
  !riderMap.includes('.title("Rider")') &&
  riderPuckArrow.includes('#FFFFFFFF') &&
  riderPuckArrow.includes('M13.5,2.5 L23.5,23 L13.5,18.2 L3.5,23') &&
  riderMap.includes('SymbolLayer(STOP_SHOP_ICON_LAYER_ID, STOP_SOURCE_ID)') &&
  riderMap.includes('SymbolLayer(STOP_CUSTOMER_ICON_LAYER_ID, STOP_SOURCE_ID)') &&
  riderMap.includes('source.setGeoJson(stopFeatureCollectionJson(stops, activeId))'),
  'native Rider QueueGo Map must match the Web red directional puck/heading and preserve numbered shop/customer stop layers'
);
const riderCameraPolicy=read('rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationCameraPolicy.kt');
const riderRouteMatcher=read('rider-navigation-map/src/main/java/com/queuego/ridernavigationmap/NavigationRouteMatcher.kt');
const riderNavigationIntegrationForParity=read('rider/src/main/java/com/queuego/rider/RiderNavigationIntegration.kt');
ok(
  riderMap.includes('CAMERA_BEARING_MIN_DELTA_DEGREES = 0.7f') &&
  riderMap.includes('navigationRiderDisplayFix(state)?.point') &&
  riderMap.includes('navigationFollowCameraPadding(') &&
  riderMap.includes('.padding(') &&
  riderCameraPolicy.includes('return 0.8') &&
  riderCameraPolicy.includes('speedMetersPerSecond >= 18f -> 0.76') &&
  riderCameraPolicy.includes('remainingRouteMeters != null && remainingRouteMeters <= 60.0 -> 28f') &&
  riderRouteMatcher.includes('headingDelta <= 35f -> headingDelta * 0.08') &&
  riderRouteMatcher.includes('(it - progress - 35.0).coerceAtLeast(0.0)') &&
  !riderRouteMatcher.includes('headingDelta > 105f') &&
  !riderRouteMatcher.includes('forwardJump') &&
  riderNavigationIntegrationForParity.includes('RIDER_NAV_DISPLAY_CONFIDENCE_ENABLED = false') &&
  riderMap.includes('MotionEvent.ACTION_POINTER_DOWN') &&
  riderMap.includes('if (event.pointerCount >= 2) multiTouchGesture = true'),
  'Native Rider FOLLOW, route matching, visible GPS path and gestures must stay locked to Web navigation behavior'
);
ok(source.includes('QgBottomNav')&&source.includes('.height(58.dp)'),'native bottom navigation must keep approved compact web density');

ok(source.includes('qg_merchant_action_once'),'Merchant native order actions must stay idempotent');
ok(source.includes('pos_my_shop')&&source.includes('pos_create_bill_once')&&source.includes('pos_edit_bill')&&source.includes('pos_bill_action')&&source.includes('pos_take_payment'),'Merchant native POS must reuse production POS RPCs');
ok(source.includes('pos_cancel_bill')&&source.includes('pos_apply_discount')&&source.includes('pos_refund_bill')&&source.includes('pos_change_price'),'Merchant native POS must preserve cancel discount refund and price RPCs');
ok(source.includes('pos_create_role_invite')&&source.includes('pos_set_staff_role')&&source.includes('pos_staff?select=user_id,shop_id,display_name,staff_role,permissions,active'),'Merchant native POS must preserve Production staff roles and invite management');
ok(source.includes('pos_save_table')&&source.includes('pos_delete_table')&&source.includes('qg_table_rotate_qr'),'Merchant native POS must preserve table and QR management');
ok(source.includes('pos_owner_dashboard'),'Merchant native POS must preserve storefront reports');
ok(source.includes('PosTabs(')&&source.includes('"reports" to "ยอดขาย"')&&source.includes('"history" to "ประวัติ"')&&source.includes('"staff" to "พนักงาน"'),'Merchant native POS must preserve Production counter tables kitchen bills reports history and staff tabs');
ok(source.includes('shop_profiles?select=shop_name,public_category,metadata')&&source.includes('shopCategory = shopCategory?.trim()?.lowercase().orEmpty()'),'Merchant native POS must read the authoritative shop category before enabling Kitchen');
ok(source.includes('if (kitchenEnabled) add("kitchen" to "ครัว")')&&source.includes('canSendKitchen = kitchenEnabled && can("send_kitchen")'),'Merchant native POS must hide Kitchen and its actions for non-food merchants');
ok(source.includes('bills = openBills.filter { !kitchenEnabled || it.kitchenStatus in setOf("READY", "SERVED") }')&&source.includes('if (canClose && (!kitchenEnabled || selectedBill.kitchenStatus in setOf("READY", "SERVED")))'),'Merchant native POS must allow direct checkout without a Kitchen status for non-food merchants');
ok(source.includes('pendingPosRequestId')&&source.includes('ตรวจบิลเดิม')&&source.includes('requestId = requestId'),'Merchant native POS must preserve idempotent new-bill recovery after uncertain network results');
ok(source.includes('pos_submit_offline_draft')&&source.includes('put("request_ids", JSONArray(item.requestIds))')&&source.includes('item.copy(requestIds = if'),'Merchant offline draft submission must use one atomic server request with stable per-unit retry IDs');
ok(source.includes('offlineDraftStore.saveCatalog')&&source.includes('offlineDraftStore.loadCatalog')&&source.includes('offlineCatalogOnly = true'),'Merchant POS must restore a device-local catalog after offline relaunch without restoring bills or permissions');
ok(source.includes('NET_CAPABILITY_VALIDATED')&&source.includes('ยังไม่ได้สร้างบิลหรือรับชำระเงิน')&&source.includes('ไม่ชำระเงิน และไม่ส่งเข้าครัว'),'Merchant offline POS must label the local draft and avoid offline bill/payment/kitchen writes');
ok(source.includes('NativeRealtimeSubscription("orders", "shop_id=eq.$shopId")')&&source.includes('repeatOnLifecycle(Lifecycle.State.STARTED)')&&source.includes('delay(5_000L)'),'Merchant native POS must refresh Production orders from Realtime with polling fallback');
ok(source.includes('table-order.html#scan/')&&merchantGradle.includes('com.google.zxing:core:3.5.3'),'Merchant native POS must render Production table QR links without a WebView');
ok(source.includes('MerchantPrintBridge')&&source.includes('HttpsURLConnection')&&source.includes('Android อนุญาตเฉพาะ HTTPS Print Bridge'),'Merchant native printer must use HTTPS Print Bridge instead of unsupported Web Bluetooth or USB');
ok(source.includes('"printer" to "เครื่องพิมพ์"')&&source.includes('autoKitchen')&&source.includes('autoReceipt')&&source.includes('merchantKitchenTicket')&&source.includes('merchantReceiptTicket'),'Merchant native POS must preserve printer settings auto-print and reprint flows');

ok(source.includes('get_my_shop_orders'),'Merchant native Orders must use production order source');
ok(source.includes('ToneGenerator')&&source.includes('STREAM_NOTIFICATION'),'Merchant native must alert on new orders');
ok(source.includes('queuego_delete_or_archive_product')&&source.includes('saveProduct('),'Merchant native must support safe product archive and full product editing');
ok(source.includes('shop_support_messages?select=id,sender_user_id,body,created_at'),'Merchant native must preserve Admin support messaging');
const merchantSetup=read('merchant/src/main/java/com/queuego/merchant/MerchantShopSetupScreen.kt');
const merchantApiSource=read('merchant/src/main/java/com/queuego/merchant/MerchantApi.kt');
const merchantImageUpload=read('merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt');
const merchantManifest=read('merchant/src/main/AndroidManifest.xml');
ok(merchantSetup.includes('QgLocationPickerMap(')&&merchantSetup.includes('ใช้ตำแหน่ง GPS ปัจจุบัน')&&merchantSetup.includes('บันทึกข้อมูลร้าน'),'Merchant native shop setup must preserve QueueGo Map picker, GPS and save flow');
ok(merchantApiSource.includes('http.reversePlaceAddress(latitude, longitude)')&&merchantSetup.includes('resolveAddress: suspend (Double, Double) -> String?')&&merchantSetup.includes('addressLockedByUser'),'Merchant reverse labels must use shared QueueGo Places authority without overwriting typed addresses');
ok(merchantSetup.includes('mobile_accessories')&&merchantSetup.includes('automotive_motorcycle')&&merchantSetup.includes('home_decor'),'Merchant native Shopping setup must preserve web subcategories');
ok(merchantImageUpload.includes('/storage/v1/object/merchant-media/')&&merchantImageUpload.includes('12 * 1024 * 1024')&&merchantImageUpload.includes('3 * 1024 * 1024'),'Merchant native shop images must use production merchant-media limits');
ok(merchantManifest.includes('ACCESS_FINE_LOCATION')&&merchantManifest.includes('queuego.map.service.base_url')&&merchantManifest.includes('${queuegoMapServiceBaseUrl}'),'Merchant native setup must declare location and QueueGo Map service config');
ok(source.includes('saveShopSetup(')&&source.includes('shop_profiles?id=eq.')&&source.includes('users?id=eq.'),'Merchant native setup must persist owned shop and user profile');
const merchantHours=read('merchant/src/main/java/com/queuego/merchant/MerchantHoursScreen.kt');
ok(source.includes('merchant_save_hours')&&source.includes('shop_business_hours?select=weekday,opens_at,closes_at,is_closed')&&source.includes('shop_special_hours?select=day,is_closed,opens_at,closes_at'),'Merchant native hours must reuse Production hours tables and RPC');
ok(merchantHours.includes('อาทิตย์')&&merchantHours.includes('เสาร์')&&merchantHours.includes('วันหยุดหรือเวลาพิเศษ')&&merchantHours.includes('บันทึกเวลาทำการ'),'Merchant native hours must preserve seven-day and special-day web flow');
ok(source.includes('MerchantProfileSectionTitle("ข้อมูลร้าน")')&&source.includes('MerchantProfileSectionTitle("จัดการร้าน")')&&source.includes('แก้ไขข้อมูลร้าน')&&source.includes('เวลาทำการและวันหยุด'),'Merchant native account must preserve compact grouped web structure');
const merchantProfileStart=source.indexOf('private fun MerchantProfileScreen(');
const merchantProfileEnd=source.indexOf('\n@Composable\nprivate fun MerchantProfileSectionTitle',merchantProfileStart);
const merchantProfile=source.slice(merchantProfileStart,merchantProfileEnd);
ok(
  merchantProfileStart>=0 && merchantProfileEnd>merchantProfileStart &&
  !merchantProfile.includes('MerchantProfileSectionTitle("ความพร้อมร้าน")') &&
  !merchantProfile.includes('title = "ศูนย์การแจ้งเตือน"') &&
  merchantProfile.includes('MerchantProfileSectionTitle("ความเป็นส่วนตัวและบัญชี")') &&
  merchantProfile.includes('title = "นโยบายความเป็นส่วนตัว"') &&
  merchantProfile.includes('title = "ลบบัญชีถาวร"') &&
  merchantProfile.includes('title = "ออกจากระบบ"') &&
  merchantProfile.includes('if (showDeletion)') &&
  merchantProfile.indexOf('MerchantProfileSectionTitle("ความเป็นส่วนตัวและบัญชี")') <
    merchantProfile.indexOf('MerchantProfileSectionTitle("ช่วยเหลือ")'),
  'Merchant Profile must match Web hierarchy: compact privacy/account before Help without readiness or duplicate notification-center rows'
);
const merchantRevenue=read('merchant/src/main/java/com/queuego/merchant/MerchantRevenueScreen.kt');
const queueGoCreditCard=read('shared/src/main/java/com/queuego/shared/QueueGoCreditCard.kt');
const riderEarningsCredit=read('rider/src/main/java/com/queuego/rider/RiderEarningsScreen.kt');
ok(queueGoCreditCard.includes('"ใช้ได้ ${money(data.optLong("available_minor"))}"')&&!queueGoCreditCard.includes('"กำลังโหลด..."')&&!queueGoCreditCard.includes('เงินจริง')&&!queueGoCreditCard.includes('โปรโมชั่น')&&!queueGoCreditCard.includes('· กันไว้'),'Merchant/Rider native credit surface must show one unified usable balance without loading chrome');
ok(queueGoCreditCard.includes('"topup" to "เพิ่มเครดิต"')&&queueGoCreditCard.includes('"promo" to "เพิ่มเครดิต"'),'Native credit history must not expose paid-vs-admin-credit balance buckets');
ok(
  merchantRevenue.includes('QueueGoCreditCard(') &&
  merchantRevenue.includes('kind = "merchant"') &&
  riderEarningsCredit.includes('QueueGoCreditInline(auth.user.id, auth.session.accessToken, "rider")'),
  'Merchant must retain the full shared credit panel while Rider Earnings uses the Web inline credit surface'
);
ok(
  queueGoCreditCard.includes('fun QueueGoCreditInline(') &&
  queueGoCreditCard.includes('"queuego_credit_summary"') &&
  /Text\(\s*"เครดิต"/.test(queueGoCreditCard) &&
  queueGoCreditCard.includes('Text(if (busy) "กำลังเช็ก" else "เช็กยอด"') &&
  riderEarningsCredit.includes('QueueGoCreditInline(') &&
  !riderEarningsCredit.includes('QueueGoCreditCard('),
  'Rider Earnings credit must match Web mountInline: balance plus explicit recheck without expanded ledger/top-up history'
);

ok(source.includes('merchant_revenue_days')&&!source.includes('report_shop_gp'),'Merchant revenue must read reports without the retired GP collection RPC');
ok(source.includes('gpAmount = r.optDouble("gp_credit_deducted", 0.0)')&&source.includes('legacyGpDue = r.optDouble("gp_due", 0.0)'),'Merchant revenue must separate credit-deducted GP from legacy pre-credit due');
ok(merchantRevenue.includes('GP ที่หักจากเครดิต')&&merchantRevenue.includes('GP เดิมก่อนระบบเครดิต'),'Merchant native revenue copy must match the unified credit collection model');
ok(merchantRevenue.includes('7 วันที่ผ่านมา')&&merchantRevenue.includes('30 วันที่ผ่านมา')&&merchantRevenue.includes('ดาวน์โหลดรายงาน 30 วัน (CSV)'),'Merchant native revenue must preserve web period and CSV controls');
ok(!merchantRevenue.includes('แนบสลิปและแจ้งโอน')&&merchantRevenue.includes('QueueGoCreditCard')&&!fs.existsSync(path.join(root,'merchant/src/main/java/com/queuego/merchant/MerchantGpSlipUpload.kt')),'Merchant GP collection must use credit only; retired upload implementation must be absent');
ok(merchantRevenue.includes('วิเคราะห์ร้านค้า')&&merchantRevenue.includes('RevenueRangeRow')&&merchantRevenue.includes('RevenueHeroCard')&&merchantRevenue.includes('RevenueMetricRow')&&merchantRevenue.includes('shopName = shop?.name.orEmpty()')===false,'Merchant revenue screen must use the compact analytics hierarchy without embedding shell navigation state');
ok(source.includes('shopName = shop?.name.orEmpty()')&&source.includes('onSettings = { screen = "profile" }'),'Merchant shell must pass current shop context and settings navigation into analytics');
ok(queueGoCreditCard.includes('if (kind == "merchant")')&&queueGoCreditCard.includes('MerchantCreditAction')&&queueGoCreditCard.includes('ระบบเติมเครดิตยังไม่เปิดใช้งาน')&&queueGoCreditCard.includes('รายการล่าสุด'),'Merchant unified credit component must provide the approved compact Merchant analytics card, status, actions and history');
ok(!read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt').includes('MerchantRevenueEntry(')&&!read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt').includes('รายได้ทั้งหมด'),'Merchant native dashboard must not duplicate the Reports entry with a separate revenue card');
const merchantPromotion=read('merchant/src/main/java/com/queuego/merchant/MerchantPromotionScreen.kt');
ok(source.includes('promotions?select=id,category,status,max_budget,period_days,payment_status,metadata,created_at')&&source.includes('"promotions"'),'Merchant native promotions must use Production promotions table and route');
ok(merchantPromotion.includes('ทั้งหมด')&&merchantPromotion.includes('กำลังใช้งาน')&&merchantPromotion.includes('รออนุมัติ')&&merchantPromotion.includes('สร้างโปรโมชั่นใหม่'),'Merchant native promotions must preserve web filters and create flow');
ok(source.includes('MerchantMenuTile("โปรโมชั่น"')&&source.includes('MerchantMenuTile("รายงาน"'),'Merchant native dashboard must expose promotions and reports');
ok(!read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt').includes('ร้านยังตั้งค่าไม่ครบ')&&!read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt').includes('กรอกข้อมูลร้าน รูป พิกัด และสินค้าให้ครบก่อนส่งตรวจ'),'Merchant native Home must not show a duplicate readiness card; readiness enforcement remains in data and settings flows');
ok(source.includes('QgNavItem("home", "หน้าหลัก", "home")')&&source.includes('QgNavItem("orders", "คำสั่งซื้อ", "orders")')&&source.includes('QgNavItem("promotions", "โปรโมชั่น", "tag")')&&source.includes('QgNavItem("profile", "บัญชี", "user")'),'Merchant native bottom navigation must match Production web labels and routes');
const merchantHome=read('merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
ok(
  merchantHome.includes('Text(\n                "Queue"')&&
  merchantHome.includes('Text(\n                "Go"')&&
  merchantHome.includes('badge = unreadCount')&&
  !merchantHome.includes('QgIcon("brand_q"'),
  'Merchant native header must match the Customer QueueGo wordmark plus notification action'
);
const merchantNotifications=read('merchant/src/main/java/com/queuego/merchant/MerchantNotificationsScreen.kt');
ok(source.includes('notifications?select=id,title,message,type,reference_id,is_read,created_at')&&source.includes('markNotificationsRead('),'Merchant native notification center must use owned Production notifications table');
ok(merchantNotifications.includes('ทั้งหมด')&&merchantNotifications.includes('ออเดอร์')&&merchantNotifications.includes('ระบบ')&&merchantNotifications.includes('โปรโมชัน'),'Merchant native notification center must preserve web filter groups');
ok(source.includes('unreadCount = notifications.count { !it.isRead }')&&source.includes('QgIconButton(')&&source.includes('"notifications" -> MerchantNotificationsScreen'),'Merchant native header must expose unread notification badge and route');
const merchantProductModel=read('merchant/src/main/java/com/queuego/merchant/MerchantProductModel.kt');
const merchantProductsScreen=read('merchant/src/main/java/com/queuego/merchant/MerchantProductsScreen.kt');
ok(source.includes('products?select=id,name,description,price,delivery_price,image,category,stock,variants,available,pos_available,delivery_available,pos_price,delivery_restriction,inventory_stock_tracking,grocery_master_id,grocery_stock_tracking,metadata'),'Merchant native products must load Production stock/category/variant/channel and inventory-tracking fields');
ok(source.includes('merchantDeliveryPriceFromStore')&&source.includes('1.0 + rate / 100.0'),'Merchant native Delivery price must add GP to the storefront price like Production web');
ok(source.includes('resolution=merge-duplicates')&&source.includes('on_conflict=id'),'Merchant native new-product save must use stable idempotent upsert');
ok(merchantProductModel.includes('เมนูแนะนำ / เมนูขายดี')&&merchantProductModel.includes('เพิ่มเติม / ท็อปปิ้ง')&&merchantProductModel.includes('ของสดพร้อมปรุง'),'Merchant native product categories must preserve Production food and market catalogs');
ok(merchantProductModel.includes('queuego.menu-options.v1')&&merchantProductModel.includes('ธรรมดา')&&merchantProductModel.includes('พิเศษ')&&merchantProductModel.includes('ท็อปปิ้ง'),'Merchant native food editor must preserve structured menu options');
ok(merchantProductsScreen.includes('จำนวนในสต็อก *')&&merchantProductsScreen.includes('ขายหน้าร้าน')&&merchantProductsScreen.includes('ขาย Delivery')&&merchantProductsScreen.includes('รูปจะเก็บในคลังรูปภาพของร้าน'),'Merchant native product editor must preserve stock, channels and image flow');
ok(source.includes('"product" ->')&&!source.includes('private fun ProductsScreen('),'Merchant native must remove the legacy inline product implementation');
const merchantCatalog=read('merchant/src/main/java/com/queuego/merchant/MerchantCatalogScreen.kt');
ok(source.includes('"catalog" ->')&&merchantCatalog.includes('เพิ่มสินค้าจากคลังสินค้า')&&merchantCatalog.includes('ค้นหาเมนูในร้าน...')&&merchantCatalog.includes('ดูและแก้ไข'),'Merchant native product add flow must preserve Production catalog page');
const merchantMedia=read('merchant/src/main/java/com/queuego/merchant/MerchantMediaScreen.kt');
const merchantModules=read('merchant/src/main/java/com/queuego/merchant/MerchantModulesScreen.kt');
ok(merchantMedia.includes('/storage/v1/object/list/merchant-media')&&merchantMedia.includes('auth.session.authUserId + "/"')&&merchantMedia.includes('เพิ่มรูปจากเครื่อง'),'Merchant native media library must list and upload only the signed-in shop prefix');
ok(source.includes('shop_modules?select=id,shop_user_id,module_key,status,started_at,expires_at,created_at,updated_at')&&source.includes('shop_modules?on_conflict=shop_user_id,module_key'),'Merchant native settings must reuse Production shop_modules');
ok(source.includes('"storefront" to "หน้าร้าน"')&&source.includes('"products" to "หน้าสินค้า"')&&source.includes('"orders" to "ระบบรับออเดอร์"')&&source.includes('"promote" to "ระบบโปรโมต"'),'Merchant native settings must preserve Production module catalog');
ok(source.includes('MerchantMenuTile("คลังรูปภาพ"')&&source.includes('MerchantMenuTile("ติดต่อแอดมิน"')&&!source.includes('MerchantMenuTile("ข้อมูลร้านค้า"')&&!source.includes('MerchantMenuTile("ตั้งค่า"')&&!source.includes('MerchantMenuTile("การแจ้งเตือน"'),'Merchant native Home must keep media/support while deduping Account, Settings and Notifications');
ok(source.includes('title = "ตั้งค่าร้านค้า"')&&source.includes('onClick = onModules'),'Merchant native Account must retain Settings access after Home dedupe');
ok(source.includes('pauseShop(')&&source.includes('30 นาที')&&source.includes('1 ชั่วโมง')&&source.includes('1 วัน')&&source.includes('resume_at'),'Merchant native dashboard must preserve Production timed pause controls');
const merchantPosApi=read('merchant/src/main/java/com/queuego/merchant/MerchantPosApi.kt');
const merchantPosScreen=read('merchant/src/main/java/com/queuego/merchant/MerchantPosScreen.kt');
const merchantPosOptions=read('merchant/src/main/java/com/queuego/merchant/MerchantPosOptions.kt');
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
const specialTracking=fs.readFileSync(path.join(root,'customer/src/main/java/com/queuego/customer/QueueGoSpecialTracking.kt'),'utf8');
ok(specialTracking.includes('api.trip(auth, trip.id)')&&specialTracking.includes('api.order(auth, order.id)')&&specialTracking.includes('repeatOnLifecycle(Lifecycle.State.STARTED)')&&specialTracking.includes('catch (cancelled: CancellationException) { throw cancelled }'),'Market/Laundry detail must read authoritative owner/id snapshots and suspend polling in background');
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
ok(groceryMerchantCache.includes('readGroceryCategories(ownerId: String, shopId: String)')&&groceryMerchantCache.includes('writeGroceryCategories(ownerId: String, shopId: String')&&groceryMerchantCache.includes('groceryCategoryShopId')&&groceryMerchantCache.includes('optJSONObject("shop")?.optString("id") != shopId')&&groceryMerchantApp.includes('contentCache.readGroceryCategories(owner, activeShop.id)')&&groceryMerchantApp.includes('contentCache.writeGroceryCategories(owner, activeShop.id, categories)'),'Merchant Grocery category references must be account/shop scoped, bounded, cached locally and server refreshed');
const marketCatalogMerchant=read('merchant/src/main/java/com/queuego/merchant/MerchantMarketCatalogScreen.kt');
ok(groceryMerchantCache.includes('readMarketCatalogCategories(ownerId: String, shopId: String)')&&groceryMerchantCache.includes('writeMarketCatalogCategories(ownerId: String, shopId: String')&&marketCatalogMerchant.includes('readMarketCatalogCategories(auth.user.id, shop.id)')&&marketCatalogMerchant.includes('writeMarketCatalogCategories(auth.user.id, shop.id, fresh)')&&!marketCatalogMerchant.includes('val categoryRead = async')&&marketCatalogMerchant.includes('api.marketCatalogCategories(auth)'),'Merchant Market catalog categories must be locally cached per owner/shop and refreshed once per visible screen lifecycle, not every search/page query');


const customerLaundryCatalogApi=read('customer/src/main/java/com/queuego/customer/CustomerLaundryApi.kt');
ok(customerLaundryCatalogApi.includes('"queuego_laundry_customer_catalog"')&&customerLaundryCatalogApi.includes('catalog(auth).servicesForCustomerHub(hubId)')&&!customerLaundryCatalogApi.includes('laundry_hubs?select=')&&!customerLaundryCatalogApi.includes('laundry_shop_settings?select=')&&!customerLaundryCatalogApi.includes('laundry_services?select='),'Native Customer Laundry discovery must use the same eligible catalog authority as Production Web, without raw-table fallback');


const customerRideArrivalSource=read('customer/src/main/java/com/queuego/customer/CustomerRideScreen.kt');
ok(customerRideArrivalSource.includes('text("arrived_pickup_at")')&&customerRideArrivalSource.includes('text("arrived_dropoff_at")')&&customerRideArrivalSource.includes('Text(passengerRideStatus(current))'),'Native Customer Ride must render arrival timestamps from the existing server state');

console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Customer Merchant Rider native Android source'}));
