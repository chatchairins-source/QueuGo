const fs=require('node:fs'),assert=require('node:assert/strict');
const app=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/QueueGoCustomerApp.kt','utf8');
const ui=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerExpressScreen.kt','utf8');
const cart=fs.readFileSync('native-android/customer/src/main/java/com/queuego/customer/CustomerCartStore.kt','utf8');
const design=fs.readFileSync('native-android/shared/src/main/java/com/queuego/shared/QueueGoDesign.kt','utf8');
const blueprint=JSON.parse(fs.readFileSync('data/native-web-parity-blueprint.json','utf8'));
let checks=0;const ok=(v,m)=>{assert(v,m);checks++;};

const homeRoutes=blueprint.customer.home.categories.map(x=>x.route);
ok(!homeRoutes.includes('express'),'Pinned Web main does not expose Express on Customer Home');
ok(!app.match(/private val homeCategories = listOf\(([\s\S]*?)\n\)/)?.[1].includes('"express" to "ส่งด่วน"'),'Native Home must not add an Android-only Express tile');
ok(app.includes('"express" -> screen = "express"')&&app.includes('"express" -> CustomerExpressScreen('),'Express capability and internal route must remain available off Home');
ok(design.includes('"express" -> {'),'Shared QueueGo icon family must include Express');
for(const token of ['queuego-express-quote','queuego_request_express','queuego_express_state','queuego_express_config','qg_customer_cancel_order']) ok(ui.includes(token),'Customer Express missing '+token);
for(const token of ['senderName','senderPhone','receiverName','receiverPhone','itemType','weightText','itemSize','vehicleType','instructions']) ok(ui.includes(token),'Customer Express form missing '+token);
ok(ui.includes('.put("pickup", point(pickup))')&&ui.includes('.put("destination", point(destination))'),'Express quote must send both real map points');
ok(ui.includes('CustomerCheckoutRecovery<PendingExpress, CustomerExpressOrder>'),'Express request must reuse durable checkout recovery');
ok(cart.includes('fun expressJournal()')&&cart.includes('"pending_express"'),'Express pending request must survive process/network loss');
ok(ui.includes('"searching_rider"')&&ui.includes('"rider_assigned"')&&ui.includes('"picked_up"')&&ui.includes('"in_progress"')&&ui.includes('"completed"'),'Express Customer state UI must cover the Phase 1 lifecycle');
ok(ui.includes('QgTrackingMap(')&&ui.includes('current.riderLat')&&ui.includes('current.riderLng'),'Express Customer must track assigned Rider from authoritative state');
ok(ui.includes('ค่าส่งเงินสด')&&!/credit card|e-payment|promptpay/i.test(ui),'Express Customer surface must stay cash-first without e-payment controls');
const refreshStart=ui.indexOf('    suspend fun refresh() {');
const refreshEnd=ui.indexOf('    LaunchedEffect(',refreshStart);
ok(refreshStart>=0&&refreshEnd>refreshStart,'Native Express refresh lifecycle must be present');
const refresh=ui.slice(refreshStart,refreshEnd);
ok(refresh.indexOf('api.config(auth)')>=0&&refresh.indexOf('api.config(auth)')<refresh.indexOf('api.state(auth)'),
  'Native must read Express rollout capability before querying state');
ok(refresh.includes('catch (e: QueueGoHttpException)')&&refresh.includes('e.statusCode != 404')&&refresh.includes('loaded = true'),
  'Missing Express RPC must show unavailable, but other HTTP errors must still propagate');
ok(refresh.includes('enabled = config.optBoolean("enabled")')&&refresh.includes('val recent = api.state(auth)'),
  'Disabled but installed Express must still allow retrieving existing orders');

console.log(JSON.stringify({checks,failures:0,scope:'Native Customer Express capability preserved off Home while Home presentation follows pinned Web main; no feature enablement or Production mutation'}));
