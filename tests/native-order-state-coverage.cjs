const fs=require('fs'),assert=require('assert');

const read=p=>fs.readFileSync(p,'utf8');
const matrix=JSON.parse(read('native-android/qa/native-order-state-coverage-20261010.json'));
const customerApi=read('native-android/customer/src/main/java/com/queuego/customer/CustomerApi.kt');
const customerTracking=read('native-android/customer/src/main/java/com/queuego/customer/CustomerOrderTrackingScreen.kt');
const customerPolicy=read('native-android/customer/src/main/java/com/queuego/customer/CustomerTrackingPolicy.kt');
const merchantApi=read('native-android/merchant/src/main/java/com/queuego/merchant/MerchantApi.kt');
const merchantUi=read('native-android/merchant/src/main/java/com/queuego/merchant/QueueGoMerchantApp.kt');
const riderApi=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoApi.kt');
const riderUi=read('native-android/rider/src/main/java/com/queuego/rider/QueueGoRiderApp.kt');
const riderModels=read('native-android/rider/src/main/java/com/queuego/rider/Models.kt');

let checks=0;
const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(matrix.physical_state_parity==='OPEN','Native source state coverage must never certify physical parity');
assert.deepEqual(matrix.canonical_db_states,[
  'pending','accepted','searching_rider','rider_assigned','preparing','ready',
  'picked_up','in_progress','completed','cancelled','no_rider_available'
]);
checks++;

ok(matrix.compatibility_aliases.assigned==='rider_assigned','assigned alias must map to rider_assigned');
ok(matrix.compatibility_aliases.rider_to_customer==='in_progress','rider_to_customer alias must map to in_progress');
ok(/Derived only/.test(matrix.presentation_only.arrived),'arrived must remain presentation-only');

// Customer
ok(customerApi.includes('&status=in.(pending,accepted,searching_rider,rider_assigned,preparing,ready,assigned,picked_up,in_progress)'),
  'Customer active query must retain every non-terminal normal delivery state');
for(const state of matrix.roles.customer.required_visible_states){
  ok(customerTracking.includes('"'+state+'"'), 'Customer tracking source missing visible state: '+state);
}
ok(customerTracking.includes('order.status.equals("in_progress", true)')&&
   customerTracking.includes('riderArrivedCustomerAt')&&
   customerTracking.includes(') "arrived" else order.status'),
  'Customer arrived must derive from in_progress plus arrival timestamp');
ok(customerPolicy.includes('setOf("completed", "cancelled", "no_rider_available")'),
  'Customer terminal policy must close completed/cancelled/no-rider orders');
ok(!customerApi.includes('status=in.(arrived')&&!customerApi.includes(',arrived'),
  'Customer Data API query must never treat arrived as a database order state');

// Merchant
ok(merchantApi.includes('val riderArrivedCustomerAt: String? = null'),
  'Merchant order model must carry customer arrival timestamp');
ok(merchantApi.includes('orders?select=id,rider_arrived_customer_at&id=in.('),
  'Merchant must enrich arrival from the existing authenticated orders RLS path');
ok(merchantApi.includes('order.copy(riderArrivedCustomerAt = arrivals[order.id])'),
  'Merchant loaded orders must receive the arrival timestamp');
for(const state of matrix.roles.merchant.required_visible_states.filter(s=>s!=='arrived')){
  ok(merchantUi.includes('"'+state+'"'), 'Merchant Native UI missing visible state: '+state);
}
ok(merchantUi.includes('return "Rider ถึงลูกค้าแล้ว"')&&
   merchantUi.includes('!riderArrivedCustomerAt.isNullOrBlank()'),
  'Merchant arrived presentation must derive from a non-null arrival timestamp');
ok((merchantUi.match(/merchantStatus\(order\.status, order\.riderArrivedCustomerAt\)/g)||[]).length>=2,
  'Merchant order card and detail must both use derived arrival status');
ok(merchantUi.includes('"no_rider_available" -> "ไม่พบ Rider"'),
  'Merchant must translate no_rider_available instead of exposing a raw status');
ok(merchantUi.includes('"no_rider_available" -> QgCard')&&merchantUi.includes('ไม่พบ Rider · ออเดอร์สิ้นสุดแล้ว'),
  'Merchant detail must render a terminal no-rider state');
ok(merchantUi.includes('"cancelled" -> QgCard')&&merchantUi.includes('ออเดอร์ถูกยกเลิก'),
  'Merchant detail must render cancelled as a terminal state');
ok(merchantUi.includes('"pending" -> {')&&merchantUi.includes('onAction("accepted", null)')&&merchantUi.includes('Text("ปฏิเสธออเดอร์")'),
  'Merchant pending state must expose accept plus guarded rejection');
ok(merchantUi.includes('"accepted" -> {')&&merchantUi.includes('ร้านรับออเดอร์แล้ว · รอระบบจัดหา Rider')&&merchantUi.includes('Text("ยกเลิกออเดอร์")'),
  'Merchant accepted compatibility state must wait and retain guarded cancellation');
ok(merchantUi.includes('"searching_rider" -> QgCard')&&merchantUi.includes('รอ Rider รับงานก่อนเริ่มเตรียมออเดอร์'),
  'Merchant searching_rider must remain a wait-only state');
ok(merchantUi.includes('"rider_assigned", "assigned" -> {')&&merchantUi.includes('onAction("preparing", null)')&&merchantUi.includes('Text("ยกเลิกออเดอร์")'),
  'Merchant rider-assigned state must expose prepare plus guarded cancellation');
ok(merchantUi.includes('"preparing" -> {')&&merchantUi.includes('onAction("ready", null)'),
  'Merchant preparing state must move to ready');
ok(merchantUi.includes('private fun MerchantCancelGuard('),'Merchant must keep a dedicated cancellation guard');
for(const reason of ['สินค้าหมด','ร้านไม่สามารถจัดเตรียมสินค้าได้','ร้านปิดหรือมีเหตุฉุกเฉิน','ลูกค้าขอให้ยกเลิก','อื่น ๆ']){
  ok(merchantUi.includes('"'+reason+'"'),'Merchant cancellation guard missing reason: '+reason);
}
ok(merchantUi.includes('ฉันตรวจสอบออเดอร์นี้แล้ว')&&merchantUi.includes('Slider(')&&merchantUi.includes('slide >= 95f && ready'),
  'Merchant cancellation must require acknowledgement plus near-complete slide confirmation');
ok(merchantUi.includes('otherReason = it.take(420)'),'Merchant custom cancellation reason must remain length-bounded');
ok(merchantUi.includes('order.status in setOf("pending", "accepted", "rider_assigned", "assigned")'),
  'Merchant cancellation guard must fail closed outside Web-allowed pre-preparation states');
ok(merchantUi.includes('LaunchedEffect(order.status)')&&merchantUi.includes('if (!cancellable) onDismiss()'),
  'Realtime status advancement must dismiss a stale Merchant cancellation guard');
ok(!/"preparing" -> \{[\s\S]{0,900}Text\("ยกเลิกออเดอร์"\)/.test(merchantUi),
  'Merchant cancellation control must stay absent after preparation starts');
ok(!merchantApi.includes('status=in.(arrived')&&!merchantApi.includes(',arrived'),
  'Merchant must never query arrived as a database order state');

// Rider
ok(riderApi.includes('&status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)'),
  'Rider active snapshot must contain only assigned-through-delivery states');
for(const state of matrix.roles.rider.required_active_states){
  ok(riderUi.includes('"'+state+'"'), 'Rider UI missing active state: '+state);
}
ok(riderModels.includes('get() = status == "picked_up" || status == "in_progress"'),
  'Rider delivery navigation target must start only after pickup');
ok(riderModels.includes('get() = !arrivedShopAt.isNullOrBlank()')&&
   riderModels.includes('get() = !arrivedCustomerAt.isNullOrBlank()'),
  'Rider arrival presentation must derive from persisted arrival timestamps');
ok(riderUi.includes('if (!job.customerArrived)')&&riderUi.includes('Text("ถึงลูกค้าแล้ว"'),
  'Rider in-progress state must gate delivery handoff on arrival timestamp');
ok(riderUi.includes('"picked_up" -> {')&&riderUi.includes('Text("เริ่มจัดส่งและนำทาง"'),
  'Rider picked_up state must explicitly start delivery/navigation');
ok(riderUi.includes('"in_progress" -> {')&&riderUi.includes('Text("นำทางไปลูกค้า"'),
  'Rider in_progress state must navigate to the customer');
ok(riderApi.includes('&status=eq.completed&order=updated_at.desc'),
  'Rider history must retain completed jobs outside the active snapshot');
ok(!riderApi.includes('status=in.(arrived')&&!riderApi.includes(',arrived'),
  'Rider Data API query must never treat arrived as a database order state');

console.log(JSON.stringify({checks,failures:0,scope:'Native Customer/Merchant/Rider normal shopping order-state and derived-arrival coverage; physical parity remains OPEN'}));
