const fs=require('fs'),assert=require('assert'),path=require('path');
const root=path.resolve(__dirname,'..','native-android');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

for(const p of [
  'settings.gradle.kts',
  'build.gradle.kts',
  'shared/build.gradle.kts',
  'shared/src/main/java/com/queuego/shared/QueueGoAccountDeletion.kt',
  'customer/build.gradle.kts',
  'customer/src/main/AndroidManifest.xml',
  'merchant/build.gradle.kts',
  'rider/build.gradle.kts',
  'rider/src/main/AndroidManifest.xml',
  'rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt',
  'rider/src/main/java/com/queuego/rider/QueueGoApi.kt',
  'rider/src/main/java/com/queuego/rider/RiderPush.kt',
  'rider/src/main/res/drawable/qg_notification.xml',
  'rider/src/main/java/com/queuego/rider/RiderHistoryCard.kt',
  'rider/src/main/java/com/queuego/rider/RiderChat.kt',
  'rider/src/main/java/com/queuego/rider/RiderMessagesScreen.kt',
  'rider/src/main/java/com/queuego/rider/RiderEarningsScreen.kt',
  'rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt',
  'rider/src/main/java/com/queuego/rider/RiderSupportScreen.kt',
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
  'merchant/src/main/java/com/queuego/merchant/MerchantApi.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantShopSetupScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantHoursScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantRevenueScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantGpSlipUpload.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantPromotionScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantNotificationsScreen.kt',
  'merchant/src/main/java/com/queuego/merchant/MerchantProductModel.kt',
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

const all=[];
(function walk(dir){
  for(const name of fs.readdirSync(dir)){
    const p=path.join(dir,name),st=fs.statSync(p);
    if(st.isDirectory())walk(p); else if(/\.(kt|kts|xml)$/.test(name))all.push(fs.readFileSync(p,'utf8'));
  }
})(root);
const source=all.join('\n');

ok(!/android\.webkit\.WebView|<WebView\b|loadUrl\(/.test(source),'native apps must not use WebView UI');
ok(source.includes('expectedRole = "customer"')&&source.includes('expectedRole = "shop"'),'Customer/Merchant must validate real QueueGo roles');
const customerMain=read('customer/src/main/java/com/queuego/customer/MainActivity.kt');
const merchantMain=read('merchant/src/main/java/com/queuego/merchant/MainActivity.kt');
ok(customerMain.includes('QueueGoCustomerApp()')&&!customerMain.includes('QueueGoRoleNativeApp'),'Customer must launch full native app, not role shell');
ok(merchantMain.includes('QueueGoMerchantApp()')&&!merchantMain.includes('QueueGoRoleNativeApp'),'Merchant must launch full native app, not role shell');
ok(source.includes('queuego_place_cash_order'),'Customer native checkout must use production cash-order RPC');
const customerCartCheckout=read('customer/src/main/java/com/queuego/customer/CustomerCartCheckout.kt');
ok(source.includes('"cart" -> CustomerCartScreen')&&source.includes('"checkout" -> CustomerCheckoutScreen')&&customerCartCheckout.includes('"ตะกร้าสินค้า"')&&customerCartCheckout.includes('"ไปชำระเงิน"')&&customerCartCheckout.includes('"ยืนยันคำสั่งซื้อ"')&&customerCartCheckout.includes('height(115.dp)')&&customerCartCheckout.includes('QgLongdoLocationPickerMap')&&customerCartCheckout.includes('"ยืนยันสั่งซื้อ"'),'Customer native must preserve separate Production cart and checkout screens with checkout Longdo map and fixed action dock');
ok(!/private fun CartScreen\(/.test(source),'legacy combined Customer cart-checkout implementation must stay removed');
ok(customerCartCheckout.includes('checkoutPending: Boolean')&&customerCartCheckout.includes('enabled = !checkoutPending')&&customerCartCheckout.includes('pendingCheckout: PendingCustomerCheckout?')&&customerCartCheckout.includes('p_expected_delivery_fee'),'Customer pending checkout must lock cart edits and render the persisted Production quote');
ok(source.includes('loadActiveOrder(auth)')&&source.includes('&status=in.(pending,accepted,searching_rider,rider_assigned,preparing,ready,assigned,picked_up,in_progress)'),'Customer checkout must preserve Production active-order guard before creating another order');
ok(source.includes('if (checkoutPending) {')&&source.includes('กรุณาตรวจผลคำสั่งซื้อเดิมก่อนแก้ตะกร้า')&&source.includes('if (!checkoutPending) cart = cart.mapNotNull')&&source.includes('if (!checkoutPending) cart = cart.filterNot'),'Customer pending checkout must block every native cart mutation path');
ok(source.includes('market_public_catalog_v2')&&source.includes('queuego_place_market_order'),'Customer native Market must use production market RPCs');
ok(source.includes('queuego_add_market_order_shops'),'Customer native Market must preserve add-shop flow');
ok(source.includes('queuego_place_laundry_order_v2'),'Customer native Laundry must use production laundry order RPC');
ok(source.includes('notifications?select=id,title,message,type,reference_id,is_read,created_at'),'Customer native notifications must use production notifications table');
ok(source.includes('qg_support_tickets?select=id,category,details,status,order_id,admin_note,created_at')&&source.includes('qg_create_ticket'),'Customer native support must use production ticket RPC');
ok(source.includes('qg_customer_order_context')&&source.includes('CustomerTrackingRider'),'Customer native tracking must use production order context');
const customerTrackingScreen=read('customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
ok(source.includes('"order" -> CustomerOrderTrackingScreen')&&customerTrackingScreen.includes('"สถานะล่าสุด"')&&customerTrackingScreen.includes('"ส่งไปที่"')&&customerTrackingScreen.includes('CustomerTrackingProgress(stage)')&&customerTrackingScreen.includes('"Rider ของคุณ"')&&customerTrackingScreen.includes('"รายการสินค้า"')&&customerTrackingScreen.includes('"ยอดรวม"'),'Customer native tracking must preserve Production order-detail hierarchy');
ok(customerTrackingScreen.includes('size(46.dp)')&&customerTrackingScreen.includes('RoundedCornerShape(20.dp)')&&customerTrackingScreen.includes('fontSize = 22.sp')&&customerTrackingScreen.includes('"แจ้งปัญหา"'),'Customer native tracking must preserve Production rider, hero, total and support proportions');
const customerTrackingMap=read('customer/src/main/java/com/queuego/customer/CustomerTrackingMap.kt');
ok(customerTrackingMap.includes('RoundedCornerShape(18.dp)')&&customerTrackingMap.includes('.padding(10.dp)')&&customerTrackingMap.includes('Color(0xFF20B46D)')&&customerTrackingMap.includes('fontSize = 9.sp'),'Customer native tracking map card must preserve Production radius, padding and live-location indicator');
ok(!/private fun OrderTrackingScreen\(/.test(source),'legacy generic Customer tracking screen must stay removed');
ok(source.includes('qg_customer_cancel_order'),'Customer native cancellation must use production RPC');
ok(source.includes('qg_chat_moderation_state')&&source.includes('qg_accept_ugc_terms')&&source.includes('qg_block_chat_counterpart')&&source.includes('qg_report_chat'),'Customer native chat must preserve UGC safety RPCs');
ok(source.includes('order_chat_messages?select=id,sender_id,message,created_at'),'Customer native chat must use production chat table');
ok(source.includes('qg_customer_save_review')&&source.includes('reviews?select=id,rating,food_rating,rider_rating,comment'),'Customer native completed orders must preserve review RPC');
ok(source.includes('\"search\" -> CustomerSearchScreen')&&source.includes('CustomerBottomNavigation(screen, cart.sumOf')&&source.includes('Triple(\"search\", \"ค้นหา\", R.drawable.qg_nav_search)')&&source.includes('onSelect(key)'),'Customer native must preserve global search navigation');
ok(source.includes('GuestCustomerShell(')&&source.includes('loadShopsPublic()')&&source.includes('loadProductsPublic(')&&source.includes('เข้าสู่ระบบเพื่อสั่งซื้อ'),'Customer native must preserve Production guest Home/catalog/cart/Market browsing before authentication');
ok(source.includes('CustomerCartStore(context, "guest")')&&source.includes('destinationAfterLogin')&&source.includes('onCheckout = { onLogin("checkout") }'),'Customer native guest cart must persist locally and return to checkout after authentication');
ok(source.includes('CustomerPromotionScreen(')&&source.includes('qg_public_promotions'),'Customer native guest routing must preserve public promotion browsing');
ok(source.includes('auth: NativeAuth?')&&source.includes('onRequireLogin'),'Customer public shop/Market renderers must share native implementations without fake authenticated actors');
ok(source.includes('ServiceCategoryScreen')&&source.includes('"category" -> ServiceCategoryScreen'),'Food Drink Grocery Shopping must use dedicated native category pages');

ok(source.includes('CustomerCategoryButton')&&source.includes('.size(54.dp)')&&source.includes('Modifier.size(92.dp)')&&source.includes('top = 13.dp, bottom = 10.dp')&&source.includes('fontSize = 18.sp'),'Customer native Home must preserve approved web location, category, heading and shop-card proportions');
ok(source.includes('CustomerTopAction(')&&source.includes('R.drawable.qg_nav_bag')&&source.includes('R.drawable.qg_top_bell')&&source.includes('R.drawable.qg_top_user'),'Customer top actions must remain exact icon-first web vectors');
ok(source.includes('MerchantMenuTile')&&source.includes('height(128.dp)')&&source.includes('"จัดการร้าน"'),'Merchant native dashboard must preserve store cover KPI and menu-grid blueprint');
ok(source.includes('RiderBlueprintTopBar')&&source.includes('RoundedCornerShape(21.dp)')&&source.includes('"รับงาน · " + secondsLeft + " วิ"'),'Rider native must preserve approved topbar job-card and accept-countdown blueprint');
ok(source.includes('val marketVehicleReady = current.profile?.let')&&source.includes('it.vehicleStatus == "active" && !it.vehicleVerifiedAt.isNullOrBlank()'),'Rider Home must derive Market/Grocery eligibility from the same verified-vehicle state enforced by Production dispatch');
ok(source.includes('"ออนไลน์ · พร้อมรับงานอาหาร"')&&source.includes('"งานตลาด/ของชำรอตรวจสอบยานพาหนะ"'),'Rider Home must not imply Market/Grocery eligibility while vehicle verification is still pending');
const riderApi=read('rider/src/main/java/com/queuego/rider/QueueGoApi.kt');
const riderApp=read('rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
const riderPush=read('rider/src/main/java/com/queuego/rider/RiderPush.kt');
const riderSessionStore=read('rider/src/main/java/com/queuego/rider/SessionStore.kt');
const riderManifest=read('rider/src/main/AndroidManifest.xml');
const nativeRootGradle=read('build.gradle.kts');
const pilotWorkflow=fs.readFileSync(path.resolve(root,'..','.github','workflows','build-native-rider-pilot.yml'),'utf8');
ok(riderApi.includes('qg_get_my_rider_offer')&&!riderApi.includes('rpcArray("get_rider_delivery_pool"'),'Rider native must show only the server-selected live offer, never a shared delivery pool');
ok(riderApi.includes('qg_rider_decline_offer')&&riderApi.includes('qg_rider_action_once'),'Rider native offer accept/decline must stay on guarded Production RPCs');
ok(riderApp.includes('ระบบกำลังหางานและจัดให้คุณอัตโนมัติ')&&riderApp.includes('ไม่มีการแย่งงานกับ Rider คนอื่น'),'Rider waiting state must explain automatic sequential assignment');
ok(riderApp.includes('onExpired =')&&riderApp.includes('ข้อเสนอนี้หมดเวลา ระบบกำลังส่งงานให้ Rider คนถัดไป'),'Rider expired offers must immediately leave the card and continue sequential dispatch');
ok(riderApp.includes('ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)')&&riderApp.includes('มีงานใหม่ที่ระบบจัดให้ กรุณาตอบรับภายใน 30 วินาที'),'Rider foreground must alert once for a newly issued server offer');
ok(riderApi.includes('if (!online)')&&riderApi.includes('qg_get_my_rider_offer')&&riderApi.includes('qg_rider_decline_offer'),'Rider going offline must release any live offer immediately');
ok(riderApp.includes('api.setOnline(current, false, null, null)')&&riderApp.includes('api.revoke(current.session)'),'Rider logout must take the Rider offline before session revocation');
ok(riderApp.includes('รับงานสำเร็จ · กำลังเปิดนำทางไปร้าน')&&riderApp.includes('updated?.activeJob?.let { accepted ->')&&riderApp.includes('openNavigation(context, accepted)'),'Rider native must auto-open navigation to the shop after accepting a server offer');
ok(riderApp.includes('รับสินค้าแล้ว · กำลังเปิดนำทางไปลูกค้า')&&riderApp.includes('completedMode == "pickup"')&&riderApp.includes('openNavigation(context, pickedUp)'),'Rider native must auto-open navigation to the customer after pickup proof');
ok(riderApi.includes('queuego_laundry_rider_invites')&&riderApi.includes('queuego_laundry_rider_active_job')&&riderApi.includes('queuego_laundry_rider_pool'),'Rider native must load Production laundry invites, active job and eligible pool');
ok(riderApi.includes('queuego_set_laundry_rider_mode')&&riderApi.includes('queuego_laundry_rider_invite_action')&&riderApi.includes('queuego_claim_laundry_job')&&riderApi.includes('queuego_laundry_rider_action'),'Rider native laundry mutations must reuse Production RPCs');
ok(riderApp.includes('RiderLaundryActiveCard(')&&riderApp.includes('RiderLaundryPoolCard(')&&riderApp.includes('RiderLaundryModeCard('),'Rider native must expose laundry active, pool, mode and invite UI');
ok(riderApp.includes('รับผ้าแล้ว · กำลังเปิดนำทางไปปลายทาง')&&riderApp.includes('openLaundryNavigation(context, it)'),'Rider native laundry collect must continue navigation to destination');
const riderMap=read('rider/src/main/java/com/queuego/rider/LongdoRiderMap.kt');
const nativeRealtimeSubscriptions=read('shared/src/main/java/com/queuego/shared/NativeRealtimeSubscription.kt');
const riderDispatchLaundryMigration=fs.readFileSync(path.resolve(root,'..','supabase','migrations','20261009044500_rider_dispatch_excludes_active_laundry.sql'),'utf8');
ok(riderMap.includes('laundryJob: RiderLaundryJob?')&&riderMap.includes('laundryJob.fromLatitude')&&riderMap.includes('laundryJob.toLatitude'),'Rider native Longdo map must show laundry pickup and destination pins');
ok(nativeRealtimeSubscriptions.includes('"laundry_rider_jobs"')&&nativeRealtimeSubscriptions.includes('"laundry_rider_invites"')&&nativeRealtimeSubscriptions.includes('"laundry_rider_preferences"'),'Rider native must refresh laundry state from owned Realtime changes');
ok(riderDispatchLaundryMigration.includes("from public.laundry_rider_jobs lj")&&riderDispatchLaundryMigration.includes("lj.status in('assigned','accepted','arrived','collected')"),'automatic Rider dispatch must not offer normal delivery work during an active Laundry leg');
const riderMessages=read('rider/src/main/java/com/queuego/rider/RiderMessagesScreen.kt');
const riderEarnings=read('rider/src/main/java/com/queuego/rider/RiderEarningsScreen.kt');
const riderProfile=read('rider/src/main/java/com/queuego/rider/RiderProfileScreen.kt');
ok(riderApp.includes('RiderBottomNavigation(')&&riderApp.includes('Triple("home", "หน้าแรก", "home")')&&riderApp.includes('Triple("chat", "ข้อความ", "support")')&&riderApp.includes('Triple("earn", "รายได้", "chart")')&&riderApp.includes('Triple("profile", "โปรไฟล์", "user")'),'Rider native must preserve Production four-tab bottom navigation');
ok(riderApp.includes('BackHandler(enabled = activeTab != "home"')&&riderApp.includes('activeTab = "home"'),'Rider native Android back must return non-home tabs to Home');
ok(riderApp.includes('chatJob = null')&&riderApp.includes('activeTab = "home"')&&riderApp.includes('มีงานใหม่ที่ระบบจัดให้ กรุณาตอบรับภายใน 30 วินาที'),'new selected Rider offer must immediately surface on Home from any tab');
ok(riderMessages.includes('order_chat_messages?select=order_id,sender_id,message,created_at')&&riderMessages.includes('deliveries?select=order_id,delivered_at')&&riderMessages.includes('30 * 60_000L'),'Rider native message inbox must use Production chat and 30-minute completed-order window');
ok(riderEarnings.includes('api.periodSummary(auth, days)')&&riderEarnings.includes('api.cashLedger(auth, from.toString(), to.toString())')&&riderEarnings.includes('"วันนี้"')&&riderEarnings.includes('"7 วัน"')&&riderEarnings.includes('"30 วัน"'),'Rider native earnings must preserve Production periods and ledger source');
ok(riderProfile.includes('api.updateAccount(auth, name, phone)')&&riderProfile.includes('api.setLaundryMode(auth, enabled)')&&riderProfile.includes('api.testNativePush(auth)')&&riderProfile.includes('QgAccountDeletionSection'),'Rider native profile must preserve account, Laundry, push and deletion controls');
const riderSupport=read('rider/src/main/java/com/queuego/rider/RiderSupportScreen.kt');
ok(riderProfile.includes('RiderSupportScreen(')&&riderProfile.includes('แจ้งปัญหา / ติดตามเรื่อง'),'Rider native profile must expose Production support flow');
ok(riderSupport.includes('qg_support_tickets?select=id,category,details,status,order_id,created_at,admin_note,evidence_path')&&riderSupport.includes('"qg_create_ticket"')&&riderSupport.includes('/storage/v1/object/qg-evidence/')&&riderSupport.includes('/storage/v1/object/sign/qg-evidence/')&&riderSupport.includes('"expiresIn", 300')&&riderSupport.includes('"ดูหลักฐาน"'),'Rider native support must reuse Production ticket RPC/table, private evidence bucket and signed evidence viewer');
ok(riderApi.includes('qg_rider_cash_ledger')&&riderApi.includes('/rest/v1/users?auth_user_id=eq.')&&riderApi.includes('/rest/v1/rider_profiles?user_id=eq.'),'Rider native profile and earnings must reuse Production ownership APIs');
ok(nativeRootGradle.includes('com.google.gms.google-services')&&gradle.includes('com.google.firebase:firebase-bom:35.0.0')&&gradle.includes('com.google.firebase:firebase-messaging'),'Rider native must compile with current Firebase Messaging transport');
ok(riderManifest.includes('.QueueGoRiderMessagingService')&&riderManifest.includes('com.google.firebase.MESSAGING_EVENT')&&riderManifest.includes('queuego_orders'),'Rider native manifest must register FCM service and order channel');
ok(riderPush.includes('FirebaseMessagingService')&&riderPush.includes('NotificationManager.IMPORTANCE_HIGH')&&riderPush.includes('syncRiderNativePush'),'Rider native must receive high-priority background job notifications and sync FCM tokens');
ok(riderApi.includes('/functions/v1/queuego-push')&&riderApi.includes('"subscribe-native"')&&riderApi.includes('"unsubscribe-native"'),'Rider native must reuse QueueGo Production push backend');
ok(riderSessionStore.includes('fun pushDeviceId()')&&riderSessionStore.includes('UUID.randomUUID().toString()'),'Rider push subscription must use a stable UUID device id');
ok(riderApp.includes('syncRiderNativePush(context, auth, api, pushStore)')&&riderApp.includes('disableRiderNativePush(current, api, store)'),'Rider login/logout must synchronize native push subscription lifecycle');
ok(pilotWorkflow.includes('QG_FIREBASE_GOOGLE_SERVICES_JSON_B64')&&pilotWorkflow.includes('native-android/rider/google-services.json')&&pilotWorkflow.includes('com.queuego.rider'),'native Rider CI must inject and validate Firebase config without committing it');
ok(source.includes('RiderLongdoMap')&&source.includes('MapGLSurfaceView')&&source.includes('LongdoLayer'),'native Rider must include Longdo map SDK host');
ok(riderApp.includes('RiderLongdoMap(')&&riderApp.includes('heightIn(max = maxHeight * 0.62f)') && !riderApp.includes('fillMaxHeight(0.62f)')&&riderApp.includes('recenterSignal'),'Rider native must keep map-first home with bottom dock and recenter control');
ok(source.includes('longdo.map.key'),'native Rider manifest must provide Longdo map key');
const customerManifest=read('customer/src/main/AndroidManifest.xml');
ok(customerManifest.includes('longdo.map.key')&&customerManifest.includes('ACCESS_FINE_LOCATION'),'native Customer Longdo picker must declare map key and location permission');
ok(source.includes('deviceLocation')&&source.includes('setLocation(MapLocation')&&source.includes('clearPin')&&source.includes('pushPin'),'native Rider map must use Android GPS and show rider/job pins');
ok(source.includes('QgBottomNav')&&source.includes('.height(58.dp)'),'native bottom navigation must keep approved compact web density');

ok(source.includes('qg_merchant_action_once'),'Merchant native order actions must stay idempotent');
ok(source.includes('pos_my_shop')&&source.includes('pos_create_bill_once')&&source.includes('pos_edit_bill')&&source.includes('pos_bill_action')&&source.includes('pos_take_payment'),'Merchant native POS must reuse production POS RPCs');
ok(source.includes('pos_cancel_bill')&&source.includes('pos_apply_discount')&&source.includes('pos_refund_bill')&&source.includes('pos_change_price'),'Merchant native POS must preserve cancel discount refund and price RPCs');
ok(source.includes('pos_create_role_invite')&&source.includes('pos_set_staff_role')&&source.includes('pos_staff?select=user_id,shop_id,display_name,staff_role,permissions,active'),'Merchant native POS must preserve Production staff roles and invite management');
ok(source.includes('pos_save_table')&&source.includes('pos_delete_table')&&source.includes('qg_table_rotate_qr'),'Merchant native POS must preserve table and QR management');
ok(source.includes('pos_owner_dashboard')&&source.includes('pos_delivery_kitchen_action')&&source.includes('pos_enable_delivery'),'Merchant native POS must preserve reports and Delivery integration');
ok(source.includes('PosTabs(')&&source.includes('"reports" to "ยอดขาย"')&&source.includes('"history" to "ประวัติ"')&&source.includes('"delivery" to "Delivery"')&&source.includes('"staff" to "พนักงาน"'),'Merchant native POS must preserve Production counter tables kitchen bills reports history Delivery and staff tabs');
ok(source.includes('pendingPosRequestId')&&source.includes('ตรวจบิลเดิม')&&source.includes('requestId = requestId'),'Merchant native POS must preserve idempotent new-bill recovery after uncertain network results');
ok(source.includes('NativeRealtimeSubscription("orders", "shop_id=eq.$shopId")')&&source.includes('repeatOnLifecycle(Lifecycle.State.STARTED)')&&source.includes('delay(5_000L)'),'Merchant native POS must refresh Production orders from Realtime with polling fallback');
ok(source.includes('table-order.html#scan/')&&merchantGradle.includes('com.google.zxing:core:3.5.3'),'Merchant native POS must render Production table QR links without a WebView');
ok(source.includes('MerchantPrintBridge')&&source.includes('HttpsURLConnection')&&source.includes('Android อนุญาตเฉพาะ HTTPS Print Bridge'),'Merchant native printer must use HTTPS Print Bridge instead of unsupported Web Bluetooth or USB');
ok(source.includes('"printer" to "เครื่องพิมพ์"')&&source.includes('autoKitchen')&&source.includes('autoReceipt')&&source.includes('merchantKitchenTicket')&&source.includes('merchantReceiptTicket'),'Merchant native POS must preserve printer settings auto-print and reprint flows');

ok(source.includes('get_my_shop_orders'),'Merchant native Orders must use production order source');
ok(source.includes('ToneGenerator')&&source.includes('STREAM_NOTIFICATION'),'Merchant native must alert on new orders');
ok(source.includes('queuego_delete_or_archive_product')&&source.includes('saveProduct('),'Merchant native must support safe product archive and full product editing');
ok(source.includes('shop_support_messages?select=id,sender_user_id,body,created_at'),'Merchant native must preserve Admin support messaging');
const merchantSetup=read('merchant/src/main/java/com/queuego/merchant/MerchantShopSetupScreen.kt');
const merchantImageUpload=read('merchant/src/main/java/com/queuego/merchant/MerchantImageUpload.kt');
const merchantManifest=read('merchant/src/main/AndroidManifest.xml');
ok(merchantSetup.includes('QgLongdoLocationPickerMap(')&&merchantSetup.includes('ใช้ตำแหน่ง GPS ปัจจุบัน')&&merchantSetup.includes('บันทึกข้อมูลร้าน'),'Merchant native shop setup must preserve Longdo picker, GPS and save flow');
ok(merchantSetup.includes('mobile_accessories')&&merchantSetup.includes('automotive_motorcycle')&&merchantSetup.includes('home_decor'),'Merchant native Shopping setup must preserve web subcategories');
ok(merchantImageUpload.includes('/storage/v1/object/merchant-media/')&&merchantImageUpload.includes('12 * 1024 * 1024')&&merchantImageUpload.includes('3 * 1024 * 1024'),'Merchant native shop images must use production merchant-media limits');
ok(merchantManifest.includes('ACCESS_FINE_LOCATION')&&merchantManifest.includes('longdo.map.key'),'Merchant native setup must declare location and Longdo key');
ok(source.includes('saveShopSetup(')&&source.includes('shop_profiles?id=eq.')&&source.includes('users?id=eq.'),'Merchant native setup must persist owned shop and user profile');
const merchantHours=read('merchant/src/main/java/com/queuego/merchant/MerchantHoursScreen.kt');
ok(source.includes('merchant_save_hours')&&source.includes('shop_business_hours?select=weekday,opens_at,closes_at,is_closed')&&source.includes('shop_special_hours?select=day,is_closed,opens_at,closes_at'),'Merchant native hours must reuse Production hours tables and RPC');
ok(merchantHours.includes('อาทิตย์')&&merchantHours.includes('เสาร์')&&merchantHours.includes('วันหยุดหรือเวลาพิเศษ')&&merchantHours.includes('บันทึกเวลาทำการ'),'Merchant native hours must preserve seven-day and special-day web flow');
ok(source.includes('MerchantProfileSectionTitle("ข้อมูลร้าน")')&&source.includes('MerchantProfileSectionTitle("จัดการร้าน")')&&source.includes('แก้ไขข้อมูลร้าน')&&source.includes('เวลาทำการและวันหยุด'),'Merchant native account must preserve compact grouped web structure');
const merchantRevenue=read('merchant/src/main/java/com/queuego/merchant/MerchantRevenueScreen.kt');
const merchantGpSlip=read('merchant/src/main/java/com/queuego/merchant/MerchantGpSlipUpload.kt');
ok(source.includes('merchant_revenue_days')&&source.includes('report_shop_gp'),'Merchant native revenue must reuse Production revenue and GP RPCs');
ok(merchantRevenue.includes('7 วันที่ผ่านมา')&&merchantRevenue.includes('30 วันที่ผ่านมา')&&merchantRevenue.includes('ดาวน์โหลดรายงาน 30 วัน (CSV)'),'Merchant native revenue must preserve web period and CSV controls');
ok(merchantRevenue.includes('แนบสลิปและแจ้งโอน')&&merchantGpSlip.includes('/storage/v1/object/gp-slips/')&&merchantGpSlip.includes('5 * 1024 * 1024'),'Merchant native GP slip must preserve Production bucket and 5 MB limit');
ok(source.includes('MerchantRevenueEntry(')&&source.includes('รายได้ทั้งหมด')&&source.includes('เลือกวัน ดูยอดขาย เงินสด และ GP ย้อนหลัง'),'Merchant native dashboard must preserve revenue entry blueprint');
const merchantPromotion=read('merchant/src/main/java/com/queuego/merchant/MerchantPromotionScreen.kt');
ok(source.includes('promotions?select=id,category,status,max_budget,period_days,payment_status,metadata,created_at')&&source.includes('"promotions"'),'Merchant native promotions must use Production promotions table and route');
ok(merchantPromotion.includes('ทั้งหมด')&&merchantPromotion.includes('กำลังใช้งาน')&&merchantPromotion.includes('รออนุมัติ')&&merchantPromotion.includes('สร้างโปรโมชั่นใหม่'),'Merchant native promotions must preserve web filters and create flow');
ok(source.includes('MerchantMenuTile("โปรโมชั่น"')&&source.includes('MerchantMenuTile("รายงาน"'),'Merchant native dashboard must expose promotions and reports');
ok(source.includes('QgNavItem("home", "หน้าหลัก", "home")')&&source.includes('QgNavItem("orders", "คำสั่งซื้อ", "orders")')&&source.includes('QgNavItem("promotions", "โปรโมชั่น", "tag")')&&source.includes('QgNavItem("profile", "บัญชี", "user")'),'Merchant native bottom navigation must match Production web labels and routes');
ok(source.includes('QgIcon("brand_q"')&&source.includes('badge = unreadCount')&&!source.includes('QueueGoBrand(suffix = "Merchant")'),'Merchant native header must match Production Q-mark plus notification action');
const merchantNotifications=read('merchant/src/main/java/com/queuego/merchant/MerchantNotificationsScreen.kt');
ok(source.includes('notifications?select=id,title,message,type,reference_id,is_read,created_at')&&source.includes('markNotificationsRead('),'Merchant native notification center must use owned Production notifications table');
ok(merchantNotifications.includes('ทั้งหมด')&&merchantNotifications.includes('ออเดอร์')&&merchantNotifications.includes('ระบบ')&&merchantNotifications.includes('โปรโมชัน'),'Merchant native notification center must preserve web filter groups');
ok(source.includes('unreadCount = notifications.count { !it.isRead }')&&source.includes('QgIconButton(')&&source.includes('"notifications" -> MerchantNotificationsScreen'),'Merchant native header must expose unread notification badge and route');
const merchantProductModel=read('merchant/src/main/java/com/queuego/merchant/MerchantProductModel.kt');
const merchantProductsScreen=read('merchant/src/main/java/com/queuego/merchant/MerchantProductsScreen.kt');
ok(source.includes('products?select=id,name,description,price,delivery_price,image,category,stock,variants,available,pos_available,delivery_available,pos_price,delivery_restriction,metadata'),'Merchant native products must load Production stock/category/variant/channel fields');
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
ok(source.includes('MerchantMenuTile("คลังรูปภาพ"')&&source.includes('MerchantMenuTile("การแจ้งเตือน"')&&source.includes('MerchantMenuTile("ติดต่อแอดมิน"'),'Merchant native dashboard must expose Production media notification and support routes');
ok(source.includes('pauseShop(')&&source.includes('30 นาที')&&source.includes('1 ชั่วโมง')&&source.includes('1 วัน')&&source.includes('resume_at'),'Merchant native dashboard must preserve Production timed pause controls');
const merchantMarketStock=read('merchant/src/main/java/com/queuego/merchant/MerchantMarketStockScreen.kt');
ok(source.includes('rpc/market_save_product')===false && source.includes('"market_save_product"')&&source.includes('"market_adjust_stock"'),'Merchant native market stock must call Production market RPCs through shared RPC client');
ok(source.includes('market_products?select=product_id,unit,pack_size,item_weight_kg,stock_quantity,min_stock,cost_price')&&source.includes('market_stock_movements?select=type,quantity,note,created_at'),'Merchant native market stock must load authoritative market inventory and movements');
ok(merchantMarketStock.includes('สินค้าและสต๊อกตลาด')&&merchantMarketStock.includes('รับเข้า / ปรับยอด')&&merchantMarketStock.includes('สต๊อกเริ่มต้น')&&merchantMarketStock.includes('แจ้งเตือนใกล้หมด'),'Merchant native market stock must preserve Production stock workflow');
ok(source.includes('"market-stock" ->')&&source.includes('MerchantMenuTile("ตลาดและสต๊อก"'),'Merchant native market shops must expose dedicated Market stock route');
const sharedDesign=read('shared/src/main/java/com/queuego/shared/QueueGoDesign.kt');
ok(sharedDesign.includes('"tag" ->')&&sharedDesign.includes('"chart" ->')&&sharedDesign.includes('"clock" ->')&&sharedDesign.includes('"support" ->'),'shared native icon set must preserve Merchant web vectors');
ok(source.includes('queuego_laundry_merchant_state')&&source.includes('queuego_laundry_shop_action_v2'),'Merchant native Laundry must use production laundry state/actions');
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
ok(!/cash-confirm|ยืนยันชำระเงินให้ร้าน|ยืนยันเก็บเงินจากลูกค้า/i.test(source),'native Rider must not reintroduce manual cash confirmation screens');
ok(!/service_role|sb_secret_/i.test(source),'no privileged Supabase secret');
console.log(JSON.stringify({checks,failures:0,scope:'QueueGo Customer Merchant Rider native Android source'}));
