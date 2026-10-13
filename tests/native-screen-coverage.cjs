const fs=require('fs'),assert=require('assert');
const matrix=JSON.parse(fs.readFileSync('native-android/qa/native-screen-coverage-20261010.json','utf8'));
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(matrix.physical_visual_parity==='OPEN','source coverage matrix must never certify rendered/physical visual parity');
ok(/not rendered or physical visual parity certification/i.test(matrix.scope),'matrix scope must remain source-only');
ok(/API\/store\/model\/validation-only files are intentionally excluded/i.test(matrix.audit_note||''),'coverage matrix must preserve the reviewed non-UI exclusion boundary');

const minimum={customer:18,merchant:17,rider:14};
const requiredSurfaces={
  customer:['tracking_map_component'],
  merchant:['app_shell'],
  rider:['chat_image_component']
};
for(const [role,surfaces] of Object.entries(matrix.roles)){
  ok(Array.isArray(surfaces)&&surfaces.length>=minimum[role],role+' source coverage surface count regressed');
  const seen=new Set();
  for(const entry of surfaces){
    const [surface,path,kind='screen',requiredToken]=entry;
    ok(typeof surface==='string'&&surface.length>0,role+' surface name missing');
    ok(!seen.has(surface),role+' duplicate surface: '+surface); seen.add(surface);
    ok(kind==='screen'||kind==='helper',role+' unsupported coverage kind: '+surface);
    ok(typeof path==='string'&&path.startsWith('native-android/'+role+'/src/main/java/'),role+' surface path outside Native role: '+surface);
    ok(fs.existsSync(path),role+' source surface missing: '+surface+' -> '+path);
    const source=fs.readFileSync(path,'utf8');
    ok(source.length>300,role+' source surface is unexpectedly empty/thin: '+surface);
    if(kind==='screen') ok(source.includes('@Composable'),role+' UI surface lost Compose implementation: '+surface);
    else {
      ok(typeof requiredToken==='string'&&requiredToken.length>0,role+' helper coverage requires a source token: '+surface);
      ok(source.includes(requiredToken),role+' helper coverage token missing: '+surface+' -> '+requiredToken);
    }
  }
  for(const required of requiredSurfaces[role]||[]) ok(seen.has(required),role+' required UI surface missing from coverage matrix: '+required);
}

const riderHistory=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderEarningsScreen.kt','utf8');
ok(riderHistory.includes('api.cashLedger(')&&riderHistory.includes('rows.take(30).forEachIndexed')&&riderHistory.includes('รายการงานล่าสุด'),'Rider history must retain the Production ledger reader and rendered job cards');

const riderLogin=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/RiderLoginScreen.kt','utf8');
ok(riderLogin.includes('onRegister: () -> Unit'),'Rider registration entry must remain on the Native login surface');
ok(!/ACTION_VIEW|startActivity\s*\(/.test(riderLogin),'Rider registration must not regress to external-browser signup');
ok(/if \(!wide\) \{[\s\S]*?verticalArrangement = Arrangement\.Center[\s\S]*?card\(\)/.test(riderLogin),
   'Rider mobile login group must remain vertically centered while retaining scroll/IME handling');
ok(riderLogin.includes('if (!wide) {')&&riderLogin.includes('Text("Queue"')&&riderLogin.includes('Text("Go"')&&
   riderLogin.includes('Text("Rider"')&&riderLogin.includes('Text("Q", color = Color.White'),
   'Rider mobile login group must retain the QueueGo Rider logo/brand requested for Native auth');

const riderHome=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt','utf8');
const riderApi=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt','utf8');
const riderStore=fs.readFileSync('native-android/rider/src/main/java/com/queuego/rider/SessionStore.kt','utf8');
ok(riderStore.includes('setOf("home", "earn", "chat", "profile")')&&
   riderStore.includes('"earnings" -> "earn"')&&riderHome.includes('activeTab == "earn"'),
   'Rider tab persistence must use the actual earnings tab key and migrate the old key without returning Home');
for(const field of ['customerSubtotal','customerDeliveryFee','customerServiceFee']){
  ok(riderStore.includes('.put("'+field+'", job.'+field+')')&&
     riderStore.includes(field+' = o.optNullableDouble("'+field+'")'),
     'Rider cached job must preserve '+field+' across process recreation');
}
ok(riderHome.includes('private fun RiderBlueprintBrandPill()')&&riderHome.includes('horizontalArrangement = Arrangement.SpaceBetween')&&
   riderHome.includes('Text("Queue"')&&riderHome.includes('Text("Go", color = QgRed')&&riderHome.includes('Text("Rider", color = Color(0xFF8D8589)'),
   'Rider Native home topbar must retain the Production QueueGo Rider brand pill beside online status');
ok(riderHome.includes('shop = offerShopDisplay')&&riderHome.includes('logoUrl = shop?.shopLogo')&&
   riderHome.includes('value = shop?.shopName ?: job.pickupAddress ?: "ร้านค้า"')&&
   riderHome.includes('trailing = "รับสินค้า"')&&riderHome.includes('trailing = "ส่งสินค้า"'),
   'Rider offer card must retain Production shop identity and pickup/delivery row semantics');
ok(riderApi.includes('suspend fun offerShopDisplay(')&&riderApi.includes('/rest/v1/orders?select=shop_id&id=eq.')&&
   riderApi.includes('/rest/v1/shop_profiles?select=shop_name,public_logo&id=eq.'),
   'Rider offer identity must come from the authenticated Production order/shop profile path, not mock data');
ok(riderHome.includes('setPackage(GOOGLE_MAPS_PACKAGE)')&&riderHome.includes('"google.navigation:q="')&&
   !riderHome.includes('if (navigationState.active)')&&!riderHome.includes('https://www.google.com/maps/dir/'),
   'Rider navigation must hand off exclusively to the installed Google Maps app without the retired in-app route badge or web fallback');

console.log(JSON.stringify({checks,failures:0,scope:'Native Customer/Merchant/Rider source-surface coverage; visual parity remains OPEN'}));
