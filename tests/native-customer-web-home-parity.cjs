'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');

const native=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt','utf8');
const blueprint=JSON.parse(fs.readFileSync('data/native-web-parity-blueprint.json','utf8'));

assert.equal(blueprint.source_web_ref,'main');
assert.match(blueprint.source_web_sha,/^[0-9a-f]{40}$/);
const webHome=blueprint.customer.home.categories;
assert.deepEqual(webHome.map(x=>x.label),['อาหาร','เครื่องดื่ม','ร้านขายของชำ','ฝากซัก','ตลาดสด','บริการเรียกรถ','ช้อปปิ้ง']);
const nearbyCategories=blueprint.customer.home.nearby_shop_categories;
assert.deepEqual(nearbyCategories,['food','cafe']);
const tile=blueprint.customer.home.category_tile;
assert.deepEqual(tile,{
  tile_width_css_px:58,tile_width_native_dp:58,
  gap_css_px:9,gap_native_dp:9,
  circle_css_px:48,circle_native_dp:48,
  icon_css_px:22,icon_native_dp:22,
  label_font_css_px:9.5,label_font_native_sp:9.5,
  label_nowrap:true
});
const shopCard=blueprint.customer.home.shop_card;
assert.deepEqual(shopCard,{
  radius_css_px:16,radius_native_dp:16,
  list_gap_css_px:7,list_gap_native_dp:7,
  padding_css_px:8,padding_native_dp:8,
  image_css_px:78,image_native_dp:78,
  compact_image_css_px:72,compact_image_native_dp:72,
  image_radius_css_px:13,image_radius_native_dp:13,
  content_gap_css_px:9,content_gap_native_dp:9,
  meta_gap_css_px:5,meta_gap_native_dp:5,
  meta_font_css_px:10.5,meta_font_native_sp:10.5,
  status_margin_top_css_px:5,status_margin_top_native_dp:5,
  status_padding_horizontal_css_px:6,status_padding_horizontal_native_dp:6,
  status_padding_vertical_css_px:3,status_padding_vertical_native_dp:3,
  status_font_css_px:9.5,status_font_native_sp:9.5
});

const block=native.match(/private val homeCategories = listOf\(([\s\S]*?)\n\)/);
assert.ok(block,'Native Customer Home must declare an explicit Web-parity category list');
const nativePairs=[...block[1].matchAll(/"([^"]+)"\s+to\s+"([^"]+)"/g)].map(x=>[x[1],x[2]]);
assert.deepEqual(nativePairs.map(x=>x[0]),webHome.map(x=>x.route));
assert.deepEqual(nativePairs.map(x=>x[1]),webHome.map(x=>x.label));
assert.ok(native.includes('homeCategories.forEach { (key, label) ->'),'Customer Home must render the Web-parity list directly');
assert.ok(native.includes('private val homeNearbyShopCategories = setOf("food", "cafe")'),'Native nearby Home authority must be an explicit Web allowlist');
assert.ok(native.includes('shop.category.lowercase() in homeNearbyShopCategories'),'Native Home nearby shops must use the Web allowlist');
assert.ok(!native.includes('key != "grocery" && key != "shopping"'),'Native Home must not approximate Web nearby authority with an exclusion list');
const categorySource=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerServiceCategory.kt','utf8');
assert.ok(categorySource.includes('"food" -> it.category == "food"')&&categorySource.includes('"cafe" -> it.category == "cafe"')&&categorySource.includes('"grocery" -> it.category == "grocery"'),'Food/Cafe/Grocery category rows must use exact Web public_category filters');
assert.ok(!categorySource.includes('setOf("food", "restaurant")')&&!categorySource.includes('setOf("cafe", "drink", "beverage")')&&!categorySource.includes('setOf("grocery", "convenience")'),'Customer categories must not admit Native-only legacy aliases');
assert.ok(!block[1].includes('"express"'),'Express must not create an Android-only Home category tile');
assert.ok(native.includes('"express" -> CustomerExpressScreen('),'Express capability must remain available; this change is Home presentation only');
assert.ok(native.includes('"express" -> screen = "express"'),'Express routing must remain intact for existing entry points');
const categoryFn=native.slice(native.indexOf('private fun CustomerCategoryButton('),native.indexOf('@Composable\nprivate fun CustomerBlueprintShopCard'));
assert.ok(categoryFn.includes('.width(' + tile.tile_width_native_dp + '.dp)'),'Native category tile width must match Web Home');
assert.ok(!categoryFn.includes('horizontalArrangement = Arrangement.spacedBy('),'Category button owns width only; row gap remains in Customer Home');
assert.ok(native.includes('horizontalArrangement = Arrangement.spacedBy(' + tile.gap_native_dp + '.dp)'),'Native Home category gap must match Web Home');
assert.ok(categoryFn.includes('.size(' + tile.circle_native_dp + '.dp)'),'Native category circle must match Web Home');
assert.ok(categoryFn.includes('QgIcon(icon, Modifier.size(' + tile.icon_native_dp + '.dp), QgRed)'),'Native category icon must match Web Home');
assert.ok(categoryFn.includes('fontSize = ' + tile.label_font_native_sp + '.sp'),'Native category label font must match Web Home');
assert.ok(categoryFn.includes('maxLines = 1')&&categoryFn.includes('softWrap = false'),'Native category label must stay on one line like Web nowrap');

const shopCardFn=native.slice(native.indexOf('private fun CustomerBlueprintShopCard('),native.indexOf('internal fun customerDistanceKm'));
assert.ok(shopCardFn.includes('val radius = RoundedCornerShape(' + shopCard.radius_native_dp + '.dp)'),'Native Home shop card radius must match Web');
assert.ok(shopCardFn.includes('.padding(bottom = ' + shopCard.list_gap_native_dp + '.dp)'),'Native Home shop list gap must match Web');
assert.ok(shopCardFn.includes('.padding(' + shopCard.padding_native_dp + '.dp)'),'Native Home shop card padding must match Web');
assert.ok(shopCardFn.includes('if (compact) Modifier.size(' + shopCard.compact_image_native_dp + '.dp) else Modifier.size(' + shopCard.image_native_dp + '.dp)'),'Native Home shop image size must match Web phone/desktop geometry');
assert.ok(shopCardFn.includes('cornerRadius = ' + shopCard.image_radius_native_dp + '.dp'),'Native Home shop image radius must match Web');
assert.ok(shopCardFn.includes('Spacer(Modifier.width(' + shopCard.content_gap_native_dp + '.dp))'),'Native Home shop image/copy gap must match Web');
assert.ok(shopCardFn.includes('Arrangement.spacedBy(' + shopCard.meta_gap_native_dp + '.dp)'),'Native Home shop metadata gap must match Web');
assert.equal((shopCardFn.match(/fontSize = 10\.5\.sp/g)||[]).length,2,'Native Home shop metadata font must match Web');
assert.ok(shopCardFn.includes('.padding(horizontal = ' + shopCard.status_padding_horizontal_native_dp + '.dp, vertical = ' + shopCard.status_padding_vertical_native_dp + '.dp)'),'Native Home shop status pill padding must match Web');
assert.ok(shopCardFn.includes('fontSize = ' + shopCard.status_font_native_sp + '.sp'),'Native Home shop status font must match Web');

const guestSource=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerGuestShell.kt','utf8');

const guestTopBar=blueprint.customer.guest_top_bar_geometry;
assert.deepEqual(guestTopBar,{
  home_horizontal_padding_css_px:16,home_horizontal_padding_native_dp:16,
  home_vertical_padding_css_px:10,home_vertical_padding_native_dp:10,
  compact_horizontal_padding_css_px:12,compact_horizontal_padding_native_dp:12,
  compact_vertical_padding_css_px:6,compact_vertical_padding_native_dp:6,
  brand_letter_spacing_css_px:-0.4,brand_letter_spacing_native_sp:-0.4,
  login_label:'เข้าสู่ระบบ',login_color:'red',login_font_css_px:13,login_font_native_sp:13,
  login_font_weight:700,login_background:'none'
});
const guestHeader=guestSource.slice(guestSource.indexOf('private fun GuestCustomerTopBar('));
assert.ok(guestHeader.includes('home: Boolean'),'Guest top bar must know whether Home density applies');
assert.ok(guestHeader.includes('horizontal = if (home) 16.dp else 12.dp')&&guestHeader.includes('vertical = if (home) 10.dp else 6.dp'),'Guest top bar density must match Web Home and compact pages');
assert.equal((guestHeader.match(/letterSpacing = \(-0\.4\)\.sp/g)||[]).length,2,'Guest QueueGo wordmark must match Web tracking');
assert.ok(guestHeader.includes('\"เข้าสู่ระบบ\"')&&guestHeader.includes('color = QgRed')&&guestHeader.includes('fontSize = 13.sp'),'Guest login action must be plain red Web label');
assert.ok(!guestHeader.includes('background(Color(0xFFFFF0F1)'),'Guest login label must not render a Native-only pink button');

const guestDestinations=blueprint.customer.guest_login_destinations;
assert.deepEqual(guestDestinations,{
  ride:'home',express:'home',orders:'home',laundry:'home',
  market_submit:'home',default_protected:'home',checkout:'checkout'
});
assert.ok(guestSource.includes('internal fun customerGuestPostLoginDestination(route: String): String'),'Guest protected actions must use one post-login destination authority');
assert.ok(guestSource.includes('return if (root == "checkout") "checkout" else "home"'),'Only checkout may preserve its post-login destination like Web');
assert.ok(guestSource.includes('"ride" -> onLogin(customerGuestPostLoginDestination("ride"))'),'Guest Ride must return to Home after login like Web');
assert.ok(!guestSource.includes('"ride" -> onLogin("ride")'),'Guest Ride must not preserve an Android-only post-login Ride destination');
assert.ok(guestSource.includes('"laundry" -> onLogin(customerGuestPostLoginDestination("laundry"))'),'Guest Laundry must return Home after login like Web');
assert.ok(guestSource.includes('"orders" -> onLogin(customerGuestPostLoginDestination("orders"))'),'Guest Orders bottom nav must return Home after login like Web');
assert.ok(guestSource.includes('onCheckout = { onLogin(customerGuestPostLoginDestination("checkout")) }'),'Guest checkout alone must preserve checkout after login like Web');
assert.ok(guestSource.includes('route in setOf("orders", "laundry", "checkout", "ride", "express")'),'Guest banner Ride/Express must follow the Web protected-route auth policy');
assert.ok(guestSource.includes('else -> onLogin(customerGuestPostLoginDestination(route))'),'Unknown protected hash routes must fail closed to login then Home like Web');
assert.ok(!guestSource.includes('onLogin("market-cart")'),'Market submit must not preserve an Android-only market-cart destination after login');
assert.ok(guestSource.includes('onRequireLogin = { onLogin(customerGuestPostLoginDestination("market-submit")) }'),'Guest Market submit must authenticate then return Home like Web');

const laundryShell=blueprint.customer.laundry_shell;
assert.deepEqual(laundryShell,{global_top_bar:true,bottom_navigation:true,bottom_navigation_selected:'home',home_density:false});
assert.ok(!native.includes('if (screen != "laundry") CustomerBottomNavigation'),'Authenticated Laundry must keep the global Web bottom navigation');
assert.ok(!native.includes('if (screen != "laundry") CustomerTopBar'),'Authenticated Laundry must keep the global Web QueueGo top bar');

const navItems=blueprint.customer.bottom_navigation;
assert.deepEqual(navItems.map(x=>x.label),['หน้าหลัก','ค้นหา','ตะกร้า','ออเดอร์']);
const navSource=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerBottomNavigation.kt','utf8');
assert.ok(navSource.includes('"home", "category", "shopping", "shop", "market", "laundry", "location" -> "home"'),'Laundry bottom navigation must select Home like Web');
assert.ok(navSource.includes('val home = screen == "home"'),'Laundry must keep default compact navigation density rather than Home density');

const navTriples=[...navSource.matchAll(/Triple\("([^"]+)",\s*"([^"]+)",\s*R\.drawable\.[^)]+\)/g)].map(x=>[x[1],x[2]]);
assert.deepEqual(navTriples.map(x=>x[0]),navItems.map(x=>x.route));
assert.deepEqual(navTriples.map(x=>x[1]),navItems.map(x=>x.label));
const navGeo=blueprint.customer.bottom_navigation_geometry;
assert.ok(navSource.includes('padding(horizontal = '+navGeo.outer_horizontal_padding_native_dp+'.dp, vertical = if (home) '+navGeo.home_outer_vertical_padding_native_dp+'.dp else '+navGeo.default_outer_vertical_padding_native_dp+'.dp)'));
assert.ok(navSource.includes('padding(vertical = if (home) '+navGeo.home_item_vertical_padding_native_dp+'.dp else '+navGeo.default_item_vertical_padding_native_dp+'.dp)'));
assert.ok(navSource.includes('Modifier.size(if (home) '+navGeo.home_icon_box_native_dp+'.dp else '+navGeo.default_icon_box_native_dp+'.dp)'));
assert.ok(navSource.includes('Modifier.size('+navGeo.icon_native_dp+'.dp)'));
assert.ok(navSource.includes('fontSize = if (home) '+navGeo.home_label_font_native_sp+'.sp else '+navGeo.default_label_font_native_sp+'.sp'));
assert.equal(navGeo.fixed_row_height,false);
assert.equal(navGeo.explicit_label_line_height,false);
assert.ok(!/Row\(Modifier\.fillMaxWidth\(\)\.height\(if \(home\)/.test(navSource),'Native bottom nav must not impose Android-only fixed row height');
assert.ok(!/Text\(label,[^\n]*lineHeight/.test(navSource),'Native bottom nav label must not impose Android-only line height');

console.log(JSON.stringify({checks:55,failures:0,scope:'Customer Android Home, protected Guest login policy and authenticated Laundry global chrome match pinned Web main'}));
